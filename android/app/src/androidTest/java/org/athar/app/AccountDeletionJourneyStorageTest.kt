package org.athar.app

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.gson.*
import kotlinx.coroutines.runBlocking
import org.athar.app.auth.*
import org.athar.app.books.*
import org.athar.app.core.data.*
import org.athar.app.core.network.*
import org.athar.app.core.security.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.UUID

class AccountDeletionJourneyStorageTest {
    private class Server : ApiTransport {
        var lostReply=false;var proofOffline=false;var receipt=false;var requestId="";var secret="";var deletes=0
        override suspend fun request(method:String,path:String,body:Map<String,Any?>,query:Map<String,String>,bearer:String?,language:String):JsonElement {
            if(path=="auth/login"){
                val id=if((body["email"] as String).startsWith("two"))2 else 1
                return JsonParser.parseString("""{"user":{"id":$id,"email":"fixture@example.test","email_verified":true},"session":{"id":"s$id","access_token":"a$id","refresh_token":"r$id","expires_in":900,"scope":"full"}}""")
            }
            if(path=="me"&&method=="DELETE"){
                deletes++;check(bearer=="a1")
                if(body["password"]!="correct fixture")throw ApiException(422,"invalid_credentials","")
                requestId=body["request_id"] as String;secret=body["recovery_secret"] as String;receipt=true
                if(lostReply)throw IOException("response lost after fixture commit")
                return JsonParser.parseString("{\"status\":\"deleted\"}")
            }
            check(path=="auth/deletion-receipt"&&bearer==null)
            if(proofOffline)throw IOException("fixture proof unavailable")
            if(!receipt||body["request_id"]!=requestId||body["recovery_secret"]!=secret)throw ApiException(404,"not_found","")
            if(method=="DELETE"){receipt=false;return JsonParser.parseString("{\"status\":\"forgotten\"}")}
            return JsonParser.parseString("""{"status":"deleted","request_id":"$requestId","owner_id":1}""")
        }
    }
    @Test fun lostCommittedResponseSurvivesReopenAndPurgesOnlyAWhileColdCredentialsAndBStayCorrect()=runBlocking {
        val base=ApplicationProvider.getApplicationContext<Context>()
        val root=File(base.cacheDir,"qa-deletion-flow-${UUID.randomUUID()}");check(root.mkdirs())
        val context=object:ContextWrapper(base){
            override fun getFilesDir()=root
            override fun getNoBackupFilesDir()=File(root,"no-backup").apply{check(isDirectory||mkdirs())}
            override fun getApplicationContext():Context=this
        }
        val name="deletion-flow-${UUID.randomUUID()}.db"
        fun open()=Room.databaseBuilder(base,AppDatabase::class.java,name).build()
        var db=open();val cipher=PrivateCipher();val namespace="123456abcdef";val credentials=KeystoreTokenStore(context,cipher,namespace=namespace);val server=Server()
        var store=AccountDeletionStore(namespace,db,cipher)
        fun session()=SessionRepository(server,credentials,ownerBlocked={store.isBlocked(it)})
        var session=session()
        fun coordinator()=AccountDeletionCoordinator(context,namespace,db,session,store,persistentWork=false)
        var coordinator=coordinator()
        fun copy(owner:Long):BookCopy{
            val files=BookFiles(File(root,"books"),BookScope(namespace,owner));val request=files.begin(7)
            files.openPart(7,request).use{it.write("%PDF-1.4\nprivate fixture".toByteArray());it.fd.sync()}
            return files.publish(7,request)
        }
        try {
            session.login("one@example.test","correct fixture")
            val a=CachedDocument(1,"owned",cipher.seal("private A"),1);val b=CachedDocument(2,"owned",cipher.seal("private B"),1)
            db.dao().cache(a);db.dao().cache(b);val copyA=copy(1);val copyB=copy(2)
            val exemptA=PrivateExemptions(1,cipher.seal("private exemption A"));val exemptB=PrivateExemptions(2,cipher.seal("private exemption B"))
            db.dao().exemptions(exemptA);db.dao().exemptions(exemptB)
            val nightA=LocalNightSession(1,"night-a","2026-01-01",1,null,cipher.seal("private night A"),1,0,false,true,1)
            val nightB=nightA.copy(userId=2,id="night-b",payloadCipher=cipher.seal("private night B"))
            db.dao().night(nightA);db.dao().night(nightB)
            db.dao().enqueue(OutboxMutation(userId=1,mutationId=UUID.randomUUID().toString(),sessionId="night-a",operation="put",expectedRevision=0,payloadCipher=cipher.seal("pending A")))
            db.dao().enqueue(OutboxMutation(userId=2,mutationId=UUID.randomUUID().toString(),sessionId="night-b",operation="put",expectedRevision=0,payloadCipher=cipher.seal("pending B")))
            val queuedB=db.dao().allMutations(2)
            assertTrue(runCatching{coordinator.delete(1,"wrong fixture")}.exceptionOrNull() is ApiException)
            assertFalse(store.isBlocked(1));assertEquals(a,db.dao().cache(1,"owned"));assertTrue(copyA.file.isFile)
            server.lostReply=true;server.proofOffline=true
            assertTrue(runCatching{coordinator.delete(1,"correct fixture")}.exceptionOrNull() is DeletionUnconfirmed)
            assertEquals(DeletionPhase.PENDING,store.intent(1)!!.phase)
            assertEquals(a,db.dao().cache(1,"owned"));assertEquals(b,db.dao().cache(2,"owned"))
            assertEquals(exemptA,db.dao().exemptions(1));assertEquals(nightA,db.dao().night(1,"night-a"))
            assertTrue(copyA.file.isFile&&copyB.file.isFile);assertNotNull(credentials.load())
            val encrypted=checkNotNull(db.dao().deletion(1)).payloadCipher!!
            assertFalse(encrypted.contains(server.secret));assertFalse(encrypted.contains("correct fixture"))
            db.close();db=open();store=AccountDeletionStore(namespace,db,cipher);session=session();coordinator=coordinator()
            assertNull(session.userId);server.proofOffline=false
            assertEquals(DeletionRecoveryResult.COMPLETE,coordinator.recover(1))
            assertNull(db.dao().cache(1,"owned"));assertFalse(copyA.file.exists())
            assertNull(db.dao().exemptions(1));assertNull(db.dao().night(1,"night-a"));assertTrue(db.dao().allMutations(1).isEmpty())
            assertEquals(b,db.dao().cache(2,"owned"));assertTrue(copyB.file.exists())
            assertEquals(exemptB,db.dao().exemptions(2));assertEquals(nightB,db.dao().night(2,"night-b"));assertEquals(queuedB,db.dao().allMutations(2))
            assertNull(credentials.load());assertEquals(AuthState.SignedOut,session.state.value)
            assertTrue(store.isBlocked(1));assertNull(db.dao().deletion(1)!!.payloadCipher);assertFalse(server.receipt)
            assertTrue(runCatching{db.dao().cache(a)}.exceptionOrNull() is AccountDeletionPending)
            session.login("two@example.test","correct fixture");val before=credentials.load()
            assertEquals(DeletionRecoveryResult.COMPLETE,coordinator.recover(1));assertEquals(before,credentials.load())
            assertEquals(2,server.deletes);assertEquals(2L,session.userId)
        } finally {
            db.close();base.deleteDatabase(name)
            check(root.parentFile==base.cacheDir&&root.name.startsWith("qa-deletion-flow-"));root.deleteRecursively()
        }
    }
}
