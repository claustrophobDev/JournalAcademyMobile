package com.claustrophob.journal.data

import com.claustrophob.journal.data.net.HOMEWORK_PAGE_SIZE
import com.claustrophob.journal.data.net.HomeworkDto

// Задания по страницам. API больше 6 штук за раз не даёт, а раньше я качал
// сразу все страницы, и если проверенных работ под сотню — это штук 20 запросов
// на одно открытие. Теперь грузится одна страница, следующая — когда долистал.
// Дёргать только из одной корутины, не из разных потоков.
class HomeworkFeed(
    private val pageSize: Int = HOMEWORK_PAGE_SIZE,
    private val fetch: suspend (page: Int) -> List<HomeworkDto>,
) {
    private val collected = LinkedHashMap<Int, HomeworkDto>()

    // У каких-то филиалов страницы идут с 1, у каких-то с 0. Проверяем один раз.
    private var basePage: Int? = null
    private var nextPage = 0

    var endReached: Boolean = false
        private set

    val items: List<HomeworkDto> get() = collected.values.toList()

    // Заново с первой страницы. Старый список не чистим, пока не пришёл ответ,
    // а то без интернета он просто пропадёт.
    suspend fun refresh(): List<HomeworkDto> {
        var base = basePage ?: FIRST_PAGE
        var chunk = fetch(base)
        if (chunk.isEmpty() && basePage == null) {
            base = FALLBACK_PAGE
            chunk = fetch(base)
        }
        basePage = base
        collected.clear()
        chunk.forEach { collected.putIfAbsent(it.id, it) }
        nextPage = base + 1
        endReached = chunk.size < pageSize
        return items
    }

    suspend fun loadMore(): List<HomeworkDto> {
        if (endReached) return items
        if (basePage == null) return refresh()
        val chunk = fetch(nextPage)
        val before = collected.size
        chunk.forEach { collected.putIfAbsent(it.id, it) }
        nextPage++
        // Если на странице ничего нового, значит сервер игнорит номер страницы
        // и отдаёт одно и то же. Дальше листать смысла нет.
        endReached = chunk.size < pageSize || collected.size == before
        return items
    }

    // Показать то, что лежит в кэше, пока идёт первый запрос.
    fun seed(cached: List<HomeworkDto>) {
        if (collected.isNotEmpty()) return
        cached.forEach { collected.putIfAbsent(it.id, it) }
    }

    private companion object {
        const val FIRST_PAGE = 1
        const val FALLBACK_PAGE = 0
    }
}
