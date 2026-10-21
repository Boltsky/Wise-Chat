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

class OpenAiCompatibleProvider(
    override val config: AiProviderConfig,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(config.timeoutSeconds.toLong(), TimeUnit.SECONDS)
        .readTimeout(config.timeoutSeconds.toLong(), TimeUnit.SECONDS)
        .build()
) : AiProvider {

    override suspend fun generateReplies(params: GenerateRepliesParams): Result<ReplyGenerationResponse> =
        withContext(Dispatchers.IO) {
            try {
                val systemPrompt = PromptBuilder.buildSystemInstruction(params.styleOptions, params.customPreset)
                val userContent = PromptBuilder.buildUserMessage(params.conversationContext)

                val requestJson = JSONObject().apply {
                    put("model", config.model)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", systemPrompt)
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", userContent)
                        })
                    })
                    // Only request json_object if not a simple local LLM that might fail
                    put("temperature", 0.7)
                }

                val url = "${config.baseUrl.trimEnd('/')}/chat/completions"
                val requestBuilder = Request.Builder()
                    .url(url)
                    .post(requestJson.toString().toRequestBody("application/json".toMediaType()))

                if (config.apiKey.isNotBlank()) {
                    requestBuilder.addHeader("Authorization", "Bearer ${config.apiKey.trim()}")
                }

                config.customHeaders.forEach { (k, v) ->
                    requestBuilder.addHeader(k, v)
                }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            RuntimeException("${config.name} error (${response.code}): $body")
                        )
                    }

                    val parsed = parseCompatibleResponse(body, params.styleOptions.selectedStyle)
                    Result.success(parsed)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun refineReply(params: RefineReplyParams): Result<ReplyGenerationResponse> =
        withContext(Dispatchers.IO) {
            try {
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
                            put("content", "You are Wise Chat refining chat replies. Respond with valid JSON.")
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        })
                    })
                    put("temperature", 0.7)
                }

                val url = "${config.baseUrl.trimEnd('/')}/chat/completions"
                val requestBuilder = Request.Builder()
                    .url(url)
                    .post(requestJson.toString().toRequestBody("application/json".toMediaType()))

                if (config.apiKey.isNotBlank()) {
                    requestBuilder.addHeader("Authorization", "Bearer ${config.apiKey.trim()}")
                }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(RuntimeException("${config.name} error (${response.code}): $body"))
                    }
                    val parsed = parseCompatibleResponse(body, ReplyStyle.CASUAL)
                    Result.success(parsed)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun testConnection(): ProviderTestResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val url = "${config.baseUrl.trimEnd('/')}/models"
            val requestBuilder = Request.Builder().url(url).get()
            if (config.apiKey.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer ${config.apiKey.trim()}")
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                val elapsed = System.currentTimeMillis() - startTime
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val models = extractModelIds(body)
                    ProviderTestResult(
                        isSuccess = true,
                        responseTimeMs = elapsed,
                        message = "Connected to ${config.name} successfully (${elapsed}ms)",
                        availableModels = models.take(10)
                    )
                } else {
                    val body = response.body?.string() ?: ""
                    ProviderTestResult(
                        isSuccess = false,
                        responseTimeMs = elapsed,
                        message = "${config.name} returned HTTP ${response.code}: $body"
                    )
                }
            }
        } catch (e: Exception) {
            ProviderTestResult(false, 0, e.localizedMessage ?: "Failed to connect to ${config.name}")
        }
    }

    private fun parseCompatibleResponse(rawJson: String, defaultStyle: ReplyStyle): ReplyGenerationResponse {
        val root = JSONObject(rawJson)
        val choices = root.optJSONArray("choices")
        if (choices == null || choices.length() == 0) {
            throw IllegalStateException("${config.name} returned empty choices.")
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

        return try {
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
            ReplyGenerationResponse(targetMessage, detectedLanguage, summary, suggestionsList)
        } catch (_: Exception) {
            // Fallback: Split raw plain text response into lines if model didn't output strict JSON
            val lines = cleaned.lines().map { it.trim().removePrefix("-").removePrefix("•").trim() }
                .filter { it.length > 3 && !it.startsWith("{") && !it.startsWith("}") }
            val suggestions = lines.take(4).map {
                ReplySuggestion(text = it, style = defaultStyle, description = "Suggestion")
            }
            ReplyGenerationResponse("", "English", "", suggestions)
        }
    }

    private fun extractModelIds(body: String): List<String> {
        return try {
            val root = JSONObject(body)
            val data = root.optJSONArray("data") ?: return emptyList()
            val list = mutableListOf<String>()
            for (i in 0 until data.length()) {
                val id = data.getJSONObject(i).optString("id")
                if (id.isNotBlank()) list.add(id)
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }
}
