package com.wisechat.app.engine.provider

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.wisechat.app.domain.model.*
import com.wisechat.app.engine.prompt.PromptBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiProvider(
    override val config: AiProviderConfig,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(config.timeoutSeconds.toLong(), TimeUnit.SECONDS)
        .readTimeout(config.timeoutSeconds.toLong(), TimeUnit.SECONDS)
        .build()
) : AiProvider {

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    override suspend fun generateReplies(params: GenerateRepliesParams): Result<ReplyGenerationResponse> =
        withContext(Dispatchers.IO) {
            try {
                if (config.apiKey.isBlank()) {
                    return@withContext Result.failure(
                        IllegalStateException("Gemini API key is missing. Please add your API key in Settings -> AI Providers.")
                    )
                }

                val systemPrompt = PromptBuilder.buildSystemInstruction(params.styleOptions, params.customPreset)
                val userContent = PromptBuilder.buildUserMessage(params.conversationContext)

                val partsArray = org.json.JSONArray().put(JSONObject().put("text", userContent))
                val imageBase64 = params.conversationContext.screenshotBase64
                if (!imageBase64.isNullOrBlank()) {
                    partsArray.put(JSONObject().apply {
                        put("inlineData", JSONObject().apply {
                            put("mimeType", "image/jpeg")
                            put("data", imageBase64)
                        })
                    })
                }

                val requestJson = JSONObject().apply {
                    put("system_instruction", JSONObject().apply {
                        put("parts", org.json.JSONArray().put(JSONObject().put("text", systemPrompt)))
                    })
                    put("contents", org.json.JSONArray().put(
                        JSONObject().apply {
                            put("role", "user")
                            put("parts", partsArray)
                        }
                    ))
                    put("generationConfig", JSONObject().apply {
                        put("responseMimeType", "application/json")
                        put("temperature", 0.75)
                    })
                }

                var targetModel = config.model
                var url = "${config.baseUrl}/v1beta/models/$targetModel:generateContent?key=${config.apiKey}"
                var request = Request.Builder()
                    .url(url)
                    .addHeader("x-goog-api-key", config.apiKey)
                    .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                var response = client.newCall(request).execute()
                var body = response.body?.string() ?: ""

                // Automatic fallback for deprecated or unavailable models (e.g. gemini-1.5-flash / gemini-2.0-flash)
                if (!response.isSuccessful && response.code == 404 && targetModel != "gemini-flash-lite-latest") {
                    response.close()
                    targetModel = "gemini-flash-lite-latest"
                    url = "${config.baseUrl}/v1beta/models/$targetModel:generateContent?key=${config.apiKey}"
                    request = Request.Builder()
                        .url(url)
                        .addHeader("x-goog-api-key", config.apiKey)
                        .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    response = client.newCall(request).execute()
                    body = response.body?.string() ?: ""
                }

                response.use {
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            parseHttpError(response.code, body, "Google Gemini")
                        )
                    }

                    val parsed = parseGeminiResponse(body, params.styleOptions.selectedStyle)
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
                    return@withContext Result.failure(IllegalStateException("Gemini API key is missing."))
                }

                val prompt = PromptBuilder.buildRefinementPrompt(
                    originalReply = params.originalReply,
                    refinementType = params.refinementType,
                    targetMessage = params.conversationContext.resolvedTargetMessage?.text ?: "",
                    context = params.conversationContext
                )

                val requestJson = JSONObject().apply {
                    put("contents", org.json.JSONArray().put(
                        JSONObject().apply {
                            put("role", "user")
                            put("parts", org.json.JSONArray().put(JSONObject().put("text", prompt)))
                        }
                    ))
                    put("generationConfig", JSONObject().apply {
                        put("responseMimeType", "application/json")
                        put("temperature", 0.7)
                    })
                }

                val url = "${config.baseUrl}/v1beta/models/${config.model}:generateContent?key=${config.apiKey}"
                val request = Request.Builder()
                    .url(url)
                    .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(parseHttpError(response.code, body, "Google Gemini"))
                    }
                    val parsed = parseGeminiResponse(body, ReplyStyle.CASUAL)
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
                        IllegalStateException("Gemini API key is missing. Please add your API key in Settings -> AI Providers.")
                    )
                }

                val systemPrompt = PromptBuilder.buildStartersSystemInstruction(params.category, params.language)
                val userContent = PromptBuilder.buildStartersUserMessage(params.category, params.profileOrTopic)

                val requestJson = JSONObject().apply {
                    put("system_instruction", JSONObject().apply {
                        put("parts", org.json.JSONArray().put(JSONObject().put("text", systemPrompt)))
                    })
                    put("contents", org.json.JSONArray().put(
                        JSONObject().apply {
                            put("role", "user")
                            put("parts", org.json.JSONArray().put(JSONObject().put("text", userContent)))
                        }
                    ))
                    put("generationConfig", JSONObject().apply {
                        put("responseMimeType", "application/json")
                        put("temperature", 0.85)
                    })
                }

                var targetModel = config.model
                var url = "${config.baseUrl}/v1beta/models/$targetModel:generateContent?key=${config.apiKey}"
                var request = Request.Builder()
                    .url(url)
                    .addHeader("x-goog-api-key", config.apiKey)
                    .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                var response = client.newCall(request).execute()
                var body = response.body?.string() ?: ""

                if (!response.isSuccessful && response.code == 404 && targetModel != "gemini-flash-lite-latest") {
                    response.close()
                    targetModel = "gemini-flash-lite-latest"
                    url = "${config.baseUrl}/v1beta/models/$targetModel:generateContent?key=${config.apiKey}"
                    request = Request.Builder()
                        .url(url)
                        .addHeader("x-goog-api-key", config.apiKey)
                        .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    response = client.newCall(request).execute()
                    body = response.body?.string() ?: ""
                }

                response.use {
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(parseHttpError(response.code, body, "Google Gemini"))
                    }

                    val root = JSONObject(body)
                    val candidates = root.optJSONArray("candidates")
                    val rawText = candidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text") ?: ""
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

            var targetModel = config.model
            var url = "${config.baseUrl}/v1beta/models/$targetModel?key=${config.apiKey}"
            var request = Request.Builder()
                .url(url)
                .addHeader("x-goog-api-key", config.apiKey)
                .get()
                .build()

            var response = client.newCall(request).execute()
            if (!response.isSuccessful && response.code == 404 && targetModel != "gemini-flash-lite-latest") {
                response.close()
                targetModel = "gemini-flash-lite-latest"
                url = "${config.baseUrl}/v1beta/models/$targetModel?key=${config.apiKey}"
                request = Request.Builder()
                    .url(url)
                    .addHeader("x-goog-api-key", config.apiKey)
                    .get()
                    .build()
                response = client.newCall(request).execute()
            }

            response.use { resp ->
                val elapsed = System.currentTimeMillis() - startTime
                if (resp.isSuccessful) {
                    ProviderTestResult(
                        isSuccess = true,
                        responseTimeMs = elapsed,
                        message = "Connected successfully to $targetModel (${elapsed}ms)",
                        availableModels = listOf(
                            "gemini-flash-lite-latest",
                            "gemini-flash-latest",
                            "gemini-pro-latest",
                            "gemini-3.1-flash-lite",
                            "gemini-2.5-pro",
                            "gemini-2.5-flash"
                        )
                    )
                } else {
                    val body = resp.body?.string() ?: ""
                    ProviderTestResult(
                        isSuccess = false,
                        responseTimeMs = elapsed,
                        message = parseHttpError(resp.code, body, "Google Gemini").message ?: "Connection failed"
                    )
                }
            }
        } catch (e: Exception) {
            ProviderTestResult(false, 0, e.localizedMessage ?: "Network error connecting to Gemini")
        }
    }

    private fun parseGeminiResponse(rawJson: String, defaultStyle: ReplyStyle): ReplyGenerationResponse {
        val root = JSONObject(rawJson)
        val candidates = root.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            throw IllegalStateException("Gemini returned no candidates.")
        }

        val firstCandidate = candidates.getJSONObject(0)
        val content = firstCandidate.getJSONObject("content")
        val parts = content.getJSONArray("parts")
        val rawText = parts.getJSONObject(0).getString("text")

        // Parse structured output
        return parseReplyJson(rawText, defaultStyle)
    }

    private fun parseReplyJson(rawText: String, defaultStyle: ReplyStyle): ReplyGenerationResponse {
        // Find JSON payload if surrounded by markdown fences
        val cleaned = rawText.trim()
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
                400 -> IllegalArgumentException("$provider request error: $message")
                401, 403 -> SecurityException("$provider authentication failed. Check your API key.")
                429 -> IllegalStateException("$provider quota exceeded or rate limit reached.")
                else -> RuntimeException("$provider error ($code): $message")
            }
        } catch (_: Exception) {
            RuntimeException("$provider returned HTTP $code: $body")
        }
    }
}
