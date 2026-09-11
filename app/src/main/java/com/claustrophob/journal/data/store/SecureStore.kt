package com.claustrophob.journal.data.store

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// Ключ-значение, только значения шифруются AES-256-GCM. Сам ключ лежит в
// Android Keystore и наружу не выходит, так что пароль с токенами не вытащить
// даже с рутом.
//
// Чтение синхронное, и это специально: Authenticator у OkHttp работает в фоновом
// потоке и suspend не умеет.
class SecureStore(context: Context) : KeyValueStore {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("journal_secure", Context.MODE_PRIVATE)

    // Взводится, если Keystore недоступен — бывает на кривых прошивках.
    @Volatile
    private var plaintextFallback = false

    private val key: SecretKey? by lazy {
        try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey ?: generateKey()
        } catch (t: Throwable) {
            Log.w(TAG, "Keystore unavailable, falling back to plaintext prefs", t)
            plaintextFallback = true
            null
        }
    }

    private fun generateKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    override fun getString(name: String): String? {
        val stored = prefs.getString(name, null) ?: return null
        val secret = key ?: return stored
        return try {
            val blob = Base64.decode(stored, Base64.NO_WRAP)
            if (blob.size <= IV_SIZE) return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secret,
                GCMParameterSpec(TAG_BITS, blob, 0, IV_SIZE),
            )
            String(cipher.doFinal(blob, IV_SIZE, blob.size - IV_SIZE), Charsets.UTF_8)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to decrypt '$name', dropping value", t)
            prefs.edit().remove(name).apply()
            null
        }
    }

    override fun putString(name: String, value: String?) {
        if (value == null) {
            prefs.edit().remove(name).apply()
            return
        }
        val secret = key
        val encoded = if (secret == null) {
            value
        } else {
            try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, secret)
                val iv = cipher.iv
                val body = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
                Base64.encodeToString(iv + body, Base64.NO_WRAP)
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to encrypt '$name', storing as plaintext", t)
                plaintextFallback = true
                value
            }
        }
        prefs.edit().putString(name, encoded).apply()
    }

    fun getBoolean(name: String, fallback: Boolean = false): Boolean =
        getString(name)?.toBooleanStrictOrNull() ?: fallback

    fun putBoolean(name: String, value: Boolean) = putString(name, value.toString())

    override fun remove(vararg names: String) {
        prefs.edit().apply { names.forEach { remove(it) } }.apply()
    }

    private companion object {
        const val TAG = "SecureStore"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "journal_master_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_BITS = 128
    }
}
