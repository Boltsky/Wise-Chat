package com.wisechat.app.domain.model

enum class ReplyStyle(
    val title: String,
    val description: String,
    val defaultDirective: String,
    val category: StyleCategory
) {
    CASUAL(
        title = "Casual",
        description = "Relaxed, natural, everyday language",
        defaultDirective = "Adopt a relaxed, natural, friendly tone. Use everyday vocabulary and conversational flow as if texting a good friend.",
        category = StyleCategory.EVERYDAY
    ),
    FUNNY(
        title = "Funny",
        description = "Lighthearted humor and witty observations",
        defaultDirective = "Include playful humor, witty punchlines, light teasing, or clever banter without being harsh or offensive.",
        category = StyleCategory.HUMOR_CHARM
    ),
    FLIRTY(
        title = "Flirty",
        description = "Magnetic charm, heart-melting banter, and butterflies",
        defaultDirective = "Deliver irresistible charm, heart-melting sweetness, and magnetic banter that leaves the recipient grinning, blushing, and saying 'aww'. Seamlessly balance witty push-pull teasing with genuine, disarming compliments. Never sound generic, cringe, or robotic—make every reply feel effortlessly smooth, captivating, and impossible not to smile at.",
        category = StyleCategory.HUMOR_CHARM
    ),
    INQUISITIVE(
        title = "Inquisitive",
        description = "Curious questions that spark dialogue",
        defaultDirective = "Ask thoughtful, engaging follow-up questions that naturally invite the other person to share more and keep the conversation going.",
        category = StyleCategory.ENGAGEMENT
    ),
    FORMAL(
        title = "Formal",
        description = "Polite, structured, conventional courtesy",
        defaultDirective = "Maintain formal grammatical correctness, high courtesy, structured etiquette, and respectful conventions.",
        category = StyleCategory.BUSINESS
    ),
    PROFESSIONAL(
        title = "Professional",
        description = "Workplace-ready, executive clarity",
        defaultDirective = "Communicate with crisp executive clarity, constructive professionalism, and action-oriented phrasing suitable for colleagues and clients.",
        category = StyleCategory.BUSINESS
    ),
    EMOTIONAL(
        title = "Emotional",
        description = "Expressive, authentic feeling",
        defaultDirective = "Convey genuine emotion and authenticity, mirroring the depth and vulnerability of the conversation without melodrama.",
        category = StyleCategory.INTERPERSONAL
    ),
    EMPATHETIC(
        title = "Empathetic",
        description = "Comforting, supportive, understanding",
        defaultDirective = "Show deep emotional validation, compassionate active listening, and sincere comfort. Acknowledge the other person's perspective.",
        category = StyleCategory.INTERPERSONAL
    ),
    ROMANTIC(
        title = "Romantic",
        description = "Affectionate, tender, deeply personal",
        defaultDirective = "Express tender warmth, affection, and personal fondness suited to an established romantic connection or intimate bond.",
        category = StyleCategory.HUMOR_CHARM
    ),
    CONFIDENT(
        title = "Confident",
        description = "Self-assured, direct, clear",
        defaultDirective = "Speak with calm authority, decisive clarity, and self-assurance without sounding aggressive, defensive, or arrogant.",
        category = StyleCategory.ENGAGEMENT
    ),
    WITTY(
        title = "Witty",
        description = "Clever repartee and sharp intellect",
        defaultDirective = "Craft clever, intellectually sharp, and concise responses with subtle irony and sophisticated wordplay.",
        category = StyleCategory.HUMOR_CHARM
    ),
    PLAYFUL(
        title = "Playful",
        description = "Bouncy, energetic, teasing",
        defaultDirective = "Bring high positive energy, fun banter, cheeky commentary, and lively engagement without requiring romantic undertones.",
        category = StyleCategory.EVERYDAY
    ),
    SUPPORTIVE(
        title = "Supportive",
        description = "Encouraging, inspiring, uplifting",
        defaultDirective = "Provide uplifting reinforcement, words of encouragement, belief in their capabilities, and reliable reassurance.",
        category = StyleCategory.INTERPERSONAL
    ),
    APOLOGETIC(
        title = "Apologetic",
        description = "Sincere ownership and constructive repair",
        defaultDirective = "Offer a sincere, humble apology, acknowledge any misunderstanding or oversight without excessive self-blame, and focus on constructive repair.",
        category = StyleCategory.INTERPERSONAL
    ),
    PERSUASIVE(
        title = "Persuasive",
        description = "Compelling rationale and constructive framing",
        defaultDirective = "Articulate a compelling argument or request using clear logic, positive mutual benefit, and respectful framing without manipulation.",
        category = StyleCategory.BUSINESS
    ),
    DIPLOMATIC(
        title = "Diplomatic",
        description = "Tactful, balanced, bridge-building",
        defaultDirective = "Navigate sensitive disagreements or tension with tact, balanced viewpoints, de-escalation, and face-saving respect for all parties.",
        category = StyleCategory.BUSINESS
    ),
    BOUNDARY_SETTING(
        title = "Boundary-setting",
        description = "Firm, polite assertiveness and limits",
        defaultDirective = "Establish clear personal or professional boundaries with calm firmness, polite refusal, and unambiguous clarity while staying courteous.",
        category = StyleCategory.INTERPERSONAL
    ),
    CONCISE(
        title = "Concise",
        description = "Short, direct, minimal wording",
        defaultDirective = "Be extremely concise, brief, and to the point. Strip all unnecessary filler, adjectives, and greetings while preserving courtesy.",
        category = StyleCategory.FORMAT
    ),
    DETAILED(
        title = "Detailed",
        description = "Thorough context and complete explanation",
        defaultDirective = "Provide comprehensive context, structured reasoning, and a complete explanation to leave no ambiguities unresolved.",
        category = StyleCategory.FORMAT
    ),
    CUSTOM(
        title = "Custom",
        description = "User-defined communication persona",
        defaultDirective = "Follow the user's custom prompt persona instructions accurately.",
        category = StyleCategory.CUSTOM
    );

    enum class StyleCategory(val label: String) {
        EVERYDAY("Everyday"),
        HUMOR_CHARM("Humor & Charm"),
        BUSINESS("Business & Work"),
        INTERPERSONAL("Interpersonal"),
        ENGAGEMENT("Engagement"),
        FORMAT("Structure & Length"),
        CUSTOM("Personalized")
    }
}
