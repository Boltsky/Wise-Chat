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
    fun `test holistic conversation synthesis instructions present in system prompt`() {
        val customization = StyleCustomization(selectedStyle = ReplyStyle.CASUAL)
        val systemInstruction = PromptBuilder.buildSystemInstruction(customization)

        assertTrue(systemInstruction.contains("Holistic Conversation Synthesis & Rhyme"))
        assertTrue(systemInstruction.contains("Never view the latest message in a vacuum"))
        assertTrue(systemInstruction.contains("'Rhyme' with the whole chat"))
        assertTrue(systemInstruction.contains("Avoid shallow or empty acknowledgment"))
        assertTrue(systemInstruction.contains("conversation_summary"))
    }

    @Test
    fun `test flirty style includes heart-melting aww and banter directives`() {
        val customization = StyleCustomization(
            selectedStyle = ReplyStyle.FLIRTY,
            intensity = ToneIntensity.BALANCED,
            length = ReplyLength.SHORT,
            emojiUsage = EmojiUsage.OCCASIONAL
        )

        val systemInstruction = PromptBuilder.buildSystemInstruction(customization)

        assertTrue(systemInstruction.contains("SELECTED STYLE: Flirty"))
        assertTrue(systemInstruction.contains("SPECIALIZED FLIRTY PERSONA GUIDELINES"))
        assertTrue(systemInstruction.contains("THE 'AWW' & SMILE MANDATE"))
        assertTrue(systemInstruction.contains("WITTY PUSH-PULL & TEASING"))
        assertTrue(systemInstruction.contains("IRRESISTIBLE CHARISMA & RIZZ"))
        assertTrue(systemInstruction.contains("The Heart-Melter"))
        assertTrue(systemInstruction.contains("The Playful Tease"))
        assertTrue(systemInstruction.contains("The Smooth Charmer"))
        assertTrue(systemInstruction.contains("The Callback Flirt"))
    }

    @Test
    fun `test user message isolates untrusted conversation text and marks latest message`() {
        val messages = listOf(
            ChatMessage(text = "Are you coming to the party?", sender = SenderRole.INCOMING, isTarget = true)
        )
        val context = ConversationContext(messages = messages, targetMessage = messages[0])

        val userMessage = PromptBuilder.buildUserMessage(context)

        assertTrue(userMessage.contains("<<<FULL_CONVERSATION_HISTORY>>>"))
        assertTrue(userMessage.contains("Them: Are you coming to the party? [LATEST MESSAGE TO ANSWER]"))
        assertTrue(userMessage.contains("LATEST MESSAGE TO ANSWER IN CONTEXT:\n\"Are you coming to the party?\""))
        assertTrue(userMessage.contains("rhyme with the full conversation history"))
    }

    @Test
    fun `test refinement prompt incorporates conversation context for holistic coherence`() {
        val messages = listOf(
            ChatMessage(text = "Had a crazy day at work today", sender = SenderRole.INCOMING),
            ChatMessage(text = "Tell me everything", sender = SenderRole.OUTGOING),
            ChatMessage(text = "My boss announced surprise audit", sender = SenderRole.INCOMING, isTarget = true)
        )
        val context = ConversationContext(messages = messages, targetMessage = messages[2])

        val refinementPrompt = PromptBuilder.buildRefinementPrompt(
            originalReply = "That sounds stressful, hang in there!",
            refinementType = RefinementType.MORE_FLIRTY,
            targetMessage = "My boss announced surprise audit",
            context = context
        )

        assertTrue(refinementPrompt.contains("<<<CONVERSATION_CONTEXT>>>"))
        assertTrue(refinementPrompt.contains("Them: Had a crazy day at work today"))
        assertTrue(refinementPrompt.contains("You: Tell me everything"))
        assertTrue(refinementPrompt.contains("Them: My boss announced surprise audit"))
        assertTrue(refinementPrompt.contains("TARGET MESSAGE BEING ANSWERED:\n\"My boss announced surprise audit\""))
        assertTrue(refinementPrompt.contains("ORIGINAL SUGGESTED REPLY:\n\"That sounds stressful, hang in there!\""))
        assertTrue(refinementPrompt.contains("REFINEMENT INSTRUCTION:\nInfuse irresistible magnetic charm"))
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
