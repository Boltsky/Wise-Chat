package com.wisechat.app.domain.model

enum class ToneIntensity(val level: String, val promptInstruction: String) {
    SUBTLE("Subtle", "Keep the tone subtle, understated, and gentle."),
    BALANCED("Balanced", "Express a balanced, natural degree of this style."),
    BOLD("Bold", "Make this tone clearly noticeable, expressive, and prominent.")
}

enum class ReplyLength(val label: String, val promptInstruction: String) {
    VERY_SHORT("Very Short", "Limit the reply to under 10 words or a single punchy phrase."),
    SHORT("Short", "Keep the reply concise: 1 to 2 short sentences."),
    MEDIUM("Medium", "Provide a balanced reply: 2 to 3 sentences."),
    DETAILED("Detailed", "Provide a comprehensive response with complete explanations or paragraphs.")
}

enum class EmojiUsage(val label: String, val promptInstruction: String) {
    NONE("No Emojis", "Do NOT use any emojis whatsoever in the reply."),
    OCCASIONAL("Occasional", "Use 1 or at most 2 relevant emojis naturally placed."),
    EXPRESSIVE("Expressive", "Feel free to use expressive and lively emojis to convey emotion.")
}

enum class ConversationalObjective(val title: String, val directive: String) {
    KEEP_GOING("Keep conversation going", "Ask an engaging question or share an interesting detail that invites continuation."),
    SHOW_INTEREST("Show genuine interest", "Demonstrate that you find the topic or the other person genuinely intriguing."),
    SOUND_CONFIDENT("Sound confident", "Project strong self-assurance, decisiveness, and steady calm."),
    MAKE_THEM_LAUGH("Make them laugh", "Deliver a witty, humorous line or comical observation to spark laughter."),
    DEESCALATE("De-escalate tension", "Reduce conflict, soothe irritation, and create a calm atmosphere of mutual respect."),
    EXPRESS_SYMPATHY("Express heartfelt sympathy", "Convey tender sorrow, care, and solidarity for what they are undergoing."),
    POLITELY_DECLINE("Politely decline", "Say no or turn down the offer graciously without ambiguity or guilt."),
    ASK_CLARIFICATION("Ask for clarification", "Inquire tactfully about unclear details before making assumptions."),
    END_GRACEFULLY("End conversation gracefully", "Wrap up the chat politely without sounding abrupt or rude."),
    GIVE_PROFESSIONAL_ANSWER("Give professional answer", "Provide a competent, structured, workplace-appropriate update or response."),
    AVOID_DESPERATE("Avoid sounding desperate", "Keep responses measured, self-contained, and emotionally self-sufficient."),
    SET_CLEAR_BOUNDARY("Set clear boundary", "Directly state a limit on time, availability, or topic with firm courtesy.")
}

data class StyleCustomization(
    val selectedStyle: ReplyStyle = ReplyStyle.CASUAL,
    val intensity: ToneIntensity = ToneIntensity.BALANCED,
    val length: ReplyLength = ReplyLength.SHORT,
    val emojiUsage: EmojiUsage = EmojiUsage.OCCASIONAL,
    val objective: ConversationalObjective? = null,
    val language: String = "English",
    val customPromptOverride: String? = null
)

data class CustomPersonaPreset(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val promptDescription: String,
    val defaultLength: ReplyLength = ReplyLength.SHORT,
    val defaultIntensity: ToneIntensity = ToneIntensity.BALANCED,
    val defaultEmojiUsage: EmojiUsage = EmojiUsage.OCCASIONAL
)
