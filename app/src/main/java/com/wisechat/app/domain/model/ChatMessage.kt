package com.wisechat.app.domain.model

enum class SenderRole {
    INCOMING,
    OUTGOING,
    UNKNOWN
}

data class MessageBoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val sender: SenderRole = SenderRole.UNKNOWN,
    val isTarget: Boolean = false,
    val confidence: Float = 1.0f,
    val boundingBox: MessageBoundingBox? = null,
    val rawTimestamp: String? = null,
    val hasMediaOrSticker: Boolean = false
)

data class ConversationContext(
    val messages: List<ChatMessage> = emptyList(),
    val targetMessage: ChatMessage? = null,
    val detectedLanguage: String = "Auto",
    val appSourceHint: String? = null,
    val screenshotBase64: String? = null
) {
    val resolvedTargetMessage: ChatMessage?
        get() = targetMessage ?: messages.lastOrNull { it.sender == SenderRole.INCOMING } ?: messages.lastOrNull()

    fun withTarget(messageId: String): ConversationContext {
        val updated = messages.map { it.copy(isTarget = it.id == messageId) }
        val target = updated.firstOrNull { it.id == messageId }
        return copy(messages = updated, targetMessage = target)
    }

    fun updateMessageText(messageId: String, newText: String): ConversationContext {
        val updated = messages.map {
            if (it.id == messageId) it.copy(text = newText) else it
        }
        val target = if (targetMessage?.id == messageId) targetMessage.copy(text = newText) else targetMessage
        return copy(messages = updated, targetMessage = target)
    }
}
