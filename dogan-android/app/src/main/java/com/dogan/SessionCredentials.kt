package com.dogan

/**
 * Shared username/password and bearer token for all [DoganApiClient] instances
 * (API + streaming use the same token from `POST /api/v1/auth`).
 */
object SessionCredentials {
    @Volatile
    var username: String = SettingsStore.DEFAULT_USERNAME
        private set

    @Volatile
    var password: String = SettingsStore.DEFAULT_PASSWORD
        private set

    @Volatile
    var bearerToken: String? = null
        private set

    @Volatile
    var tokenExpiresAtEpochMillis: Long = 0L
        private set

    fun update(username: String, password: String) {
        this.username = username.trim()
        this.password = password
    }

    fun updateFrom(settings: AppSettings) {
        update(settings.username, settings.password)
    }

    fun applyAuthToken(token: String, expiresAtEpochMillis: Long) {
        bearerToken = token
        tokenExpiresAtEpochMillis = expiresAtEpochMillis
    }

    fun clearToken() {
        bearerToken = null
        tokenExpiresAtEpochMillis = 0L
    }

    fun clear() {
        username = ""
        password = ""
        clearToken()
    }

    fun hasFreshToken(refreshLeadMs: Long = 60_000L): Boolean {
        val token = bearerToken ?: return false
        if (token.isBlank()) return false
        return System.currentTimeMillis() < tokenExpiresAtEpochMillis - refreshLeadMs
    }
}