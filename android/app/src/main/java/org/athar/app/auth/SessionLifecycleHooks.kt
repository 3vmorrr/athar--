package org.athar.app.auth

interface SessionLifecycleHooks {
    suspend fun beforeLogout(owner: Long)
}

object NoopSessionLifecycleHooks : SessionLifecycleHooks {
    override suspend fun beforeLogout(owner: Long) = Unit
}
