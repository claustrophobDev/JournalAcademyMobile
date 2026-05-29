package com.claustrophob.journal.data.store

import android.content.Context
import com.claustrophob.journal.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = false,
    val notifyHomework: Boolean = true,
    val notifyMarks: Boolean = true,
    val notifyNews: Boolean = false,
)

class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("journal_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _agreementAccepted =
        MutableStateFlow(prefs.getInt(KEY_AGREEMENT, 0) >= AGREEMENT_VERSION)

    // false, пока не принял условия. Поднимешь AGREEMENT_VERSION — спросит заново.
    val agreementAccepted: StateFlow<Boolean> = _agreementAccepted.asStateFlow()

    init {
        // Старые списки без дат — из-за них и приходили оценки прошлых лет.
        // Удаляем, воркер в первый раз просто всё запомнит и ничего не пришлёт.
        if (LEGACY_SEEN.any(prefs::contains)) {
            prefs.edit().apply { LEGACY_SEEN.forEach(::remove) }.apply()
        }
    }

    fun acceptAgreement() {
        prefs.edit().putInt(KEY_AGREEMENT, AGREEMENT_VERSION).apply()
        _agreementAccepted.value = true
    }

    private fun read() = Settings(
        themeMode = runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_THEME, ThemeMode.System.name)!!)
        }.getOrDefault(ThemeMode.System),
        dynamicColor = prefs.getBoolean(KEY_DYNAMIC, false),
        notifyHomework = prefs.getBoolean(KEY_NOTIFY_HOMEWORK, true),
        notifyMarks = prefs.getBoolean(KEY_NOTIFY_MARKS, true),
        notifyNews = prefs.getBoolean(KEY_NOTIFY_NEWS, false),
    )

    fun update(transform: (Settings) -> Settings) {
        val next = transform(_settings.value)
        prefs.edit()
            .putString(KEY_THEME, next.themeMode.name)
            .putBoolean(KEY_DYNAMIC, next.dynamicColor)
            .putBoolean(KEY_NOTIFY_HOMEWORK, next.notifyHomework)
            .putBoolean(KEY_NOTIFY_MARKS, next.notifyMarks)
            .putBoolean(KEY_NOTIFY_NEWS, next.notifyNews)
            .apply()
        _settings.value = next
    }

    // Что уже показывали, чтобы не слать одно уведомление дважды.
    // Записи вида "yyyy-MM-dd|key", чтобы список не рос бесконечно (см. SyncPlanner).
    fun seenIds(name: String): Set<String> =
        prefs.getStringSet(SEEN_PREFIX + name, null)?.toSet() ?: emptySet()

    fun putSeenIds(name: String, ids: Set<String>) {
        prefs.edit().putStringSet(SEEN_PREFIX + name, HashSet(ids)).apply()
    }

    fun hasSeenBaseline(name: String): Boolean = prefs.contains(SEEN_PREFIX + name)

    // Для смены аккаунта. Без списка воркер сначала молча всё запомнит,
    // так что уведомления разом не посыплются.
    fun clearSeen() {
        val keys = prefs.all.keys.filter { it.startsWith(SEEN_PREFIX) }
        prefs.edit().apply { keys.forEach(::remove) }.apply()
    }

    private companion object {
        // Поднять, если поменялся смысл текста — тогда спросим согласие заново.
        const val AGREEMENT_VERSION = 1
        const val KEY_AGREEMENT = "agreement_version"
        const val KEY_THEME = "theme_mode"
        const val KEY_DYNAMIC = "dynamic_color"
        const val KEY_NOTIFY_HOMEWORK = "notify_homework"
        const val KEY_NOTIFY_MARKS = "notify_marks"
        const val KEY_NOTIFY_NEWS = "notify_news"
        const val SEEN_PREFIX = "seen2_"
        val LEGACY_SEEN = listOf("seen_homework", "seen_marks", "seen_news")
    }
}
