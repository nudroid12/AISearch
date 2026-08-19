package com.nudroid12.aisearch

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class ApiKeyStore(context: Context) {
    private val prefs =
        context.getSharedPreferences("secure_settings", Context.MODE_PRIVATE)
    private val alias = "aisearch_groq_key"

    fun save(apiKey: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8))

        prefs.edit()
            .putString(
                "key_data",
                Base64.encodeToString(encrypted, Base64.NO_WRAP)
            )
            .putString(
                "key_iv",
                Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
            )
            .apply()
    }

    fun load(): String? {
        val data = prefs.getString("key_data", null) ?: return null
        val iv = prefs.getString("key_iv", null) ?: return null

        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(
                    128,
                    Base64.decode(iv, Base64.NO_WRAP)
                )
            )
            String(
                cipher.doFinal(
                    Base64.decode(data, Base64.NO_WRAP)
                ),
                Charsets.UTF_8
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore =
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

        (keyStore.getKey(alias, null) as? SecretKey)?.let {
            return it
        }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )

        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or
                    KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(
                    KeyProperties.ENCRYPTION_PADDING_NONE
                )
                .build()
        )

        return generator.generateKey()
    }
}
