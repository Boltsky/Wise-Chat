package com.wisechat.app.engine.provider

import com.wisechat.app.domain.model.ConversationStarter
import com.wisechat.app.domain.model.CuratedStartersLibrary
import com.wisechat.app.domain.model.StarterCategory
import org.json.JSONObject

object StartersJsonParser {

    fun parse(rawText: String, defaultCategory: StarterCategory): List<ConversationStarter> {
        return try {
            val startIndex = rawText.indexOf('{')
            val endIndex = rawText.lastIndexOf('}')
            val cleaned = if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
                rawText.substring(startIndex, endIndex + 1)
            } else {
                rawText.trim()
            }

            val json = JSONObject(cleaned)
            val startersArray = json.optJSONArray("starters")
            val list = mutableListOf<ConversationStarter>()

            if (startersArray != null && startersArray.length() > 0) {
                for (i in 0 until startersArray.length()) {
                    val item = startersArray.getJSONObject(i)
                    val text = item.optString("text", "").trim()
                    if (text.isNotBlank()) {
                        val tag = item.optString("tag", "Opener")
                        val whyItWorks = item.optString("why_it_works", "")
                        list.add(
                            ConversationStarter(
                                text = text,
                                category = defaultCategory,
                                tag = tag,
                                whyItWorks = whyItWorks,
                                isAiGenerated = true
                            )
                        )
                    }
                }
            }

            if (list.isNotEmpty()) {
                list
            } else {
                // If parsing yielded no items, provide curated defaults for high reliability
                CuratedStartersLibrary.getByCategory(defaultCategory).take(4)
            }
        } catch (_: Exception) {
            CuratedStartersLibrary.getByCategory(defaultCategory).take(4)
        }
    }
}
