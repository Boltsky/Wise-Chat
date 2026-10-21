package com.wisechat.app.data.repository

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.wisechat.app.data.preferences.PreferencesManager
import com.wisechat.app.data.security.SecureCredentialStorage
import com.wisechat.app.domain.model.AiProviderConfig
import com.wisechat.app.domain.model.AuthMode
import com.wisechat.app.domain.model.ProviderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

data class StoredProviderDto(
    val id: String,
    val type: String,
    val name: String,
    val baseUrl: String,
    val model: String,
    val isEnabled: Boolean,
    val isDefault: Boolean,
    val timeoutSeconds: Int,
    val customBodyTemplate: String,
    val customResponsePath: String,
    val authMode: String? = null,
    val accountEmail: String? = null,
    val isConnected: Boolean? = null
)

class ProviderRepository(
    private val preferencesManager: PreferencesManager,
    private val secureStorage: SecureCredentialStorage
) {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val listType = Types.newParameterizedType(List::class.java, StoredProviderDto::class.java)
    private val adapter = moshi.adapter<List<StoredProviderDto>>(listType)

    val providersFlow: Flow<List<AiProviderConfig>> = preferencesManager.providerConfigsJson.map { json ->
        if (json.isNullOrBlank()) {
            getDefaultProviders()
        } else {
            try {
                val dtos = adapter.fromJson(json) ?: emptyList()
                if (dtos.isEmpty()) {
                    getDefaultProviders()
                } else {
                    dtos.map { dto ->
                        val storedKey = secureStorage.getApiKey(dto.id)
                        val providerType = try {
                            ProviderType.valueOf(dto.type)
                        } catch (_: Exception) {
                            ProviderType.GEMINI
                        }
                        val key = storedKey

                        val model = if (providerType == ProviderType.GEMINI && (dto.model == "gemini-1.5-flash" || dto.model == "gemini-2.0-flash")) {
                            "gemini-flash-lite-latest"
                        } else dto.model

                        val authMode = try {
                            dto.authMode?.let { AuthMode.valueOf(it) } ?: AuthMode.BYOK
                        } catch (_: Exception) {
                            AuthMode.BYOK
                        }

                        AiProviderConfig(
                            id = dto.id,
                            type = providerType,
                            name = dto.name,
                            apiKey = key,
                            baseUrl = dto.baseUrl,
                            model = model,
                            isEnabled = dto.isEnabled,
                            isDefault = dto.isDefault,
                            timeoutSeconds = dto.timeoutSeconds,
                            customBodyTemplate = dto.customBodyTemplate,
                            customResponsePath = dto.customResponsePath,
                            authMode = authMode,
                            accountEmail = dto.accountEmail,
                            isConnected = dto.isConnected ?: (key.isNotBlank())
                        )
                    }
                }
            } catch (_: Exception) {
                getDefaultProviders()
            }
        }
    }

    suspend fun getProviders(): List<AiProviderConfig> {
        return providersFlow.firstOrNull() ?: getDefaultProviders()
    }

    suspend fun getActiveProvider(): AiProviderConfig {
        val providers = getProviders()
        return providers.firstOrNull { it.isDefault && it.isEnabled }
            ?: providers.firstOrNull { it.isEnabled }
            ?: getDefaultProviders().first()
    }

    suspend fun saveProvider(config: AiProviderConfig) {
        val current = getProviders().toMutableList()
        val index = current.indexOfFirst { it.id == config.id }

        // Securely persist API key or service account token
        if (config.apiKey.isNotBlank()) {
            secureStorage.saveApiKey(config.id, config.apiKey)
        }

        val updatedConfig = if (config.isDefault) {
            // Unset other defaults
            current.indices.forEach { i ->
                if (current[i].id != config.id) {
                    current[i] = current[i].copy(isDefault = false)
                }
            }
            config
        } else config

        if (index >= 0) {
            current[index] = updatedConfig
        } else {
            current.add(updatedConfig)
        }

        saveToDataStore(current)
    }

    suspend fun deleteProvider(id: String) {
        secureStorage.removeApiKey(id)
        val current = getProviders().filterNot { it.id == id }.toMutableList()
        if (current.none { it.isDefault } && current.isNotEmpty()) {
            current[0] = current[0].copy(isDefault = true)
        }
        saveToDataStore(current)
    }

    suspend fun setDefaultProvider(id: String) {
        val current = getProviders().map {
            it.copy(isDefault = it.id == id)
        }
        saveToDataStore(current)
        preferencesManager.setDefaultProviderId(id)
    }

    private suspend fun saveToDataStore(providers: List<AiProviderConfig>) {
        val dtos = providers.map { config ->
            StoredProviderDto(
                id = config.id,
                type = config.type.name,
                name = config.name,
                baseUrl = config.baseUrl,
                model = config.model,
                isEnabled = config.isEnabled,
                isDefault = config.isDefault,
                timeoutSeconds = config.timeoutSeconds,
                customBodyTemplate = config.customBodyTemplate,
                customResponsePath = config.customResponsePath,
                authMode = config.authMode.name,
                accountEmail = config.accountEmail,
                isConnected = config.isConnected
            )
        }
        preferencesManager.saveProviderConfigsJson(adapter.toJson(dtos))
    }

    fun getDefaultProviders(): List<AiProviderConfig> {
        val geminiId = "default-gemini"
        val openAiId = "default-openai"
        val claudeId = "default-claude"
        val groqId = "default-groq"
        val customId = "default-custom"

        val storedGeminiKey = secureStorage.getApiKey(geminiId)
        val geminiKey = storedGeminiKey
        val openAiKey = secureStorage.getApiKey(openAiId)
        val claudeKey = secureStorage.getApiKey(claudeId)
        val groqKey = secureStorage.getApiKey(groqId)
        val customKey = secureStorage.getApiKey(customId)

        return listOf(
            AiProviderConfig(
                id = geminiId,
                type = ProviderType.GEMINI,
                name = "Google Gemini",
                apiKey = geminiKey,
                baseUrl = ProviderType.GEMINI.defaultBaseUrl,
                model = "gemini-flash-lite-latest",
                isEnabled = true,
                isDefault = true,
                authMode = AuthMode.SERVICE_ACCOUNT,
                isConnected = true
            ),
            AiProviderConfig(
                id = openAiId,
                type = ProviderType.OPENAI,
                name = "ChatGPT (OpenAI)",
                apiKey = openAiKey,
                baseUrl = ProviderType.OPENAI.defaultBaseUrl,
                model = "gpt-4o-mini",
                isEnabled = true,
                isDefault = false,
                authMode = AuthMode.SERVICE_ACCOUNT,
                isConnected = openAiKey.isNotBlank()
            ),
            AiProviderConfig(
                id = claudeId,
                type = ProviderType.CLAUDE,
                name = "Anthropic Claude",
                apiKey = claudeKey,
                baseUrl = ProviderType.CLAUDE.defaultBaseUrl,
                model = "claude-3-5-sonnet-20241022",
                isEnabled = true,
                isDefault = false,
                authMode = AuthMode.SERVICE_ACCOUNT,
                isConnected = claudeKey.isNotBlank()
            ),
            AiProviderConfig(
                id = groqId,
                type = ProviderType.OPENAI_COMPATIBLE,
                name = "Groq (Fast)",
                apiKey = groqKey,
                baseUrl = "https://api.groq.com/openai/v1",
                model = "llama-3.3-70b-versatile",
                isEnabled = true,
                isDefault = false,
                authMode = AuthMode.BYOK,
                isConnected = groqKey.isNotBlank()
            ),
            AiProviderConfig(
                id = customId,
                type = ProviderType.CUSTOM_REST,
                name = "Custom REST Server",
                apiKey = customKey,
                baseUrl = "https://api.example.com/v1/chat",
                model = "custom-model",
                isEnabled = false,
                isDefault = false,
                authMode = AuthMode.BYOK,
                isConnected = false
            )
        )
    }
}
