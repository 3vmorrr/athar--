package org.athar.app.core.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** Read failure is never equivalent to an absent record. This adapter never writes or logs payloads. */
sealed interface ProtectedRead<out T> {
    data object Loading:ProtectedRead<Nothing>
    data class Ready<T>(val value:T):ProtectedRead<T>
    data object Unreadable:ProtectedRead<Nothing>
}

fun <T> Flow<T>.protectedReads():Flow<ProtectedRead<T>> = map<T,ProtectedRead<T>> { ProtectedRead.Ready(it) }.catch { failure ->
    if(failure is CancellationException)throw failure
    if(failure !is Exception)throw failure
    emit(ProtectedRead.Unreadable)
}
