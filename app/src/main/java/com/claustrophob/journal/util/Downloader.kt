package com.claustrophob.journal.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import com.claustrophob.journal.container
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

// Качаем вложения своим клиентом и отдаём системе — пусть открывает чем хочет.
// Через браузер хуже: теряется сессия и вылезает браузерная обвязка.
object Downloader {

    suspend fun fetch(context: Context, url: String, suggestedName: String): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val client = context.container.httpClient
                val request = Request.Builder().url(url).get().build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    val body = response.body ?: error("Пустой ответ")
                    val directory = File(
                        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                            ?: context.filesDir,
                        "attachments",
                    ).apply { mkdirs() }
                    val file = File(directory, sanitize(suggestedName, url))
                    file.outputStream().use { output -> body.byteStream().copyTo(output) }
                    file
                }
            }
        }

    fun open(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.files",
            file,
        )
        val mime = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase())
            ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                context,
                "Нет приложения для открытия ${file.extension.uppercase()}",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    // Для ссылок наружу: оплата, материалы на чужих хостингах.
    // Всё, что отдаёт само API, рисуем внутри.
    fun openLink(context: Context, url: String) {
        val target = absolute(url)
        if (target.isBlank()) return
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(target)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "Нет приложения для открытия ссылки", Toast.LENGTH_LONG).show()
        }
    }

    private fun sanitize(name: String, url: String): String {
        val fallback = url.substringAfterLast('/').substringBefore('?').ifBlank { "file" }
        val chosen = name.ifBlank { fallback }
        return chosen.replace(Regex("[^A-Za-z0-9._ -А-Яа-яЁё]"), "_").take(120)
    }

    // API иногда отдаёт относительный путь — достраиваем.
    fun absolute(path: String): String = when {
        path.isBlank() -> ""
        path.startsWith("http", ignoreCase = true) -> path
        path.startsWith("//") -> "https:$path"
        else -> "https://msapi.top-academy.ru/${path.removePrefix("/")}"
    }
}
