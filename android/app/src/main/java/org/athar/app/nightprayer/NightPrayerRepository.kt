package org.athar.app.nightprayer

import androidx.room.withTransaction
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.athar.app.auth.SessionRepository
import org.athar.app.core.data.*
import org.athar.app.core.security.PrivateCipher
import org.athar.app.core.network.ApiException
import java.time.LocalDate
import java.util.UUID

/** Every method is scoped to the captured account. Exemptions never enter the outbox. */
class NightPrayerRepository(private val database: AppDatabase, private val session: SessionRepository, private val cipher: PrivateCipher,private val requestSync:(Long)->Unit={}) {
    private val dao = database.dao()
    private val syncLock = Mutex()
    @Volatile private var continuationOwner:Long?=null
    private fun user() = session.requireVerifiedOwner(session.userId)
    private fun verseCount(payload:JsonObject):Int {
        val count=when(payload["entry_type"].asString){
            "range"->Math.addExact(Math.subtractExact(payload["end_index"].asInt,payload["start_index"].asInt),1)
            "manual"->payload["verse_count"].asInt
            else->throw IllegalArgumentException("Unreadable local session")
        }
        require(count>0)
        return count
    }
    private fun read(row:LocalNightSession):JsonObject {
        val payload=JsonParser.parseString(cipher.open(row.payloadCipher)).asJsonObject
        LocalDate.parse(row.nightDate)
        require(payload["night_date"].asString==row.nightDate&&verseCount(payload)==row.verseCount)
        require(payload["measured_seconds"]?.takeUnless{it.isJsonNull}?.asInt==row.measuredSeconds)
        return payload
    }
    fun observe(user: Long) = dao.nights(user).map { rows -> rows.map { it to read(it) } }.flowOn(Dispatchers.Default)
    private fun retainedBody(row:LocalNightSession):JsonObject? {
        if(!row.deleted)return read(row)
        require(row.nightDate.isEmpty()&&row.verseCount==0&&row.measuredSeconds==null)
        val body=JsonParser.parseString(cipher.open(row.payloadCipher)).asJsonObject
        require(body.entrySet().all{it.key in setOf("id","revision","deleted")})
        if(body.size()>0)require(body["id"].asString==row.id&&body["deleted"].asBoolean)
        return null
    }
    fun observeConflicts(user:Long)=dao.observeNightConflictIds(user).map{ids->
        ids.map{id->val row=requireNotNull(dao.night(user,id));NightConflict(row,retainedBody(row))}
    }.flowOn(Dispatchers.IO)

    private fun rejectionKey(mutationId:String)="night-rejection:$mutationId"
    private fun rejectionMeta(status:Int,code:String)=JsonObject().apply{
        addProperty("status",status)
        addProperty("code",code.take(80))
    }
    private suspend fun markRejection(user:Long,mutation:OutboxMutation,status:Int,code:String){
        dao.cache(CachedDocument(user,rejectionKey(mutation.mutationId),cipher.seal(rejectionMeta(status,code).toString()),System.currentTimeMillis()))
    }
    private suspend fun rejectedSession(user:Long,id:String):NightRejection{
        try{
            val local=requireNotNull(dao.night(user,id))
            val body=validateRetained(local)
            var selected:OutboxMutation?=null
            var document:CachedDocument?=null
            for(mutation in dao.sessionMutations(user,id)){
                val candidate=dao.cache(user,rejectionKey(mutation.mutationId))
                if(candidate!=null){selected=mutation;document=candidate;break}
            }
            val mutation=selected?:throw NightRejectionChanged()
            val meta=JsonParser.parseString(cipher.open(requireNotNull(document).payloadCipher)).asJsonObject
            val status=meta["status"].asInt
            val code=meta["code"]?.takeUnless{it.isJsonNull}?.asString.orEmpty()
            require(status in 400..499&&status !in setOf(408,409,425,429))
            require(code.length<=80)
            return NightRejection(local,body,mutation,status,code)
        }catch(failure:CancellationException){throw failure}
        catch(failure:NightRejectionChanged){throw failure}
        catch(_:Exception){throw NightRetainedUnreadable()}
    }
    fun observeRejections(user:Long)=dao.observeNightRejectedSessionIds(user).map{ids->
        val result=ArrayList<NightRejection>(ids.size)
        for(id in ids)result+=rejectedSession(user,id)
        result
    }.flowOn(Dispatchers.IO)

    suspend fun save(payload: JsonObject, id: String = UUID.randomUUID().toString(),expectedOwner:Long?=session.userId) = withContext(Dispatchers.IO) {
        val user = user()
        session.requireVerifiedOwner(expectedOwner)
        database.withTransaction {
            session.requireVerifiedOwner(user)
            val previous = dao.night(user, id)
            check(!dao.hasNightConflict(user,id))
            check(!dao.hasNightRejection(user,id))
            require(previous?.deleted != true)
            if(previous!=null)read(previous)
            val expected = previous?.localRevision ?: 0
            val revision = expected + 1
            val body = payload.deepCopy().apply { remove("id"); remove("revision"); remove("deleted") }
            val mutation = UUID.randomUUID().toString()
            val outgoing = body.deepCopy().apply { addProperty("mutation_id", mutation); addProperty("expected_revision", expected) }
            val count = verseCount(body)
            dao.night(LocalNightSession(user, id, body["night_date"].asString, count, body["measured_seconds"]?.takeUnless { it.isJsonNull }?.asInt, cipher.seal(body.toString()), revision, previous?.serverRevision ?: 0, false, true, System.currentTimeMillis()))
            dao.enqueue(OutboxMutation(userId = user, mutationId = mutation, sessionId = id, operation = "put", expectedRevision = expected, payloadCipher = cipher.seal(outgoing.toString())))
        }
        requestSync(user)
    }

    suspend fun delete(id: String,expectedOwner:Long?=session.userId) = withContext(Dispatchers.IO) {
        val user = user()
        session.requireVerifiedOwner(expectedOwner)
        database.withTransaction {
            session.requireVerifiedOwner(user)
            val previous = requireNotNull(dao.night(user, id)); if (previous.deleted) return@withTransaction
            check(!dao.hasNightConflict(user,id));check(!dao.hasNightRejection(user,id));read(previous)
            val mutation = UUID.randomUUID().toString()
            val body = JsonObject().apply { addProperty("mutation_id", mutation); addProperty("expected_revision", previous.localRevision) }
            dao.night(previous.copy(nightDate = "", verseCount = 0, measuredSeconds = null, payloadCipher = cipher.seal("{}"), localRevision = previous.localRevision + 1, deleted = true, dirty = true))
            dao.enqueue(OutboxMutation(userId = user, mutationId = mutation, sessionId = id, operation = "delete", expectedRevision = previous.localRevision, payloadCipher = cipher.seal(body.toString())))
        }
        requestSync(user)
    }

    suspend fun hasPending(user:Long):Boolean=withContext(Dispatchers.IO){continuationOwner==user||dao.hasEligiblePending(user)}
    suspend fun sync(expectedOwner:Long?=null): Int = syncLock.withLock { withContext(Dispatchers.IO) {
        val user = user()
        if(expectedOwner!=null&&expectedOwner!=user)throw ApiException(401,"account_changed","")
        // A background pull must not silently replace an unreadable retained personal record.
        dao.allNights(user).filter{!it.deleted}.forEach{read(it)}
        var conflicts = 0
        var temporaryFailure:ApiException?=null
        var more: Boolean
        var batches = 0
        do {
            session.requireVerifiedOwner(user)
            val eligible = dao.eligiblePending(user)
            val cursorDoc = dao.cache(user, "night-sync-cursor")
            val cursor = cursorDoc?.let { cipher.open(it.payloadCipher).toLong() } ?: 0L
            val operations = eligible.map { mapOf("id" to it.sessionId, "operation" to it.operation, "data" to JsonParser.parseString(cipher.open(it.payloadCipher))) }
            val reply = session.request("POST", "night-prayer/sync", mapOf("cursor" to cursor, "operations" to operations),expectedUser=user).asJsonObject
            session.requireVerifiedOwner(user)
            database.withTransaction {
                session.requireVerifiedOwner(user)
                reply["results"].asJsonArray.forEachIndexed { index, element ->
                    val result = element.asJsonObject; val mutation = eligible[index]
                    if (result["status"].asString == "applied") {
                        dao.acknowledge(user, mutation.mutationId)
                        dao.night(user, mutation.sessionId)?.let { local -> dao.night(local.copy(serverRevision = result["result"].asJsonObject["revision"].asInt, dirty = dao.pendingCount(user, mutation.sessionId) > 0)) }
                    } else {
                        val error=result["error"].asJsonObject
                        val status=error["status"]?.asInt?:500
                        val code=error["code"]?.takeUnless{it.isJsonNull}?.asString?:"night_sync_rejected"
                        when{
                            status==409->{dao.markConflict(user,mutation.mutationId);conflicts++}
                            status==408||status==425||status==429||status>=500->{
                                if(temporaryFailure==null)temporaryFailure=ApiException(status,code,"")
                            }
                            status in 400..499->markRejection(user,mutation,status,code)
                            else->{if(temporaryFailure==null)temporaryFailure=ApiException(status,code,"")}
                        }
                    }
                }
                reply["changes"].asJsonArray.forEach { element ->
                    val item = element.asJsonObject; val id = item["id"].asString
                    if (dao.pendingCount(user, id) == 0) replaceRemote(user, item)
                }
                dao.cache(CachedDocument(user, "night-sync-cursor", cipher.seal(reply["next_cursor"].asString), System.currentTimeMillis()))
            }
            temporaryFailure?.let{throw it}
            more = reply["has_more"].asBoolean || dao.hasEligiblePending(user)
            batches++
        } while (more && batches < 20)
        continuationOwner=if(more)user else null
        conflicts
    } }

    private suspend fun validateRetained(row:LocalNightSession):JsonObject? {
        try{
        val body=retainedBody(row)
        for(op in dao.sessionMutations(row.userId,row.id)){
            val queued=JsonParser.parseString(cipher.open(op.payloadCipher)).asJsonObject
            require(queued["mutation_id"].asString==op.mutationId&&queued["expected_revision"].toString()==op.expectedRevision.toString())
            require(op.operation in setOf("put","delete"))
        }
            return body
        }catch(failure:CancellationException){throw failure}catch(_:Exception){throw NightRetainedUnreadable()}
    }
    private suspend fun accountVersion(owner:Long,id:String):JsonObject {
        val body=session.request("GET","night-prayer/sessions/$id",expectedUser=owner).asJsonObject
        session.requireVerifiedOwner(owner)
        require(body["id"].asString==id&&body["revision"].asInt>0&&body["deleted"].asJsonPrimitive.isBoolean)
        val deleted=body["deleted"].asBoolean
        val row=LocalNightSession(owner,id,if(deleted)"" else body["night_date"].asString,if(deleted)0 else body["verse_count"].asInt,
            body["measured_seconds"]?.takeUnless{it.isJsonNull}?.asInt,cipher.seal(body.toString()),body["revision"].asInt,body["revision"].asInt,deleted,false,0)
        retainedBody(row)
        return body
    }
    suspend fun reviewConflict(id:String,expectedOwner:Long?=session.userId):NightConflictReview=syncLock.withLock{withContext(Dispatchers.IO){
        val owner=user();check(expectedOwner==owner);check(dao.hasNightConflict(owner,id))
        val local=requireNotNull(dao.night(owner,id));val body=validateRetained(local)
        val account=accountVersion(owner,id)
        if(dao.night(owner,id)!=local)throw NightConflictChanged()
        NightConflictReview(owner,local,body,account)
    }}
    /** Only a reviewed explicit choice can replace this session's queued edits. */
    suspend fun resolveConflict(review:NightConflictReview,choice:NightConflictChoice):String=syncLock.withLock{withContext(Dispatchers.IO){
        val owner=user();check(owner==review.owner)
        val id=review.local.id;val account=accountVersion(owner,id)
        if(account!=review.account)throw NightConflictChanged()
        var resultingId=id
        database.withTransaction{
            session.requireVerifiedOwner(owner)
            val local=requireNotNull(dao.night(owner,id))
            if(local!=review.local||!dao.hasNightConflict(owner,id))throw NightConflictChanged()
            val body=validateRetained(local)
            val removed=account["deleted"].asBoolean
            if(choice==NightConflictChoice.ACCOUNT||(removed&&local.deleted)){
                dao.removeSessionMutations(owner,id);replaceRemote(owner,account)
            }else{
                val copy=removed&&!local.deleted
                val revision=if(copy)0 else account["revision"].asInt
                resultingId=if(copy)UUID.randomUUID().toString()else id
                val mutation=UUID.randomUUID().toString()
                val data=JsonObject()
                if(!local.deleted){
                    val fields=setOf("night_date","timezone","boundary_source","calculation_method","maghrib_at","fajr_at","started_at","ended_at","entry_type","start_index","end_index","verse_count","measured_seconds","note")
                    val source=requireNotNull(body)
                    require(source.entrySet().all{it.key in fields||it.key in setOf("id","revision","deleted","created_at","updated_at")})
                    fields.filter{source.has(it)}.forEach{data.add(it,source[it].deepCopy())}
                }
                val outgoing=data.deepCopy().apply{addProperty("mutation_id",mutation);addProperty("expected_revision",revision)}
                dao.removeSessionMutations(owner,id)
                if(copy)replaceRemote(owner,account)
                dao.night(local.copy(id=resultingId,payloadCipher=cipher.seal(data.toString()),localRevision=revision+1,serverRevision=revision,dirty=true,updatedAt=System.currentTimeMillis()))
                dao.enqueue(OutboxMutation(userId=owner,mutationId=mutation,sessionId=resultingId,operation=if(local.deleted)"delete"else"put",expectedRevision=revision,payloadCipher=cipher.seal(outgoing.toString())))
            }
        }
        requestSync(owner);resultingId
    }}

    private suspend fun accountVersionOrMissing(owner:Long,id:String):JsonObject?=try{
        accountVersion(owner,id)
    }catch(failure:ApiException){
        if(failure.status==404)null else throw failure
    }
    private suspend fun clearRejectionMarkers(owner:Long,id:String){
        for(mutation in dao.sessionMutations(owner,id))dao.removeCache(owner,rejectionKey(mutation.mutationId))
    }
    suspend fun reviewRejection(id:String,expectedOwner:Long?=session.userId):NightRejectionReview=syncLock.withLock{withContext(Dispatchers.IO){
        val owner=user();check(expectedOwner==owner)
        val rejection=rejectedSession(owner,id)
        val account=accountVersionOrMissing(owner,id)
        if(dao.night(owner,id)!=rejection.local||rejectedSession(owner,id)!=rejection)throw NightRejectionChanged()
        NightRejectionReview(owner,rejection,account)
    }}
    suspend fun retryRejection(review:NightRejectionReview)=syncLock.withLock{withContext(Dispatchers.IO){
        val owner=user();check(owner==review.owner)
        val id=review.rejection.local.id
        val account=accountVersionOrMissing(owner,id)
        if(account!=review.account||rejectedSession(owner,id)!=review.rejection)throw NightRejectionChanged()
        database.withTransaction{
            session.requireVerifiedOwner(owner)
            val local=requireNotNull(dao.night(owner,id))
            if(local!=review.rejection.local)throw NightRejectionChanged()
            clearRejectionMarkers(owner,id)
        }
        requestSync(owner)
    }}
    suspend fun acceptAccountRejection(review:NightRejectionReview)=syncLock.withLock{withContext(Dispatchers.IO){
        val owner=user();check(owner==review.owner)
        val id=review.rejection.local.id
        val account=accountVersionOrMissing(owner,id)
        if(account!=review.account||rejectedSession(owner,id)!=review.rejection)throw NightRejectionChanged()
        database.withTransaction{
            session.requireVerifiedOwner(owner)
            val local=requireNotNull(dao.night(owner,id))
            if(local!=review.rejection.local)throw NightRejectionChanged()
            clearRejectionMarkers(owner,id)
            dao.removeSessionMutations(owner,id)
            if(account!=null)replaceRemote(owner,account)
            else if(local.deleted)dao.night(local.copy(serverRevision=0,dirty=false,updatedAt=System.currentTimeMillis()))
            else dao.removeNight(owner,id)
        }
    }}
    private fun repairedBody(payload:JsonObject):JsonObject{
        val allowed=setOf("night_date","timezone","boundary_source","calculation_method","maghrib_at","fajr_at","started_at","ended_at","entry_type","start_index","end_index","verse_count","measured_seconds","note")
        require(payload.entrySet().all{it.key in allowed})
        val body=payload.deepCopy()
        LocalDate.parse(body["night_date"].asString)
        verseCount(body)
        return body
    }
    suspend fun repairRejection(review:NightRejectionReview,payload:JsonObject):String=syncLock.withLock{withContext(Dispatchers.IO){
        val owner=user();check(owner==review.owner)
        val id=review.rejection.local.id
        require(!review.rejection.local.deleted)
        val body=repairedBody(payload)
        val account=accountVersionOrMissing(owner,id)
        if(account!=review.account||rejectedSession(owner,id)!=review.rejection)throw NightRejectionChanged()
        val copy=account?.get("deleted")?.asBoolean==true
        val expected=account?.get("revision")?.asInt?:0
        val resultingId=if(copy)UUID.randomUUID().toString()else id
        val mutation=UUID.randomUUID().toString()
        val outgoing=body.deepCopy().apply{addProperty("mutation_id",mutation);addProperty("expected_revision",expected)}
        database.withTransaction{
            session.requireVerifiedOwner(owner)
            val local=requireNotNull(dao.night(owner,id))
            if(local!=review.rejection.local)throw NightRejectionChanged()
            clearRejectionMarkers(owner,id)
            dao.removeSessionMutations(owner,id)
            if(copy&&account!=null)replaceRemote(owner,account)
            val next=local.copy(
                id=resultingId,
                nightDate=body["night_date"].asString,
                verseCount=verseCount(body),
                measuredSeconds=body["measured_seconds"]?.takeUnless{it.isJsonNull}?.asInt,
                payloadCipher=cipher.seal(body.toString()),
                localRevision=expected+1,
                serverRevision=expected,
                deleted=false,
                dirty=true,
                updatedAt=System.currentTimeMillis()
            )
            dao.night(next)
            dao.enqueue(OutboxMutation(userId=owner,mutationId=mutation,sessionId=resultingId,operation="put",expectedRevision=expected,payloadCipher=cipher.seal(outgoing.toString())))
        }
        requestSync(owner);resultingId
    }}

    private suspend fun replaceRemote(user: Long, item: JsonObject) {
        val deleted = item["deleted"].asBoolean; val revision = item["revision"].asInt
        dao.night(LocalNightSession(user, item["id"].asString, if (deleted) "" else item["night_date"].asString, if (deleted) 0 else item["verse_count"].asInt, item["measured_seconds"]?.takeUnless { it.isJsonNull }?.asInt, cipher.seal(item.toString()), revision, revision, deleted, false, System.currentTimeMillis()))
    }

    suspend fun localExemptions(expectedOwner:Long?=session.userId): Set<LocalDate> = withContext(Dispatchers.IO) {
        val owner=user()
        check(expectedOwner==owner)
        val result=dao.exemptions(owner)?.let { readExemptions(it) }.orEmpty()
        session.requireVerifiedOwner(owner)
        result
    }
    private fun readExemptions(row:PrivateExemptions):Set<LocalDate> = JsonParser.parseString(cipher.open(row.encryptedPayload)).asJsonArray.map { LocalDate.parse(it.asString) }.toSet()
    suspend fun localExemptions(dates: Set<LocalDate>,expectedOwner:Long?=session.userId) = withContext(Dispatchers.IO) {
        require(dates.size <= 3660)
        val owner=user()
        check(expectedOwner==owner)
        dao.exemptions(owner)?.let{readExemptions(it)}
        session.requireVerifiedOwner(owner)
        dao.exemptions(PrivateExemptions(owner, cipher.seal(com.google.gson.Gson().toJson(dates.map { it.toString() }.sorted()))))
    }
}
