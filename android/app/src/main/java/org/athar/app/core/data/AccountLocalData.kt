package org.athar.app.core.data

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.first
import org.athar.app.auth.SessionRepository
import org.athar.app.learning.LessonRepository
import org.athar.app.nightprayer.NightPrayerRepository

/** Validate everything before the caller opens/truncates a user-selected export file. */
suspend fun retainedAccountData(owner:Long,session:SessionRepository,lessons:LessonRepository,night:NightPrayerRepository):JsonObject {
    check(session.userId==owner)
    val progress=lessons.retained(owner)
    val nights=JsonArray().apply{night.observe(owner).first().forEach{add(it.second)}}
    check(session.userId==owner)
    return JsonObject().apply{
        add("device_lesson_progress",Gson().toJsonTree(progress))
        add("device_night_sessions",nights)
    }
}
