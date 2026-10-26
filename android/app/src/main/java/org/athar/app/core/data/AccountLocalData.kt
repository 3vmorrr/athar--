package org.athar.app.core.data

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.flow.first
import org.athar.app.auth.SessionRepository
import org.athar.app.learning.LessonRepository
import org.athar.app.nightprayer.NightPrayerRepository
import java.time.Instant

/** Validate everything before the caller opens/truncates a user-selected export file. */
suspend fun retainedAccountData(owner:Long,session:SessionRepository,lessons:LessonRepository,night:NightPrayerRepository):JsonObject {
    session.requireVerifiedOwner(owner)
    val progress=lessons.retained(owner)
    val nights=JsonArray().apply{night.observe(owner).first().forEach{add(it.second)}}
    session.requireVerifiedOwner(owner)
    return JsonObject().apply{
        add("device_lesson_progress",Gson().toJsonTree(progress))
        add("device_night_sessions",nights)
    }
}

fun accountExportEnvelope(
    owner:Long,
    account:JsonObject,
    device:JsonObject,
    generatedAt:Instant=Instant.now(),
):JsonObject {
    require(AccountPayloads.validExport(owner,account))
    require(device.entrySet().none {
        it.key in setOf(
            "private_exemptions",
            "local_exemptions",
            "device_private_exemptions",
        )
    })
    return JsonObject().apply {
        addProperty("export_version",1)
        addProperty("generated_at",generatedAt.toString())
        addProperty("owner_id",owner)
        add("account",account.deepCopy())
        add("device",device.deepCopy())
        add(
            "privacy",
            JsonObject().apply {
                addProperty("local_private_exemptions_included",false)
                add(
                    "excluded_device_data",
                    JsonArray().apply {
                        add(JsonPrimitive("private_exemptions"))
                    },
                )
            },
        )
    }
}

