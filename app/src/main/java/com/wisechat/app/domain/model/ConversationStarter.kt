package com.wisechat.app.domain.model

enum class StarterCategory(
    val title: String,
    val description: String,
    val iconEmoji: String,
    val directive: String
) {
    SWEET_AWW(
        title = "Sweet & 'Aww'",
        description = "Heart-melting, adorable compliments and sweet vulnerability",
        iconEmoji = "🥺",
        directive = "Craft uniquely sweet, heart-melting, and endearing lines that make the recipient smile warmly and say 'aww'. Use simple, everyday conversational English. Never sound cheesy, creepy, or fake—sound genuinely disarmed, observant, and cute."
    ),
    PLAYFUL_BANTER(
        title = "Playful Tease",
        description = "Cheeky push-pull banter and witty challenges",
        iconEmoji = "😏",
        directive = "Craft clever, playful teasing and push-pull icebreakers that spark immediate fun tension and banter. Keep the wording casual, witty, and effortless without sounding arrogant or mean."
    ),
    SMOOTH_CHARM(
        title = "Smooth Rizz",
        description = "Effortlessly confident and magnetic charm",
        iconEmoji = "✨",
        directive = "Deliver smooth, charismatic, self-assured lines with natural magnetic confidence. Avoid clichés completely—make every opener feel fresh, original, and cool."
    ),
    BIO_HOOK(
        title = "Profile & Bio Hook",
        description = "Tailored opener based on their interests, photos, or bio",
        iconEmoji = "🎯",
        directive = "Create a sharp, observant conversation opener that hooks into their interests, photo vibes, or bio details. Spark curiosity and make it effortless for them to reply."
    ),
    INTRIGUING_QUESTION(
        title = "Fun & Curious",
        description = "Unexpected dilemmas and engaging questions",
        iconEmoji = "🤔",
        directive = "Ask an intriguing, fun, or lightly controversial question or dilemma that nobody else has asked them, making it impossible to leave on read."
    )
}

data class ConversationStarter(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val category: StarterCategory = StarterCategory.SWEET_AWW,
    val tag: String = "",
    val whyItWorks: String = "",
    val isAiGenerated: Boolean = false
)

data class StartersGenerationResponse(
    val category: StarterCategory,
    val contextTopic: String?,
    val starters: List<ConversationStarter> = emptyList()
)

object CuratedStartersLibrary {

    val allStarters: List<ConversationStarter> = listOf(
        // === Sweet & 'Aww' (Heart-Melters) ===
        ConversationStarter(
            text = "I won't lie, your smile completely distracted me from whatever I was about to do.",
            category = StarterCategory.SWEET_AWW,
            tag = "Heart-Melter",
            whyItWorks = "Disarmingly honest and flattering without feeling like an over-rehearsed line."
        ),
        ConversationStarter(
            text = "My day was pretty ordinary until I saw your profile, now I can't stop smiling at my phone.",
            category = StarterCategory.SWEET_AWW,
            tag = "Sweet & Sincere",
            whyItWorks = "Makes the recipient feel uniquely special right away."
        ),
        ConversationStarter(
            text = "I was going to come up with a clever line, but honestly, you just look really sweet and I wanted to say hi.",
            category = StarterCategory.SWEET_AWW,
            tag = "Endearing Honesty",
            whyItWorks = "Drops all pickup games in favor of refreshing vulnerability."
        ),
        ConversationStarter(
            text = "Quick warning: if we start talking, I might get used to seeing your cute smile every day.",
            category = StarterCategory.SWEET_AWW,
            tag = "Cute Anticipation",
            whyItWorks = "Playfully implies romantic potential while giving a warm compliment."
        ),
        ConversationStarter(
            text = "I have a weakness for kind eyes and good energy, and you clearly have both.",
            category = StarterCategory.SWEET_AWW,
            tag = "Genuine Compliment",
            whyItWorks = "Compliments their warmth and personality rather than superficial traits."
        ),
        ConversationStarter(
            text = "Tell me one small thing that made you genuinely happy today. I'd love to hear it.",
            category = StarterCategory.SWEET_AWW,
            tag = "Warm Connection",
            whyItWorks = "Invites them to share a positive memory in simple, comfortable English."
        ),
        ConversationStarter(
            text = "I'm usually pretty good at focusing, but your pictures completely broke my concentration.",
            category = StarterCategory.SWEET_AWW,
            tag = "Adorable Confession",
            whyItWorks = "Cute, lighthearted admission that always evokes a grin."
        ),
        ConversationStarter(
            text = "You look like someone who gives the warmest hugs and knows the absolute best food spots.",
            category = StarterCategory.SWEET_AWW,
            tag = "Cozy Charm",
            whyItWorks = "Creates cozy comfort and provides an effortless pivot to favorite food or coffee."
        ),

        // === Playful Tease & Banter ===
        ConversationStarter(
            text = "I have a strict rule about not texting first, but your smile made me break it in about three seconds.",
            category = StarterCategory.PLAYFUL_BANTER,
            tag = "Playful Tease",
            whyItWorks = "Playfully concedes defeat to their charm, giving them an ego boost."
        ),
        ConversationStarter(
            text = "Are you as fun in real life as you look in your photos, or should I be prepared to carry the conversation? 😏",
            category = StarterCategory.PLAYFUL_BANTER,
            tag = "Cheeky Challenge",
            whyItWorks = "A friendly push-pull challenge that sparks an energetic, witty comeback."
        ),
        ConversationStarter(
            text = "Two truths and a lie, go! And don't make it easy, I'm competitive.",
            category = StarterCategory.PLAYFUL_BANTER,
            tag = "Fun Game",
            whyItWorks = "Interactive, zero-pressure game that gets an instant response 90% of the time."
        ),
        ConversationStarter(
            text = "I'm debating between asking you out for coffee or challenging you to Mario Kart. Which one do you lose at faster?",
            category = StarterCategory.PLAYFUL_BANTER,
            tag = "Playful Bet",
            whyItWorks = "Suggests a fun date idea while keeping the tone light and teasing."
        ),
        ConversationStarter(
            text = "On a scale from 1 to 10, how likely are you to steal my fries if we eat together?",
            category = StarterCategory.PLAYFUL_BANTER,
            tag = "Food Banter",
            whyItWorks = "Universal humor about food sharing that everyone relates to."
        ),
        ConversationStarter(
            text = "You look like trouble, but the kind of trouble that's actually worth it.",
            category = StarterCategory.PLAYFUL_BANTER,
            tag = "Banter Spark",
            whyItWorks = "Classic flirtatious tease with modern, relaxed phrasing."
        ),

        // === Smooth Rizz ===
        ConversationStarter(
            text = "I'm not usually one to believe in luck, but matching with you feels like a pretty good sign.",
            category = StarterCategory.SMOOTH_CHARM,
            tag = "Smooth Charm",
            whyItWorks = "Self-assured and complimentary without sounding arrogant."
        ),
        ConversationStarter(
            text = "I think we just solved the biggest problem of my week—finding someone actually exciting to talk to.",
            category = StarterCategory.SMOOTH_CHARM,
            tag = "Confident Hook",
            whyItWorks = "High confidence that immediately signals strong interest."
        ),
        ConversationStarter(
            text = "I feel like we'd either be best friends in five minutes or stay up talking until 2 AM.",
            category = StarterCategory.SMOOTH_CHARM,
            tag = "Magnetic Vibe",
            whyItWorks = "Paints an attractive picture of effortless connection."
        ),
        ConversationStarter(
            text = "You have the kind of energy that makes people want to hear what you have to say. What's on your mind today?",
            category = StarterCategory.SMOOTH_CHARM,
            tag = "Attentive Charm",
            whyItWorks = "Deeply flattering and opens a direct door for genuine conversation."
        ),
        ConversationStarter(
            text = "I was about to delete this app, but then you showed up and gave me a reason to stay.",
            category = StarterCategory.SMOOTH_CHARM,
            tag = "High Impact",
            whyItWorks = "Bold yet sweet line that proves their profile stood out."
        ),

        // === Profile & Bio Hooks ===
        ConversationStarter(
            text = "Okay, I saw your travel photos and now I need the story behind that one picture.",
            category = StarterCategory.BIO_HOOK,
            tag = "Photo Hook",
            whyItWorks = "Everyone loves talking about the story behind their favorite trip photo."
        ),
        ConversationStarter(
            text = "Your dog is adorable! But honestly, who chose who in that relationship?",
            category = StarterCategory.BIO_HOOK,
            tag = "Pet Icebreaker",
            whyItWorks = "Pet owners love talking about how they found their pets."
        ),
        ConversationStarter(
            text = "Your music taste in your bio caught my attention. What song is currently on repeat for you?",
            category = StarterCategory.BIO_HOOK,
            tag = "Music Hook",
            whyItWorks = "Simple, easy question that gives insight into their current mood."
        ),
        ConversationStarter(
            text = "I saw your bio and had to ask: what's the most underrated spot in your city that you secretly love?",
            category = StarterCategory.BIO_HOOK,
            tag = "Local Secrets",
            whyItWorks = "People love sharing their hidden gems and favorite cozy spots."
        ),
        ConversationStarter(
            text = "I noticed you're into food and cooking—what's your absolute signature dish that you can make with your eyes closed?",
            category = StarterCategory.BIO_HOOK,
            tag = "Culinary Passion",
            whyItWorks = "Food is passion; asking someone's best dish always gets an enthusiastic response."
        ),
        ConversationStarter(
            text = "Your profile gave me such a calm, peaceful vibe. What does your ideal slow Sunday look like?",
            category = StarterCategory.BIO_HOOK,
            tag = "Cozy Sunday",
            whyItWorks = "Paints a gentle, comforting scenario that leads directly to cozy date conversations."
        ),

        // === Fun & Curious Dilemmas ===
        ConversationStarter(
            text = "Important life question: if you had to listen to only one album for the rest of the year, what are you picking?",
            category = StarterCategory.INTRIGUING_QUESTION,
            tag = "Music Dilemma",
            whyItWorks = "Forces a fun, personal choice that reveals personality immediately."
        ),
        ConversationStarter(
            text = "If you could teleport right now to any city in the world for late-night food, where are we going?",
            category = StarterCategory.INTRIGUING_QUESTION,
            tag = "Midnight Food",
            whyItWorks = "Spontaneous, vivid imagination prompt that feels like a shared adventure."
        ),
        ConversationStarter(
            text = "Quick poll: is cereal soup? Think carefully, your answer determines our compatibility.",
            category = StarterCategory.INTRIGUING_QUESTION,
            tag = "Silly Debate",
            whyItWorks = "Playful, funny debate that instantly breaks the ice with laughter."
        ),
        ConversationStarter(
            text = "What's the best piece of advice you've ever received that you actually use?",
            category = StarterCategory.INTRIGUING_QUESTION,
            tag = "Thought-Provoking",
            whyItWorks = "Invites meaningful dialogue without feeling like a job interview."
        ),
        ConversationStarter(
            text = "What's a weird hill you're 100% willing to die on? Mine is that pineapple on pizza is criminally misunderstood.",
            category = StarterCategory.INTRIGUING_QUESTION,
            tag = "Passionate Debate",
            whyItWorks = "Lighthearted controversy invites passionate, funny arguments."
        ),
        ConversationStarter(
            text = "Would you rather have a private chef for life or an unlimited first-class flight pass? (Choose wisely!)",
            category = StarterCategory.INTRIGUING_QUESTION,
            tag = "Dream Dilemma",
            whyItWorks = "Everyone loves fun 'would you rather' hypotheticals."
        )
    )

    fun getByCategory(category: StarterCategory): List<ConversationStarter> {
        return allStarters.filter { it.category == category }
    }
}
