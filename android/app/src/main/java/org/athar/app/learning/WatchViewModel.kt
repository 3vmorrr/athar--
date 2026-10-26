package org.athar.app.learning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.athar.app.AppGraph
import org.athar.app.core.ui.text

data class WatchState(val lesson: JsonObject? = null, val loading: Boolean = true, val offline: Boolean = false, val error: Boolean = false, val resume: Int = 0, val completed: Boolean = false, val retry: Int = 0, val ended: Boolean = false)
data class EmbedState(val status: String = "loading", val errorCode: Int? = null)
class WatchViewModel(private val graph: AppGraph, initial: Int) : ViewModel() {
    private val mutable = MutableStateFlow(WatchState()); val state = mutable.asStateFlow()
    private val embed = MutableStateFlow(EmbedState()); val player = embed.asStateFlow()
    private var load: Job? = null; private var generation = 0
    private var requestedLesson = initial
    private var position = 0; private var duration = 0; private var hasPlayed = false; private var savedPosition = -10
    init { select(initial) }
    fun select(id: Int) {
        requestedLesson = id
        flush(); val epoch = ++generation; load?.cancel()
        mutable.value = mutable.value.copy(loading = true, error = false)
        load = viewModelScope.launch {
            try {
                val result = graph.content.get("lessons/$id/watch")
                val lesson = result.data.asJsonObject
                val local = graph.lessons.saved(requireNotNull(graph.session.userId), id)
                val server = lesson["progress"]?.takeUnless { it.isJsonNull }?.asJsonObject
                if (epoch != generation) return@launch
                position = if (local != null && (local.pending || result.offline)) local.positionSeconds else server?.get("position_seconds")?.asInt ?: 0
                duration = if (local != null && (local.pending || result.offline)) local.durationSeconds else server?.get("duration_seconds")?.asInt ?: 0
                hasPlayed = false; savedPosition = position
                mutable.value = WatchState(lesson, false, result.offline, resume = position, completed = local?.complete == true || server?.get("is_complete")?.asInt == 1)
                embed.value = EmbedState()
            } catch (e: CancellationException) { throw e } catch (_: Exception) { if (epoch == generation) mutable.value = mutable.value.copy(loading = false, error = true) }
        }
    }
    fun event(video: String, type: String, seconds: Int, length: Int, code: Int) {
        val current = mutable.value
        if (current.loading || video != current.lesson?.text("youtube_id")) return
        position = if (length > 0) seconds.coerceAtMost(length) else seconds
        duration = length
        when (type) {
            "ready" -> embed.value = EmbedState("ready")
            "error" -> embed.value = EmbedState("error", code)
            "state" -> {
                embed.value = EmbedState(when(code) { 1 -> "playing"; 2 -> "paused"; 3 -> "buffering"; 0 -> "ended"; else -> "ready" })
                if (code == 1) { hasPlayed = true; if(current.ended)mutable.value=current.copy(ended=false) }
                if (code == 0) mutable.value = current.copy(ended = true)
                if (code in listOf(0,2)) flush()
            }
            "time" -> if (hasPlayed && kotlin.math.abs(position - savedPosition) >= 10) flush()
        }
    }
    fun unavailable(code: Int = -1) { embed.value = EmbedState("error", code) }
    fun retry() {
        if (mutable.value.error) { select(requestedLesson); return }
        embed.value = EmbedState(); mutable.value = mutable.value.copy(retry = mutable.value.retry + 1, resume = position)
    }
    fun flush() {
        val current = mutable.value; val id = current.lesson?.text("id")?.toIntOrNull() ?: return
        if (!hasPlayed || current.loading) return
        val pos = position; val len = duration; savedPosition = pos
        graph.lessons.checkpoint(id,pos,len,current.completed)
    }
    suspend fun complete() {
        val current = mutable.value; val id = requireNotNull(current.lesson).text("id").toInt();val epoch=generation
        graph.lessons.save(id, position, duration, true)
        if(epoch==generation)mutable.value = mutable.value.copy(completed = true)
    }
}
