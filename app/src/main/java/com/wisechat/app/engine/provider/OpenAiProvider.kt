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

class OpenAiProvider(
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
                        IllegalStateException("OpenAI API key is missing. Please configure your key in Settings.")
                    )
                }

                val systemPrompt = PromptBuilder.buildSystemInstruction(params.styleOptions, params.customPreset)
                val userContent = PromptBuilder.buildUserMessage(params.conversationContext)

                val requestJson = JSONObject().apply {
                    put("model", config.model)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", systemPrompt)
                        })
                        val imageBase64 = params.conversationContext.screenshotBase64
                        if (!imageBase64.isNullOrBlank()) {
                            put(JSONObject().apply {
                                put("role", "user")
                                put("content", JSONArray().apply {
                                    put(JSONObject().apply {
                                        put("type", "text")
                                        put("text", userContent)
                                    })
                                    put(JSONObject().apply {
                                        put("type", "image_url")
                                        put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$imageBase64"))
                                    })
                                })
                            })
                        } else {
                            put(JSONObject().apply {
                                put("role", "user")
                                put("content", userContent)
                            })
                        }
                    })
                    put("response_format", JSONObject().put("type", "json_object"))
                    put("temperature", 0.75)
                }

                val url = "${config.baseUrl.trimEnd('/')}/chat/completions"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer ${config.apiKey.trim()}")
                    .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(parseHttpError(response.code, body, "OpenAI"))
                    }

                    val parsed = parseOpenAiResponse(body, params.styleOptions.selectedStyle)
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
                    return@withContext Result.failure(IllegalStateException("OpenAI API key is missing."))
                }

                val prompt = PromptBuilder.buildRefinementPrompt(
                    originalReply = params.originalReply,
                    refinementType = params.refinementType,
                    targetMessage = params.conversationContext.resolvedTargetMessage?.text ?: "",
                    context = params.conversationContext
                )

                val requestJson = JSONObject().apply {
                    put("model", config.model)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", "You are Wise Chat refining chat replies. Output JSON.")
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        })
                    })
                    put("response_format", JSONObject().put("type", "json_object"))
                    put("temperature", 0.7)
                }

                val url = "${config.baseUrl.trimEnd('/')}/chat/completions"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer ${config.apiKey.trim()}")
                    .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(parseHttpError(response.code, body, "OpenAI"))
                    }
                    val parsed = parseOpenAiResponse(body, ReplyStyle.CASUAL)
                    Result.success(parsed)
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

            val url = "${config.baseUrl.trimEnd('/')}/models"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${config.apiKey.trim()}")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val elapsed = System.currentTimeMillis() - startTime
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val models = extractModelIds(body)
                    ProviderTestResult(
                        isSuccess = true,
                        responseTimeMs = elapsed,
                        message = "Connected to OpenAI successfully (${elapsed}ms)",
                        availableModels = models.take(10)
                    )
                } else {
                    val body = response.body?.string() ?: ""
                    ProviderTestResult(
                        isSuccess = false,
                        responseTimeMs = elapsed,
                        message = parseHttpError(response.code, body, "OpenAI").message ?: "Connection failed"
                    )
                }
            }
        } catch (e: Exception) {
            ProviderTestResult(false, 0, e.localizedMessage ?: "Network error connecting to OpenAI")
        }
    }

    private fun parseOpenAiResponse(rawJson: String, defaultStyle: ReplyStyle): ReplyGenerationResponse {
        val root = JSONObject(rawJson)
        val choices = root.optJSONArray("choices")
        if (choices == null || choices.length() == 0) {
            throw IllegalStateException("OpenAI returned no choices.")
        }

        val firstChoice = choices.getJSONObject(0)
        val message = firstChoice.getJSONObject("message")
        val content = message.getString("content")

        return parseReplyJson(content, defaultStyle)
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

    private fun extractModelIds(body: String): List<String> {
        return try {
            val root = JSONObject(body)
            val data = root.optJSONArray("data") ?: return emptyList()
            val list = mutableListOf<String>()
            for (i in 0 until data.length()) {
                val id = data.getJSONObject(i).optString("id")
                if (id.isNotBlank() && (id.contains("gpt") || id.contains("o1") || id.contains("chat"))) {
                    list.add(id)
                }
            }
            list
        } catch (_: Exception) {
            listOf("gpt-4o-mini", "gpt-4o")
        }
    }

    private fun parseHttpError(code: Int, body: String, provider: String): Exception {
        return try {
            val json = JSONObject(body)
            val error = json.optJSONObject("error")
            val message = error?.optString("message") ?: body
            when (code) {
                401 -> SecurityException("$provider authentication failed. Invalid API key.")
                429 -> IllegalStateException("$provider quota exceeded or rate limit reached. Verify billing/balance.")
                else -> RuntimeException("$provider error ($code): $message")
            }
        } catch (_: Exception) {
            RuntimeException("$provider returned HTTP $code: $body")
        }
    }
}
