package com.example.rpapp3.data

import com.example.rpapp3.data.model.ChatMessage
import com.example.rpapp3.data.model.ModelRequestDetails
import com.example.rpapp3.data.model.ModelRequestMessage
import com.example.rpapp3.data.model.ModelRequestParameter
import com.example.rpapp3.data.model.ModelRequestStatus
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun buildGeminiRequestDetails(
    chatId: String,
    userMessage: ChatMessage,
    history: List<ChatMessage>,
    systemPrompt: String,
    settings: ChatSettings,
    status: ModelRequestStatus = ModelRequestStatus.SENT,
    failureReason: String? = null
): ModelRequestDetails {
    val messages = history.map { message ->
        ModelRequestMessage(
            role = if (message.isUser) "user" else "model",
            text = message.text
        )
    } + ModelRequestMessage(role = "user", text = userMessage.text)

    val parameters = listOf(
        ModelRequestParameter("temperature", settings.temperature.toString()),
        ModelRequestParameter("topP", settings.topP.toString()),
        ModelRequestParameter("topK", settings.topK.toString()),
        ModelRequestParameter("maxOutputTokens", settings.maxOutputTokens.toString())
    )
    val safetySettings = geminiSafetySettings(settings)
    val rawSnapshot = buildJsonObject {
        put("_note", "SDK-equivalent semantic snapshot. The Gemini SDK does not expose the literal HTTP request body.")
        put("model", settings.aiModelId)
        put("streaming", settings.streamingEnabled)
        if (systemPrompt.isNotBlank()) {
            put("systemInstruction", buildJsonObject {
                put("role", "system")
                put("parts", textParts(systemPrompt))
            })
        }
        put("contents", buildJsonArray {
            messages.forEach { message ->
                add(buildJsonObject {
                    put("role", message.role)
                    put("parts", textParts(message.text))
                })
            }
        })
        put("generationConfig", buildJsonObject {
            put("temperature", settings.temperature)
            put("topP", settings.topP)
            put("topK", settings.topK)
            put("maxOutputTokens", settings.maxOutputTokens)
        })
        put("safetySettings", buildJsonArray {
            safetySettings.forEach { setting ->
                add(buildJsonObject {
                    put("category", setting.name)
                    put("threshold", setting.value)
                })
            }
        })
    }.toString()

    return ModelRequestDetails(
        chatId = chatId,
        messageId = userMessage.id,
        status = status,
        failureReason = failureReason,
        provider = "Gemini",
        modelId = settings.aiModelId,
        streaming = settings.streamingEnabled,
        systemPrompt = systemPrompt,
        messages = messages,
        parameters = parameters,
        safetySettings = safetySettings,
        rawSnapshotLabel = "Gemini SDK-equivalent snapshot",
        rawSnapshot = rawSnapshot
    )
}

fun buildBedrockRequestDetails(
    chatId: String,
    userMessage: ChatMessage,
    request: BedrockConverseRequest,
    status: ModelRequestStatus = ModelRequestStatus.SENT,
    failureReason: String? = null
): ModelRequestDetails {
    val generation = normalizeBedrockGenerationSettings(request.settings)
    val parameters = buildList {
        add(ModelRequestParameter("maxTokens", generation.maxTokens.toString()))
        add(ModelRequestParameter("samplingMode", generation.samplingMode.name))
        generation.temperature?.let {
            add(ModelRequestParameter("temperature", it.toString()))
        }
        generation.topP?.let {
            add(ModelRequestParameter("topP", it.toString()))
        }
        generation.topK?.let {
            add(ModelRequestParameter("topK", it.toString()))
        }
    }

    return ModelRequestDetails(
        chatId = chatId,
        messageId = userMessage.id,
        status = status,
        failureReason = failureReason,
        provider = "Amazon Bedrock",
        modelId = request.modelId,
        streaming = false,
        systemPrompt = request.systemPrompt,
        messages = request.messages.map { message ->
            ModelRequestMessage(
                role = if (message.isUser) "user" else "assistant",
                text = message.text
            )
        },
        parameters = parameters,
        endpoint = BedrockService.converseEndpoint(request.modelId),
        rawSnapshotLabel = "Exact Bedrock JSON request body",
        rawSnapshot = buildBedrockConverseRequestBody(request)
    )
}

private fun geminiSafetySettings(settings: ChatSettings): List<ModelRequestParameter> {
    return listOf(
        ModelRequestParameter("HARASSMENT", settings.safetyHarassment.toGeminiThreshold()),
        ModelRequestParameter("HATE_SPEECH", settings.safetyHateSpeech.toGeminiThreshold()),
        ModelRequestParameter("SEXUALLY_EXPLICIT", settings.safetySexuallyExplicit.toGeminiThreshold()),
        ModelRequestParameter("DANGEROUS_CONTENT", settings.safetyDangerousContent.toGeminiThreshold())
    )
}

private fun SafetyThreshold.toGeminiThreshold(): String {
    return when (this) {
        SafetyThreshold.BLOCK_NONE -> "NONE"
        SafetyThreshold.BLOCK_ONLY_HIGH -> "ONLY_HIGH"
        SafetyThreshold.BLOCK_MEDIUM_AND_ABOVE -> "MEDIUM_AND_ABOVE"
        SafetyThreshold.BLOCK_LOW_AND_ABOVE -> "LOW_AND_ABOVE"
    }
}

private fun textParts(text: String) = buildJsonArray {
    add(buildJsonObject { put("text", text) })
}
