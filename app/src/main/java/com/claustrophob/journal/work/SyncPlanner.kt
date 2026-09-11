package com.claustrophob.journal.work

import com.claustrophob.journal.util.Dates

// Решает, про что слать уведомление. Вынес из воркера, чтобы тестить.
//
// Раньше был просто список уже показанных id, и он обрезался до 400. Оценок
// за два года больше, старые вылетали из списка и через три часа приходили
// снова как новые, даже прошлогодние. Теперь у каждой записи есть дата: что
// старше двух недель — не присылаем вообще, а помним только последние 45 дней.
object SyncPlanner {

    // Что старше — не присылаем, даже если видим первый раз.
    const val FRESH_DAYS = 14

    // Сколько помним показанное. Должно быть с запасом больше FRESH_DAYS,
    // а то запись забудется, пока ещё свежая, и придёт второй раз.
    const val REMEMBER_DAYS = 45

    data class Plan<T>(
        // О чём сказать, самое новое первым.
        val notify: List<T>,
        // Что сохранить как "уже видели", в виде "yyyy-MM-dd|key".
        val remember: Set<String>,
    )

    fun <T> plan(
        items: List<T>,
        keyOf: (T) -> String,
        dateOf: (T) -> String,
        known: Set<String>,
        isBaseline: Boolean,
        today: String,
        freshDays: Int = FRESH_DAYS,
    ): Plan<T> {
        val knownKeys = known.mapTo(HashSet()) { it.substringAfter(SEPARATOR) }

        val dated = items.mapNotNull { item ->
            val date = dateOf(item).take(10)
            val age = Dates.daysBetween(date, today) ?: return@mapNotNull null
            Triple(item, date, age)
        }

        // Даты из будущего не считаем, но на день вперёд можно — часовые пояса
        // у сервера и телефона бывают разные.
        val recent = dated.filter { (_, _, age) -> age in -1..REMEMBER_DAYS }

        val remember = buildSet {
            known.filterTo(this) { entry ->
                val age = Dates.daysBetween(entry.substringBefore(SEPARATOR), today)
                age != null && age in -1..REMEMBER_DAYS
            }
            recent.forEach { (item, date, _) -> add(date + SEPARATOR + keyOf(item)) }
        }

        val notify = if (isBaseline) {
            emptyList()
        } else {
            recent
                .filter { (item, _, age) -> age <= freshDays && keyOf(item) !in knownKeys }
                .sortedByDescending { (_, date, _) -> date }
                .map { it.first }
        }

        return Plan(notify, remember)
    }

    private const val SEPARATOR = '|'
}
