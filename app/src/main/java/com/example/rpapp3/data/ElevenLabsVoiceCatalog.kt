package com.example.rpapp3.data

import com.example.rpapp3.data.model.Voice
import com.example.rpapp3.data.model.VoiceSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
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
            put("free_users_allowed", "true")
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
    return voices.filter { it.source == VoiceSource.ELEVEN_LABS }
}

internal class ElevenLabsCatalogHttpException(
    val responseCode: Int,
    responseBody: String
) : Exception("ElevenLabs voice catalog request failed ($responseCode): $responseBody")

private data class ParsedSharedVoicePage(
    val voices: List<ElevenLabsCatalogVoice>,
    val hasMore: Boolean
)

private val catalogJson = Json {
    ignoreUnknownKeys = true
}

internal fun buildDefaultVoicesPath(search: String): String {
    return buildString {
        append("/v2/voices?page_size=100&voice_type=default")
        appendSearchParameter(search)
    }
}

internal fun buildSharedVoicesPath(page: Int, search: String): String {
    require(page >= 0) { "Page must not be negative" }
    return buildString {
        append("/v1/shared-voices?page_size=100&page=")
        append(page)
        appendSearchParameter(search)
    }
}

internal fun parseFreeTierVoicePage(
    defaultVoicesJson: String?,
    sharedVoicesJson: String,
    page: Int
): ElevenLabsVoicePage {
    val defaultVoices = defaultVoicesJson?.let(::parseDefaultVoices).orEmpty()
    val sharedPage = parseSharedVoices(sharedVoicesJson)
    return ElevenLabsVoicePage(
        voices = (defaultVoices + sharedPage.voices).distinctBy { it.voiceId },
        page = page,
        hasMore = sharedPage.hasMore
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

private fun parseSharedVoices(responseBody: String): ParsedSharedVoicePage {
    val root = parseRoot(responseBody)
    val voices = root.requiredArray("voices").mapNotNull { element ->
        val voice = element as? JsonObject ?: return@mapNotNull null
        if (voice.boolean("free_users_allowed") != true) return@mapNotNull null
        val voiceId = voice.string("voice_id") ?: return@mapNotNull null
        val name = voice.string("name") ?: return@mapNotNull null
        ElevenLabsCatalogVoice(
            voiceId = voiceId,
            name = name,
            previewUrl = voice.string("preview_url"),
            description = voice.string("description"),
            language = voice.string("language"),
            gender = voice.string("gender"),
            accent = voice.string("accent"),
            category = voice.string("category"),
            source = ElevenLabsCatalogSource.SHARED
        )
    }
    return ParsedSharedVoicePage(
        voices = voices,
        hasMore = root.boolean("has_more") ?: false
    )
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

private fun JsonObject.boolean(key: String): Boolean? {
    return (this[key] as? JsonPrimitive)?.booleanOrNull
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
