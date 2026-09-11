package com.claustrophob.journal

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.claustrophob.journal.data.JournalRepository
import com.claustrophob.journal.data.net.HttpFactory
import com.claustrophob.journal.data.net.JournalApi
import com.claustrophob.journal.data.net.SessionRenewer
import com.claustrophob.journal.data.store.JsonCache
import com.claustrophob.journal.data.store.SecureStore
import com.claustrophob.journal.data.store.SessionStore
import com.claustrophob.journal.data.store.SettingsStore
import com.claustrophob.journal.work.SyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import okio.Path.Companion.toOkioPath
import java.io.File
import kotlin.concurrent.thread

// Все зависимости создаются тут один раз. Их мало, Hilt ради этого тащить не стал.
class AppContainer(context: Context) {
    val secureStore = SecureStore(context)
    val session = SessionStore(secureStore)
    val settings = SettingsStore(context)

    private val bareClient = HttpFactory.bareClient()
    val renewer = SessionRenewer(bareClient, session)
    val httpClient = HttpFactory.authorizedClient(bareClient, session, renewer)

    private val api = JournalApi(httpClient, bareClient)
    val cache = JsonCache(File(context.filesDir, "api-cache"))

    val repository = JournalRepository(api, cache, session, renewer, settings)

    // Счётчик активных ДЗ: пишет главный экран, читает бейдж. Без лишних запросов.
    val homeworkBadge = MutableStateFlow(0)
}

class JournalApp : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        SyncScheduler.ensureScheduled(this)
        // Старый кэш чистим при запуске, в фоне.
        thread(name = "cache-trim", isDaemon = true) { container.cache.trim() }
    }

    // Аватарки за тем же Referer, что и API, поэтому клиент переиспользуем.
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { container.httpClient }))
            }
            .memoryCache {
                MemoryCache.Builder().maxSizePercent(context, 0.2).build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image-cache").toOkioPath())
                    .maxSizeBytes(48L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
}

val Context.container: AppContainer
    get() = (applicationContext as JournalApp).container
