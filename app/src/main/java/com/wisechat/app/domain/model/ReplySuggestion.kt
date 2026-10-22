package com.wisechat.app.domain.model

data class ReplySuggestion(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val style: ReplyStyle = ReplyStyle.CASUAL,
    val description: String = "",
    val detectedLanguage: String = "English"
)

data class ReplyGenerationResponse(
    val targetMessage: String,
    val detectedLanguage: String,
    val conversationSummary: String = "",
    val suggestions: List<ReplySuggestion> = emptyList(),
    val warnings: List<String> = emptyList()
)

enum class RefinementType(val label: String, val instruction: String) {
    SHORTER("Make it shorter", "Condense this reply into fewer words while keeping the core meaning."),
    LONGER("Make it longer", "Expand this reply with more natural details and context."),
    MORE_NATURAL("More natural", "Make the phrasing sound more authentic, relaxed, and human."),
    ADD_HUMOR("Add humor", "Infuse witty, playful humor into this exact reply."),
    MORE_DIRECT("More direct", "Make this reply punchier, clearer, and more straightforward."),
    WARMER("Warmer", "Add more warmth, kindness, and personal connection to this reply."),
    MORE_FLIRTY("Make it flirtier", "Infuse irresistible magnetic charm, playful push-pull banter, and heart-melting sweetness that leaves them blushing and smiling."),
    MORE_CONFIDENT("More confident", "Make this reply sound completely self-assured and decisive."),
    LESS_INTENSE("Less intense", "Tone down the intensity and soften the emotional weight."),
    ADD_FOLLOWUP("Add question", "Add an engaging follow-up question to the end."),
    REMOVE_EMOJIS("Remove emojis", "Remove all emojis completely from this text."),
    ADD_EMOJIS("Add emojis", "Add 1 or 2 expressive, tasteful emojis to this text.")
}
