package com.wisechat.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.wisechat.app.domain.model.ReplyLength
import com.wisechat.app.domain.model.ReplyStyle
import com.wisechat.app.domain.model.ToneIntensity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "wise_chat_preferences")

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

class PreferencesManager(private val context: Context) {

    private object PreferencesKeys {
        val OVERLAY_ENABLED = booleanPreferencesKey("overlay_enabled")
        val DEFAULT_PROVIDER_ID = stringPreferencesKey("default_provider_id")
        val DEFAULT_STYLE = stringPreferencesKey("default_style")
        val DEFAULT_INTENSITY = stringPreferencesKey("default_intensity")
        val DEFAULT_LENGTH = stringPreferencesKey("default_length")
        val DEFAULT_LANGUAGE = stringPreferencesKey("default_language")
        val HISTORY_ENABLED = booleanPreferencesKey("history_enabled")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val THEME_MODE = stringPreferencesKey("theme_mode_enum")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val ACTIVE_PROVIDER_CONFIGS = stringPreferencesKey("active_provider_configs_json")
        val CUSTOM_STYLES_JSON = stringPreferencesKey("custom_styles_json")
    }

    val overlayEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.OVERLAY_ENABLED] ?: false
    }

    val defaultProviderId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.DEFAULT_PROVIDER_ID]
    }

    val defaultStyle: Flow<ReplyStyle> = context.dataStore.data.map { preferences ->
        val name = preferences[PreferencesKeys.DEFAULT_STYLE] ?: ReplyStyle.CASUAL.name
        try {
            ReplyStyle.valueOf(name)
        } catch (_: Exception) {
            ReplyStyle.CASUAL
        }
    }

    val defaultIntensity: Flow<ToneIntensity> = context.dataStore.data.map { preferences ->
        val name = preferences[PreferencesKeys.DEFAULT_INTENSITY] ?: ToneIntensity.BALANCED.name
        try {
            ToneIntensity.valueOf(name)
        } catch (_: Exception) {
            ToneIntensity.BALANCED
        }
    }

    val defaultLength: Flow<ReplyLength> = context.dataStore.data.map { preferences ->
        val name = preferences[PreferencesKeys.DEFAULT_LENGTH] ?: ReplyLength.SHORT.name
        try {
            ReplyLength.valueOf(name)
        } catch (_: Exception) {
            ReplyLength.SHORT
        }
    }

    val defaultLanguage: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.DEFAULT_LANGUAGE] ?: "English"
    }

    val historyEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.HISTORY_ENABLED] ?: true
    }

    val isDarkTheme: Flow<Boolean?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.DARK_THEME]
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { preferences ->
        val modeStr = preferences[PreferencesKeys.THEME_MODE]
        if (modeStr != null) {
            try {
                ThemeMode.valueOf(modeStr)
            } catch (_: Exception) {
                ThemeMode.SYSTEM
            }
        } else {
            when (preferences[PreferencesKeys.DARK_THEME]) {
                true -> ThemeMode.DARK
                false -> ThemeMode.LIGHT
                null -> ThemeMode.SYSTEM
            }
        }
    }

    val onboardingCompleted: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.ONBOARDING_COMPLETED] ?: false
    }

    val providerConfigsJson: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.ACTIVE_PROVIDER_CONFIGS]
    }

    val customStylesJson: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.CUSTOM_STYLES_JSON]
    }

    suspend fun setOverlayEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.OVERLAY_ENABLED] = enabled }
    }

    suspend fun setDefaultProviderId(providerId: String) {
        context.dataStore.edit { it[PreferencesKeys.DEFAULT_PROVIDER_ID] = providerId }
    }

    suspend fun setDefaultStyle(style: ReplyStyle) {
        context.dataStore.edit { it[PreferencesKeys.DEFAULT_STYLE] = style.name }
    }

    suspend fun setDefaultIntensity(intensity: ToneIntensity) {
        context.dataStore.edit { it[PreferencesKeys.DEFAULT_INTENSITY] = intensity.name }
    }

    suspend fun setDefaultLength(length: ReplyLength) {
        context.dataStore.edit { it[PreferencesKeys.DEFAULT_LENGTH] = length.name }
    }

    suspend fun setDefaultLanguage(language: String) {
        context.dataStore.edit { it[PreferencesKeys.DEFAULT_LANGUAGE] = language }
    }

    suspend fun setHistoryEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.HISTORY_ENABLED] = enabled }
    }

    suspend fun setDarkTheme(isDark: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.DARK_THEME] = isDark }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit {
            it[PreferencesKeys.THEME_MODE] = mode.name
            when (mode) {
                ThemeMode.SYSTEM -> it.remove(PreferencesKeys.DARK_THEME)
                ThemeMode.LIGHT -> it[PreferencesKeys.DARK_THEME] = false
                ThemeMode.DARK -> it[PreferencesKeys.DARK_THEME] = true
            }
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun saveProviderConfigsJson(json: String) {
        context.dataStore.edit { it[PreferencesKeys.ACTIVE_PROVIDER_CONFIGS] = json }
    }

    suspend fun saveCustomStylesJson(json: String) {
        context.dataStore.edit { it[PreferencesKeys.CUSTOM_STYLES_JSON] = json }
    }
}
