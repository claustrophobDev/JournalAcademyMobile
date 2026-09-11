package com.claustrophob.journal.data.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

// Обёртка над API журнала. Пути подсмотрел в самом сайте, так что запросы
// уходят такие же, как из браузера.
class JournalApi(
    private val client: OkHttpClient,
    private val bare: OkHttpClient,
) {


    suspend fun cities(): List<CityDto> =
        journalJson.decodeFromString(getRaw("/public/cities", anonymous = true))


    suspend fun userInfo(): UserInfoDto =
        journalJson.decodeFromString(getRaw("/settings/user-info"))

    suspend fun achievements(): List<AchievementDto> =
        journalJson.decodeFromString(getRaw("/profile/statistic/student-achievements"))

    suspend fun groups(): List<GroupDto> =
        journalJson.decodeFromString(getRaw("/settings/admin-groups"))

    // расписание

    suspend fun scheduleByDate(date: String): List<LessonDto> =
        journalJson.decodeFromString(
            getRaw("/schedule/operations/get-by-date", mapOf("date_filter" to date)),
        )

    suspend fun scheduleByMonth(monthAnchor: String): List<LessonDto> =
        journalJson.decodeFromString(
            getRaw("/schedule/operations/get-month", mapOf("date_filter" to monthAnchor)),
        )

    suspend fun monthEvents(monthAnchor: String): List<EventDto> =
        journalJson.decodeFromString(
            getRaw("/schedule/operations/month-events", mapOf("date_filter" to monthAnchor)),
        )


    suspend fun visits(): List<VisitDto> =
        journalJson.decodeFromString(getRaw("/progress/operations/student-visits"))

    suspend fun exams(): List<ExamResultDto> =
        journalJson.decodeFromString(getRaw("/progress/operations/student-exams"))

    // домашка

    // status — из HomeworkStatus, ALL это всё подряд.
    // type: 0 домашка, 1 лабораторная.
    suspend fun homework(
        status: Int,
        type: Int,
        page: Int = 1,
        limit: Int = HOMEWORK_PAGE_SIZE,
    ): List<HomeworkDto> =
        journalJson.decodeFromString(
            getRaw(
                "/homework/operations/list",
                mapOf(
                    "status" to status.toString(),
                    "type" to type.toString(),
                    "page" to page.toString(),
                    "limit" to limit.toString(),
                ),
            ),
        )

    // Сервер отдаёт максимум HOMEWORK_PAGE_SIZE за раз, поэтому листаем страницами.
    // С какой страницы начинается нумерация — зависит от филиала: если с первой
    // пусто, пробуем с нулевой.
    suspend fun allHomework(
        status: Int,
        type: Int,
        maxItems: Int = 150,
    ): List<HomeworkDto> {
        val fromOne = walkPages(status, type, firstPage = 1, maxItems = maxItems)
        if (fromOne.isNotEmpty()) return fromOne
        return walkPages(status, type, firstPage = 0, maxItems = maxItems)
    }

    private suspend fun walkPages(
        status: Int,
        type: Int,
        firstPage: Int,
        maxItems: Int,
    ): List<HomeworkDto> {
        val collected = LinkedHashMap<Int, HomeworkDto>()
        var page = firstPage
        while (collected.size < maxItems) {
            val chunk = homework(status, type, page = page, limit = HOMEWORK_PAGE_SIZE)
            if (chunk.isEmpty()) break
            chunk.forEach { collected.putIfAbsent(it.id, it) }
            if (chunk.size < HOMEWORK_PAGE_SIZE) break
            page++
        }
        return collected.values.toList()
    }

    suspend fun homeworkCounters(): List<PageCounterDto> =
        journalJson.decodeFromString(getRaw("/count/homework"))

    suspend fun submitHomework(
        homeworkId: Int,
        answerText: String,
        file: File?,
        spentHours: Int = 0,
        spentMinutes: Int = 0,
    ): HomeworkStudDto = withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("id", homeworkId.toString())
            .addFormDataPart("answerText", answerText)
            .addFormDataPart("spentTimeHour", spentHours.toString())
            .addFormDataPart("spentTimeMin", spentMinutes.toString())
            .apply {
                if (file != null) {
                    addFormDataPart(
                        "file",
                        file.name,
                        file.asRequestBody("application/octet-stream".toMediaTypeOrNull()),
                    )
                } else {
                    addFormDataPart("file", "")
                }
            }
            .build()
        val request = Request.Builder()
            .url("${Api.BASE}/homework/operations/create")
            .post(body)
            .build()
        journalJson.decodeFromString(execute(client, request))
    }


    suspend fun latestNews(): List<NewsDto> =
        journalJson.decodeFromString(getRaw("/news/operations/latest-news"))

    suspend fun newsDetail(id: Int): NewsDetailDto =
        journalJson.decodeFromString(
            getRaw("/news/operations/detail-news", mapOf("news_id" to id.toString())),
        )

    suspend fun unreadNewsCount(): Int =
        getRaw("/news/operations/count-unread").trim().toIntOrNull() ?: 0

    // всё для главной

    suspend fun attendanceChart(): List<ChartPointDto> =
        journalJson.decodeFromString(getRaw("/dashboard/chart/attendance"))

    suspend fun averageProgressChart(): List<ChartPointDto> =
        journalJson.decodeFromString(getRaw("/dashboard/chart/average-progress"))

    suspend fun attendanceStat(): AttendanceStatDto =
        journalJson.decodeFromString(getRaw("/dashboard/progress/attendance-statistic"))

    suspend fun academicPerformance(): AcademicPerformanceDto =
        journalJson.decodeFromString(getRaw("/dashboard/progress/academic-performance"))

    suspend fun leaderGroup(): List<LeaderRowDto> =
        journalJson.decodeFromString(getRaw("/dashboard/progress/leader-group"))

    suspend fun leaderStream(): List<LeaderRowDto> =
        journalJson.decodeFromString(getRaw("/dashboard/progress/leader-stream"))

    suspend fun leaderGroupPosition(): LeaderPositionDto =
        journalJson.decodeFromString(getRaw("/dashboard/progress/leader-group-points"))

    suspend fun leaderStreamPosition(): LeaderPositionDto =
        journalJson.decodeFromString(getRaw("/dashboard/progress/leader-stream-points"))

    suspend fun futureExams(): List<FutureExamDto> =
        journalJson.decodeFromString(getRaw("/dashboard/info/future-exams"))

    suspend fun activity(limit: Int = 30): List<ActivityDto> =
        journalJson.decodeFromString(
            getRaw("/dashboard/progress/activity", mapOf("limit" to limit.toString())),
        )


    suspend fun library(materialType: Int, specId: Int? = null): List<LibraryMaterialDto> =
        journalJson.decodeFromString(
            getRaw(
                "/library/operations/list",
                buildMap {
                    put("material_type", materialType.toString())
                    specId?.let { put("spec_id", it.toString()) }
                },
            ),
        )

    // оплата

    suspend fun paymentInfo(): PaymentInfoDto =
        journalJson.decodeFromString(getRaw("/payment/operations/index"))

    suspend fun paymentPlan(): List<PaymentPlanDto> =
        journalJson.decodeFromString(getRaw("/payment/operations/schedule"))

    suspend fun paymentHistory(): List<PaymentHistoryDto> =
        journalJson.decodeFromString(getRaw("/payment/operations/history"))


    suspend fun portfolioWorks(): List<PortfolioWorkDto> =
        journalJson.decodeFromString(getRaw("/portfolio/operations/list"))

    suspend fun portfolioRequests(): List<PortfolioRequestDto> =
        journalJson.decodeFromString(getRaw("/portfolio/operations/history"))

    // дальше внутренняя кухня

    private suspend fun getRaw(
        path: String,
        params: Map<String, String> = emptyMap(),
        anonymous: Boolean = false,
    ): String = withContext(Dispatchers.IO) {
        val url = "${Api.BASE}$path".toHttpUrl().newBuilder().apply {
            params.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        val request = Request.Builder().url(url).get().apply {
            if (anonymous) header(AuthInterceptor.SKIP_AUTH, "1")
        }.build()
        execute(if (anonymous) bare else client, request)
    }

    private fun execute(httpClient: OkHttpClient, request: Request): String {
        httpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.isSuccessful) return text
            throw response.code.toApiException(text)
        }
    }

    private fun Int.toApiException(payload: String): ApiException {
        val fields = runCatching {
            journalJson.decodeFromString<List<FieldErrorDto>>(payload)
        }.getOrDefault(emptyList())
        val message = fields.firstOrNull()?.message
            ?: runCatching {
                journalJson.decodeFromString<FieldErrorDto>(payload).message.ifBlank { null }
            }.getOrNull()
            ?: defaultMessageFor(this)
        return ApiException(this, message, fields)
    }

    private fun defaultMessageFor(code: Int): String = when (code) {
        401 -> "Сессия истекла"
        403 -> "Доступ запрещён"
        404 -> "Данные не найдены"
        422 -> "Неверные данные"
        in 500..599 -> "Сервер журнала недоступен"
        else -> "Ошибка запроса ($code)"
    }
}

// Больше шести /homework/operations/list отвечает 422.
const val HOMEWORK_PAGE_SIZE = 6

object HomeworkStatus {
    const val EXPIRED = 0
    const val DONE = 1
    const val ON_REVIEW = 2
    const val ACTIVE = 3
    const val ALL = 4
    const val DELETED = 5
}

object HomeworkType {
    const val HOMEWORK = 0
    const val LABORATORY = 1
}

// Типы материалов в библиотеке.
object LibraryType {
    const val HOMEWORK = 1
    const val LESSON = 2
    const val LABORATORY = 3
    const val BOOK = 4
    const val VIDEO = 5
    const val PRESENTATION = 6
    const val QUIZ = 7
    const val ARTICLE = 8
}

object VisitStatus {
    const val ABSENT = 0
    const val PRESENT = 1
    const val LATE = 2
}
