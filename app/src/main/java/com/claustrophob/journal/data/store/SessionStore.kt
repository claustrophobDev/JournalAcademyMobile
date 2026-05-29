package com.claustrophob.journal.data.store

import okio.ByteString.Companion.decodeBase64
import com.claustrophob.journal.data.net.TokenDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface SessionState {
    // На старте ещё не знаем, есть ли рабочая сессия.
    data object Loading : SessionState
    data object SignedOut : SessionState
    data object SignedIn : SessionState
}

data class Credentials(val cityId: Int, val username: String, val password: String)

// Всё про сессию живёт здесь.
//
// Токены держим в памяти, чтобы authenticator у OkHttp читал их без suspend, и
// дублируем в SecureStore. Логин с паролем лежат там же: сервер ротирует
// refresh-токены по своему расписанию, и когда очередной умирает, приложение
// логинится заново само. Из-за этого аккаунт и не слетает.
class SessionStore(
    private val store: KeyValueStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    @Volatile
    var accessToken: String? = null
        private set

    @Volatile
    var refreshToken: String? = null
        private set

    @Volatile
    private var accessExpiresAt: Long = 0L

    @Volatile
    private var refreshExpiresAt: Long = 0L

    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    init {
        accessToken = store.getString(KEY_ACCESS)
        refreshToken = store.getString(KEY_REFRESH)
        accessExpiresAt = store.getLong(KEY_ACCESS_EXP)
        refreshExpiresAt = store.getLong(KEY_REFRESH_EXP)
        _state.value = if (canRestore()) SessionState.SignedIn else SessionState.SignedOut
    }

    // true, если на диске осталось хоть что-то для авторизации.
    fun canRestore(): Boolean {
        val hasRefresh = !refreshToken.isNullOrBlank() && !isRefreshExpired()
        return hasRefresh || credentials() != null
    }

    fun isAccessUsable(): Boolean =
        !accessToken.isNullOrBlank() && clock() < accessExpiresAt - EXPIRY_SKEW_MS

    fun isRefreshExpired(): Boolean =
        refreshExpiresAt > 0 && clock() >= refreshExpiresAt - EXPIRY_SKEW_MS

    fun saveTokens(token: TokenDto) {
        val now = clock()
        accessToken = token.accessToken
        refreshToken = token.refreshToken.ifBlank { refreshToken }
        accessExpiresAt = expiryOf(token.accessToken, token.expiresInAccess, now, DEFAULT_ACCESS_TTL_MS)
        refreshExpiresAt = expiryOf(token.refreshToken, token.expiresInRefresh, now, DEFAULT_REFRESH_TTL_MS)

        store.putString(KEY_ACCESS, accessToken)
        store.putString(KEY_REFRESH, refreshToken)
        store.putLong(KEY_ACCESS_EXP, accessExpiresAt)
        store.putLong(KEY_REFRESH_EXP, refreshExpiresAt)
        _state.value = SessionState.SignedIn
    }

    fun saveCredentials(credentials: Credentials) {
        store.putInt(KEY_CITY, credentials.cityId)
        store.putString(KEY_USERNAME, credentials.username)
        store.putString(KEY_PASSWORD, credentials.password)
    }

    fun credentials(): Credentials? {
        val user = store.getString(KEY_USERNAME) ?: return null
        val password = store.getString(KEY_PASSWORD) ?: return null
        val city = store.getInt(KEY_CITY, 0)
        if (user.isBlank() || password.isBlank() || city == 0) return null
        return Credentials(city, user, password)
    }

    // Последний филиал, чтобы поле на входе было заполнено.
    var lastCityId: Int
        get() = store.getInt(KEY_CITY, 0)
        set(value) = store.putInt(KEY_CITY, value)

    var lastUsername: String
        get() = store.getString(KEY_USERNAME).orEmpty()
        set(value) = store.putString(KEY_USERNAME, value)

    // Сбрасываем токены, но логин с паролем оставляем — разовый сбой залечится сам.
    fun dropTokens() {
        accessToken = null
        refreshToken = null
        accessExpiresAt = 0
        refreshExpiresAt = 0
        store.remove(KEY_ACCESS, KEY_REFRESH, KEY_ACCESS_EXP, KEY_REFRESH_EXP)
    }

    // Полный выход. Только так можно вернуться на экран входа.
    fun signOut() {
        dropTokens()
        store.remove(KEY_PASSWORD)
        _state.value = SessionState.SignedOut
    }

    fun markSignedOut() {
        _state.value = SessionState.SignedOut
    }

    private fun expiryOf(token: String, expiresIn: Long, now: Long, default: Long): Long {
        jwtExpiryMillis(token)?.let { return it }
        return when {
            expiresIn <= 0 -> now + default
            // Где-то приходит timestamp, где-то время жизни в секундах.
            expiresIn > ABSOLUTE_EPOCH_THRESHOLD -> expiresIn * 1000L
            else -> now + expiresIn * 1000L
        }
    }

    private fun jwtExpiryMillis(token: String): Long? {
        return try {
            val parts = token.split('.')
            if (parts.size < 2) return null
            // okio нормально декодит base64 из JWT (там url-safe и без = в конце).
            val payload = parts[1].decodeBase64()?.utf8() ?: return null
            EXP_REGEX.find(payload)?.groupValues?.get(1)?.toLongOrNull()?.times(1000L)
        } catch (t: Throwable) {
            null
        }
    }

    private companion object {
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_ACCESS_EXP = "access_expires_at"
        const val KEY_REFRESH_EXP = "refresh_expires_at"
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
        const val KEY_CITY = "city_id"

        // Обновляем заранее, чтобы запрос не гонялся наперегонки с истечением.
        const val EXPIRY_SKEW_MS = 60_000L
        const val DEFAULT_ACCESS_TTL_MS = 30 * 60_000L
        const val DEFAULT_REFRESH_TTL_MS = 30L * 24 * 60 * 60_000L
        const val ABSOLUTE_EPOCH_THRESHOLD = 1_000_000_000L

        val EXP_REGEX = Regex("\"exp\"\\s*:\\s*(\\d+)")
    }
}
