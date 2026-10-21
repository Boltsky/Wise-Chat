package com.wisechat.app.engine.provider

import com.wisechat.app.domain.model.AiProviderConfig
import com.wisechat.app.domain.model.ProviderType
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProviderParsingTest {

    @Test
    fun `test custom rest template interpolation`() {
        val config = AiProviderConfig(
            type = ProviderType.CUSTOM_REST,
            model = "my-fast-model",
            customBodyTemplate = """{"model":"{{model}}","prompt":"{{target_message}}","style":"{{reply_style}}"}"""
        )

        val target = "Hey, are you free tonight?"
        val style = "Flirty"

        val interpolated = config.customBodyTemplate
            .replace("{{model}}", config.model)
            .replace("{{target_message}}", target)
            .replace("{{reply_style}}", style)

        val json = JSONObject(interpolated)
        assertEquals("my-fast-model", json.getString("model"))
        assertEquals("Hey, are you free tonight?", json.getString("prompt"))
        assertEquals("Flirty", json.getString("style"))
    }

    @Test
    fun `test masked api key security formatting`() {
        val longKey = "sk-proj-1234567890abcdef"
        val config = AiProviderConfig(
            type = ProviderType.OPENAI,
            apiKey = longKey
        )

        val masked = config.maskedApiKey
        assertTrue(masked.startsWith("sk-p"))
        assertTrue(masked.endsWith("cdef"))
        assertTrue(masked.contains("••••••••"))
        assertFalse(masked.contains("1234567890"))
    }
}
