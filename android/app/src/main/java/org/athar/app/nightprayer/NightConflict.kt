package org.athar.app.nightprayer

import com.google.gson.JsonObject
import org.athar.app.core.data.LocalNightSession

/** Owner-bound private review; never saved in navigation arguments or telemetry. */
data class NightConflict(val local:LocalNightSession,val body:JsonObject?)
data class NightConflictReview(val owner:Long,val local:LocalNightSession,val localBody:JsonObject?,val account:JsonObject)
enum class NightConflictChoice { ACCOUNT, DEVICE }
class NightConflictChanged:IllegalStateException("Conflict review changed")
class NightRetainedUnreadable:IllegalStateException("Retained pending data is unreadable")
