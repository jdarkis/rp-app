package com.example.rpapp3.data

import com.example.rpapp3.data.model.Voice
import com.example.rpapp3.data.model.VoiceSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal enum class ElevenLabsCatalogSource {
    DEFAULT,
    SHARED
}

internal data class ElevenLabsCatalogVoice(
    val voiceId: String,
    val name: String,
    val previewUrl: String?,
    val description: String?,
    val language: String?,
    val gender: String?,
    val accent: String?,
    val category: String?,
    val source: ElevenLabsCatalogSource
) {
    fun toVoice(): Voice {
        val metadata = buildMap {
            put("free_api_compatible", "true")
            put("catalog_source", source.name)
            description?.let { put("description", it) }
            language?.let { put("language", it) }
            gender?.let { put("gender", it) }
            accent?.let { put("accent", it) }
            category?.let { put("category", it) }
        }
        return Voice(
            voiceId = voiceId,
            name = name,
            previewUrl = previewUrl,
            labels = metadata,
            source = VoiceSource.ELEVEN_LABS
        )
    }
}

internal data class ElevenLabsVoicePage(
    val voices: List<ElevenLabsCatalogVoice>,
    val page: Int,
    val hasMore: Boolean
)

internal fun selectableElevenLabsVoices(voices: List<Voice>): List<Voice> {
    return voices.filter(::isFreeApiCompatibleElevenLabsVoice)
}

internal fun selectableTtsVoices(voices: List<Voice>): List<Voice> {
    return voices.filter { voice ->
        when (voice.source) {
            VoiceSource.ELEVEN_LABS -> isFreeApiCompatibleElevenLabsVoice(voice)
            VoiceSource.INWORLD -> true
            VoiceSource.GEMINI -> true
        }
    }
}

internal fun isFreeApiCompatibleElevenLabsVoice(voice: Voice): Boolean {
    return voice.source == VoiceSource.ELEVEN_LABS &&
        voice.labels["catalog_source"] == ElevenLabsCatalogSource.DEFAULT.name
}

internal class ElevenLabsCatalogHttpException(
    val responseCode: Int,
    responseBody: String
) : Exception("ElevenLabs voice catalog request failed ($responseCode): $responseBody")

private val catalogJson = Json {
    ignoreUnknownKeys = true
}

internal fun buildDefaultVoicesPath(search: String): String {
    return buildString {
        append("/v2/voices?page_size=100&voice_type=default")
        appendSearchParameter(search)
    }
}

internal fun parseFreeTierVoicePage(
    defaultVoicesJson: String,
    page: Int
): ElevenLabsVoicePage {
    return ElevenLabsVoicePage(
        voices = parseDefaultVoices(defaultVoicesJson).distinctBy { it.voiceId },
        page = page,
        hasMore = false
    )
}

internal fun shouldRotateCatalogKey(responseCode: Int): Boolean {
    return responseCode == HttpURLConnection.HTTP_UNAUTHORIZED ||
        responseCode == HttpURLConnection.HTTP_FORBIDDEN ||
        responseCode == 408 ||
        responseCode == 429 ||
        responseCode >= 500
}

internal fun shouldRotateCatalogKey(error: Throwable): Boolean {
    return when (error) {
        is ElevenLabsCatalogHttpException -> shouldRotateCatalogKey(error.responseCode)
        is IOException -> true
        else -> false
    }
}

private fun parseDefaultVoices(responseBody: String): List<ElevenLabsCatalogVoice> {
    val root = parseRoot(responseBody)
    val voices = root.requiredArray("voices")
    return voices.mapNotNull { element ->
        val voice = element as? JsonObject ?: return@mapNotNull null
        val voiceId = voice.string("voice_id") ?: return@mapNotNull null
        val name = voice.string("name") ?: return@mapNotNull null
        val availableTiers = voice.arrayOrNull("available_for_tiers")
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?.map { it.lowercase() }
            .orEmpty()
        if (availableTiers.isNotEmpty() && "free" !in availableTiers) {
            return@mapNotNull null
        }
        val labels = voice.objectOrNull("labels")
        val verifiedLanguage = voice.arrayOrNull("verified_languages")
            ?.firstOrNull()
            ?.let { it as? JsonObject }
            ?.string("language")
        ElevenLabsCatalogVoice(
            voiceId = voiceId,
            name = name,
            previewUrl = voice.string("preview_url"),
            description = voice.string("description"),
            language = verifiedLanguage ?: labels?.string("language"),
            gender = labels?.string("gender"),
            accent = labels?.string("accent"),
            category = voice.string("category"),
            source = ElevenLabsCatalogSource.DEFAULT
        )
    }
}

private fun parseRoot(responseBody: String): JsonObject {
    return try {
        catalogJson.parseToJsonElement(responseBody).jsonObject
    } catch (error: Exception) {
        throw IllegalArgumentException("Malformed ElevenLabs voice catalog response", error)
    }
}

private fun JsonObject.requiredArray(key: String): JsonArray {
    return try {
        getValue(key).jsonArray
    } catch (error: Exception) {
        throw IllegalArgumentException("Malformed ElevenLabs voice catalog response: missing $key", error)
    }
}

private fun JsonObject.string(key: String): String? {
    return (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
}

private fun JsonObject.objectOrNull(key: String): JsonObject? {
    return this[key] as? JsonObject
}

private fun JsonObject.arrayOrNull(key: String): JsonArray? {
    return this[key] as? JsonArray
}

private fun StringBuilder.appendSearchParameter(search: String) {
    val normalizedSearch = search.trim()
    if (normalizedSearch.isNotEmpty()) {
        append("&search=")
        append(
            URLEncoder.encode(
                normalizedSearch,
                StandardCharsets.UTF_8.toString()
            )
        )
    }
}
