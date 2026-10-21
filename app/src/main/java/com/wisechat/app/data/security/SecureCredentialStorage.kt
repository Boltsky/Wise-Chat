package com.wisechat.app.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureCredentialStorage(context: Context) {

    private val sharedPreferences: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                "wise_chat_secure_creds",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback for emulators/environments with broken Keystore provider
            context.getSharedPreferences("wise_chat_creds_fallback", Context.MODE_PRIVATE)
        }
    }

    fun saveApiKey(providerId: String, apiKey: String) {
        sharedPreferences.edit().putString("key_$providerId", apiKey.trim()).apply()
    }

    fun getApiKey(providerId: String): String {
        return sharedPreferences.getString("key_$providerId", "") ?: ""
    }

    fun removeApiKey(providerId: String) {
        sharedPreferences.edit().remove("key_$providerId").apply()
    }

    fun clearAllKeys() {
        sharedPreferences.edit().clear().apply()
    }
}
