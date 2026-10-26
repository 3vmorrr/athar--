package org.athar.app.nightprayer

import com.google.gson.JsonObject
import org.athar.app.core.data.LocalNightSession
import org.athar.app.core.data.OutboxMutation
import java.time.LocalDate
import java.time.ZoneId

enum class NightOperationOutcome { CONFLICT, REJECTED, RETRY }
fun nightOperationOutcome(status:Int):NightOperationOutcome = when {
    status==409 -> NightOperationOutcome.CONFLICT
    status in 400..499 && status !in setOf(401,408,425,429) -> NightOperationOutcome.REJECTED
    else -> NightOperationOutcome.RETRY
}

/** Private, immutable review snapshot; no server error message or private body is logged. */
data class NightRejectionReview(val owner:Long,val local:LocalNightSession,val body:JsonObject?,val mutations:List<OutboxMutation>) {
    val status:Int get()=requireNotNull(mutations.firstNotNullOfOrNull{it.rejectedStatus})
}

/** Repair deliberately uses the manual editor's supported fields; it never drops hidden timing data. */
internal fun validateNightRepair(body:JsonObject) {
    val allowed=setOf("night_date","timezone","boundary_source","entry_type","start_index","end_index","verse_count","note","measured_seconds","calculation_method","maghrib_at","fajr_at","started_at","ended_at")
    require(body.keySet().all{it in allowed})
    fun text(key:String)=body[key].also{require(it!=null&&it.isJsonPrimitive&&it.asJsonPrimitive.isString)}.asString
    fun integer(key:String,min:Int,max:Int):Int {
        val value=body[key];require(value!=null&&value.isJsonPrimitive&&value.asJsonPrimitive.isNumber)
        val number=value.asString.toInt();require(number in min..max);return number
    }
    val zone=ZoneId.of(text("timezone"));require(zone.id=="UTC"||zone.id in ZoneId.getAvailableZoneIds())
    val date=LocalDate.parse(text("night_date"));require(date.year>=1900&&date<=LocalDate.now(zone))
    require(text("boundary_source")=="manual")
    // This repair surface cannot edit calculated/timed sessions. Retain them for explicit retry.
    for(key in setOf("measured_seconds","calculation_method","maghrib_at","fajr_at","started_at","ended_at"))require(!body.has(key)||body[key].isJsonNull)
    when(text("entry_type")) {
        "manual"->{integer("verse_count",1,100000);require((!body.has("start_index")||body["start_index"].isJsonNull)&&(!body.has("end_index")||body["end_index"].isJsonNull))}
        "range"->{val start=integer("start_index",1,6236);val end=integer("end_index",start,6236);if(body.has("verse_count"))require(integer("verse_count",1,6236)==end-start+1)}
        else->throw IllegalArgumentException("Invalid entry type")
    }
    if(body.has("note")){val note=text("note");require(note.codePointCount(0,note.length)<=1000)}
}

fun canRepairNight(review:NightRejectionReview):Boolean = !review.local.deleted&&review.body?.let{body->
    body["boundary_source"]?.asString=="manual"&&setOf("measured_seconds","calculation_method","maghrib_at","fajr_at","started_at","ended_at").all{!body.has(it)||body[it].isJsonNull}
}==true
