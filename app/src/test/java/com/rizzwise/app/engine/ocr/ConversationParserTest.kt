package com.rizzwise.app.engine.ocr

import com.rizzwise.app.domain.model.MessageBoundingBox
import com.rizzwise.app.domain.model.SenderRole
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
    fun `test wide incoming message is not dropped as full width banner and correctly classified as INCOMING`() {
        val screenWidth = 1080
        val screenHeight = 2400

        // Wide incoming message taking up 85% of screen width (e.g. 50f to 960f)
        val wideIncomingElement = RawTextElement(
            text = "I was thinking we could try that new Italian restaurant downtown, let me know if that works for you!",
            boundingBox = MessageBoundingBox(left = 50f, top = 400f, right = 960f, bottom = 480f)
        )

        val elements = listOf(wideIncomingElement)
        val context = ConversationParser.parseConversation(elements, screenWidth, screenHeight)

        assertEquals(1, context.messages.size)
        assertEquals(SenderRole.INCOMING, context.messages[0].sender)
        assertEquals(wideIncomingElement.text, context.targetMessage?.text)
        assertTrue(context.messages[0].isTarget)
    }

    @Test
    fun `test incoming message containing word read or delivered is classified as INCOMING`() {
        val screenWidth = 1080
        val screenHeight = 2400

        // Received message containing the word "read" and "delivered" in prose
        val incomingWithProse = RawTextElement(
            text = "Did you read the article I sent? The package was delivered earlier today.",
            boundingBox = MessageBoundingBox(left = 60f, top = 350f, right = 850f, bottom = 430f)
        )

        val elements = listOf(incomingWithProse)
        val context = ConversationParser.parseConversation(elements, screenWidth, screenHeight)

        assertEquals(1, context.messages.size)
        // Must NOT be falsely marked OUTGOING just because "read" or "delivered" appears in prose
        assertEquals(SenderRole.INCOMING, context.messages[0].sender)
        assertEquals(context.messages[0].id, context.targetMessage?.id)
    }

    @Test
    fun `test multiline incoming message with varying line widths clusters into single INCOMING bubble`() {
        val screenWidth = 1080
        val screenHeight = 2400

        // Line 1: Wide line
        val line1 = RawTextElement(
            text = "Hey! I tried calling you earlier but your phone was unreachable.",
            boundingBox = MessageBoundingBox(left = 55f, top = 500f, right = 980f, bottom = 540f)
        )
        // Line 2: Short line
        val line2 = RawTextElement(
            text = "Call me back when you're free!",
            boundingBox = MessageBoundingBox(left = 55f, top = 545f, right = 420f, bottom = 585f)
        )

        val elements = listOf(line1, line2)
        val context = ConversationParser.parseConversation(elements, screenWidth, screenHeight)

        assertEquals(1, context.messages.size)
        assertEquals(SenderRole.INCOMING, context.messages[0].sender)
        assertEquals("Hey! I tried calling you earlier but your phone was unreachable. Call me back when you're free!", context.messages[0].text)
        assertEquals(context.messages[0].id, context.targetMessage?.id)
    }

    @Test
    fun `test conversation ending with sent message still selects latest received message as target`() {
        val screenWidth = 1080
        val screenHeight = 2400

        // 1. Received message from them
        val themMessage = RawTextElement(
            text = "Are you coming to the party tonight?",
            boundingBox = MessageBoundingBox(left = 50f, top = 300f, right = 550f, bottom = 360f)
        )

        // 2. Sent message from you at the end of the chat
        val youMessage = RawTextElement(
            text = "Yeah, I'll be there in 20 minutes! 10:45 PM ✓✓",
            boundingBox = MessageBoundingBox(left = 500f, top = 420f, right = 1030f, bottom = 480f)
        )

        val elements = listOf(themMessage, youMessage)
        val context = ConversationParser.parseConversation(elements, screenWidth, screenHeight)

        assertEquals(2, context.messages.size)
        assertEquals(SenderRole.INCOMING, context.messages[0].sender)
        assertEquals(SenderRole.OUTGOING, context.messages[1].sender)

        // Target should be the received message from Them ("Are you coming to the party tonight?"), NOT your sent reply!
        assertEquals("Are you coming to the party tonight?", context.targetMessage?.text)
        assertTrue(context.messages[0].isTarget)
        assertFalse(context.messages[1].isTarget)
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

    @Test
    fun `test avatar-offset incoming message including wide line is classified as INCOMING`() {
        val screenWidth = 1080
        val screenHeight = 2400

        // Instagram/Messenger/Tinder style: left avatar pushes text start to ~18.5% (200f)
        // and text wraps close to right edge (940f -> rightMargin ~13%)
        val avatarOffsetIncoming = RawTextElement(
            text = "Wait are you seriously telling me you didn't see that coming?",
            boundingBox = MessageBoundingBox(left = 200f, top = 500f, right = 940f, bottom = 560f)
        )

        val context = ConversationParser.parseConversation(listOf(avatarOffsetIncoming), screenWidth, screenHeight)

        assertEquals(1, context.messages.size)
        assertEquals(SenderRole.INCOMING, context.messages[0].sender)
        assertTrue(context.messages[0].isTarget)
    }

    @Test
    fun `test received message ending in prose read or sent without timestamp is INCOMING`() {
        val screenWidth = 1080
        val screenHeight = 2400

        val msgEndingWithSent = RawTextElement(
            text = "Did you check the message I sent",
            boundingBox = MessageBoundingBox(left = 190f, top = 400f, right = 880f, bottom = 460f)
        )

        val context = ConversationParser.parseConversation(listOf(msgEndingWithSent), screenWidth, screenHeight)

        assertEquals(1, context.messages.size)
        assertEquals(SenderRole.INCOMING, context.messages[0].sender)
        assertEquals("Did you check the message I sent", context.messages[0].text)
    }

    @Test
    fun `test received message near bottom input bar is captured`() {
        val screenWidth = 1080
        val screenHeight = 2400

        val olderSent = RawTextElement(
            text = "Let me know! 10:15 AM ✓✓",
            boundingBox = MessageBoundingBox(left = 480f, top = 1800f, right = 1030f, bottom = 1860f)
        )
        // Message sitting at ~90.5% to 92.5% of screen height (just above input bar)
        val bottomReceived = RawTextElement(
            text = "Just got home!",
            boundingBox = MessageBoundingBox(left = 60f, top = 2170f, right = 420f, bottom = 2225f)
        )

        val context = ConversationParser.parseConversation(listOf(olderSent, bottomReceived), screenWidth, screenHeight)

        assertEquals(2, context.messages.size)
        assertEquals(SenderRole.INCOMING, context.messages[1].sender)
        assertEquals("Just got home!", context.resolvedTargetText)
    }

    @Test
    fun `test wide incoming bubble and closely following outgoing bubble do not merge`() {
        val screenWidth = 1080
        val screenHeight = 2400

        val wideIncoming = RawTextElement(
            text = "Okay sounds good, see you at the coffee shop around 6pm!",
            boundingBox = MessageBoundingBox(left = 60f, top = 600f, right = 920f, bottom = 650f)
        )
        val shortOutgoing = RawTextElement(
            text = "Bet! ✓✓",
            boundingBox = MessageBoundingBox(left = 780f, top = 685f, right = 1030f, bottom = 735f)
        )

        val context = ConversationParser.parseConversation(listOf(wideIncoming, shortOutgoing), screenWidth, screenHeight)

        assertEquals(2, context.messages.size)
        assertEquals(SenderRole.INCOMING, context.messages[0].sender)
        assertEquals(SenderRole.OUTGOING, context.messages[1].sender)
        assertEquals(wideIncoming.text, context.resolvedTargetText)
    }

    @Test
    fun `test contiguous received streak selects all streak messages as target`() {
        val screenWidth = 1080
        val screenHeight = 2400

        val myPreviousMsg = RawTextElement(
            text = "Hey what's up? ✓✓",
            boundingBox = MessageBoundingBox(left = 550f, top = 300f, right = 1030f, bottom = 350f)
        )
        val streak1 = RawTextElement(
            text = "Heyyy",
            boundingBox = MessageBoundingBox(left = 60f, top = 460f, right = 260f, bottom = 510f)
        )
        val streak2 = RawTextElement(
            text = "Are you free tonight?",
            boundingBox = MessageBoundingBox(left = 60f, top = 580f, right = 540f, bottom = 630f)
        )
        val streak3 = RawTextElement(
            text = "??",
            boundingBox = MessageBoundingBox(left = 60f, top = 700f, right = 160f, bottom = 750f)
        )

        val context = ConversationParser.parseConversation(
            listOf(myPreviousMsg, streak1, streak2, streak3),
            screenWidth,
            screenHeight
        )

        assertEquals(4, context.messages.size)
        assertFalse(context.messages[0].isTarget)
        assertTrue(context.messages[1].isTarget)
        assertTrue(context.messages[2].isTarget)
        assertTrue(context.messages[3].isTarget)
        assertEquals(3, context.resolvedTargetMessages.size)
        assertEquals("Heyyy\nAre you free tonight?\n??", context.resolvedTargetText)
    }
}
