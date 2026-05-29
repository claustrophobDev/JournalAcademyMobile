package com.claustrophob.journal.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Даты через Calendar, а не java.time, чтобы работало на Android 7.
object Dates {

    private val ru: Locale = Locale.forLanguageTag("ru-RU")

    private val apiFormat get() = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val dayMonth get() = SimpleDateFormat("d MMMM", ru)
    private val dayMonthShort get() = SimpleDateFormat("d MMM", ru)
    private val dayMonthYear get() = SimpleDateFormat("d MMMM yyyy", ru)
    private val dayMonthShortYear get() = SimpleDateFormat("d MMM yyyy", ru)
    private val monthYear get() = SimpleDateFormat("LLLL yyyy", ru)
    private val monthShort get() = SimpleDateFormat("LLL", ru)
    private val weekday get() = SimpleDateFormat("EEEE", ru)
    private val weekdayShort get() = SimpleDateFormat("EE", ru)

    val weekdayHeaders = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")

    fun today(): String = apiFormat.format(Date())

    fun format(date: Date): String = apiFormat.format(date)

    fun parse(value: String): Date? = runCatching {
        apiFormat.parse(value.take(10))
    }.getOrNull()

    fun firstOfMonth(date: Date): String = calendar(date).apply {
        set(Calendar.DAY_OF_MONTH, 1)
    }.let { apiFormat.format(it.time) }

    // Начало учебного года: с сентября. До сентября — ещё прошлый год.
    fun academicYearStart(today: String): String {
        val year = today.take(4).toIntOrNull() ?: return today
        val month = today.drop(5).take(2).toIntOrNull() ?: return today
        return if (month >= 9) "$year-09-01" else "${year - 1}-09-01"
    }

    fun shiftDay(day: String, delta: Int): String {
        val base = parse(day) ?: Date()
        return calendar(base).apply { add(Calendar.DAY_OF_MONTH, delta) }
            .let { apiFormat.format(it.time) }
    }

    fun shiftMonth(anchor: String, delta: Int): String {
        val base = parse(anchor) ?: Date()
        return calendar(base).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, delta)
        }.let { apiFormat.format(it.time) }
    }

    fun monthTitle(anchor: String): String {
        val date = parse(anchor) ?: return anchor
        return monthYear.format(date).replaceFirstChar { it.uppercase(ru) }
    }

    // Короткое имя месяца для переключателя: "сен".
    fun monthShortName(value: String): String {
        val date = parse(value) ?: return value.take(7)
        return monthShort.format(date).trimEnd('.').replaceFirstChar { it.uppercase(ru) }
    }

    fun humanDay(value: String): String {
        val date = parse(value) ?: return value
        return dayMonth.format(date)
    }

    fun humanDayShort(value: String): String {
        val date = parse(value) ?: return value
        return dayMonthShort.format(date)
    }

    // Короткая дата с годом, например для зачисления.
    fun humanDayShortWithYear(value: String): String {
        val date = parse(value) ?: return value
        return dayMonthShortYear.format(date)
    }

    fun humanFull(value: String): String {
        val date = parse(value) ?: return value
        return dayMonthYear.format(date)
    }

    fun weekdayName(value: String): String {
        val date = parse(value) ?: return ""
        return weekday.format(date).replaceFirstChar { it.uppercase(ru) }
    }

    fun weekdayShortName(value: String): String {
        val date = parse(value) ?: return ""
        return weekdayShort.format(date)
    }

    fun isToday(value: String): Boolean = value.take(10) == today()

    // Сколько дней до даты. Минус — значит уже прошла.
    fun daysUntil(value: String): Int? {
        val date = parse(value) ?: return null
        val diff = startOfDay(date).time - startOfDay(Date()).time
        return Math.round(diff / 86_400_000.0).toInt()
    }

    // "сегодня", "завтра", "через 3 дня", "3 дня назад".
    fun relativeDayLabel(value: String): String? {
        val days = daysUntil(value) ?: return null
        return when {
            days == 0 -> "сегодня"
            days == 1 -> "завтра"
            days == 2 -> "послезавтра"
            days in 3..30 -> "через " + plural(days, "день", "дня", "дней")
            days == -1 -> "вчера"
            days in -30..-2 -> plural(-days, "день", "дня", "дней") + " назад"
            else -> null
        }
    }

    private fun plural(count: Int, one: String, few: String, many: String): String {
        val mod100 = count % 100
        val mod10 = count % 10
        val word = when {
            mod100 in 11..14 -> many
            mod10 == 1 -> one
            mod10 in 2..4 -> few
            else -> many
        }
        return "$count $word"
    }

    // Сколько дней от from до to (плюс — to позже). Сегодня передаём
    // снаружи, так проще тестить.
    fun daysBetween(from: String, to: String): Int? {
        val start = parse(from) ?: return null
        val end = parse(to) ?: return null
        val diff = startOfDay(end).time - startOfDay(start).time
        return Math.round(diff / 86_400_000.0).toInt()
    }

    fun isPast(value: String): Boolean {
        val date = parse(value) ?: return false
        return startOfDay(date).before(startOfDay(Date()))
    }

    // Все дни месяца строками yyyy-MM-dd.
    fun daysOfMonth(anchor: String): List<String> {
        val base = parse(anchor) ?: Date()
        val calendar = calendar(base).apply { set(Calendar.DAY_OF_MONTH, 1) }
        val count = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        return (1..count).map { day ->
            calendar.set(Calendar.DAY_OF_MONTH, day)
            apiFormat.format(calendar.time)
        }
    }

    // 0 понедельник, 6 воскресенье. Порядок как в weekdayHeaders.
    fun weekdayIndex(value: String): Int {
        val date = parse(value) ?: return 0
        val raw = calendar(date).get(Calendar.DAY_OF_WEEK)
        return (raw + 5) % 7
    }

    // Насколько свежий кэш: "только что", "12 мин назад".
    fun sinceLabel(timestamp: Long): String {
        if (timestamp <= 0L) return ""
        val minutes = (System.currentTimeMillis() - timestamp) / 60_000L
        return when {
            minutes < 1 -> "только что"
            minutes < 60 -> "$minutes мин назад"
            minutes < 60 * 24 -> "${minutes / 60} ч назад"
            else -> "${minutes / (60 * 24)} дн назад"
        }
    }

    // Из "2026-09-05 14:30:00" делаем что-то читаемое.
    fun timestampLabel(value: String): String {
        if (value.isBlank()) return ""
        val datePart = value.take(10)
        val timePart = value.drop(11).take(5)
        val human = parse(datePart)?.let { dayMonthShort.format(it) } ?: datePart
        return if (timePart.isBlank()) human else "$human, $timePart"
    }

    fun hhmm(value: String): String = value.take(5)

    // Время начала/конца пары в миллисекундах.
    // Дата и время приходят отдельно: "2026-09-07" и "09:00".
    // Если не разобралось — возвращаем null, а не полночь: иначе выводы будут кривые.
    fun momentOf(date: String, time: String): Long? {
        val day = parse(date.take(10)) ?: return null
        val hhmm = time.take(5)
        if (hhmm.length < 5 || hhmm[2] != ':') return null

        val hours = hhmm.take(2).toIntOrNull() ?: return null
        val minutes = hhmm.drop(3).take(2).toIntOrNull() ?: return null
        if (hours !in 0..23 || minutes !in 0..59) return null

        return calendar(day).apply {
            set(Calendar.HOUR_OF_DAY, hours)
            set(Calendar.MINUTE, minutes)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun calendar(date: Date) = Calendar.getInstance(ru).apply { time = date }

    private fun startOfDay(date: Date) = calendar(date).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.time
}
