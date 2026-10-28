package org.athar.app.core.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "cached_documents", primaryKeys = ["userId", "cacheKey"])
data class CachedDocument(val userId: Long, val cacheKey: String, val payloadCipher: String, val updatedAt: Long)
@Entity(tableName = "night_sessions", primaryKeys = ["userId", "id"], indices = [Index(value = ["userId", "nightDate"])])
data class LocalNightSession(val userId: Long, val id: String, val nightDate: String, val verseCount: Int, val measuredSeconds: Int?, val payloadCipher: String, val localRevision: Int, val serverRevision: Int, val deleted: Boolean, val dirty: Boolean, val updatedAt: Long)
@Entity(tableName = "outbox", indices = [Index(value = ["userId", "mutationId"], unique = true), Index(value = ["userId", "sessionId"])])
data class OutboxMutation(@PrimaryKey(autoGenerate = true) val sequence: Long = 0, val userId: Long, val mutationId: String, val sessionId: String, val operation: String, val expectedRevision: Int, val payloadCipher: String, val conflict: Boolean = false, val rejectedStatus: Int? = null)
@Entity(tableName = "private_exemptions", primaryKeys = ["userId"])
data class PrivateExemptions(val userId: Long, val encryptedPayload: String)

@Dao
interface AtharDao {
    @Query("SELECT * FROM cached_documents WHERE userId=:user AND cacheKey=:key") suspend fun cache(user: Long, key: String): CachedDocument?
    @Query("SELECT * FROM cached_documents WHERE userId=:user AND cacheKey=:key") fun observeCache(user: Long, key: String): Flow<CachedDocument?>
    @Query("SELECT * FROM cached_documents WHERE userId=:user AND cacheKey LIKE :prefix") suspend fun caches(user: Long, prefix: String): List<CachedDocument>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun cache(document: CachedDocument)
    @Query("DELETE FROM cached_documents WHERE userId=:user AND cacheKey=:key") suspend fun removeCache(user: Long, key: String)
    @Query("SELECT cacheKey,updatedAt,LENGTH(payloadCipher) AS bytes FROM cached_documents WHERE userId=:user AND (cacheKey LIKE 'ar:%' OR cacheKey LIKE 'en:%')") suspend fun contentCacheEntries(user:Long):List<ContentCacheEntry>
    @Transaction suspend fun trimContentCache(user:Long,now:Long,policy:ContentCachePolicy){
        policy.evictions(contentCacheEntries(user),now).forEach{removeCache(user,it)}
    }
    @Transaction suspend fun storeContent(document:CachedDocument,now:Long,policy:ContentCachePolicy){
        require(policy.disposable(document.cacheKey))
        if(document.payloadCipher.length.toLong()<=policy.maxEntryBytes)cache(document)
        else removeCache(document.userId,document.cacheKey)
        trimContentCache(document.userId,now,policy)
    }
    @Query("SELECT * FROM night_sessions WHERE userId=:user AND deleted=0 ORDER BY nightDate DESC,updatedAt DESC") fun nights(user: Long): Flow<List<LocalNightSession>>
    @Query("SELECT * FROM night_sessions WHERE userId=:user") suspend fun allNights(user: Long): List<LocalNightSession>
    @Query("SELECT * FROM night_sessions WHERE userId=:user AND id=:id") suspend fun night(user: Long, id: String): LocalNightSession?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun night(session: LocalNightSession)
    @Insert suspend fun enqueue(mutation: OutboxMutation)
    @Query("SELECT * FROM outbox WHERE userId=:user ORDER BY sequence LIMIT 50") suspend fun pending(user: Long): List<OutboxMutation>
    @Query("SELECT * FROM outbox WHERE userId=:user ORDER BY sequence") suspend fun allMutations(user: Long): List<OutboxMutation>
    @Query("SELECT o.* FROM outbox o WHERE o.userId=:user AND NOT EXISTS (SELECT 1 FROM outbox c WHERE c.userId=o.userId AND c.sessionId=o.sessionId AND (c.conflict=1 OR c.rejectedStatus IS NOT NULL OR c.sequence<o.sequence)) ORDER BY o.sequence LIMIT 50") suspend fun eligiblePending(user:Long):List<OutboxMutation>
    @Query("SELECT EXISTS (SELECT 1 FROM outbox o WHERE o.userId=:user AND NOT EXISTS (SELECT 1 FROM outbox c WHERE c.userId=o.userId AND c.sessionId=o.sessionId AND (c.conflict=1 OR c.rejectedStatus IS NOT NULL)))") suspend fun hasEligiblePending(user:Long):Boolean
    @Query("SELECT DISTINCT sessionId FROM outbox WHERE userId=:user AND rejectedStatus IS NOT NULL ORDER BY sequence") fun observeNightRejectionIds(user:Long):Flow<List<String>>
    @Query("SELECT EXISTS (SELECT 1 FROM outbox WHERE userId=:user AND sessionId=:id AND rejectedStatus IS NOT NULL)") suspend fun hasNightRejection(user:Long,id:String):Boolean
    @Query("UPDATE outbox SET rejectedStatus=:status WHERE userId=:user AND mutationId=:id") suspend fun markRejected(user:Long,id:String,status:Int)
    @Query("UPDATE outbox SET rejectedStatus=NULL WHERE userId=:user AND sessionId=:id") suspend fun retryRejected(user:Long,id:String)
    @Query("SELECT o.sessionId FROM outbox o LEFT JOIN night_sessions n ON n.userId=o.userId AND n.id=o.sessionId WHERE o.userId=:user AND o.conflict=1 GROUP BY o.sessionId ORDER BY MIN(o.sequence)") fun observeNightConflictIds(user:Long):Flow<List<String>>
    @Query("SELECT EXISTS (SELECT 1 FROM outbox WHERE userId=:user AND sessionId=:id AND conflict=1)") suspend fun hasNightConflict(user:Long,id:String):Boolean
    @Query("SELECT * FROM outbox WHERE userId=:user AND sessionId=:id ORDER BY sequence") suspend fun sessionMutations(user:Long,id:String):List<OutboxMutation>
    @Query("SELECT COUNT(*) FROM outbox WHERE userId=:user AND sessionId=:id") suspend fun pendingCount(user: Long, id: String): Int
    @Query("DELETE FROM outbox WHERE userId=:user AND mutationId=:id") suspend fun acknowledge(user: Long, id: String)
    @Query("DELETE FROM outbox WHERE userId=:user AND sessionId=:id") suspend fun removeSessionMutations(user: Long, id: String)
    @Query("UPDATE outbox SET conflict=1 WHERE userId=:user AND mutationId=:id") suspend fun markConflict(user: Long, id: String)
    @Query("SELECT * FROM private_exemptions WHERE userId=:user") suspend fun exemptions(user: Long): PrivateExemptions?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun exemptions(value: PrivateExemptions)
    @Query("DELETE FROM private_exemptions WHERE userId=:user") suspend fun deleteExemptions(user: Long)
    @Query("DELETE FROM cached_documents WHERE userId=:user") suspend fun deleteCache(user: Long)
    @Query("DELETE FROM night_sessions WHERE userId=:user") suspend fun deleteNights(user: Long)
    @Query("DELETE FROM outbox WHERE userId=:user") suspend fun deleteOutbox(user: Long)
    @Transaction suspend fun deleteUser(user: Long) { deleteCache(user); deleteNights(user); deleteOutbox(user); deleteExemptions(user) }
}

@Database(entities = [CachedDocument::class, LocalNightSession::class, OutboxMutation::class, PrivateExemptions::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AtharDao
    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1,2) {
            override fun migrate(db:androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE outbox ADD COLUMN rejectedStatus INTEGER")
            }
        }
    }
}
