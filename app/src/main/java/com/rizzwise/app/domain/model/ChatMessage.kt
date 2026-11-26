package com.rizzwise.app.domain.model

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
        get() = targetMessage
            ?: messages.lastOrNull { it.sender == SenderRole.INCOMING }
            ?: messages.lastOrNull { it.sender != SenderRole.OUTGOING }
            ?: messages.lastOrNull()

    val resolvedTargetMessages: List<ChatMessage>
        get() {
            val explicitlyTargeted = messages.filter { it.isTarget }
            if (explicitlyTargeted.isNotEmpty()) return explicitlyTargeted
            return listOfNotNull(resolvedTargetMessage)
        }

    val resolvedTargetText: String
        get() = resolvedTargetMessages
            .joinToString("\n") { it.text }
            .ifBlank { resolvedTargetMessage?.text.orEmpty() }

    val latestIncomingStreak: List<ChatMessage>
        get() {
            val lastIncomingIdx = messages.indexOfLast { it.sender == SenderRole.INCOMING }
            if (lastIncomingIdx == -1) return emptyList()
            var startIdx = lastIncomingIdx
            while (startIdx > 0 && messages[startIdx - 1].sender == SenderRole.INCOMING) {
                startIdx--
            }
            return messages.subList(startIdx, lastIncomingIdx + 1)
        }

    fun withTarget(messageId: String): ConversationContext {
        val updated = messages.map { it.copy(isTarget = it.id == messageId) }
        val target = updated.firstOrNull { it.id == messageId }
        return copy(messages = updated, targetMessage = target)
    }

    fun withTargetStreak(): ConversationContext {
        val streakIds = latestIncomingStreak.map { it.id }.toSet()
        if (streakIds.isEmpty()) return this
        val updated = messages.map { it.copy(isTarget = it.id in streakIds) }
        return copy(messages = updated, targetMessage = updated.lastOrNull { it.isTarget })
    }

    fun updateMessageText(messageId: String, newText: String): ConversationContext {
        val updated = messages.map {
            if (it.id == messageId) it.copy(text = newText) else it
        }
        val target = if (targetMessage?.id == messageId) targetMessage.copy(text = newText) else targetMessage
        return copy(messages = updated, targetMessage = target)
    }
}
