package com.wisechat.app.engine.ocr

import com.wisechat.app.domain.model.ChatMessage
import com.wisechat.app.domain.model.ConversationContext
import com.wisechat.app.domain.model.MessageBoundingBox
import com.wisechat.app.domain.model.SenderRole
import java.util.regex.Pattern

data class RawTextElement(
    val text: String,
    val boundingBox: MessageBoundingBox,
    val confidence: Float = 1.0f
)

object ConversationParser {

    // Regex patterns for common chat noise (timestamps, status headers)
    private val TIMESTAMP_PATTERN = Pattern.compile(
        "^\\s*([0-1]?[0-9]|2[0-3]):[0-5][0-9](\\s*(AM|PM|am|pm))?\\s*$"
    )
    private val TIME_WITH_STATUS_PATTERN = Pattern.compile(
        "^\\s*([0-1]?[0-9]|2[0-3]):[0-5][0-9](\\s*(AM|PM|am|pm))?(\\s*(✓|✓✓|Delivered|Read|Sent|Edited))?\\s*$",
        Pattern.CASE_INSENSITIVE
    )
    private val TRAILING_TIME_CHECKMARK_REGEX = Regex(
        "\\s+([0-1]?[0-9]|2[0-3]):[0-5][0-9](\\s*(AM|PM|am|pm))?(\\s*(✓|✓✓|Delivered|Read|Sent|Edited))?\\s*$",
        RegexOption.IGNORE_CASE
    )

    private val NOISE_WORDS = setOf(
        "today", "yesterday", "online", "typing...", "last seen",
        "messages and calls are end-to-end encrypted", "tap here for info",
        "type a message", "message...", "send", "chat", "voice call", "video call",
        "search", "status", "calls", "chats", "camera", "settings"
    )

    // UI phrases from Wise Chat itself to prevent self-analyzing its own screen
    private val APP_INTERNAL_UI_NOISE = listOf(
        "wise chat", "ai reply assistant", "ai engine", "floating assistant",
        "bubble active on screen", "import & analyze screenshot", "select screenshot",
        "reply personalities", "custom voices", "reply history", "ai accounts",
        "byok", "one-tap account connection", "not connected", "google gemini",
        "anthropic claude", "service account"
    )

    enum class BubbleTint {
        OUTGOING_GREEN,
        OUTGOING_BLUE,
        NEUTRAL,
        UNKNOWN
    }

    /**
     * Parses raw extracted OCR elements and clusters them into coherent chat messages.
     * @param elements Extracted OCR text elements with spatial bounding boxes.
     * @param imageWidth Width of the captured screenshot in pixels.
     * @param imageHeight Height of the captured screenshot in pixels.
     * @param bitmap Optional original screenshot bitmap for color tint and media sampling.
     */
    fun parseConversation(
        elements: List<RawTextElement>,
        imageWidth: Int,
        imageHeight: Int,
        bitmap: android.graphics.Bitmap? = null
    ): ConversationContext {
        if (elements.isEmpty()) {
            return ConversationContext()
        }

        // Crop margins: ignore top contact bar (~8%) and bottom input bar (~10%)
        val topCropMargin = imageHeight * 0.08f
        val bottomCropMargin = imageHeight * 0.90f

        // Filter out status bar/input bar elements, pure noise, full-width cards, and Wise Chat UI
        val contentElements = elements.filter { element ->
            val box = element.boundingBox
            val isWithinVerticalBounds = box.top >= topCropMargin && box.bottom <= bottomCropMargin
            val isFullWidthBanner = box.width > (imageWidth * 0.78f) && (box.left < imageWidth * 0.12f)
            isWithinVerticalBounds && !isFullWidthBanner && !isSystemNoise(element.text)
        }

        // Sort elements from top to bottom
        val sortedElements = contentElements.sortedBy { it.boundingBox.top }

        // Group elements vertically into bubbles
        val clusteredMessages = mutableListOf<ChatMessage>()
        var currentCluster = mutableListOf<RawTextElement>()

        for (elem in sortedElements) {
            if (currentCluster.isEmpty()) {
                currentCluster.add(elem)
            } else {
                val prev = currentCluster.last()
                val verticalGap = elem.boundingBox.top - prev.boundingBox.bottom
                val sameHorizontalSide = isSameSide(prev.boundingBox, elem.boundingBox, imageWidth)

                // If gap is small and on same side, merge into the same chat bubble
                val maxMergeGap = (prev.boundingBox.height * 1.5f).coerceAtLeast(35f)
                if (verticalGap < maxMergeGap && sameHorizontalSide) {
                    currentCluster.add(elem)
                } else {
                    val msg = buildMessageFromCluster(currentCluster, imageWidth, bitmap)
                    if (msg.text.isNotBlank() && !isSystemNoise(msg.text)) {
                        clusteredMessages.add(msg)
                    }
                    currentCluster = mutableListOf(elem)
                }
            }
        }

        if (currentCluster.isNotEmpty()) {
            val msg = buildMessageFromCluster(currentCluster, imageWidth, bitmap)
            if (msg.text.isNotBlank() && !isSystemNoise(msg.text)) {
                clusteredMessages.add(msg)
            }
        }

        // Identify candidate target message:
        // Priority 1: Latest incoming message (from "Them")
        // Priority 2: Very last message if no incoming message was detected
        val candidateTarget = clusteredMessages.lastOrNull { it.sender == SenderRole.INCOMING }
            ?: clusteredMessages.lastOrNull()

        val markedMessages = clusteredMessages.map {
            if (it.id == candidateTarget?.id) it.copy(isTarget = true) else it
        }

        return ConversationContext(
            messages = markedMessages,
            targetMessage = candidateTarget
        )
    }

    private fun buildMessageFromCluster(
        cluster: List<RawTextElement>,
        imageWidth: Int,
        bitmap: android.graphics.Bitmap? = null
    ): ChatMessage {
        val rawClusterText = cluster.joinToString(" ") { it.text }
        val hasDeliveryStatus = rawClusterText.contains("✓") ||
                rawClusterText.contains("✓✓") ||
                rawClusterText.contains("√√") ||
                rawClusterText.contains("Delivered", ignoreCase = true) ||
                rawClusterText.contains("Read", ignoreCase = true)

        var mergedText = cluster.joinToString(" ") { it.text.trim() }
            .replace("\\s+".toRegex(), " ")
            .trim()

        // Strip trailing timestamps and checkmarks from chat text (e.g. "Hey! 10:45 AM ✓✓")
        mergedText = mergedText.replace(TRAILING_TIME_CHECKMARK_REGEX, "").trim()

        val left = cluster.minOf { it.boundingBox.left }
        val top = cluster.minOf { it.boundingBox.top }
        val right = cluster.maxOf { it.boundingBox.right }
        val bottom = cluster.maxOf { it.boundingBox.bottom }

        val boundingBox = MessageBoundingBox(left, top, right, bottom)
        val senderRole = determineSenderRole(boundingBox, imageWidth, hasDeliveryStatus, bitmap)

        return ChatMessage(
            text = mergedText,
            sender = senderRole,
            confidence = cluster.map { it.confidence }.average().toFloat(),
            boundingBox = boundingBox
        )
    }

    private fun determineSenderRole(
        box: MessageBoundingBox,
        imageWidth: Int,
        hasDeliveryStatus: Boolean = false,
        bitmap: android.graphics.Bitmap? = null
    ): SenderRole {
        // Factor 1: Delivery checkmarks (✓ or ✓✓ or Delivered/Read) are exclusively shown on Outgoing messages
        if (hasDeliveryStatus) {
            return SenderRole.OUTGOING
        }

        // Factor 2: Edge margin ratios
        val leftMargin = box.left / imageWidth.toFloat()
        val rightMargin = (imageWidth - box.right) / imageWidth.toFloat()

        // Factor 3: Bubble background tint from screenshot
        val tint = sampleBubbleTint(box, bitmap)
        if (tint == BubbleTint.OUTGOING_GREEN || tint == BubbleTint.OUTGOING_BLUE) {
            return SenderRole.OUTGOING
        }

        // Factor 4: Edge proximity and margin contrast
        // Incoming messages are tightly anchored to the left (small leftMargin, larger rightMargin)
        // Outgoing messages are tightly anchored to the right (small rightMargin, larger leftMargin)
        return when {
            rightMargin < 0.14f && leftMargin > 0.16f -> SenderRole.OUTGOING
            leftMargin < 0.14f && rightMargin > 0.16f -> SenderRole.INCOMING
            leftMargin < rightMargin * 0.70f -> SenderRole.INCOMING
            rightMargin < leftMargin * 0.70f -> SenderRole.OUTGOING
            box.centerX > imageWidth * 0.52f -> SenderRole.OUTGOING
            box.centerX < imageWidth * 0.48f -> SenderRole.INCOMING
            else -> SenderRole.UNKNOWN
        }
    }

    private fun sampleBubbleTint(box: MessageBoundingBox, bitmap: android.graphics.Bitmap?): BubbleTint {
        if (bitmap == null || bitmap.isRecycled) return BubbleTint.UNKNOWN
        try {
            val bmpWidth = bitmap.width
            val bmpHeight = bitmap.height

            // Sample pixels slightly outside the left/right edges of the text
            val sampleX = (box.left.toInt() - 6).coerceIn(0, bmpWidth - 1)
            val sampleY = (box.centerY.toInt()).coerceIn(0, bmpHeight - 1)
            val pixel = bitmap.getPixel(sampleX, sampleY)

            val r = android.graphics.Color.red(pixel)
            val g = android.graphics.Color.green(pixel)
            val b = android.graphics.Color.blue(pixel)

            // WhatsApp outgoing green tint (#dcf8c6)
            if (g > r + 15 && g > b + 25) {
                return BubbleTint.OUTGOING_GREEN
            }
            // WhatsApp dark mode green (#005c4b)
            if (g > 60 && g > r + 15 && g > b + 10 && r < 50) {
                return BubbleTint.OUTGOING_GREEN
            }
            // Telegram / Messenger blue/purple outgoing
            if (b > r + 20 && b > g + 15) {
                return BubbleTint.OUTGOING_BLUE
            }

            return BubbleTint.NEUTRAL
        } catch (_: Exception) {
            return BubbleTint.UNKNOWN
        }
    }

    private fun isSameSide(boxA: MessageBoundingBox, boxB: MessageBoundingBox, imageWidth: Int): Boolean {
        val sideA = boxA.centerX > imageWidth * 0.5f
        val sideB = boxB.centerX > imageWidth * 0.5f
        return sideA == sideB
    }

    fun isSystemNoise(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.length <= 1) return true
        if (TIMESTAMP_PATTERN.matcher(trimmed).matches()) return true
        if (TIME_WITH_STATUS_PATTERN.matcher(trimmed).matches()) return true
        val lower = trimmed.lowercase()
        if (NOISE_WORDS.contains(lower)) return true

        // Filter out internal UI strings if screen capture captured Wise Chat's own activity
        for (noise in APP_INTERNAL_UI_NOISE) {
            if (lower == noise || lower.startsWith("$noise ") || lower.contains(noise)) {
                return true
            }
        }

        // Battery status e.g. "98%"
        if (trimmed.matches("^\\d{1,3}%$".toRegex())) return true
        return false
    }
}
