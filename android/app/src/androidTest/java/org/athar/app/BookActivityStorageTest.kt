package org.athar.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.gson.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.athar.app.auth.SessionRepository
import org.athar.app.books.*
import org.athar.app.core.data.*
import org.athar.app.core.network.ApiTransport
import org.athar.app.core.security.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class BookActivityStorageTest {
    private class Store : TokenStore {
        var value: StoredSession? = null
        override suspend fun load() = value
        override suspend fun save(session: StoredSession) { value = session }
        override suspend fun clear() { value = null }
    }
    private class Server : ApiTransport {
        val recorded = ConcurrentHashMap<String,String>()
        val bearers = java.util.Collections.synchronizedList(mutableListOf<String>())
        var loseReply = false; var hold = false
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        override suspend fun request(method:String,path:String,body:Map<String,Any?>,query:Map<String,String>,bearer:String?,language:String):JsonElement {
            if (path == "auth/login") {
                val owner = if (body["email"] == "two@example.test") 2 else 1
                return JsonParser.parseString("""{"user":{"id":$owner,"email":"fixture@example.test","email_verified":true},"session":{"id":"s$owner","access_token":"a$owner","refresh_token":"r$owner","expires_in":900,"scope":"full"}}""")
            }
            assertEquals("POST",method);assertEquals("me/activity",path)
            assertEquals("book_download_completed",body["activity_type"])
            val actor=requireNotNull(bearer);require(actor in setOf("a1","a2"));bearers.add(actor)
            val key=actor+":"+body["event_id"];val date=body["activity_date"].toString()
            val previous=recorded.putIfAbsent(key,date);assertTrue(previous==null||previous==date)
            if (hold) { started.complete(Unit); release.await() }
            if (loseReply) { loseReply=false;throw IOException("Committed fixture response lost") }
            return JsonObject().apply{addProperty("recorded",true)}
        }
    }
    private class Fixture : AutoCloseable {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val name="book-activity-fixture-${UUID.randomUUID()}.db"
        val root=File(context.cacheDir,"qa-book-activity-${UUID.randomUUID()}").apply{check(mkdirs())}
        val namespace="123456abcdef"
        val cipher=PrivateCipher();val tokens=Store();val server=Server()
        private fun open()=Room.databaseBuilder(context,AppDatabase::class.java,name).build()
        var db=open();var deletion=AccountDeletionStore(namespace,db,cipher)
        var session=SessionRepository(server,tokens,ownerBlocked={deletion.isBlocked(it)})
        var publicationDay=LocalDate.now(ZoneOffset.UTC).minusDays(1)
        fun files(owner:Long)=BookFiles(File(root,"books"),BookScope(namespace,owner)){book,request,hash->
            cipher.seal(BookCompletionEvent(namespace,owner,book,request,hash,publicationDay).json().toString())
        }
        fun repository()=BookActivityRepository(namespace,db,cipher,session,files={files(it)})
        suspend fun login(owner:Long)=session.login(if(owner==2L)"two@example.test" else "one@example.test","Fixture123!")
        fun publish(owner:Long,book:Int=7):BookCopy {
            val files=files(owner);val request=files.begin(book)
            files.openPart(book,request).use{it.write("%PDF-1.4\nfixture-$request".toByteArray());it.fd.sync()}
            return files.publish(book,request)
        }
        suspend fun reopen() { db.close();db=open();deletion=AccountDeletionStore(namespace,db,cipher);deletion.initialize();session=SessionRepository(server,tokens,ownerBlocked={deletion.isBlocked(it)}) }
        override fun close(){server.release.complete(Unit);db.close();context.deleteDatabase(name);check(root.parentFile==context.cacheDir&&root.name.startsWith("qa-book-activity-"));root.deleteRecursively()}
    }
    @Test fun committedLostReplySurvivesRealRoomReopenAndKeepsOriginalUtcIdAndDate()=runBlocking {
        val f=Fixture()
        try {
            f.login(1);val day=f.publicationDay;val copy=f.publish(1);val repository=f.repository()
            repository.capture(1);val rows=f.db.dao().caches(1,"activity-completion:%");assertEquals(1,rows.size)
            assertFalse(rows.single().payloadCipher.contains(copy.token));assertTrue(f.files(1).completionReceipts().isEmpty())
            f.db.dao().trimContentCache(1,System.currentTimeMillis()+10L*24*60*60*1000,ContentCachePolicy(maxEntries=1,maxBytes=64,maxEntryBytes=64,maxAgeMillis=1))
            assertEquals(rows,f.db.dao().caches(1,"activity-completion:%"))
            f.publicationDay=day.plusDays(2);f.server.loseReply=true
            assertTrue(runCatching{repository.sync(1)}.exceptionOrNull() is IOException)
            assertEquals(rows,f.db.dao().caches(1,"activity-completion:%"));assertEquals(day.toString(),f.server.recorded["a1:${copy.token}"])
            f.reopen();f.login(1);val cold=f.repository();assertTrue(cold.hasPending(1));cold.sync(1)
            assertEquals(1,f.server.recorded.size);assertFalse(cold.hasPending(1));assertTrue(f.files(1).verify(copy))
            assertEquals(day.toString(),f.server.recorded["a1:${copy.token}"])
        } finally {f.close()}
    }
    @Test fun capturedWorkerCanQueueAAfterLoginBButNeverUsesBCredentialsForA()=runBlocking {
        val f=Fixture()
        try {
            f.login(1);val a=f.publish(1)
            f.login(2);val b=f.publish(2);val repository=f.repository();repository.capture(2);repository.capture(1)
            val pendingB=f.db.dao().caches(2,"activity-completion:%")
            assertEquals(1,retainedBookActivity(f.namespace,1,f.db.dao(),f.cipher).size())
            assertTrue(runCatching{repository.sync(1)}.isFailure);assertTrue(f.server.bearers.isEmpty())
            f.login(1);repository.sync(1)
            assertEquals(listOf("a1"),f.server.bearers.toList());assertNotNull(f.server.recorded["a1:${a.token}"])
            assertFalse(f.server.recorded.containsKey("a2:${a.token}"));assertEquals(pendingB,f.db.dao().caches(2,"activity-completion:%"))
            assertTrue(f.files(2).verify(b));assertTrue(f.db.dao().caches(1,"activity-completion:%").isEmpty())
        } finally {f.close()}
    }
    @Test fun accountSwitchAfterRemoteCommitRetainsPendingAAndRetryCannotDuplicateOrTouchB()=runBlocking {
        val f=Fixture()
        try {
            f.login(1);val a=f.publish(1);val repository=f.repository();repository.capture(1)
            val pending=f.db.dao().caches(1,"activity-completion:%");f.server.hold=true
            val request=async {runCatching{repository.sync(1)}}
            withTimeout(10000){f.server.started.await()};f.login(2);f.server.hold=false;f.server.release.complete(Unit)
            assertTrue(request.await().isFailure);assertEquals(pending,f.db.dao().caches(1,"activity-completion:%"))
            assertEquals(2L,f.session.userId);assertEquals(listOf("a1"),f.server.bearers.toList())
            f.login(1);repository.sync(1);assertEquals(1,f.server.recorded.size)
            assertNotNull(f.server.recorded["a1:${a.token}"]);assertTrue(f.db.dao().caches(1,"activity-completion:%").isEmpty())
        } finally {f.close()}
    }
    @Test fun unreadableOrCrossOwnerReceiptsAndDeletionFencesPreserveBytesInsteadOfEmptyingHistory()=runBlocking {
        val f=Fixture()
        try {
            f.login(1);val a=f.publish(1);val repository=f.repository();repository.capture(1)
            val row=f.db.dao().caches(1,"activity-completion:%").single()
            f.login(2);val swapped=row.copy(userId=2);f.db.dao().cache(swapped)
            assertEquals(ProtectedRead.Unreadable,repository.observePending(2).protectedReads().first())
            assertTrue(runCatching{retainedBookActivity(f.namespace,2,f.db.dao(),f.cipher)}.isFailure)
            assertTrue(runCatching{repository.sync(2)}.exceptionOrNull() is BookActivityUnreadable)
            assertTrue(f.server.bearers.isEmpty());assertEquals(swapped,f.db.dao().cache(2,row.cacheKey))
            f.db.dao().removeCache(2,row.cacheKey);f.login(1)
            val later=f.publish(1,8);val intent=f.deletion.prepare(1){f.session.requireVerifiedOwner(1)}
            f.files(1).pauseDeletion(intent.requestId)
            assertTrue(runCatching{repository.capture(1)}.isFailure);assertTrue(runCatching{repository.sync(1)}.isFailure)
            assertEquals(row,f.db.dao().cache(1,row.cacheKey));assertTrue(f.files(2).completionReceipts().isEmpty())
            assertTrue(a.file.exists()&&later.file.exists());assertTrue(f.server.bearers.isEmpty())
        } finally {f.close()}
    }
}
