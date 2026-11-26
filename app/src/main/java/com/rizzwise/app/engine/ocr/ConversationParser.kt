package com.rizzwise.app.engine.ocr

import android.graphics.Bitmap
import android.graphics.Color
import com.rizzwise.app.domain.model.ChatMessage
import com.rizzwise.app.domain.model.ConversationContext
import com.rizzwise.app.domain.model.MessageBoundingBox
import com.rizzwise.app.domain.model.SenderRole
import java.util.regex.Pattern
import kotlin.math.abs

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
        "^\\s*([0-1]?[0-9]|2[0-3]):[0-5][0-9](\\s*(AM|PM|am|pm))?(\\s*(✓|✓✓|√√|✔|✔✔|Delivered|Read|Sent|Edited))?\\s*$",
        Pattern.CASE_INSENSITIVE
    )
    private val TRAILING_TIME_CHECKMARK_REGEX = Regex(
        "\\s+([0-1]?[0-9]|2[0-3]):[0-5][0-9](\\s*(AM|PM|am|pm))?(\\s*(✓|✓✓|√√|✔|✔✔|Delivered|Read|Sent|Edited))?\\s*$",
        RegexOption.IGNORE_CASE
    )
    private val END_OF_MESSAGE_DELIVERY_REGEX = Regex(
        "(?:\\s+(?:[0-1]?[0-9]|2[0-3]):[0-5][0-9](?:\\s*(?:AM|PM|am|pm))?\\s*(?:[✓✔√]{1,2}|\\b(?:Delivered|Read|Sent|Edited)\\b)|\\s*[✓✔√]{1,2})\\s*$",
        RegexOption.IGNORE_CASE
    )
    private val TRAILING_CHECKMARK_ONLY_REGEX = Regex(
        "\\s*[✓✔√]{1,2}\\s*$"
    )

    private val NOISE_WORDS = setOf(
        "today", "yesterday", "online", "typing...", "last seen",
        "type a message", "message...", "send", "chat", "voice call", "video call",
        "search", "status", "calls", "chats", "camera", "settings"
    )

    // Keywords that denote centered security cards or administrative system notices
    private val SYSTEM_BANNER_KEYWORDS = listOf(
        "end-to-end encrypted", "security code changed", "disappearing messages",
        "messages to this chat and calls are now secured", "waiting for this message",
        "added to group", "created group", "changed the subject", "changed the group",
        "tap here for info", "tap for info"
    )

    // UI phrases from RizzWise / Wise Chat to prevent self-analyzing its own floating overlay
    private val APP_INTERNAL_UI_NOISE = listOf(
        "rizzwise", "wise chat", "floating rizz assistant", "ai reply assistant", "ai engine",
        "floating assistant", "bubble active on screen", "import & analyze screenshot", "select screenshot",
        "reply personalities", "custom voices", "reply history", "ai accounts", "byok",
        "one-tap account connection", "not connected", "google gemini", "anthropic claude", "service account",
        "open full rizzwise app"
    )

    enum class BubbleTint {
        OUTGOING_GREEN,
        OUTGOING_BLUE,
        NEUTRAL,
        UNKNOWN
    }

    /**
     * Parses raw extracted OCR elements and clusters them into coherent chat messages.
     * Accurately distinguishes between received (incoming/left) and sent (outgoing/right) messages.
     *
     * @param elements Extracted OCR text elements with spatial bounding boxes.
     * @param imageWidth Width of the captured screenshot in pixels.
     * @param imageHeight Height of the captured screenshot in pixels.
     * @param bitmap Optional original screenshot bitmap for color tint and media sampling.
     */
    fun parseConversation(
        elements: List<RawTextElement>,
        imageWidth: Int,
        imageHeight: Int,
        bitmap: Bitmap? = null
    ): ConversationContext {
        if (elements.isEmpty()) {
            return ConversationContext()
        }

        // Crop margins: ignore top contact bar (~8%) and bottom input bar (~7%)
        val topCropMargin = imageHeight * 0.08f
        val bottomCropMargin = imageHeight * 0.93f

        // Filter out status bar, input bar elements, timestamps, and centered administrative notices
        val contentElements = elements.filter { element ->
            val box = element.boundingBox
            val isWithinVerticalBounds = box.top >= topCropMargin && box.bottom <= bottomCropMargin
            val isNoise = isSystemNoise(element.text) || isSystemBanner(element.text, box, imageWidth)
            isWithinVerticalBounds && !isNoise
        }

        if (contentElements.isEmpty()) {
            return ConversationContext()
        }

        // Sort elements chronologically from top to bottom
        val sortedElements = contentElements.sortedBy { it.boundingBox.top }

        // Group elements vertically into message bubbles
        val clusteredMessages = mutableListOf<ChatMessage>()
        var currentCluster = mutableListOf<RawTextElement>()

        for (elem in sortedElements) {
            if (currentCluster.isEmpty()) {
                currentCluster.add(elem)
            } else {
                val prev = currentCluster.last()
                if (isSameBubble(prev, elem, imageWidth)) {
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

        // Identify candidate target streak:
        // Priority 1: Latest contiguous incoming message streak (from "Them")
        // Priority 2: Latest non-outgoing message (e.g. UNKNOWN sender)
        // Priority 3: Very last message if only outgoing messages exist
        val lastIncomingIdx = clusteredMessages.indexOfLast { it.sender == SenderRole.INCOMING }
        val targetIds: Set<String> = if (lastIncomingIdx != -1) {
            var startIdx = lastIncomingIdx
            while (startIdx > 0 && clusteredMessages[startIdx - 1].sender == SenderRole.INCOMING) {
                startIdx--
            }
            clusteredMessages.subList(startIdx, lastIncomingIdx + 1).map { it.id }.toSet()
        } else {
            val fallback = clusteredMessages.lastOrNull { it.sender != SenderRole.OUTGOING }
                ?: clusteredMessages.lastOrNull()
            setOfNotNull(fallback?.id)
        }

        val markedMessages = clusteredMessages.map {
            it.copy(isTarget = it.id in targetIds)
        }
        val candidateTarget = markedMessages.lastOrNull { it.isTarget }

        return ConversationContext(
            messages = markedMessages,
            targetMessage = candidateTarget
        )
    }

    private fun isSameBubble(
        prev: RawTextElement,
        curr: RawTextElement,
        imageWidth: Int
    ): Boolean {
        val boxA = prev.boundingBox
        val boxB = curr.boundingBox

        // Vertical gap check: prevent merging elements that belong to separate bubbles
        val verticalGap = boxB.top - boxA.bottom
        val maxMergeGap = (boxA.height * 0.95f).coerceAtLeast(24f)
        if (verticalGap > maxMergeGap) {
            return false
        }

        // Both lines are left-anchored (Incoming bubble lines, including avatar-offset apps)
        val bothLeftAnchored = boxA.left < imageWidth * 0.24f && boxB.left < imageWidth * 0.24f
        if (bothLeftAnchored) {
            val leftDiff = abs(boxA.left - boxB.left)
            if (leftDiff < imageWidth * 0.12f) return true
        }

        // Both lines are right-anchored (Outgoing bubble lines)
        val bothRightAnchored = (imageWidth - boxA.right) < imageWidth * 0.20f &&
                (imageWidth - boxB.right) < imageWidth * 0.20f &&
                boxA.left > imageWidth * 0.22f && boxB.left > imageWidth * 0.22f
        if (bothRightAnchored) {
            val rightDiff = abs(boxA.right - boxB.right)
            if (rightDiff < imageWidth * 0.12f) return true
        }

        // Shared left margin for multiline incoming message where one line is wide
        if (boxA.left < imageWidth * 0.22f && boxB.left < imageWidth * 0.22f &&
            abs(boxA.left - boxB.left) < imageWidth * 0.08f
        ) {
            return true
        }

        // Shared right margin for multiline outgoing message where one line is wide
        if ((imageWidth - boxA.right) < imageWidth * 0.16f &&
            (imageWidth - boxB.right) < imageWidth * 0.16f &&
            boxA.left > imageWidth * 0.20f && boxB.left > imageWidth * 0.20f
        ) {
            return true
        }

        return false
    }

    private fun buildMessageFromCluster(
        cluster: List<RawTextElement>,
        imageWidth: Int,
        bitmap: Bitmap? = null
    ): ChatMessage {
        val rawClusterText = cluster.joinToString(" ") { it.text }

        // Strict end-of-bubble delivery status check (avoids matching words in prose like "read" or "delivered")
        val hasDeliveryStatus = END_OF_MESSAGE_DELIVERY_REGEX.containsMatchIn(rawClusterText)

        var mergedText = cluster.joinToString(" ") { it.text.trim() }
            .replace("\\s+".toRegex(), " ")
            .trim()

        // Strip trailing timestamps and checkmarks from chat text (e.g. "Hey! 10:45 AM ✓✓")
        mergedText = mergedText.replace(TRAILING_TIME_CHECKMARK_REGEX, "").trim()
        // Also strip any standalone trailing checkmarks
        mergedText = mergedText.replace(TRAILING_CHECKMARK_ONLY_REGEX, "").trim()

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

    /**
     * Determines whether a chat bubble is INCOMING (Received) or OUTGOING (Sent).
     *
     * Invariant:
     * - Incoming (received) messages are anchored to the LEFT edge of the screen.
     * - Outgoing (sent) messages are anchored to the RIGHT edge of the screen.
     */
    fun determineSenderRole(
        box: MessageBoundingBox,
        imageWidth: Int,
        hasDeliveryStatus: Boolean = false,
        bitmap: Bitmap? = null
    ): SenderRole {
        val leftMargin = box.left / imageWidth.toFloat()
        val rightMargin = (imageWidth - box.right) / imageWidth.toFloat()

        // Priority 1: Delivery checkmarks strictly on the right side confirm OUTGOING.
        if (hasDeliveryStatus && rightMargin < 0.25f && leftMargin > 0.16f) {
            return SenderRole.OUTGOING
        }

        // Priority 2: Left-anchored messages are INCOMING (received) across messaging platforms
        // (leftMargin < 0.16f without avatar, or 0.16f..0.25f with left-side profile avatar).
        if (leftMargin < 0.16f) {
            if (rightMargin < 0.10f) {
                val tint = sampleBubbleTint(box, bitmap)
                if (tint == BubbleTint.OUTGOING_GREEN || tint == BubbleTint.OUTGOING_BLUE) {
                    return SenderRole.OUTGOING
                }
            }
            return SenderRole.INCOMING
        }

        if (leftMargin < 0.25f) {
            if (rightMargin > 0.16f) {
                return SenderRole.INCOMING
            }
            // Wide bubble starting in avatar-offset zone (0.16..0.25): check outgoing color tint
            val tint = sampleBubbleTint(box, bitmap)
            if (tint == BubbleTint.OUTGOING_GREEN || tint == BubbleTint.OUTGOING_BLUE) {
                return SenderRole.OUTGOING
            }
            return SenderRole.INCOMING
        }

        // Priority 3: Clear Right Anchorage (OUTGOING / SENT)
        if (rightMargin < 0.22f) {
            return SenderRole.OUTGOING
        }

        // Priority 4: Relative Margin Contrast
        // If one margin is significantly smaller, the bubble is anchored to that side
        if (leftMargin < rightMargin * 0.75f) {
            return SenderRole.INCOMING
        }
        if (rightMargin < leftMargin * 0.75f) {
            return SenderRole.OUTGOING
        }

        // Priority 5: Center-of-Mass Spatial Threshold
        if (box.centerX < imageWidth * 0.46f) {
            return SenderRole.INCOMING
        }
        if (box.centerX > imageWidth * 0.54f) {
            return SenderRole.OUTGOING
        }

        // Priority 6: Bubble tint tiebreaker for ambiguous messages near the center
        val tint = sampleBubbleTint(box, bitmap)
        if (tint == BubbleTint.OUTGOING_GREEN || tint == BubbleTint.OUTGOING_BLUE) {
            return SenderRole.OUTGOING
        }

        // Default fallback: left half = INCOMING, right half = OUTGOING
        return if (box.centerX <= imageWidth * 0.50f) SenderRole.INCOMING else SenderRole.OUTGOING
    }

    private fun sampleBubbleTint(box: MessageBoundingBox, bitmap: Bitmap?): BubbleTint {
        if (bitmap == null || bitmap.isRecycled) return BubbleTint.UNKNOWN
        try {
            val bmpWidth = bitmap.width
            val bmpHeight = bitmap.height

            // Sample slightly inside the bubble content area to avoid wallpaper artifacts
            val sampleX = (box.centerX.toInt()).coerceIn(0, bmpWidth - 1)
            val sampleY = (box.centerY.toInt()).coerceIn(0, bmpHeight - 1)
            val pixel = bitmap.getPixel(sampleX, sampleY)

            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)

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

    private fun isSystemBanner(text: String, box: MessageBoundingBox, imageWidth: Int): Boolean {
        val lower = text.lowercase().trim()
        val containsBannerKeyword = SYSTEM_BANNER_KEYWORDS.any { lower.contains(it) }
        if (!containsBannerKeyword) return false

        // System banners are horizontally centered across the screen
        val leftMargin = box.left / imageWidth.toFloat()
        val rightMargin = (imageWidth - box.right) / imageWidth.toFloat()
        val isRoughlyCentered = abs(leftMargin - rightMargin) < 0.15f
        return isRoughlyCentered
    }

    fun isSystemNoise(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.length <= 1) return true
        if (TIMESTAMP_PATTERN.matcher(trimmed).matches()) return true
        if (TIME_WITH_STATUS_PATTERN.matcher(trimmed).matches()) return true
        val lower = trimmed.lowercase()
        if (NOISE_WORDS.contains(lower)) return true

        // Filter out internal UI strings if screen capture captured RizzWise's own overlay
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
