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

class CustomRestProvider(
    override val config: AiProviderConfig,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(config.timeoutSeconds.toLong(), TimeUnit.SECONDS)
        .readTimeout(config.timeoutSeconds.toLong(), TimeUnit.SECONDS)
        .build()
) : AiProvider {

    override suspend fun generateReplies(params: GenerateRepliesParams): Result<ReplyGenerationResponse> =
        withContext(Dispatchers.IO) {
            try {
                val target = params.conversationContext.resolvedTargetMessage?.text ?: ""
                val convText = params.conversationContext.messages.joinToString("\n") {
                    val speaker = when (it.sender) {
                        SenderRole.INCOMING -> "Them"
                        SenderRole.OUTGOING -> "You"
                        SenderRole.UNKNOWN -> "Participant"
                    }
                    "$speaker: ${it.text}"
                }

                // Interpolate template variables safely
                val interpolatedBody = config.customBodyTemplate
                    .replace("{{model}}", config.model)
                    .replace("{{target_message}}", escapeJson(target))
                    .replace("{{conversation}}", escapeJson(convText))
                    .replace("{{reply_style}}", params.styleOptions.selectedStyle.title)
                    .replace("{{reply_length}}", params.styleOptions.length.label)
                    .replace("{{language}}", params.styleOptions.language)
                    .replace("{{objective}}", params.styleOptions.objective?.title ?: "None")

                val requestBuilder = Request.Builder()
                    .url(config.baseUrl)
                    .post(interpolatedBody.toRequestBody("application/json".toMediaType()))

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
                            RuntimeException("Custom endpoint HTTP ${response.code}: $body")
                        )
                    }

                    val suggestions = extractSuggestionsFromCustomResponse(body, config.customResponsePath)
                    Result.success(
                        ReplyGenerationResponse(
                            targetMessage = target,
                            detectedLanguage = params.styleOptions.language,
                            suggestions = suggestions
                        )
                    )
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun refineReply(params: RefineReplyParams): Result<ReplyGenerationResponse> =
        withContext(Dispatchers.IO) {
            try {
                val target = params.conversationContext.resolvedTargetMessage?.text ?: ""
                val requestBody = JSONObject().apply {
                    put("model", config.model)
                    put("instruction", params.refinementType.instruction)
                    put("original_reply", params.originalReply)
                    put("target_message", target)
                }

                val requestBuilder = Request.Builder()
                    .url(config.baseUrl)
                    .post(requestBody.toString().toRequestBody("application/json".toMediaType()))

                if (config.apiKey.isNotBlank()) {
                    requestBuilder.addHeader("Authorization", "Bearer ${config.apiKey.trim()}")
                }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(RuntimeException("Custom endpoint HTTP ${response.code}: $body"))
                    }
                    val suggestions = extractSuggestionsFromCustomResponse(body, config.customResponsePath)
                    Result.success(ReplyGenerationResponse(target, "English", suggestions = suggestions))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun generateConversationStarters(params: GenerateStartersParams): Result<List<ConversationStarter>> =
        withContext(Dispatchers.IO) {
            try {
                val prompt = PromptBuilder.buildStartersUserMessage(params.category, params.profileOrTopic)
                val requestBody = JSONObject().apply {
                    put("model", config.model)
                    put("prompt", prompt)
                    put("category", params.category.title)
                }

                val requestBuilder = Request.Builder()
                    .url(config.baseUrl)
                    .post(requestBody.toString().toRequestBody("application/json".toMediaType()))

                if (config.apiKey.isNotBlank()) {
                    requestBuilder.addHeader("Authorization", "Bearer ${config.apiKey.trim()}")
                }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        return@withContext Result.success(CuratedStartersLibrary.getByCategory(params.category).take(4))
                    }
                    val starters = StartersJsonParser.parse(body, params.category)
                    Result.success(starters)
                }
            } catch (_: Exception) {
                Result.success(CuratedStartersLibrary.getByCategory(params.category).take(4))
            }
        }

    override suspend fun testConnection(): ProviderTestResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val testBody = JSONObject().apply {
                put("model", config.model)
                put("test", true)
            }.toString()

            val requestBuilder = Request.Builder()
                .url(config.baseUrl)
                .post(testBody.toRequestBody("application/json".toMediaType()))

            if (config.apiKey.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer ${config.apiKey.trim()}")
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                val elapsed = System.currentTimeMillis() - startTime
                if (response.isSuccessful || response.code == 400) { // 400 means endpoint is alive and responsive
                    ProviderTestResult(
                        isSuccess = true,
                        responseTimeMs = elapsed,
                        message = "Endpoint reached successfully (${elapsed}ms, HTTP ${response.code})"
                    )
                } else {
                    ProviderTestResult(
                        isSuccess = false,
                        responseTimeMs = elapsed,
                        message = "Endpoint returned HTTP ${response.code}"
                    )
                }
            }
        } catch (e: Exception) {
            ProviderTestResult(false, 0, e.localizedMessage ?: "Failed to reach custom endpoint")
        }
    }

    private fun extractSuggestionsFromCustomResponse(body: String, path: String): List<ReplySuggestion> {
        val list = mutableListOf<ReplySuggestion>()
        try {
            val root = JSONObject(body)
            // If path is "choices[0].message.content"
            if (path.contains("choices")) {
                val choices = root.optJSONArray("choices")
                val content = choices?.getJSONObject(0)?.getJSONObject("message")?.getString("content") ?: ""
                list.add(ReplySuggestion(text = content, style = ReplyStyle.CUSTOM))
            } else {
                val suggestionsArray = root.optJSONArray("suggestions")
                if (suggestionsArray != null) {
                    for (i in 0 until suggestionsArray.length()) {
                        val item = suggestionsArray.optJSONObject(i)
                        val text = item?.optString("text") ?: suggestionsArray.optString(i)
                        if (text.isNotBlank()) {
                            list.add(ReplySuggestion(text = text, style = ReplyStyle.CUSTOM))
                        }
                    }
                } else {
                    // Fallback to top-level message or reply field
                    val reply = root.optString("reply")
                        .ifBlank { root.optString("response") }
                        .ifBlank { root.optString("text") }
                    if (reply.isNotBlank()) {
                        list.add(ReplySuggestion(text = reply, style = ReplyStyle.CUSTOM))
                    }
                }
            }
        } catch (_: Exception) {
            // If plain text
            val lines = body.lines().map { it.trim() }.filter { it.isNotBlank() }
            lines.take(3).forEach {
                list.add(ReplySuggestion(text = it, style = ReplyStyle.CUSTOM))
            }
        }
        return list
    }

    private fun escapeJson(text: String): String {
        return JSONObject.quote(text).removeSurrounding("\"")
    }
}
