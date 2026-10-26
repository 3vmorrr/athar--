package org.athar.app.learning

import com.google.gson.JsonElement
import org.athar.app.core.ui.text
import java.time.Duration
import java.time.Instant

enum class EventStatus { DRAFT, SCHEDULED, LIVE, PUBLISHED, CANCELLED }
enum class CountdownUnit { MINUTES, HOURS, DAYS, AWAITING_START }
data class EventCountdown(val amount:Int,val unit:CountdownUnit)
data class EventBook(val id:Int,val title:String,val pageStart:Int?,val pageEnd:Int?)

fun eventCountdown(start:Instant,now:Instant):EventCountdown {
    val remaining=Duration.between(now,start)
    if(remaining.isNegative||remaining.isZero)return EventCountdown(0,CountdownUnit.AWAITING_START)
    val seconds=remaining.seconds+if(remaining.nano>0)1 else 0
    val minutes=(seconds+59)/60
    return when {
        minutes<60 -> EventCountdown(minutes.toInt(),CountdownUnit.MINUTES)
        minutes<1440 -> EventCountdown(((minutes+59)/60).toInt(),CountdownUnit.HOURS)
        else -> EventCountdown(((minutes+1439)/1440).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),CountdownUnit.DAYS)
    }
}

data class UpcomingLesson(
    val id:Int,val title:String,val description:String,val status:EventStatus,val start:Instant?,
    val scholarId:Int?,val scholarName:String,val seriesId:Int?,val seriesName:String,
    val durationMinutes:Int?,val revision:Int,val canWatch:Boolean,
    val courseTitle:String="",val topics:List<String> = emptyList(),val relatedBook:EventBook?=null,val youtubeId:String="",
    val eventId:Long?=null,val scheduleTimezone:String="UTC",val remindersEnabled:Boolean=false,
) {
    companion object {
        fun from(value:JsonElement):UpcomingLesson {
            val status=EventStatus.valueOf(value.text("status").uppercase(java.util.Locale.ROOT))
            return UpcomingLesson(value.text("id").toInt(),value.text("title"),value.text("description"),status,
                value.text("scheduled_at").takeIf(String::isNotBlank)?.let(Instant::parse),
                value.text("scholar_id").toIntOrNull(),value.text("scholar_name"),value.text("series_id").toIntOrNull(),value.text("series_title"),
                value.text("estimated_duration_minutes").toIntOrNull(),value.text("revision").toInt(),
                value.text("can_watch")=="true"&&status in setOf(EventStatus.LIVE,EventStatus.PUBLISHED),
                value.text("course_title"),value.asJsonObject["topics"]?.takeIf{it.isJsonArray}?.asJsonArray?.map{it.asString}.orEmpty(),
                value.asJsonObject["related_book"]?.takeUnless{it.isJsonNull}?.let{EventBook(it.text("id").toInt(),it.text("title"),it.text("page_start").toIntOrNull(),it.text("page_end").toIntOrNull())},value.text("youtube_id"),
                value.text("event_id").toLongOrNull(),value.text("schedule_timezone").ifBlank{"UTC"},value.text("reminders_enabled")=="true")
        }
    }
}
