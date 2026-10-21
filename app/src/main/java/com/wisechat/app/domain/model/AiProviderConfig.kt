package com.wisechat.app.domain.model

enum class ProviderType(val displayName: String, val defaultBaseUrl: String, val defaultModel: String) {
    GEMINI("Google Gemini", "https://generativelanguage.googleapis.com", "gemini-flash-lite-latest"),
    OPENAI("OpenAI (ChatGPT)", "https://api.openai.com/v1", "gpt-4o-mini"),
    CLAUDE("Anthropic Claude", "https://api.anthropic.com/v1", "claude-3-5-sonnet-20241022"),
    OPENAI_COMPATIBLE("OpenAI-Compatible", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"),
    CUSTOM_REST("Custom REST", "https://api.example.com/v1/chat", "custom-model")
}

enum class AuthMode(val label: String) {
    SERVICE_ACCOUNT("Account Sign-In / Service Account"),
    BYOK("Manual API Key (BYOK)")
}

data class AiProviderConfig(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: ProviderType,
    val name: String = type.displayName,
    val apiKey: String = "",
    val baseUrl: String = type.defaultBaseUrl,
    val model: String = type.defaultModel,
    val isEnabled: Boolean = true,
    val isDefault: Boolean = false,
    val timeoutSeconds: Int = 30,
    val authMode: AuthMode = AuthMode.BYOK,
    val accountEmail: String? = null,
    val serviceAccountJson: String? = null,
    val isConnected: Boolean = false,
    val customBodyTemplate: String = DEFAULT_CUSTOM_TEMPLATE,
    val customResponsePath: String = "suggestions", // or "choices[0].message.content"
    val customHeaders: Map<String, String> = emptyMap()
) {
    val maskedApiKey: String
        get() {
            if (apiKey.isBlank()) return "Not Configured"
            return if (apiKey.length <= 8) {
                "••••••••"
            } else {
                "${apiKey.take(4)}••••••••${apiKey.takeLast(4)}"
            }
        }

    companion object {
        const val DEFAULT_CUSTOM_TEMPLATE = """{
  "model": "{{model}}",
  "target_message": "{{target_message}}",
  "conversation": "{{conversation}}",
  "style": "{{reply_style}}",
  "length": "{{reply_length}}",
  "language": "{{language}}",
  "objective": "{{objective}}"
}"""
    }
}

data class ProviderTestResult(
    val isSuccess: Boolean,
    val responseTimeMs: Long = 0,
    val message: String,
    val availableModels: List<String> = emptyList()
)
