package com.claustrophob.journal.data.store

import android.util.Log
import com.claustrophob.journal.data.net.journalJson
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.File

// Ответы API сохраняю на диск как есть, один файл на ключ. Поэтому экраны
// открываются сразу, даже без интернета.
//
// Про Room думал, но смысла нет: ответ всегда сохраняется и меняется целиком,
// таблицы тут не нужны. К тому же SQLite не любит строки больше 2 МБ, а
// посещения за пару лет примерно такие. Настоящие проблемы были другие: файл
// читался в строку целиком, писался поверх старого (если приложение убьют
// посреди записи, кэш битый) и вообще никогда не удалялся.
class JsonCache(
    private val dir: File,
    private val json: Json = journalJson,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    init {
        dir.mkdirs()
    }

    data class Entry<T>(val value: T, val savedAt: Long)

    // Последние прочитанные ответы держу в памяти, чтобы при переключении
    // вкладок не парсить одно и то же заново.
    private val memory = object : LinkedHashMap<String, Entry<*>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry<*>>?) =
            size > MEMORY_ENTRIES
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun <T> read(key: String, serializer: KSerializer<T>): Entry<T>? {
        synchronized(memory) { memory[key] }?.let {
            @Suppress("UNCHECKED_CAST")
            return it as Entry<T>
        }
        val file = fileFor(key)
        if (!file.exists()) return null
        return runCatching {
            // Парсим сразу из файла, без лишней строки на весь файл.
            val value = file.inputStream().buffered().use { json.decodeFromStream(serializer, it) }
            Entry(value, file.lastModified())
        }.onFailure {
            // Файл битый или модель поменялась — удаляем, толку от него нет.
            Log.w(TAG, "cache read failed for $key", it)
            file.delete()
        }.getOrNull()?.also { remember(key, it) }
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun <T> write(key: String, serializer: KSerializer<T>, value: T) {
        val now = clock()
        remember(key, Entry(value, now))
        val target = fileFor(key)
        val temp = File(dir, target.name + TEMP_SUFFIX)
        runCatching {
            // Сначала пишем во временный файл, потом переименовываем. Если
            // приложение убьют посреди записи, останется старый файл, а не пол-JSON.
            temp.outputStream().buffered().use { json.encodeToStream(serializer, value, it) }
            if (!temp.renameTo(target)) {
                target.delete()
                check(temp.renameTo(target)) { "rename failed" }
            }
            target.setLastModified(now)
        }.onFailure {
            Log.w(TAG, "cache write failed for $key", it)
            temp.delete()
        }
    }

    // Удалить всё, что начинается с prefix. Например, сдал задание — надо
    // сбросить списки заданий и главную, а расписание с оценками не трогать.
    fun invalidate(prefix: String) {
        synchronized(memory) { memory.keys.removeAll { it.startsWith(prefix) } }
        val safe = safeName(prefix)
        dir.listFiles()?.filter { it.name.startsWith(safe) }?.forEach { it.delete() }
    }

    fun clear() {
        synchronized(memory) { memory.clear() }
        runCatching { dir.listFiles()?.forEach { it.delete() } }
    }

    // Чистка старого. Раньше расписание за каждый открытый месяц лежало вечно,
    // пока не выйдешь из аккаунта. Теперь удаляется всё старше maxAgeMs и всё,
    // что не влезает в maxBytes (свежее оставляем).
    fun trim(maxAgeMs: Long = MAX_AGE_MS, maxBytes: Long = MAX_BYTES) {
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        val now = clock()

        files.filter { it.name.endsWith(TEMP_SUFFIX) }.forEach { it.delete() }

        var total = 0L
        files.filter { it.name.endsWith(EXTENSION) }
            .sortedByDescending { it.lastModified() }
            .forEach { file ->
                total += file.length()
                if (now - file.lastModified() > maxAgeMs || total > maxBytes) file.delete()
            }

        synchronized(memory) { memory.keys.removeAll { !fileFor(it).exists() } }
    }

    private fun remember(key: String, entry: Entry<*>) {
        synchronized(memory) { memory[key] = entry }
    }

    private fun fileFor(key: String) = File(dir, safeName(key) + EXTENSION)

    private fun safeName(key: String) = key.replace(NON_FILE_SAFE, "_")

    private companion object {
        const val TAG = "JsonCache"
        const val EXTENSION = ".json"
        const val TEMP_SUFFIX = ".tmp"
        const val MEMORY_ENTRIES = 12
        const val MAX_AGE_MS = 45L * 24 * 60 * 60 * 1000
        const val MAX_BYTES = 16L * 1024 * 1024
        val NON_FILE_SAFE = Regex("[^A-Za-z0-9._-]")
    }
}
