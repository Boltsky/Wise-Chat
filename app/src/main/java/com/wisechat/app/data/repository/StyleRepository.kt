package com.wisechat.app.data.repository

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.wisechat.app.data.preferences.PreferencesManager
import com.wisechat.app.domain.model.CustomPersonaPreset
import com.wisechat.app.domain.model.EmojiUsage
import com.wisechat.app.domain.model.ReplyLength
import com.wisechat.app.domain.model.ToneIntensity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

data class StoredPresetDto(
    val id: String,
    val name: String,
    val promptDescription: String,
    val defaultLength: String,
    val defaultIntensity: String,
    val defaultEmojiUsage: String
)

class StyleRepository(private val preferencesManager: PreferencesManager) {

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val listType = Types.newParameterizedType(List::class.java, StoredPresetDto::class.java)
    private val adapter = moshi.adapter<List<StoredPresetDto>>(listType)

    val customPresetsFlow: Flow<List<CustomPersonaPreset>> = preferencesManager.customStylesJson.map { json ->
        if (json.isNullOrBlank()) {
            getDefaultPresets()
        } else {
            try {
                val dtos = adapter.fromJson(json) ?: emptyList()
                if (dtos.isEmpty()) {
                    getDefaultPresets()
                } else {
                    dtos.map { dto ->
                        CustomPersonaPreset(
                            id = dto.id,
                            name = dto.name,
                            promptDescription = dto.promptDescription,
                            defaultLength = try { ReplyLength.valueOf(dto.defaultLength) } catch (_: Exception) { ReplyLength.SHORT },
                            defaultIntensity = try { ToneIntensity.valueOf(dto.defaultIntensity) } catch (_: Exception) { ToneIntensity.BALANCED },
                            defaultEmojiUsage = try { EmojiUsage.valueOf(dto.defaultEmojiUsage) } catch (_: Exception) { EmojiUsage.OCCASIONAL }
                        )
                    }
                }
            } catch (_: Exception) {
                getDefaultPresets()
            }
        }
    }

    suspend fun getPresets(): List<CustomPersonaPreset> {
        return customPresetsFlow.firstOrNull() ?: getDefaultPresets()
    }

    suspend fun savePreset(preset: CustomPersonaPreset) {
        val current = getPresets().toMutableList()
        val index = current.indexOfFirst { it.id == preset.id }
        if (index >= 0) {
            current[index] = preset
        } else {
            current.add(preset)
        }
        saveToDataStore(current)
    }

    suspend fun deletePreset(id: String) {
        val current = getPresets().filterNot { it.id == id }
        saveToDataStore(current)
    }

    private suspend fun saveToDataStore(presets: List<CustomPersonaPreset>) {
        val dtos = presets.map {
            StoredPresetDto(
                id = it.id,
                name = it.name,
                promptDescription = it.promptDescription,
                defaultLength = it.defaultLength.name,
                defaultIntensity = it.defaultIntensity.name,
                defaultEmojiUsage = it.defaultEmojiUsage.name
            )
        }
        preferencesManager.saveCustomStylesJson(adapter.toJson(dtos))
    }

    private fun getDefaultPresets(): List<CustomPersonaPreset> {
        return listOf(
            CustomPersonaPreset(
                name = "My Dating Persona",
                promptDescription = "Irresistibly charming, witty push-pull banter, heart-melting sweet lines, and effortless charisma that makes them smile and blush.",
                defaultLength = ReplyLength.SHORT,
                defaultIntensity = ToneIntensity.BALANCED,
                defaultEmojiUsage = EmojiUsage.OCCASIONAL
            ),
            CustomPersonaPreset(
                name = "Executive Client Voice",
                promptDescription = "Concise, outcome-driven, polished professional, reassuring and clear.",
                defaultLength = ReplyLength.SHORT,
                defaultIntensity = ToneIntensity.SUBTLE,
                defaultEmojiUsage = EmojiUsage.NONE
            ),
            CustomPersonaPreset(
                name = "Bestie Energy",
                promptDescription = "Hype, supportive, energetic, casual slang, warm and excited.",
                defaultLength = ReplyLength.SHORT,
                defaultIntensity = ToneIntensity.BOLD,
                defaultEmojiUsage = EmojiUsage.EXPRESSIVE
            )
        )
    }
}
