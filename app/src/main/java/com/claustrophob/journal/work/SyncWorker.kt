package com.claustrophob.journal.work

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.claustrophob.journal.container
import com.claustrophob.journal.data.JournalRepository
import com.claustrophob.journal.data.net.VisitDto
import com.claustrophob.journal.data.store.SessionState
import com.claustrophob.journal.data.store.SettingsStore
import com.claustrophob.journal.util.Dates
import com.claustrophob.journal.util.pluralRu
import java.util.concurrent.TimeUnit

// Фоном проверяем то, о чём хочется узнать не открывая приложение: новое задание,
// оценку, новость. Что считать новым, решает SyncPlanner.
// На первом запуске просто запоминаем состояние, иначе сразу после установки
// прилетит сотня уведомлений.
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = applicationContext.container
        // Кэш чистим в любом случае, даже если из аккаунта вышли.
        container.cache.trim()

        if (container.session.state.value != SessionState.SignedIn) return Result.success()
        if (!container.session.canRestore()) return Result.success()

        val settings = container.settings
        val repository = container.repository
        val today = Dates.today()

        return try {
            if (settings.settings.value.notifyHomework) {
                syncHomework(repository, settings, today)
            }
            if (settings.settings.value.notifyMarks) {
                syncMarks(repository, settings, today)
            }
            if (settings.settings.value.notifyNews) {
                syncNews(repository, settings, today)
            }
            Result.success()
        } catch (e: Throwable) {
            Log.w(TAG, "sync failed: ${e.message}")
            Result.retry()
        }
    }

    private suspend fun syncHomework(
        repository: JournalRepository,
        settings: SettingsStore,
        today: String,
    ) {
        val plan = SyncPlanner.plan(
            items = repository.peekActiveHomework(),
            keyOf = { it.id.toString() },
            dateOf = { it.creationTime.ifBlank { today } },
            known = settings.seenIds(SEEN_HOMEWORK),
            isBaseline = !settings.hasSeenBaseline(SEEN_HOMEWORK),
            today = today,
        )
        settings.putSeenIds(SEEN_HOMEWORK, plan.remember)

        val items = plan.notify.map { homework ->
            val deadline = homework.completionTime.take(10)
            val suffix = if (deadline.isNotBlank()) " · до ${Dates.humanDayShort(deadline)}" else ""
            Notifier.Item(
                key = homework.id.toString(),
                title = homework.nameSpec.ifBlank { "Новое задание" },
                text = homework.theme.ifBlank { "Без названия" } + suffix,
            )
        }
        Notifier.post(
            context = applicationContext,
            topic = Notifier.Topic.Homework,
            items = items,
            summaryTitle = pluralRu(items.size, "новое задание", "новых задания", "новых заданий")
                .replaceFirstChar { it.uppercase() },
        )
    }

    private suspend fun syncMarks(
        repository: JournalRepository,
        settings: SettingsStore,
        today: String,
    ) {
        val plan = SyncPlanner.plan(
            items = repository.peekVisits().filter { it.bestMark() > 0 },
            keyOf = { it.markKey() },
            dateOf = { it.dateVisit },
            known = settings.seenIds(SEEN_MARKS),
            isBaseline = !settings.hasSeenBaseline(SEEN_MARKS),
            today = today,
        )
        settings.putSeenIds(SEEN_MARKS, plan.remember)

        val single = plan.notify.size == 1
        val items = plan.notify.map { visit ->
            Notifier.Item(
                key = visit.markKey(),
                title = if (single) "Новая оценка: ${visit.bestMark()}" else "${visit.bestMark()} — ${visit.specName}",
                text = if (single) {
                    "${visit.specName} · ${Dates.humanDayShort(visit.dateVisit)}"
                } else {
                    Dates.humanDayShort(visit.dateVisit)
                },
            )
        }
        Notifier.post(
            context = applicationContext,
            topic = Notifier.Topic.Marks,
            items = items,
            summaryTitle = pluralRu(items.size, "новая оценка", "новые оценки", "новых оценок")
                .replaceFirstChar { it.uppercase() },
        )
    }

    private suspend fun syncNews(
        repository: JournalRepository,
        settings: SettingsStore,
        today: String,
    ) {
        val plan = SyncPlanner.plan(
            items = repository.peekNews(),
            keyOf = { it.id.toString() },
            dateOf = { it.time.ifBlank { today } },
            known = settings.seenIds(SEEN_NEWS),
            isBaseline = !settings.hasSeenBaseline(SEEN_NEWS),
            today = today,
        )
        settings.putSeenIds(SEEN_NEWS, plan.remember)

        val items = plan.notify.map { news ->
            Notifier.Item(key = news.id.toString(), title = "Новость академии", text = news.theme)
        }
        Notifier.post(
            context = applicationContext,
            topic = Notifier.Topic.News,
            items = items,
            summaryTitle = "Новости академии",
        )
    }

    private fun VisitDto.bestMark(): Int = maxOf(
        controlWorkMark,
        homeWorkMark,
        labWorkMark,
        classWorkMark,
        practicalWorkMark,
        finalWorkMark,
    )

    // Оценки тоже в ключе: если препод исправил оценку, это тоже новость.
    private fun VisitDto.markKey(): String =
        "$specId|$lessonNumber|$controlWorkMark$homeWorkMark$labWorkMark" +
            "$classWorkMark$practicalWorkMark$finalWorkMark"

    private companion object {
        const val TAG = "SyncWorker"
        const val SEEN_HOMEWORK = "homework"
        const val SEEN_MARKS = "marks"
        const val SEEN_NEWS = "news"
    }
}

object SyncScheduler {
    private const val WORK_NAME = "journal-sync"

    fun ensureScheduled(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(3, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setInitialDelay(30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
