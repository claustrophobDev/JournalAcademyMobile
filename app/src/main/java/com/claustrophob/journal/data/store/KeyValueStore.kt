package com.claustrophob.journal.data.store

// Интерфейс для хранилища, чтобы SessionStore можно было тестить
// без Android Keystore.
interface KeyValueStore {
    fun getString(name: String): String?
    fun putString(name: String, value: String?)
    fun remove(vararg names: String)

    fun getLong(name: String, fallback: Long = 0L): Long =
        getString(name)?.toLongOrNull() ?: fallback

    fun putLong(name: String, value: Long) = putString(name, value.toString())

    fun getInt(name: String, fallback: Int = 0): Int = getString(name)?.toIntOrNull() ?: fallback

    fun putInt(name: String, value: Int) = putString(name, value.toString())
}
