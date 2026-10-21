package com.wisechat.app.engine.ocr

import com.wisechat.app.domain.model.MessageBoundingBox
import com.wisechat.app.domain.model.SenderRole
import org.junit.Assert.*
import org.junit.Test

class ConversationParserTest {

    @Test
    fun `test system noise detection`() {
        assertTrue(ConversationParser.isSystemNoise("10:45 AM"))
        assertTrue(ConversationParser.isSystemNoise("14:30"))
        assertTrue(ConversationParser.isSystemNoise("10:45 PM ✓✓"))
        assertTrue(ConversationParser.isSystemNoise("typing..."))
        assertTrue(ConversationParser.isSystemNoise("98%"))
        assertFalse(ConversationParser.isSystemNoise("Hey, are we still meeting today at 5?"))
    }

    @Test
    fun `test incoming and outgoing classification based on coordinates`() {
        val screenWidth = 1080
        val screenHeight = 2400

        // Incoming message: Left-anchored
        val incomingElement = RawTextElement(
            text = "Hey! What are you doing tonight?",
            boundingBox = MessageBoundingBox(left = 50f, top = 300f, right = 450f, bottom = 380f)
        )

        // Outgoing message: Right-anchored
        val outgoingElement = RawTextElement(
            text = "Not much, just finishing up work. You?",
            boundingBox = MessageBoundingBox(left = 550f, top = 420f, right = 1030f, bottom = 500f)
        )

        // Latest Incoming message (target to answer)
        val targetElement = RawTextElement(
            text = "Do you want to grab drinks at 8?",
            boundingBox = MessageBoundingBox(left = 50f, top = 540f, right = 500f, bottom = 620f)
        )

        val elements = listOf(incomingElement, outgoingElement, targetElement)
        val context = ConversationParser.parseConversation(elements, screenWidth, screenHeight)

        assertEquals(3, context.messages.size)
        assertEquals(SenderRole.INCOMING, context.messages[0].sender)
        assertEquals(SenderRole.OUTGOING, context.messages[1].sender)
        assertEquals(SenderRole.INCOMING, context.messages[2].sender)

        // Verify target message auto-selection
        assertNotNull(context.targetMessage)
        assertEquals("Do you want to grab drinks at 8?", context.targetMessage?.text)
        assertTrue(context.messages[2].isTarget)
    }

    @Test
    fun `test noise elements are filtered out`() {
        val screenWidth = 1080
        val screenHeight = 2400

        val timeElement = RawTextElement(
            text = "11:30 AM",
            boundingBox = MessageBoundingBox(left = 480f, top = 200f, right = 600f, bottom = 240f)
        )
        val realMessage = RawTextElement(
            text = "See you tomorrow!",
            boundingBox = MessageBoundingBox(left = 50f, top = 350f, right = 400f, bottom = 420f)
        )

        val elements = listOf(timeElement, realMessage)
        val context = ConversationParser.parseConversation(elements, screenWidth, screenHeight)

        assertEquals(1, context.messages.size)
        assertEquals("See you tomorrow!", context.messages[0].text)
    }
}
