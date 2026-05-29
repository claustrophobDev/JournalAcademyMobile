package com.claustrophob.journal.util

// Две причины, по которым правильные с виду логин и пароль не принимаются.
//
// Первая — раскладка: кириллическая "С" и латинская "C" выглядят одинаково, а на
// экране разницы вообще не видно. Вторая — пробел в начале или конце, его любит
// подставлять клавиатура при свайпе.
//
// Чинить молча нельзя: и то и другое может быть частью настоящего пароля.
// Поэтому просто предупреждаем и предлагаем поправить одной кнопкой.
object LoginHints {

    private val CYRILLIC_RANGE = 'Ѐ'..'ӿ'

    // Кириллица, неотличимая от латиницы.
    private val LOOKALIKES = mapOf(
        'А' to 'A', 'В' to 'B', 'Е' to 'E', 'К' to 'K', 'М' to 'M', 'Н' to 'H',
        'О' to 'O', 'Р' to 'P', 'С' to 'C', 'Т' to 'T', 'У' to 'Y', 'Х' to 'X',
        'І' to 'I', 'Ј' to 'J', 'Ѕ' to 'S',
        'а' to 'a', 'е' to 'e', 'о' to 'o', 'р' to 'p', 'с' to 'c', 'у' to 'y',
        'х' to 'x', 'і' to 'i', 'ј' to 'j', 'ѕ' to 's', 'һ' to 'h', 'ԁ' to 'd',
    )

    fun hasCyrillic(value: String): Boolean = value.any { it in CYRILLIC_RANGE }

    fun toLatin(value: String): String =
        value.map { LOOKALIKES[it] ?: it }.joinToString("")

    // true, если всю кириллицу заменили. Иначе это просто русские буквы, не наш случай.
    fun fixableLayout(value: String): Boolean =
        hasCyrillic(value) && !hasCyrillic(toLatin(value))

    fun hasEdgeSpace(value: String): Boolean = value.isNotEmpty() && value != value.trim()
}
