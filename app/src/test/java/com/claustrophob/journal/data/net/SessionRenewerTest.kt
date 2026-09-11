package com.claustrophob.journal.data.net

import com.claustrophob.journal.data.store.Credentials
import com.claustrophob.journal.data.store.KeyValueStore
import com.claustrophob.journal.data.store.SessionState
import com.claustrophob.journal.data.store.SessionStore
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException
import java.util.Base64

class SessionRenewerTest {

    private class MemoryStore : KeyValueStore {
        val map = HashMap<String, String>()
        override fun getString(name: String): String? = map[name]
        override fun putString(name: String, value: String?) {
            if (value == null) map.remove(name) else map[name] = value
        }

        override fun remove(vararg names: String) {
            names.forEach { map.remove(it) }
        }
    }

    // Сервер-заглушка: отвечает по пути, в сеть не ходит.
    private class FakeServer : Interceptor {
        val calls = mutableListOf<String>()
        val routes = HashMap<String, () -> Pair<Int, String>>()

        override fun intercept(chain: Interceptor.Chain): Response {
            val path = chain.request().url.encodedPath.removePrefix("/api/v2")
            calls += path
            val (code, body) = routes[path]?.invoke() ?: (404 to "")
            return Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("fake")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
    }

    private var now = 1_800_000_000_000L
    private lateinit var store: MemoryStore
    private lateinit var session: SessionStore
    private lateinit var server: FakeServer
    private lateinit var renewer: SessionRenewer

    private val credentials = Credentials(cityId = 42, username = "student", password = "secret")

    @Before
    fun setUp() {
        store = MemoryStore()
        session = SessionStore(store) { now }
        server = FakeServer()
        renewer = SessionRenewer(OkHttpClient.Builder().addInterceptor(server).build(), session)
    }

    private fun jwt(expSeconds: Long): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"none"}""".toByteArray())
        val payload = encoder.encodeToString("""{"sub":"1","exp":$expSeconds}""".toByteArray())
        return "$header.$payload.sig"
    }

    private fun tokenJson(access: String, refresh: String) =
        """{"access_token":"$access","refresh_token":"$refresh"}"""

    // Сессия, у которой доступ протух, а refresh ещё жив.
    private fun expiredAccessSession() {
        session.saveTokens(
            TokenDto(
                accessToken = jwt(now / 1000 - 10),
                refreshToken = jwt(now / 1000 + 86_400),
            ),
        )
        session.saveCredentials(credentials)
    }

    @Test
    fun `refresh прошёл — логин не нужен`() {
        expiredAccessSession()
        val fresh = jwt(now / 1000 + 3600)
        server.routes["/auth/refresh"] = { 200 to tokenJson(fresh, jwt(now / 1000 + 90_000)) }

        assertTrue(renewer.renew(session.accessToken))

        assertEquals(fresh, session.accessToken)
        assertEquals(listOf("/auth/refresh"), server.calls)
        assertTrue(session.isAccessUsable())
    }

    @Test
    fun `refresh отвергнут — тихий перевход сохранёнными данными`() {
        expiredAccessSession()
        val fresh = jwt(now / 1000 + 3600)
        server.routes["/auth/refresh"] = { 401 to "" }
        server.routes["/auth/login"] = { 200 to tokenJson(fresh, jwt(now / 1000 + 90_000)) }

        assertTrue(renewer.renew(session.accessToken))

        assertEquals(listOf("/auth/refresh", "/auth/login"), server.calls)
        assertEquals(fresh, session.accessToken)
        assertEquals(SessionState.SignedIn, session.state.value)
    }

    @Test
    fun `не помогло ничего — выходим, но логин с паролем остаются`() {
        expiredAccessSession()
        server.routes["/auth/refresh"] = { 401 to "" }
        server.routes["/auth/login"] = { 401 to "" }

        assertFalse(renewer.renew(session.accessToken))

        assertNull(session.accessToken)
        assertNull(session.refreshToken)
        assertEquals(SessionState.SignedOut, session.state.value)
        assertEquals(credentials, session.credentials())
    }

    @Test
    fun `соседний поток уже обновил — в сеть не идём`() {
        session.saveTokens(TokenDto(accessToken = jwt(now / 1000 + 3600), refreshToken = "r"))

        assertTrue(renewer.renew(previousAccessToken = "устаревший"))

        assertTrue(server.calls.isEmpty())
    }

    @Test
    fun `протухший refresh даже не пробуем`() {
        session.saveTokens(
            TokenDto(accessToken = jwt(now / 1000 - 10), refreshToken = jwt(now / 1000 - 5)),
        )
        session.saveCredentials(credentials)
        server.routes["/auth/login"] = { 200 to tokenJson(jwt(now / 1000 + 3600), "r2") }

        assertTrue(renewer.renew(session.accessToken))

        assertEquals(listOf("/auth/login"), server.calls)
    }

    @Test
    fun `нет ни refresh ни пароля — сразу выход`() {
        assertFalse(renewer.renew(null))
        assertTrue(server.calls.isEmpty())
        assertEquals(SessionState.SignedOut, session.state.value)
    }

    @Test
    fun `отказ сервера при входе несёт его текст`() {
        server.routes["/auth/login"] = {
            422 to """[{"field":"password","message":"Неверный логин или пароль"}]"""
        }

        val result = renewer.loginWith(credentials)

        assertTrue(result is LoginResult.Rejected)
        result as LoginResult.Rejected
        assertEquals(422, result.code)
        assertEquals("Неверный логин или пароль", result.message)
        assertNull(session.credentials())
    }

    @Test
    fun `вход без токена в ответе — не успех`() {
        server.routes["/auth/login"] = { 200 to """{"access_token":""}""" }

        assertTrue(renewer.loginWith(credentials) is LoginResult.Failed)
    }

    @Test
    fun `нет сети при входе — понятная причина`() {
        server.routes["/auth/login"] = { throw UnknownHostException("msapi.top-academy.ru") }

        val result = renewer.loginWith(credentials)

        assertTrue(result is LoginResult.Failed)
        assertTrue((result as LoginResult.Failed).detail.startsWith("UnknownHostException"))
    }

    @Test
    fun `обрыв при входе тоже не падение`() {
        server.routes["/auth/login"] = { throw IOException("reset") }

        assertTrue(renewer.loginWith(credentials) is LoginResult.Failed)
    }

    @Test
    fun `успешный вход сохраняет данные для перевхода`() {
        server.routes["/auth/login"] = { 200 to tokenJson(jwt(now / 1000 + 3600), "r") }

        assertEquals(LoginResult.Success, renewer.loginWith(credentials))

        assertEquals(credentials, session.credentials())
        assertNotNull(session.accessToken)
    }

    @Test
    fun `срок доступа берётся из exp в токене`() {
        val exp = now / 1000 + 3600
        session.saveTokens(TokenDto(accessToken = jwt(exp), refreshToken = "r"))
        assertTrue(session.isAccessUsable())

        // За минуту до конца уже считаем протухшим, чтобы не гоняться с часами.
        now = exp * 1000 - 30_000
        assertFalse(session.isAccessUsable())
    }

    @Test
    fun `срок без JWT — секунды или абсолютное время`() {
        session.saveTokens(TokenDto(accessToken = "opaque", refreshToken = "r", expiresInAccess = 1800))
        now += 1700 * 1000L
        assertTrue(session.isAccessUsable())
        now += 200 * 1000L
        assertFalse(session.isAccessUsable())

        val absolute = now / 1000 + 7200
        session.saveTokens(TokenDto(accessToken = "opaque2", refreshToken = "r", expiresInAccess = absolute))
        assertTrue(session.isAccessUsable())
    }
}
