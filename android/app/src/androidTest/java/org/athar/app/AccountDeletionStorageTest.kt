package org.athar.app

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import org.athar.app.core.data.*
import org.athar.app.core.security.PrivateCipher
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class AccountDeletionStorageTest {
    @Test fun encryptedIntentSurvivesReopenRejectsSwapsAndKeepsAPermanentFenceAfterCleanup() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val name="deletion-journal-${UUID.randomUUID()}.db"
        fun open()=Room.databaseBuilder(context,AppDatabase::class.java,name).build()
        var db=open();val cipher=PrivateCipher();val namespace="123456abcdef"
        try {
            var store=AccountDeletionStore(namespace,db,cipher)
            val prepared=store.prepare(1){}
            assertTrue(store.isBlocked(1))
            val encrypted=checkNotNull(db.dao().deletion(1)).payloadCipher!!
            assertFalse(encrypted.contains(prepared.secret));assertFalse(encrypted.contains(prepared.requestId))
            val pending=store.transition(prepared,DeletionPhase.PENDING)
            db.close();db=open();store=AccountDeletionStore(namespace,db,cipher);store.initialize()
            assertEquals(pending,store.intent(1));assertTrue(store.isBlocked(1))
            val original=checkNotNull(db.dao().deletion(1))
            val swapped=original.copy(userId=2);db.dao().deletion(swapped)
            assertTrue(runCatching{store.intent(2)}.isFailure);assertEquals(swapped,db.dao().deletion(2))
            db.dao().abortDeletion(2,swapped.requestId)
            db.dao().deletion(original.copy(state="rejected"))
            assertTrue(runCatching{store.intent(1)}.isFailure)
            assertEquals(original.payloadCipher,db.dao().deletion(1)!!.payloadCipher)
            db.dao().deletion(original)
            assertTrue(runCatching{AccountDeletionStore("abcdef123456",db,cipher).intent(1)}.isFailure)
            assertTrue(runCatching{store.prepare(3){error("actor changed fixture")}}.isFailure)
            assertNull(db.dao().deletion(3))
            val confirmed=store.transition(pending,DeletionPhase.CONFIRMED)
            db.dao().deleteUser(1)
            val cleaned=store.transition(confirmed,DeletionPhase.CLEANED);store.forgetCapability(cleaned)
            assertNull(db.dao().deletion(1)!!.payloadCipher)
            db.close();db=open();store=AccountDeletionStore(namespace,db,cipher);store.initialize()
            assertTrue(store.isBlocked(1));assertNull(store.intent(1))
            assertTrue(runCatching{db.dao().cache(CachedDocument(1,"late","cipher",1))}.exceptionOrNull() is AccountDeletionPending)
            db.dao().cache(CachedDocument(2,"keep","other-owner",1))
            val rejected=store.transition(store.transition(store.prepare(4){},DeletionPhase.PENDING),DeletionPhase.REJECTED)
            store.removeRejected(rejected);assertFalse(store.isBlocked(4))
            assertEquals("other-owner",db.dao().cache(2,"keep")!!.payloadCipher)
        } finally { db.close();context.deleteDatabase(name) }
    }
    @Test fun durableFenceBlocksQueuedLateWritesAndNeverTouchesAnotherOwner() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.dao(); val cipher = PrivateCipher()
            val first = CachedDocument(1, "owned", cipher.seal("private one"), 1)
            val other = CachedDocument(2, "owned", cipher.seal("private two"), 1)
            dao.cache(first); dao.cache(other)
            val record = LocalAccountDeletion(1, UUID.randomUUID().toString(), "pending", cipher.seal("recovery fixture"), 1)
            dao.deletion(record)
            assertTrue(runCatching { dao.cache(first.copy(updatedAt = 2)) }.exceptionOrNull() is AccountDeletionPending)
            assertTrue(runCatching { dao.night(LocalNightSession(1,"session","2026-01-01",1,null,"cipher",1,0,false,true,1)) }.exceptionOrNull() is AccountDeletionPending)
            assertTrue(runCatching { dao.enqueue(OutboxMutation(userId=1,mutationId="mutation",sessionId="session",operation="put",expectedRevision=0,payloadCipher="cipher")) }.exceptionOrNull() is AccountDeletionPending)
            assertTrue(runCatching { dao.exemptions(PrivateExemptions(1,"private")) }.exceptionOrNull() is AccountDeletionPending)
            assertEquals(first,dao.cache(1,"owned")); assertEquals(other,dao.cache(2,"owned"))
            dao.abortDeletion(1,UUID.randomUUID().toString()); assertEquals(record,dao.deletion(1))
            dao.abortDeletion(1,record.requestId); dao.cache(first.copy(updatedAt=3))
            assertNull(dao.deletion(1)); assertEquals(3L,dao.cache(1,"owned")!!.updatedAt)
            val entered=CompletableDeferred<Unit>(); val release=CompletableDeferred<Unit>()
            val cleanup=async(Dispatchers.IO){db.withTransaction{
                dao.deletion(record.copy(state="confirmed"));dao.deleteUser(1)
                entered.complete(Unit);release.await()
            }}
            entered.await()
            val late=async(Dispatchers.IO){runCatching{dao.cache(first)}}
            yield();release.complete(Unit);cleanup.await()
            assertTrue(late.await().exceptionOrNull() is AccountDeletionPending)
            assertNull(dao.cache(1,"owned")); assertNotNull(dao.deletion(1))
            dao.abortDeletion(1,record.requestId); assertNotNull(dao.deletion(1))
            dao.cache(other.copy(updatedAt=4));assertEquals(4L,dao.cache(2,"owned")!!.updatedAt)
        } finally { db.close() }
    }

    @Test fun versionTwoUpgradePreservesPrivateRowsAndAddsAnEmptyDeletionJournal() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val name="deletion-migration-${UUID.randomUUID()}.db"
        try {
            val schema=InstrumentationRegistry.getInstrumentation().context.assets.open("org.athar.app.core.data.AppDatabase/2.json").bufferedReader().use{JsonParser.parseReader(it).asJsonObject["database"].asJsonObject}
            val old=object:android.database.sqlite.SQLiteOpenHelper(context,name,null,2){
                override fun onCreate(db:android.database.sqlite.SQLiteDatabase){
                    schema["entities"].asJsonArray.forEach{element->val row=element.asJsonObject;val table=row["tableName"].asString
                        db.execSQL(row["createSql"].asString.replace("\${TABLE_NAME}",table))
                        row["indices"]?.asJsonArray?.forEach{db.execSQL(it.asJsonObject["createSql"].asString.replace("\${TABLE_NAME}",table))}
                    }
                    schema["setupQueries"].asJsonArray.forEach{db.execSQL(it.asString)}
                }
                override fun onUpgrade(db:android.database.sqlite.SQLiteDatabase,oldVersion:Int,newVersion:Int)=error("Unexpected fixture upgrade")
            }
            old.writableDatabase.execSQL("INSERT INTO cached_documents VALUES(1,'owned','opaque retained ciphertext',123)")
            old.writableDatabase.execSQL("INSERT INTO private_exemptions VALUES(1,'opaque exemptions')")
            old.close()
            val db=Room.databaseBuilder(context,AppDatabase::class.java,name).addMigrations(AppDatabase.MIGRATION_2_3).build()
            try {
                assertEquals("opaque retained ciphertext",db.dao().cache(1,"owned")!!.payloadCipher)
                assertEquals("opaque exemptions",db.dao().exemptions(1)!!.encryptedPayload)
                assertEquals(3,db.openHelper.writableDatabase.version);assertTrue(db.dao().allDeletions().isEmpty())
            } finally { db.close() }
        } finally { context.deleteDatabase(name) }
    }
}
