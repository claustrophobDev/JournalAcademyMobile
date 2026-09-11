package com.claustrophob.journal.data

import com.claustrophob.journal.data.net.ChartPointDto

// В графиках на главной по точке на месяц. Склеиваю два графика в один список
// месяцев, и выбор месяца есть без лишних запросов.
// Склеиваю по "yyyy-MM", потому что дни у точек бывают разные, а в одном
// графике может не быть месяца, который есть в другом.
internal fun mergeMonths(
    attendance: List<ChartPointDto>,
    average: List<ChartPointDto>,
): List<MonthPoint> {
    val byMonth = LinkedHashMap<String, MonthPoint>()
    attendance.forEach { point ->
        val key = monthKey(point.date) ?: return@forEach
        byMonth[key] = MonthPoint(point.date, point.points, byMonth[key]?.average ?: 0.0)
    }
    average.forEach { point ->
        val key = monthKey(point.date) ?: return@forEach
        val existing = byMonth[key]
        byMonth[key] = existing?.copy(average = point.points)
            ?: MonthPoint(point.date, 0.0, point.points)
    }
    return byMonth.values.sortedBy { it.date }
}

private fun monthKey(date: String): String? =
    date.take(7).takeIf { it.length == 7 && it[4] == '-' }
