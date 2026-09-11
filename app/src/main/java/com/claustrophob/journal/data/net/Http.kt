package com.claustrophob.journal.data.net

import android.util.Log
import com.claustrophob.journal.data.store.Credentials
import com.claustrophob.journal.data.store.SessionStore
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import okhttp3.internal.http2.ErrorCode
import okhttp3.internal.http2.StreamResetException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

object Api {
    const val BASE = "https://msapi.top-academy.ru/api/v2"
    const val SITE = "https://journal.top-academy.ru"

    // Без Referer сервер отдаёт 403 вообще на всё, даже на публичные методы.
    // Из-за этого кажется, что API закрыт, а на самом деле не хватает заголовка.
    const val REFERER = "$SITE/"
    const val ORIGIN = SITE

    // Подсмотрел в форме логина на сайте)))
    const val APPLICATION_KEY =
        "6a56a5df2667e65aab73ce76d1dd737f7d1faef9c52e8b8c55ac75f565d8e8a6"

    const val ACCEPT_LANGUAGE = "ru_RU, ru"
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; SM-A515F) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Mobile Safari/537.36"

    val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
}

class ApiException(
    val code: Int,
    override val message: String,
    val fieldErrors: List<FieldErrorDto> = emptyList(),
) : Exception(message) {
    val isAuth: Boolean get() = code == 401
    val isValidation: Boolean get() = code == 422
}

// Чем кончился вход. Сначала тут был Boolean, и любая беда — хоть обрыв связи,
// хоть кривой JSON — превращалась в "неверный логин или пароль". Разобраться по
// такому сообщению было невозможно.
sealed interface LoginResult {
    data object Success : LoginResult

    // Сервер ответил и отказал: не тот логин, пароль или филиал.
    data class Rejected(val message: String, val code: Int) : LoginResult

    // Не дошли до сервера или не поняли ответ. detail идёт в "Подробности".
    data class Failed(val message: String, val detail: String) : LoginResult
}

// Вытаскиваем текст ошибки из ответа, а если его нет — смотрим на код.
fun describeApiError(code: Int, payload: String): String {
    runCatching { journalJson.decodeFromString<List<FieldErrorDto>>(payload) }
        .getOrNull()
        ?.firstOrNull { it.message.isNotBlank() }
        ?.let { return it.message }

    runCatching { journalJson.decodeFromString<FieldErrorDto>(payload) }
        .getOrNull()
        ?.message
        ?.takeIf { it.isNotBlank() }
        ?.let { return it }

    return when (code) {
        401, 422 -> "Неверный логин, пароль или филиал"
        403 -> "Сервер отклонил запрос"
        404 -> "Метод входа не найден"
        429 -> "Слишком много попыток, подождите немного"
        in 500..599 -> "Сервер журнала недоступен"
        else -> "Ошибка входа ($code)"
    }
}

val journalJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
    explicitNulls = false
}

// Заголовки, которые API ждёт от браузера.
class HeaderInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("Referer", Api.REFERER)
            .header("Origin", Api.ORIGIN)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", Api.ACCEPT_LANGUAGE)
            .header("User-Agent", Api.USER_AGENT)
            .build()
        return chain.proceed(request)
    }
}

// Подставляет токен. Если он вот-вот протухнет — обновляем заранее, чтобы не
// ловить 401 и не делать лишний запрос.
class AuthInterceptor(
    private val session: SessionStore,
    private val renewer: SessionRenewer,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header(SKIP_AUTH) != null) {
            return chain.proceed(request.newBuilder().removeHeader(SKIP_AUTH).build())
        }
        if (!session.isAccessUsable() && session.canRestore()) {
            renewer.renew(session.accessToken)
        }
        val token = session.accessToken
        val authorized = if (token.isNullOrBlank()) {
            request
        } else {
            request.newBuilder().header("Authorization", "Bearer $token").build()
        }
        return chain.proceed(authorized)
    }

    companion object {
        const val SKIP_AUTH = "X-Journal-Skip-Auth"
    }
}

// Если 401 всё же прилетел — обновляем сессию и повторяем запрос. Один раз.
class SessionAuthenticator(
    private val session: SessionStore,
    private val renewer: SessionRenewer,
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null
        val failedToken = response.request.header("Authorization")?.removePrefix("Bearer ")
        if (!renewer.renew(failedToken)) return null
        val token = session.accessToken ?: return null
        if (token == failedToken) return null
        return response.request.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}

// Продлеваем сессию: сначала refresh-токеном, если не вышло — тихо логинимся
// сохранёнными данными. На экран входа выкидываем, только когда не помогло ничего.
class SessionRenewer(
    private val bare: OkHttpClient,
    private val session: SessionStore,
) {
    @Synchronized
    fun renew(previousAccessToken: String?): Boolean {
        // Пока ждали блокировку, соседний поток мог уже обновить.
        val current = session.accessToken
        if (!current.isNullOrBlank() && current != previousAccessToken && session.isAccessUsable()) {
            return true
        }

        val refresh = session.refreshToken
        if (!refresh.isNullOrBlank() && !session.isRefreshExpired() && refreshWith(refresh)) {
            return true
        }

        val credentials = session.credentials()
        if (credentials != null && loginWith(credentials) is LoginResult.Success) {
            Log.i(TAG, "Session restored by silent re-login")
            return true
        }

        session.dropTokens()
        session.markSignedOut()
        return false
    }

    private fun refreshWith(refreshToken: String): Boolean = runCatching {
        val body = journalJson.encodeToString(RefreshRequest(refreshToken))
            .toRequestBody(Api.JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("${Api.BASE}/auth/refresh")
            .post(body)
            .header(AuthInterceptor.SKIP_AUTH, "1")
            .build()
        bare.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) return@runCatching false
            val token = journalJson.decodeFromString<TokenDto>(text)
            if (token.accessToken.isBlank()) return@runCatching false
            session.saveTokens(token)
            true
        }
    }.getOrElse {
        Log.w(TAG, "Refresh failed: ${it.message}")
        false
    }

    fun loginWith(credentials: Credentials): LoginResult {
        val payload = LoginRequest(
            applicationKey = Api.APPLICATION_KEY,
            idCity = credentials.cityId,
            username = credentials.username,
            password = credentials.password,
        )
        val body = journalJson.encodeToString(payload).toRequestBody(Api.JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("${Api.BASE}/auth/login")
            .post(body)
            .header(AuthInterceptor.SKIP_AUTH, "1")
            .build()

        return try {
            bare.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.w(TAG, "login rejected: HTTP ${response.code}")
                    return LoginResult.Rejected(
                        describeApiError(response.code, text),
                        response.code,
                    )
                }
                val token = journalJson.decodeFromString<TokenDto>(text)
                if (token.accessToken.isBlank()) {
                    return LoginResult.Failed(
                        "Сервер принял вход, но не выдал токен",
                        "empty access_token",
                    )
                }
                session.saveTokens(token)
                session.saveCredentials(credentials)
                LoginResult.Success
            }
        } catch (e: UnknownHostException) {
            LoginResult.Failed(
                "Не удалось найти сервер журнала. Проверьте интернет; если включён VPN — " +
                    "попробуйте выключить.",
                "UnknownHostException: ${e.message}",
            )
        } catch (e: SSLException) {
            LoginResult.Failed(
                "Не удалось установить защищённое соединение. Чаще всего виноваты неверные дата " +
                    "и время на устройстве или VPN.",
                "${e.javaClass.simpleName}: ${e.message}",
            )
        } catch (e: SocketTimeoutException) {
            LoginResult.Failed(
                "Сервер журнала не ответил вовремя",
                "SocketTimeoutException: ${e.message}",
            )
        } catch (e: IOException) {
            LoginResult.Failed(
                "Нет связи с сервером журнала",
                "${e.javaClass.simpleName}: ${e.message}",
            )
        } catch (e: Exception) {
            Log.w(TAG, "login failed", e)
            LoginResult.Failed(
                "Не удалось разобрать ответ сервера",
                "${e.javaClass.simpleName}: ${e.message}",
            )
        }
    }

    private companion object {
        const val TAG = "SessionRenewer"
    }
}

// Сервер режет лишние HTTP/2-потоки с REFUSED_STREAM. Выглядело это как случайно
// пустые плитки на главной — полдня искал. Повторять такой запрос безопасно:
// отклонённый поток сервер даже не начинал.
class RetryInterceptor(private val maxAttempts: Int = 3) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var lastFailure: IOException? = null
        repeat(maxAttempts) { attempt ->
            try {
                return chain.proceed(chain.request())
            } catch (e: StreamResetException) {
                if (e.errorCode != ErrorCode.REFUSED_STREAM) throw e
                lastFailure = e
                Thread.sleep(BACKOFF_MS * (attempt + 1))
            }
        }
        throw lastFailure ?: IOException("Request failed")
    }

    private companion object {
        const val BACKOFF_MS = 120L
    }
}

// Логируем только неудачные запросы. Главная дёргает десяток эндпоинтов и живёт,
// даже если часть упала — без лога потом не понять, почему экран полупустой.
class FailureLogInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        return try {
            val response = chain.proceed(request)
            if (!response.isSuccessful) {
                Log.w(TAG, "${response.code} ${request.method} ${request.url.encodedPath}")
            }
            response
        } catch (e: Exception) {
            Log.w(TAG, "${request.method} ${request.url.encodedPath} failed: ${e.javaClass.simpleName}: ${e.message}")
            throw e
        }
    }

    private companion object {
        const val TAG = "JournalHttp"
    }
}

object HttpFactory {
    fun bareClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HeaderInterceptor())
        .addInterceptor(RetryInterceptor())
        .addInterceptor(FailureLogInterceptor())
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun authorizedClient(
        bare: OkHttpClient,
        session: SessionStore,
        renewer: SessionRenewer,
    ): OkHttpClient = bare.newBuilder()
        .addInterceptor(AuthInterceptor(session, renewer))
        .authenticator(SessionAuthenticator(session, renewer))
        .build()
}
