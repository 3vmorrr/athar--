package org.athar.app.nightprayer

import com.google.gson.JsonObject
import org.athar.app.core.data.LocalNightSession
import org.athar.app.core.data.OutboxMutation

/** Owner-bound private review; never saved in navigation arguments or telemetry. */
data class NightConflict(val local:LocalNightSession,val body:JsonObject?)
data class NightConflictReview(val owner:Long,val local:LocalNightSession,val localBody:JsonObject?,val account:JsonObject)
enum class NightConflictChoice { ACCOUNT, DEVICE }
class NightConflictChanged:IllegalStateException("Conflict review changed")
class NightRetainedUnreadable:IllegalStateException("Retained pending data is unreadable")

/** Durable non-conflict operation rejection. Original encrypted outbox bytes stay untouched until explicit recovery. */
data class NightRejection(val local:LocalNightSession,val body:JsonObject?,val mutation:OutboxMutation,val status:Int,val code:String)
data class NightRejectionReview(val owner:Long,val rejection:NightRejection,val account:JsonObject?)
class NightRejectionChanged:IllegalStateException("Rejected operation review changed")
