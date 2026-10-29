package org.athar.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.athar.app.core.data.AppDatabase
import org.junit.Test
import org.junit.Assert.*
import java.util.UUID

class NightOutboxMigrationTest {
    @Test fun versionOneUpgradePreservesPendingCiphertextConflictsAndAllPrivateRows()=runBlocking {
        val name="migration-fixture-${UUID.randomUUID()}.db"
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val original="opaque fixture ciphertext"
        try{
            val schema=InstrumentationRegistry.getInstrumentation().context.assets.open("org.athar.app.core.data.AppDatabase/1.json").bufferedReader().use{com.google.gson.JsonParser.parseReader(it).asJsonObject["database"].asJsonObject}
            val old=object:android.database.sqlite.SQLiteOpenHelper(context,name,null,1){
                override fun onCreate(db:android.database.sqlite.SQLiteDatabase){
                    schema["entities"].asJsonArray.forEach{element->
                        val entity=element.asJsonObject;val table=entity["tableName"].asString
                        db.execSQL(entity["createSql"].asString.replace("\${TABLE_NAME}",table))
                        entity["indices"]?.asJsonArray?.forEach{db.execSQL(it.asJsonObject["createSql"].asString.replace("\${TABLE_NAME}",table))}
                    }
                    schema["setupQueries"].asJsonArray.forEach{db.execSQL(it.asString)}
                }
                override fun onUpgrade(db:android.database.sqlite.SQLiteDatabase,oldVersion:Int,newVersion:Int)=error("Unexpected fixture upgrade")
            }
            old.writableDatabase.apply{
                execSQL("INSERT INTO outbox (sequence,userId,mutationId,sessionId,operation,expectedRevision,payloadCipher,conflict) VALUES (1,1,'mutation','session','put',0,?,1)",arrayOf(original))
                execSQL("INSERT INTO night_sessions VALUES (1,'session','2026-10-24',12,NULL,?,1,0,0,1,1)",arrayOf(original))
                execSQL("INSERT INTO cached_documents VALUES (1,'night-sync-cursor',?,1)",arrayOf(original))
                execSQL("INSERT INTO private_exemptions VALUES (1,?)",arrayOf(original))
            }
            old.close()
            val db=Room.databaseBuilder(context,AppDatabase::class.java,name).addMigrations(AppDatabase.MIGRATION_1_2,AppDatabase.MIGRATION_2_3).build()
            try{
                val dao=db.dao();val pending=dao.pending(1).single()
                assertEquals(3,db.openHelper.writableDatabase.version)
                assertEquals(original,pending.payloadCipher);assertTrue(pending.conflict);assertNull(pending.rejectedStatus)
                assertEquals(original,dao.night(1,"session")!!.payloadCipher)
                assertEquals(original,dao.cache(1,"night-sync-cursor")!!.payloadCipher)
                assertEquals(original,dao.exemptions(1)!!.encryptedPayload)
            }finally{db.close()}
        }finally{context.deleteDatabase(name)}
    }
}
