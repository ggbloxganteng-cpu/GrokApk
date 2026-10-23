package com.mercury3.assistant.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecurePrefs(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "grok_secure",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var apiKey: String
        get() = prefs.getString("api_key", "") ?: ""
        set(v) = prefs.edit().putString("api_key", v).apply()

    var baseUrl: String
        get() = prefs.getString("base_url", "https://api.inceptionai.com/v1/") ?: "https://api.inceptionai.com/v1/"
        set(v) = prefs.edit().putString("base_url", v).apply()

    var temperature: Float
        get() = prefs.getFloat("temp", 0.3f)
        set(v) = prefs.edit().putFloat("temp", v).apply()

    var maxTokens: Int
        get() = prefs.getInt("max_tokens", 1024)
        set(v) = prefs.edit().putInt("max_tokens", v).apply()

    var privacyMode: Boolean
        get() = prefs.getBoolean("privacy", false)
        set(v) = prefs.edit().putBoolean("privacy", v).apply()

    var bubbleX: Int
        get() = prefs.getInt("bx", -1)
        set(v) = prefs.edit().putInt("bx", v).apply()

    var bubbleY: Int
        get() = prefs.getInt("by", -1)
        set(v) = prefs.edit().putInt("by", v).apply()
}
