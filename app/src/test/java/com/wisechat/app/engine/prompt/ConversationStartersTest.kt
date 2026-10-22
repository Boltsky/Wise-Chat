package com.wisechat.app.engine.prompt

import com.wisechat.app.domain.model.CuratedStartersLibrary
import com.wisechat.app.domain.model.StarterCategory
import com.wisechat.app.engine.provider.StartersJsonParser
import org.junit.Assert.*
import org.junit.Test

class ConversationStartersTest {

    @Test
    fun `test buildStartersSystemInstruction enforces strict anti-cliche negative constraints`() {
        val systemPrompt = PromptBuilder.buildStartersSystemInstruction(
            category = StarterCategory.SWEET_AWW
        )

        // Strict Anti-Cliché Negative Constraints
        assertTrue(systemPrompt.contains("ABSOLUTELY FORBIDDEN: Do NOT generate cliché"))
        assertTrue(systemPrompt.contains("Did it hurt when you fell from heaven"))
        assertTrue(systemPrompt.contains("Are you a magician"))
        assertTrue(systemPrompt.contains("NO DRY OR BORING OPENERS"))
        assertTrue(systemPrompt.contains("NO CRINGE OR AGGRESSIVE LINES"))
    }

    @Test
    fun `test buildStartersSystemInstruction enforces aww mandate and simple understandable english`() {
        val systemPrompt = PromptBuilder.buildStartersSystemInstruction(
            category = StarterCategory.SWEET_AWW
        )

        // The "Aww" & Smile Mandate
        assertTrue(systemPrompt.contains("THE 'AWW' & SMILE MANDATE"))
        assertTrue(systemPrompt.contains("heart-melting warmth"))
        assertTrue(systemPrompt.contains("soft smile"))

        // Simple Understandable English
        assertTrue(systemPrompt.contains("SIMPLE, UNDERSTANDABLE ENGLISH"))
        assertTrue(systemPrompt.contains("modern, natural conversational language"))
        assertTrue(systemPrompt.contains("Do NOT use flowery, archaic, pretentious"))
    }

    @Test
    fun `test buildStartersSystemInstruction requires structured JSON output schema`() {
        val systemPrompt = PromptBuilder.buildStartersSystemInstruction(
            category = StarterCategory.SMOOTH_CHARM
        )

        assertTrue(systemPrompt.contains("REQUIRED JSON OUTPUT FORMAT"))
        assertTrue(systemPrompt.contains("\"starters\": ["))
        assertTrue(systemPrompt.contains("\"text\":"))
        assertTrue(systemPrompt.contains("\"tag\":"))
        assertTrue(systemPrompt.contains("\"why_it_works\":"))
    }

    @Test
    fun `test buildStartersUserMessage formats category and bio hook`() {
        val userPromptWithTopic = PromptBuilder.buildStartersUserMessage(
            category = StarterCategory.BIO_HOOK,
            profileOrTopic = "Loves mountain hiking and black coffee"
        )

        assertTrue(userPromptWithTopic.contains("CONTEXT / PERSON'S BIO OR TOPIC:"))
        assertTrue(userPromptWithTopic.contains("Loves mountain hiking and black coffee"))
        assertTrue(userPromptWithTopic.contains("Bio Hook"))

        val userPromptWithoutTopic = PromptBuilder.buildStartersUserMessage(
            category = StarterCategory.PLAYFUL_BANTER,
            profileOrTopic = null
        )
        assertFalse(userPromptWithoutTopic.contains("CONTEXT / PERSON'S BIO OR TOPIC:"))
        assertTrue(userPromptWithoutTopic.contains("No specific bio provided"))
        assertTrue(userPromptWithoutTopic.contains(StarterCategory.PLAYFUL_BANTER.title))
    }

    @Test
    fun `test StartersJsonParser correctly parses valid JSON response`() {
        val json = """
            {
              "starters": [
                {
                  "text": "I hope you have a really sweet day today. Just wanted to let you know your smile is contagious even through a screen.",
                  "tag": "Sweet Warmth",
                  "why_it_works": "Genuinely brightens their day without asking for anything in return."
                },
                {
                  "text": "Not to be overly dramatic, but seeing your profile genuinely made my whole morning better.",
                  "tag": "Heart Melter",
                  "why_it_works": "Vulnerable, sweet, and disarming."
                }
              ]
            }
        """.trimIndent()

        val starters = StartersJsonParser.parse(json, StarterCategory.SWEET_AWW)

        assertEquals(2, starters.size)
        assertEquals("I hope you have a really sweet day today. Just wanted to let you know your smile is contagious even through a screen.", starters[0].text)
        assertEquals("Sweet Warmth", starters[0].tag)
        assertEquals("Genuinely brightens their day without asking for anything in return.", starters[0].whyItWorks)
        assertEquals(StarterCategory.SWEET_AWW, starters[0].category)

        assertEquals("Heart Melter", starters[1].tag)
    }

    @Test
    fun `test StartersJsonParser parses JSON enclosed in markdown code fences`() {
        val raw = """
            Here are some unique starters:
            ```json
            {
              "starters": [
                {
                  "text": "Your dog in the third photo clearly has higher standards than both of us combined.",
                  "tag": "Dog Tease",
                  "why_it_works": "Playful and pet-friendly banter."
                }
              ]
            }
            ```
            Hope you love these!
        """.trimIndent()

        val starters = StartersJsonParser.parse(raw, StarterCategory.PLAYFUL_BANTER)

        assertEquals(1, starters.size)
        assertEquals("Your dog in the third photo clearly has higher standards than both of us combined.", starters[0].text)
        assertEquals("Dog Tease", starters[0].tag)
        assertEquals(StarterCategory.PLAYFUL_BANTER, starters[0].category)
    }

    @Test
    fun `test StartersJsonParser gracefully falls back to curated library on malformed response`() {
        val malformedRaw = "Sorry, I am unable to generate JSON right now."

        val fallbackStarters = StartersJsonParser.parse(malformedRaw, StarterCategory.SWEET_AWW)

        assertTrue(fallbackStarters.isNotEmpty())
        assertEquals(StarterCategory.SWEET_AWW, fallbackStarters[0].category)
        // Verify fallback contains high quality curated content
        assertTrue(fallbackStarters.any { it.text.contains("smile") || it.text.contains("day") || it.text.contains("morning") })
    }

    @Test
    fun `test CuratedStartersLibrary contains rich unique content for all categories`() {
        for (category in StarterCategory.values()) {
            val list = CuratedStartersLibrary.getByCategory(category)
            assertTrue("Category $category should have curated items", list.isNotEmpty())
            assertTrue("Category $category should have at least 5 lines", list.size >= 5)

            for (starter in list) {
                assertTrue("Starter text should not be blank", starter.text.isNotBlank())
                assertTrue("Starter tag should not be blank", starter.tag.isNotBlank())
                assertTrue("Starter whyItWorks should not be blank", starter.whyItWorks.isNotBlank())
                assertEquals(category, starter.category)

                // Ensure none of the curated library contains cheesy clichés
                val lower = starter.text.lowercase()
                assertFalse("Should not contain heaven cliché", lower.contains("fell from heaven"))
                assertFalse("Should not contain magician cliché", lower.contains("magician"))
                assertFalse("Should not contain Tennessee cliché", lower.contains("tennessee"))
                assertFalse("Should not contain tired generic opener", lower == "hey" || lower == "hi" || lower == "what's up")
            }
        }
    }
}
