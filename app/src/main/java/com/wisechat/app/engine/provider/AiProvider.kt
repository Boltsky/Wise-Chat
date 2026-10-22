package com.wisechat.app.engine.provider

import com.wisechat.app.domain.model.AiProviderConfig
import com.wisechat.app.domain.model.ConversationContext
import com.wisechat.app.domain.model.ConversationStarter
import com.wisechat.app.domain.model.CustomPersonaPreset
import com.wisechat.app.domain.model.ProviderTestResult
import com.wisechat.app.domain.model.RefinementType
import com.wisechat.app.domain.model.ReplyGenerationResponse
import com.wisechat.app.domain.model.StarterCategory
import com.wisechat.app.domain.model.StyleCustomization

data class GenerateRepliesParams(
    val conversationContext: ConversationContext,
    val styleOptions: StyleCustomization,
    val customPreset: CustomPersonaPreset? = null
)

data class RefineReplyParams(
    val originalReply: String,
    val refinementType: RefinementType,
    val conversationContext: ConversationContext
)

data class GenerateStartersParams(
    val category: StarterCategory = StarterCategory.SWEET_AWW,
    val profileOrTopic: String? = null,
    val language: String = "English"
)

interface AiProvider {
    val config: AiProviderConfig

    suspend fun generateReplies(params: GenerateRepliesParams): Result<ReplyGenerationResponse>

    suspend fun refineReply(params: RefineReplyParams): Result<ReplyGenerationResponse>

    suspend fun generateConversationStarters(params: GenerateStartersParams): Result<List<ConversationStarter>>

    suspend fun testConnection(): ProviderTestResult
}
