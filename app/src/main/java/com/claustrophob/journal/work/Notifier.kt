package com.claustrophob.journal.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.claustrophob.journal.MainActivity
import com.claustrophob.journal.R

object Notifier {

    const val EXTRA_ROUTE = "route"

    // Отдельный канал на каждый тип, чтобы в настройках телефона можно было
    // выключить новости, а оценки оставить.
    enum class Topic(
        val channelId: String,
        val channelName: String,
        val description: String,
        val route: String,
        val baseId: Int,
    ) {
        Homework("topic_homework", "Задания", "Новые домашние задания", "homework", 10_000),
        Marks("topic_marks", "Оценки", "Новые оценки в журнале", "progress", 20_000),
        News("topic_news", "Новости", "Объявления академии", "news", 30_000),
    }

    data class Item(val key: String, val title: String, val text: String)

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        // Старый общий канал больше не нужен.
        if (manager.getNotificationChannel(LEGACY_CHANNEL_ID) != null) {
            manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        }
        Topic.entries.forEach { topic ->
            if (manager.getNotificationChannel(topic.channelId) != null) return@forEach
            val channel = NotificationChannel(
                topic.channelId,
                topic.channelName,
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = topic.description
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun canNotify(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

    // Если новое одно — обычное уведомление. Если несколько — одно общее со
    // списком, а не пять штук, которые потом смахивать по одному.
    fun post(context: Context, topic: Topic, items: List<Item>, summaryTitle: String) {
        if (items.isEmpty() || !canNotify(context)) return
        ensureChannels(context)

        val builder = NotificationCompat.Builder(context, topic.channelId)
            .setSmallIcon(R.drawable.ic_stat_journal)
            .setColor(BRAND_COLOR)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)

        val id: Int
        if (items.size == 1) {
            val item = items.single()
            id = topic.baseId + (item.key.hashCode() and 0x3FF)
            builder
                .setContentTitle(item.title)
                .setContentText(item.text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(item.text))
        } else {
            id = topic.baseId
            val inbox = NotificationCompat.InboxStyle().setBigContentTitle(summaryTitle)
            items.take(MAX_LINES).forEach { inbox.addLine("${it.title} · ${it.text}") }
            if (items.size > MAX_LINES) inbox.setSummaryText("и ещё ${items.size - MAX_LINES}")
            builder
                .setContentTitle(summaryTitle)
                .setContentText(items.first().let { "${it.title} · ${it.text}" })
                .setNumber(items.size)
                .setStyle(inbox)
        }
        builder.setContentIntent(pendingFor(context, id, topic.route))

        runCatching { NotificationManagerCompat.from(context).notify(id, builder.build()) }
    }

    private fun pendingFor(context: Context, id: Int, route: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ROUTE, route)
        }
        return PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private const val LEGACY_CHANNEL_ID = "journal_updates"
    private const val MAX_LINES = 6
    private const val BRAND_COLOR = 0xFFC00C3C.toInt()
}
