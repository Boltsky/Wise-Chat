package com.wisechat.app.engine.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.wisechat.app.domain.model.ConversationContext
import com.wisechat.app.domain.model.MessageBoundingBox
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MlKitOcrEngine {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun processImage(bitmap: Bitmap): Result<ConversationContext> {
        return try {
            // Guard: Check if bitmap is solid black or white (e.g. from secure screen capture)
            if (isSolidBitmap(bitmap)) {
                return Result.failure(
                    IllegalStateException("The screen appears blank or protected by security flags (FLAG_SECURE). Please import a screenshot or type text manually.")
                )
            }

            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val visionText = suspendCancellableCoroutine { continuation ->
                recognizer.process(inputImage)
                    .addOnSuccessListener { result ->
                        continuation.resume(result)
                    }
                    .addOnFailureListener { exception ->
                        continuation.resumeWithException(exception)
                    }
            }

            val elements = mutableListOf<RawTextElement>()
            for (block in visionText.textBlocks) {
                for (line in block.lines) {
                    val box = line.boundingBox
                    if (box != null) {
                        elements.add(
                            RawTextElement(
                                text = line.text,
                                boundingBox = MessageBoundingBox(
                                    left = box.left.toFloat(),
                                    top = box.top.toFloat(),
                                    right = box.right.toFloat(),
                                    bottom = box.bottom.toFloat()
                                ),
                                confidence = line.confidence ?: 1.0f
                            )
                        )
                    }
                }
            }

            val screenshotBase64 = compressBitmapToBase64(bitmap)

            if (elements.isEmpty()) {
                // If no text was recognized, but the screen has visual content (e.g. an image or sticker),
                // create a media message so multimodal AI can still analyze and reply to the image/sticker!
                val mediaMessage = com.wisechat.app.domain.model.ChatMessage(
                    text = "[Sticker / Image sent]",
                    sender = com.wisechat.app.domain.model.SenderRole.INCOMING,
                    isTarget = true,
                    hasMediaOrSticker = true
                )
                val mediaContext = ConversationContext(
                    messages = listOf(mediaMessage),
                    targetMessage = mediaMessage,
                    screenshotBase64 = screenshotBase64
                )
                return Result.success(mediaContext)
            }

            val rawContext = ConversationParser.parseConversation(
                elements = elements,
                imageWidth = bitmap.width,
                imageHeight = bitmap.height,
                bitmap = bitmap
            )

            if (rawContext.messages.isEmpty()) {
                return Result.failure(
                    IllegalStateException("No chat messages were detected on screen. Please make sure your messaging app (WhatsApp, Telegram, etc.) is open with messages visible, then tap Re-scan.")
                )
            }

            val context = rawContext.copy(screenshotBase64 = screenshotBase64)
            Result.success(context)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun compressBitmapToBase64(bitmap: Bitmap, maxDimension: Int = 1024): String {
        try {
            val width = bitmap.width
            val height = bitmap.height
            val scale = if (width > maxDimension || height > maxDimension) {
                maxDimension.toFloat() / maxOf(width, height)
            } else {
                1.0f
            }

            val scaledBitmap = if (scale < 1.0f) {
                Bitmap.createScaledBitmap(bitmap, (width * scale).toInt(), (height * scale).toInt(), true)
            } else {
                bitmap
            }

            val stream = java.io.ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
            val byteArray = stream.toByteArray()
            if (scaledBitmap != bitmap) {
                scaledBitmap.recycle()
            }
            return android.util.Base64.encodeToString(byteArray, android.util.Base64.NO_WRAP)
        } catch (_: Exception) {
            return ""
        }
    }

    private fun isSolidBitmap(bitmap: Bitmap): Boolean {
        if (bitmap.width <= 0 || bitmap.height <= 0) return true
        val sampleStepX = (bitmap.width / 10).coerceAtLeast(1)
        val sampleStepY = (bitmap.height / 10).coerceAtLeast(1)

        val firstPixel = bitmap.getPixel(0, 0)
        var allSame = true

        for (x in 0 until bitmap.width step sampleStepX) {
            for (y in 0 until bitmap.height step sampleStepY) {
                if (bitmap.getPixel(x, y) != firstPixel) {
                    allSame = false
                    break
                }
            }
            if (!allSame) break
        }
        return allSame
    }
}
