package com.wisechat.app.engine.provider

import com.wisechat.app.domain.model.*
import com.wisechat.app.engine.prompt.PromptBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AnthropicProvider(
    override val config: AiProviderConfig,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(config.timeoutSeconds.toLong(), TimeUnit.SECONDS)
        .readTimeout(config.timeoutSeconds.toLong(), TimeUnit.SECONDS)
        .build()
) : AiProvider {

    override suspend fun generateReplies(params: GenerateRepliesParams): Result<ReplyGenerationResponse> =
        withContext(Dispatchers.IO) {
            try {
                if (config.apiKey.isBlank()) {
                    return@withContext Result.failure(
                        IllegalStateException("Claude API key is missing. Please connect your Anthropic account in Settings -> AI Providers.")
                    )
                }

                val systemPrompt = PromptBuilder.buildSystemInstruction(params.styleOptions, params.customPreset)
                val userContent = PromptBuilder.buildUserMessage(params.conversationContext)

                val requestJson = JSONObject().apply {
                    put("model", config.model)
                    put("max_tokens", 1024)
                    put("system", systemPrompt)
                    val contentArray = JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "text")
                            put("text", userContent)
                        })
                        val imageBase64 = params.conversationContext.screenshotBase64
                        if (!imageBase64.isNullOrBlank()) {
                            put(JSONObject().apply {
                                put("type", "image")
                                put("source", JSONObject().apply {
                                    put("type", "base64")
                                    put("media_type", "image/jpeg")
                                    put("data", imageBase64)
                                })
                            })
                        }
                    }

                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", contentArray)
                        })
                    })
                    put("temperature", 0.75)
                }

                val url = "${config.baseUrl.trimEnd('/')}/messages"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("x-api-key", config.apiKey.trim())
                    .addHeader("anthropic-version", "2023-06-01")
                    .addHeader("content-type", "application/json")
                    .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(parseHttpError(response.code, body, "Claude"))
                    }

                    val parsed = parseAnthropicResponse(body, params.styleOptions.selectedStyle)
                    Result.success(parsed)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun refineReply(params: RefineReplyParams): Result<ReplyGenerationResponse> =
        withContext(Dispatchers.IO) {
            try {
                if (config.apiKey.isBlank()) {
                    return@withContext Result.failure(IllegalStateException("Claude API key is missing."))
                }

                val prompt = PromptBuilder.buildRefinementPrompt(
                    originalReply = params.originalReply,
                    refinementType = params.refinementType,
                    targetMessage = params.conversationContext.resolvedTargetMessage?.text ?: "",
                    context = params.conversationContext
                )

                val requestJson = JSONObject().apply {
                    put("model", config.model)
                    put("max_tokens", 1024)
                    put("system", "You are Wise Chat refining suggested replies. Output pure JSON only.")
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        })
                    })
                    put("temperature", 0.7)
                }

                val url = "${config.baseUrl.trimEnd('/')}/messages"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("x-api-key", config.apiKey.trim())
                    .addHeader("anthropic-version", "2023-06-01")
                    .addHeader("content-type", "application/json")
                    .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(parseHttpError(response.code, body, "Claude"))
                    }
                    val parsed = parseAnthropicResponse(body, ReplyStyle.CASUAL)
                    Result.success(parsed)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun generateConversationStarters(params: GenerateStartersParams): Result<List<ConversationStarter>> =
        withContext(Dispatchers.IO) {
            try {
                if (config.apiKey.isBlank()) {
                    return@withContext Result.failure(
                        IllegalStateException("Anthropic API key is missing. Please add your API key in Settings -> AI Providers.")
                    )
                }

                val systemPrompt = PromptBuilder.buildStartersSystemInstruction(params.category, params.language)
                val userContent = PromptBuilder.buildStartersUserMessage(params.category, params.profileOrTopic)

                val requestJson = JSONObject().apply {
                    put("model", config.model)
                    put("max_tokens", 1024)
                    put("system", systemPrompt)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", userContent)
                        })
                    })
                    put("temperature", 0.85)
                }

                val url = "${config.baseUrl.trimEnd('/')}/messages"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("x-api-key", config.apiKey.trim())
                    .addHeader("anthropic-version", "2023-06-01")
                    .addHeader("content-type", "application/json")
                    .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(parseHttpError(response.code, body, "Claude"))
                    }

                    val json = JSONObject(body)
                    val content = json.getJSONArray("content")
                    val rawText = content.getJSONObject(0).getString("text")
                    val starters = StartersJsonParser.parse(rawText, params.category)
                    Result.success(starters)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun testConnection(): ProviderTestResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            if (config.apiKey.isBlank()) {
                return@withContext ProviderTestResult(false, 0, "API key is empty")
            }

            // Test ping with a 1-token query to verify credentials and quota
            val testBody = JSONObject().apply {
                put("model", config.model)
                put("max_tokens", 5)
                put("messages", JSONArray().put(JSONObject().apply {
                    put("role", "user")
                    put("content", "ping")
                }))
            }

            val url = "${config.baseUrl.trimEnd('/')}/messages"
            val request = Request.Builder()
                .url(url)
                .addHeader("x-api-key", config.apiKey.trim())
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("content-type", "application/json")
                .post(testBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val elapsed = System.currentTimeMillis() - startTime
                if (response.isSuccessful) {
                    ProviderTestResult(
                        isSuccess = true,
                        responseTimeMs = elapsed,
                        message = "Connected to Anthropic Claude successfully (${elapsed}ms)",
                        availableModels = listOf("claude-3-5-sonnet-20241022", "claude-3-5-haiku-20241022", "claude-3-opus-20240229")
                    )
                } else {
                    val body = response.body?.string() ?: ""
                    ProviderTestResult(
                        isSuccess = false,
                        responseTimeMs = elapsed,
                        message = parseHttpError(response.code, body, "Claude").message ?: "Connection failed"
                    )
                }
            }
        } catch (e: Exception) {
            ProviderTestResult(false, 0, e.localizedMessage ?: "Network error connecting to Claude")
        }
    }

    private fun parseAnthropicResponse(rawJson: String, defaultStyle: ReplyStyle): ReplyGenerationResponse {
        val root = JSONObject(rawJson)
        val contentArray = root.optJSONArray("content")
        if (contentArray == null || contentArray.length() == 0) {
            throw IllegalStateException("Claude returned empty response content.")
        }

        val textBlock = contentArray.getJSONObject(0)
        val text = textBlock.getString("text")

        return parseReplyJson(text, defaultStyle)
    }

    private fun parseReplyJson(content: String, defaultStyle: ReplyStyle): ReplyGenerationResponse {
        val cleaned = content.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val json = JSONObject(cleaned)
        val targetMessage = json.optString("target_message", "")
        val detectedLanguage = json.optString("detected_language", "English")
        val summary = json.optString("conversation_summary", "")

        val suggestionsArray = json.optJSONArray("suggestions")
        val suggestionsList = mutableListOf<ReplySuggestion>()

        if (suggestionsArray != null) {
            for (i in 0 until suggestionsArray.length()) {
                val item = suggestionsArray.getJSONObject(i)
                val text = item.optString("text", "").trim()
                if (text.isNotBlank()) {
                    val styleTitle = item.optString("style", defaultStyle.title)
                    val style = ReplyStyle.values().firstOrNull { it.title.equals(styleTitle, ignoreCase = true) }
                        ?: defaultStyle
                    val description = item.optString("description", "")
                    suggestionsList.add(
                        ReplySuggestion(
                            text = text,
                            style = style,
                            description = description,
                            detectedLanguage = detectedLanguage
                        )
                    )
                }
            }
        }

        return ReplyGenerationResponse(
            targetMessage = targetMessage,
            detectedLanguage = detectedLanguage,
            conversationSummary = summary,
            suggestions = suggestionsList
        )
    }

    private fun parseHttpError(code: Int, body: String, provider: String): Exception {
        return try {
            val json = JSONObject(body)
            val error = json.optJSONObject("error")
            val message = error?.optString("message") ?: body
            when (code) {
                401 -> SecurityException("$provider authentication failed. Invalid API key or service token.")
                429 -> IllegalStateException("$provider rate limit or usage quota exceeded.")
                else -> RuntimeException("$provider error ($code): $message")
            }
        } catch (_: Exception) {
            RuntimeException("$provider returned HTTP $code: $body")
        }
    }
}
