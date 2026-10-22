package com.wisechat.app.engine.provider

import com.wisechat.app.data.repository.ProviderRepository
import com.wisechat.app.domain.model.AiProviderConfig
import com.wisechat.app.domain.model.ConversationStarter
import com.wisechat.app.domain.model.CuratedStartersLibrary
import com.wisechat.app.domain.model.ProviderType
import com.wisechat.app.domain.model.ReplyGenerationResponse

class ProviderCoordinator(
    private val providerRepository: ProviderRepository
) {

    fun createProvider(config: AiProviderConfig): AiProvider {
        return when (config.type) {
            ProviderType.GEMINI -> GeminiProvider(config)
            ProviderType.OPENAI -> OpenAiProvider(config)
            ProviderType.CLAUDE -> AnthropicProvider(config)
            ProviderType.OPENAI_COMPATIBLE -> OpenAiCompatibleProvider(config)
            ProviderType.CUSTOM_REST -> CustomRestProvider(config)
        }
    }

    suspend fun generateReplies(params: GenerateRepliesParams): Result<ReplyGenerationResponse> {
        val providers = providerRepository.getProviders().filter { it.isEnabled }
        val active = providerRepository.getActiveProvider()

        val primary = createProvider(active)
        val primaryResult = primary.generateReplies(params)

        if (primaryResult.isSuccess) {
            return primaryResult
        }

        // Attempt fallback to secondary enabled provider if available
        val secondary = providers.firstOrNull { it.id != active.id && it.apiKey.isNotBlank() }
        if (secondary != null) {
            val fallbackProvider = createProvider(secondary)
            val fallbackResult = fallbackProvider.generateReplies(params)
            if (fallbackResult.isSuccess) {
                return fallbackResult
            }
        }

        return primaryResult
    }

    suspend fun refineReply(params: RefineReplyParams): Result<ReplyGenerationResponse> {
        val active = providerRepository.getActiveProvider()
        val provider = createProvider(active)
        return provider.refineReply(params)
    }

    suspend fun generateConversationStarters(params: GenerateStartersParams): Result<List<ConversationStarter>> {
        val providers = providerRepository.getProviders().filter { it.isEnabled }
        val active = providerRepository.getActiveProvider()

        val primary = createProvider(active)
        val primaryResult = primary.generateConversationStarters(params)

        if (primaryResult.isSuccess && primaryResult.getOrNull()?.isNotEmpty() == true) {
            return primaryResult
        }

        // Secondary fallback
        val secondary = providers.firstOrNull { it.id != active.id && it.apiKey.isNotBlank() }
        if (secondary != null) {
            val fallbackProvider = createProvider(secondary)
            val fallbackResult = fallbackProvider.generateConversationStarters(params)
            if (fallbackResult.isSuccess && fallbackResult.getOrNull()?.isNotEmpty() == true) {
                return fallbackResult
            }
        }

        // Curated library fallback ensures zero disappointment
        val curated = CuratedStartersLibrary.getByCategory(params.category).take(4)
        return Result.success(curated)
    }
}
