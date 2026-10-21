package com.wisechat.app.engine.prompt

import com.wisechat.app.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class PromptBuilderTest {

    @Test
    fun `test system instruction contains selected style directive and constraints`() {
        val customization = StyleCustomization(
            selectedStyle = ReplyStyle.FUNNY,
            intensity = ToneIntensity.BOLD,
            length = ReplyLength.SHORT,
            emojiUsage = EmojiUsage.OCCASIONAL,
            objective = ConversationalObjective.MAKE_THEM_LAUGH,
            language = "English"
        )

        val systemInstruction = PromptBuilder.buildSystemInstruction(customization)

        assertTrue(systemInstruction.contains("SELECTED STYLE: Funny"))
        assertTrue(systemInstruction.contains("Make this tone clearly noticeable"))
        assertTrue(systemInstruction.contains("1 to 2 short sentences"))
        assertTrue(systemInstruction.contains("CONVERSATIONAL OBJECTIVE: Make them laugh"))
        assertTrue(systemInstruction.contains("REQUIRED JSON OUTPUT FORMAT"))
    }

    @Test
    fun `test user message isolates untrusted conversation text`() {
        val messages = listOf(
            ChatMessage(text = "Are you coming to the party?", sender = SenderRole.INCOMING, isTarget = true)
        )
        val context = ConversationContext(messages = messages, targetMessage = messages[0])

        val userMessage = PromptBuilder.buildUserMessage(context)

        assertTrue(userMessage.contains("<<<CONVERSATION_SNIPPET>>>"))
        assertTrue(userMessage.contains("Them: Are you coming to the party? [TARGET TO ANSWER]"))
        assertTrue(userMessage.contains("TARGET MESSAGE TO REPLY TO:\n\"Are you coming to the party?\""))
    }

    @Test
    fun `test custom persona preset overrides default directive`() {
        val preset = CustomPersonaPreset(
            name = "My Executive Tone",
            promptDescription = "Be extremely concise, decisive, and focused on milestones."
        )
        val customization = StyleCustomization(selectedStyle = ReplyStyle.CUSTOM)

        val systemInstruction = PromptBuilder.buildSystemInstruction(customization, preset)

        assertTrue(systemInstruction.contains("Be extremely concise, decisive, and focused on milestones."))
    }
}
