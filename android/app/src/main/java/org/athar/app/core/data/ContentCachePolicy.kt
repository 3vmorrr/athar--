package org.athar.app.core.data

/** Applies only to disposable localized HTTP documents, never private progress/outbox rows. */
data class ContentCacheEntry(val cacheKey:String,val updatedAt:Long,val bytes:Long)

class ContentCachePolicy(
    val maxEntries:Int=200,
    val maxBytes:Long=8L*1024*1024,
    val maxEntryBytes:Long=1024L*1024,
    val maxAgeMillis:Long=7L*24*60*60*1000
) {
    init{require(maxEntries>0&&maxBytes>0&&maxEntryBytes in 1..maxBytes&&maxAgeMillis>0)}
    fun disposable(key:String)=key.startsWith("ar:")||key.startsWith("en:")
    fun fresh(updatedAt:Long,now:Long)=updatedAt>=0&&updatedAt<=now&&now-updatedAt<=maxAgeMillis
    fun evictions(entries:List<ContentCacheEntry>,now:Long):Set<String> {
        val remove=linkedSetOf<String>();var count=0;var bytes=0L
        entries.filter{disposable(it.cacheKey)}.sortedWith(compareByDescending<ContentCacheEntry>{it.updatedAt}.thenBy{it.cacheKey}).forEach{
            if(!fresh(it.updatedAt,now)||it.bytes !in 1..maxEntryBytes||count>=maxEntries||it.bytes>maxBytes-bytes)remove+=it.cacheKey
            else{count++;bytes+=it.bytes}
        }
        return remove
    }
}
