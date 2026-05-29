package com.claustrophob.journal.data

import com.claustrophob.journal.data.net.AcademicPerformanceDto
import com.claustrophob.journal.data.net.AchievementDto
import com.claustrophob.journal.data.net.ActivityDto
import com.claustrophob.journal.data.net.ApiException
import com.claustrophob.journal.data.net.AttendanceStatDto
import com.claustrophob.journal.data.net.ChartPointDto
import com.claustrophob.journal.data.net.CityDto
import com.claustrophob.journal.data.net.ExamResultDto
import com.claustrophob.journal.data.net.FutureExamDto
import com.claustrophob.journal.data.net.HomeworkDto
import com.claustrophob.journal.data.net.HomeworkStatus
import com.claustrophob.journal.data.net.HomeworkType
import com.claustrophob.journal.data.net.JournalApi
import com.claustrophob.journal.data.net.LeaderPositionDto
import com.claustrophob.journal.data.net.LeaderRowDto
import com.claustrophob.journal.data.net.LessonDto
import com.claustrophob.journal.data.net.LibraryMaterialDto
import com.claustrophob.journal.data.net.LoginResult
import com.claustrophob.journal.data.net.NewsDetailDto
import com.claustrophob.journal.data.net.NewsDto
import com.claustrophob.journal.data.net.PageCounterDto
import com.claustrophob.journal.data.net.PaymentHistoryDto
import com.claustrophob.journal.data.net.PaymentInfoDto
import com.claustrophob.journal.data.net.PaymentPlanDto
import com.claustrophob.journal.data.net.PortfolioRequestDto
import com.claustrophob.journal.data.net.PortfolioWorkDto
import com.claustrophob.journal.data.net.SessionRenewer
import com.claustrophob.journal.data.net.UserInfoDto
import com.claustrophob.journal.data.net.VisitDto
import com.claustrophob.journal.data.store.Credentials
import com.claustrophob.journal.data.store.JsonCache
import com.claustrophob.journal.data.store.SessionStore
import com.claustrophob.journal.data.store.SettingsStore
import com.claustrophob.journal.util.Dates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.io.IOException

// Почему не пустило. detail показываем по кнопке "Подробности".
data class SignInError(
    val message: String,
    val detail: String,
    // true — когда сервер отверг именно логин с паролем, а не связь подвела.
    val credentialsLikely: Boolean,
)

// То, что доходит до экрана: данные (может быть, с диска) или текст ошибки.
sealed interface Res<out T> {
    data class Ok<T>(val data: T, val stale: Boolean = false, val savedAt: Long = 0L) : Res<T>
    data class Err(val message: String, val offline: Boolean = false) : Res<Nothing>
}

// Месяц в том виде, в каком его отдают графики на главной.
@Serializable
data class MonthPoint(
    val date: String,
    val attendance: Double,
    val average: Double,
)

@Serializable
data class Dashboard(
    val user: UserInfoDto,
    val attendance: AttendanceStatDto,
    val performance: AcademicPerformanceDto,
    val groupPosition: LeaderPositionDto,
    val streamPosition: LeaderPositionDto,
    val futureExams: List<FutureExamDto>,
    val attendanceChart: List<ChartPointDto>,
    val progressChart: List<ChartPointDto>,
    val months: List<MonthPoint> = emptyList(),
    val today: List<LessonDto>,
    // Завтрашние пары: вечером главная показывает, что будет утром.
    val tomorrow: List<LessonDto> = emptyList(),
    val activeHomework: Int,
)

@Serializable
data class Leaderboards(
    val group: List<LeaderRowDto>,
    val stream: List<LeaderRowDto>,
)

@Serializable
data class Payments(
    val info: PaymentInfoDto,
    val plan: List<PaymentPlanDto>,
    val history: List<PaymentHistoryDto>,
)

@Serializable
data class Portfolio(
    val works: List<PortfolioWorkDto>,
    val requests: List<PortfolioRequestDto>,
)

@Serializable
data class ProgressBundle(
    val visits: List<VisitDto>,
    val exams: List<ExamResultDto>,
)

class JournalRepository(
    private val api: JournalApi,
    private val cache: JsonCache,
    private val session: SessionStore,
    private val renewer: SessionRenewer,
    private val settings: SettingsStore,
) {

    suspend fun cities(): Res<List<CityDto>> =
        fetchOnce(KEY_CITIES, ListSerializer(CityDto.serializer())) { api.cities() }

    // Возвращает null если вошли, иначе — что пошло не так.
    suspend fun signIn(cityId: Int, username: String, password: String): SignInError? {
        val credentials = Credentials(cityId, username.trim(), password)
        return try {
            when (val result = withContext(Dispatchers.IO) { renewer.loginWith(credentials) }) {
                is LoginResult.Success -> {
                    forgetLocalData()
                    null
                }

                is LoginResult.Rejected -> SignInError(
                    message = result.message,
                    detail = "HTTP ${result.code}",
                    // На чужой филиал ответ такой же, как на неверный пароль.
                    credentialsLikely = result.code == 401 || result.code == 422,
                )

                is LoginResult.Failed -> SignInError(
                    message = result.message,
                    detail = result.detail,
                    credentialsLikely = false,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            SignInError(describe(e), e.javaClass.simpleName, credentialsLikely = false)
        }
    }

    fun signOut() {
        session.signOut()
        forgetLocalData()
    }

    // Кэш и список показанных уведомлений привязаны к аккаунту. Зашёл под другим —
    // старые оценки не должны ни показаться, ни прийти уведомлением.
    private fun forgetLocalData() {
        cache.clear()
        settings.clearSeen()
    }

    // дальше по экранам

    fun dashboard(): Flow<Res<Dashboard>> = cached(KEY_DASHBOARD, Dashboard.serializer()) {
        coroutineScope {
            val user = async { guarded { api.userInfo() } }
            val attendance = async { guarded(AttendanceStatDto()) { api.attendanceStat() } }
            val performance =
                async { guarded(AcademicPerformanceDto()) { api.academicPerformance() } }
            val group = async { guarded(LeaderPositionDto()) { api.leaderGroupPosition() } }
            val stream = async { guarded(LeaderPositionDto()) { api.leaderStreamPosition() } }
            val exams = async { guarded(emptyList<FutureExamDto>()) { api.futureExams() } }
            val attendanceChart =
                async { guarded(emptyList<ChartPointDto>()) { api.attendanceChart() } }
            val progressChart =
                async { guarded(emptyList<ChartPointDto>()) { api.averageProgressChart() } }
            val day = Dates.today()
            val today = async { guarded(emptyList<LessonDto>()) { lessonsOn(day) } }
            val tomorrow =
                async { guarded(emptyList<LessonDto>()) { lessonsOn(Dates.shiftDay(day, 1)) } }
            val homework = async { guarded(0) { activeHomeworkCount() } }
            val attendancePoints = attendanceChart.await()
            val averagePoints = progressChart.await()
            Dashboard(
                user = user.await(),
                attendance = attendance.await(),
                performance = performance.await(),
                groupPosition = group.await(),
                streamPosition = stream.await(),
                futureExams = exams.await(),
                attendanceChart = attendancePoints,
                progressChart = averagePoints,
                months = mergeMonths(attendancePoints, averagePoints),
                today = today.await(),
                tomorrow = tomorrow.await(),
                activeHomework = homework.await(),
            )
        }
    }

    // Дату у пары ставим сами, иначе сегодняшние и завтрашние не отличить,
    // а на главной хранятся и те и другие.
    private suspend fun lessonsOn(day: String): List<LessonDto> =
        api.scheduleByDate(day).map { if (it.date.isBlank()) it.copy(date = day) else it }

    // Для бейджа хватает счётчика, не надо ради него листать все страницы.
    // Если счётчик не пришёл, считаем по-старому — активных обычно мало.
    private suspend fun activeHomeworkCount(): Int {
        val counters = orDefault(emptyList<PageCounterDto>()) { api.homeworkCounters() }
        counters.firstOrNull { it.counterType == HomeworkStatus.ACTIVE }?.let { return it.counter }
        return api.allHomework(HomeworkStatus.ACTIVE, HomeworkType.HOMEWORK, maxItems = 60).size
    }

    fun scheduleForMonth(monthAnchor: String): Flow<Res<List<LessonDto>>> =
        cached("schedule-$monthAnchor", ListSerializer(LessonDto.serializer())) {
            api.scheduleByMonth(monthAnchor)
        }

    fun progress(): Flow<Res<ProgressBundle>> = cached(KEY_PROGRESS, ProgressBundle.serializer()) {
        coroutineScope {
            val visits = async { api.visits() }
            val exams = async { orDefault(emptyList<ExamResultDto>()) { api.exams() } }
            ProgressBundle(visits.await(), exams.await())
        }
    }

    // Задания грузятся по страницам (HomeworkFeed). В кэш кладу то, что уже
    // догрузилось, чтобы в следующий раз экран не был пустой.
    fun homeworkFeed(status: Int, type: Int): HomeworkFeed =
        HomeworkFeed { page -> api.homework(status, type, page = page) }

    suspend fun homeworkSnapshot(status: Int, type: Int): JsonCache.Entry<List<HomeworkDto>>? =
        withContext(Dispatchers.IO) { cache.read(homeworkKey(status, type), HOMEWORK_LIST) }

    suspend fun saveHomeworkSnapshot(status: Int, type: Int, items: List<HomeworkDto>) =
        withContext(Dispatchers.IO) {
            cache.write(homeworkKey(status, type), HOMEWORK_LIST, items.take(HOMEWORK_SNAPSHOT))
        }

    // Сколько заданий в каждом статусе — для подписей на фильтрах.
    fun homeworkCounters(): Flow<Res<List<PageCounterDto>>> =
        cached(KEY_HOMEWORK_COUNTERS, ListSerializer(PageCounterDto.serializer())) {
            api.homeworkCounters()
        }

    fun news(): Flow<Res<List<NewsDto>>> =
        cached(KEY_NEWS, ListSerializer(NewsDto.serializer())) { api.latestNews() }

    suspend fun newsDetail(id: Int): Res<NewsDetailDto> =
        fetchOnce("news-$id", NewsDetailDto.serializer()) { api.newsDetail(id) }

    fun leaderboards(): Flow<Res<Leaderboards>> = cached(KEY_LEADERS, Leaderboards.serializer()) {
        coroutineScope {
            val group = async { orDefault(emptyList<LeaderRowDto>()) { api.leaderGroup() } }
            val stream = async { orDefault(emptyList<LeaderRowDto>()) { api.leaderStream() } }
            Leaderboards(group.await(), stream.await())
        }
    }

    fun activity(): Flow<Res<List<ActivityDto>>> =
        cached(KEY_ACTIVITY, ListSerializer(ActivityDto.serializer())) { api.activity(limit = 60) }

    fun achievements(): Flow<Res<List<AchievementDto>>> =
        cached(KEY_ACHIEVEMENTS, ListSerializer(AchievementDto.serializer())) { api.achievements() }

    fun profile(): Flow<Res<UserInfoDto>> =
        cached(KEY_USER, UserInfoDto.serializer()) { api.userInfo() }

    suspend fun submitHomework(id: Int, answer: String, file: File?): Res<Unit> = try {
        api.submitHomework(id, answer, file)
        // Раньше тут чистился весь кэш вместе с расписанием и оценками,
        // хотя поменялись только задания и счётчик на главной.
        cache.invalidate(KEY_HOMEWORK_PREFIX)
        cache.invalidate(KEY_DASHBOARD)
        Res.Ok(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Res.Err(describe(e), offline = e is IOException)
    }

    // Для фоновой синхронизации, мимо кэша — нам нужны свежие.
    suspend fun peekActiveHomework(): List<HomeworkDto> =
        api.allHomework(HomeworkStatus.ACTIVE, HomeworkType.HOMEWORK, maxItems = 60)

    suspend fun peekVisits(): List<VisitDto> = api.visits()

    suspend fun peekNews(): List<NewsDto> = api.latestNews()

    fun library(materialType: Int): Flow<Res<List<LibraryMaterialDto>>> =
        cached("library-$materialType", ListSerializer(LibraryMaterialDto.serializer())) {
            api.library(materialType)
        }

    fun payments(): Flow<Res<Payments>> = cached(KEY_PAYMENTS, Payments.serializer()) {
        coroutineScope {
            val info = async { guarded { api.paymentInfo() } }
            val plan = async { guarded(emptyList<PaymentPlanDto>()) { api.paymentPlan() } }
            val history = async { guarded(emptyList<PaymentHistoryDto>()) { api.paymentHistory() } }
            Payments(info.await(), plan.await(), history.await())
        }
    }

    fun portfolio(): Flow<Res<Portfolio>> = cached(KEY_PORTFOLIO, Portfolio.serializer()) {
        coroutineScope {
            val works = async { guarded(emptyList<PortfolioWorkDto>()) { api.portfolioWorks() } }
            val requests =
                async { guarded(emptyList<PortfolioRequestDto>()) { api.portfolioRequests() } }
            Portfolio(works.await(), requests.await())
        }
    }

    // Текст ошибки для экрана, если грузим не через cached().
    fun describeError(e: Throwable): Res.Err = Res.Err(describe(e), offline = e is IOException)

    // Сначала отдаём снимок с диска, чтобы экран не был пустым, потом свежее.
    // Если сети нет — снимок остаётся, наверх уходит только текст ошибки.
    private fun <T> cached(
        key: String,
        serializer: KSerializer<T>,
        fetch: suspend () -> T,
    ): Flow<Res<T>> = flow {
        val snapshot = cache.read(key, serializer)
        if (snapshot != null) {
            emit(Res.Ok(snapshot.value, stale = true, savedAt = snapshot.savedAt))
        }
        try {
            val fresh = fetch()
            cache.write(key, serializer, fresh)
            emit(Res.Ok(fresh, stale = false, savedAt = System.currentTimeMillis()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val offline = e is IOException
            emit(
                Res.Err(
                    if (snapshot != null && offline) OFFLINE_MESSAGE else describe(e),
                    offline = offline,
                ),
            )
        }
    }.flowOn(Dispatchers.IO)

    // Всё на IO. Раньше кэш тут читался и писался прямо в главном потоке.
    private suspend fun <T> fetchOnce(
        key: String,
        serializer: KSerializer<T>,
        fetch: suspend () -> T,
    ): Res<T> = withContext(Dispatchers.IO) {
        try {
            val value = fetch()
            cache.write(key, serializer, value)
            Res.Ok(value)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val fallback = cache.read(key, serializer)
            if (fallback != null) {
                Res.Ok(fallback.value, stale = true, savedAt = fallback.savedAt)
            } else {
                Res.Err(describe(e), offline = e is IOException)
            }
        }
    }

    // Лимит одновременных потоков у API маленький: выпустишь всю главную разом —
    // часть запросов отвалится. Поэтому больше четырёх не пускаем.
    private suspend fun <T> guarded(block: suspend () -> T): T = fanOut.withPermit { block() }

    private suspend fun <T> guarded(fallback: T, block: suspend () -> T): T =
        fanOut.withPermit { orDefault(fallback, block) }

    private suspend fun <T> orDefault(fallback: T, block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        fallback
    }

    private fun describe(e: Throwable): String = when (e) {
        is ApiException -> e.message
        is IOException -> OFFLINE_MESSAGE
        else -> e.message ?: "Что-то пошло не так"
    }

    private fun homeworkKey(status: Int, type: Int) = "$KEY_HOMEWORK_PREFIX$type-$status"

    private val fanOut = Semaphore(MAX_PARALLEL_REQUESTS)

    private companion object {
        const val MAX_PARALLEL_REQUESTS = 4
        const val HOMEWORK_SNAPSHOT = 60
        const val KEY_CITIES = "cities"
        const val KEY_DASHBOARD = "dashboard"
        const val KEY_PROGRESS = "progress"
        const val KEY_HOMEWORK_PREFIX = "homework-"
        const val KEY_HOMEWORK_COUNTERS = "homework-counters"
        const val KEY_NEWS = "news"
        const val KEY_LEADERS = "leaders"
        const val KEY_ACTIVITY = "activity"
        const val KEY_ACHIEVEMENTS = "achievements"
        const val KEY_USER = "user-info"
        const val KEY_PAYMENTS = "payments"
        const val KEY_PORTFOLIO = "portfolio"
        const val OFFLINE_MESSAGE = "Нет соединения — показаны сохранённые данные"
        val HOMEWORK_LIST = ListSerializer(HomeworkDto.serializer())
    }
}
