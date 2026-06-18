package com.example.rpapp3.data

import com.example.rpapp3.data.model.ChatMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL

enum class BedrockKeyStatus {
    Checking,
    Active,
    InvalidOrBlocked,
    UnableToVerify
}

data class BedrockKeyTestResult(
    val status: BedrockKeyStatus,
    val detail: String
)

data class BedrockChatMessage(
    val isUser: Boolean,
    val text: String
)

data class BedrockConverseRequest(
    val modelId: String,
    val systemPrompt: String,
    val messages: List<BedrockChatMessage>,
    val settings: ChatSettings
)

data class BedrockConverseResult(
    val text: String,
    val stopReason: String,
    val inputTokens: Long?,
    val outputTokens: Long?
)

internal data class BedrockGenerationParameters(
    val maxTokens: Int,
    val samplingMode: BedrockSamplingMode,
    val temperature: Double?,
    val topP: Double?,
    val topK: Int?
)

class BedrockHttpException(
    val statusCode: Int,
    val responseBody: String
) : Exception("Bedrock request failed with HTTP $statusCode: ${responseBody.take(500)}")

class BedrockGenerationException(message: String) : Exception(message)

class BedrockService(
    private val connectionFactory: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    }
) {
    suspend fun converseWithRetry(
        apiKey: String,
        request: BedrockConverseRequest,
        maxRetries: Int = 3
    ): BedrockConverseResult {
        var attempt = 0
        while (true) {
            try {
                return converse(apiKey, request)
            } catch (error: CancellationException) {
                throw error
            } catch (error: BedrockHttpException) {
                if (!shouldRetry(error.statusCode) || attempt >= maxRetries) {
                    throw error
                }
                delay(retryDelayMs(attempt))
                attempt++
            }
        }
    }

    suspend fun testApiKey(apiKey: String): BedrockKeyTestResult {
        if (apiKey.isBlank()) {
            return BedrockKeyTestResult(
                status = BedrockKeyStatus.InvalidOrBlocked,
                detail = "No Bedrock API key is saved."
            )
        }

        val requestBody = buildJsonObject {
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("text", "Reply with OK.")
                        })
                    })
                })
            })
        }.toString()

        return try {
            executeConverse(
                apiKey = apiKey,
                modelId = ChatSettingsManager.DEFAULT_BEDROCK_MODEL_ID,
                requestBody = requestBody
            )
            BedrockKeyTestResult(
                status = BedrockKeyStatus.Active,
                detail = "Credential active. Claude Opus 4.6 is available through US geo inference."
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: BedrockHttpException) {
            BedrockKeyTestResult(
                status = if (
                    error.statusCode == HttpURLConnection.HTTP_UNAUTHORIZED ||
                    error.statusCode == HttpURLConnection.HTTP_FORBIDDEN
                ) {
                    BedrockKeyStatus.InvalidOrBlocked
                } else {
                    BedrockKeyStatus.UnableToVerify
                },
                detail = userFacingErrorMessage(error)
            )
        } catch (error: Exception) {
            BedrockKeyTestResult(
                status = BedrockKeyStatus.UnableToVerify,
                detail = networkErrorMessage(error)
            )
        }
    }

    suspend fun validateApiKey(apiKey: String): BedrockKeyStatus {
        return testApiKey(apiKey).status
    }

    suspend fun converse(
        apiKey: String,
        request: BedrockConverseRequest
    ): BedrockConverseResult = withContext(Dispatchers.IO) {
        val responseBody = executeConverse(
            apiKey = apiKey,
            modelId = request.modelId,
            requestBody = buildBedrockConverseRequestBody(request)
        )
        parseBedrockConverseResponse(responseBody)
    }

    private suspend fun executeConverse(
        apiKey: String,
        modelId: String,
        requestBody: String
    ): String = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = connectionFactory(URL(converseEndpoint(modelId))).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
            }

            connection.outputStream.use { outputStream ->
                outputStream.write(requestBody.toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode
            val responseBody = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }

            if (responseCode !in 200..299) {
                throw BedrockHttpException(responseCode, responseBody)
            }

            responseBody
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        private const val REGION = "us-east-1"
        private const val BASE_URL = "https://bedrock-runtime.$REGION.amazonaws.com"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 600_000

        fun userFacingErrorMessage(error: Throwable): String {
            return when (error) {
                is BedrockHttpException -> when (error.statusCode) {
                    HttpURLConnection.HTTP_UNAUTHORIZED,
                    HttpURLConnection.HTTP_FORBIDDEN -> {
                        val reason = extractAwsErrorMessage(error.responseBody)
                        "Bedrock rejected the credential or model permission (${error.statusCode}). $reason"
                    }
                    HttpURLConnection.HTTP_BAD_REQUEST,
                    HttpURLConnection.HTTP_NOT_FOUND -> {
                        val reason = extractAwsErrorMessage(error.responseBody)
                        "Bedrock could not use the Claude Opus 4.6 US inference profile (${error.statusCode}). $reason"
                    }
                    HttpURLConnection.HTTP_CLIENT_TIMEOUT,
                    429,
                    HttpURLConnection.HTTP_UNAVAILABLE -> {
                        val reason = extractAwsErrorMessage(error.responseBody)
                        "Bedrock is throttled, out of quota, or temporarily unavailable (${error.statusCode}). $reason"
                    }
                    else -> {
                        val reason = extractAwsErrorMessage(error.responseBody)
                        "Bedrock request failed (${error.statusCode}). $reason"
                    }
                }
                is BedrockGenerationException -> {
                    error.message?.take(500) ?: "Bedrock could not complete the response."
                }
                else -> networkErrorMessage(error)
            }
        }

        internal fun extractAwsErrorMessage(responseBody: String): String {
            val parsedMessage = runCatching {
                val json = Json.parseToJsonElement(responseBody).jsonObject
                json["message"]?.jsonPrimitive?.contentOrNull
                    ?: json["Message"]?.jsonPrimitive?.contentOrNull
                    ?: (json["error"] as? JsonObject)
                        ?.get("message")
                        ?.jsonPrimitive
                        ?.contentOrNull
            }.getOrNull()

            return parsedMessage
                ?.trim()
                ?.take(500)
                ?.takeIf { it.isNotBlank() }
                ?: responseBody.trim().take(500).takeIf { it.isNotBlank() }
                ?: "AWS did not return an error description."
        }

        internal fun networkErrorMessage(error: Throwable): String {
            val reason = error.localizedMessage
                ?.trim()
                ?.take(400)
                ?.takeIf { it.isNotBlank() }
                ?: error.javaClass.simpleName
            return "Could not reach Amazon Bedrock. Check the device internet connection and date/time. $reason"
        }

        internal fun shouldRetry(statusCode: Int): Boolean {
            return statusCode == HttpURLConnection.HTTP_CLIENT_TIMEOUT ||
                statusCode == 429 ||
                statusCode == HttpURLConnection.HTTP_UNAVAILABLE
        }

        internal fun converseEndpoint(modelId: String): String {
            return "$BASE_URL/model/$modelId/converse"
        }

        private fun retryDelayMs(attempt: Int): Long {
            return 1_000L * (1 shl attempt)
        }
    }
}

internal fun buildBedrockConverseRequestBody(request: BedrockConverseRequest): String {
    val generation = normalizeBedrockGenerationSettings(request.settings)
    val root = buildJsonObject {
        put("messages", buildJsonArray {
            request.messages.forEach { message ->
                add(buildJsonObject {
                    put("role", if (message.isUser) "user" else "assistant")
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("text", message.text)
                        })
                    })
                })
            }
        })

        if (request.systemPrompt.isNotBlank()) {
            put("system", buildJsonArray {
                add(buildJsonObject {
                    put("text", request.systemPrompt)
                })
            })
        }

        put("inferenceConfig", buildJsonObject {
            put("maxTokens", generation.maxTokens)
            generation.temperature?.let { put("temperature", it) }
            generation.topP?.let { put("topP", it) }
        })

        generation.topK?.let { topK ->
            put("additionalModelRequestFields", buildJsonObject {
                put("top_k", topK)
            })
        }
    }

    return root.toString()
}

internal fun normalizeBedrockGenerationSettings(
    settings: ChatSettings
): BedrockGenerationParameters {
    val samplingMode = settings.bedrockSamplingMode
    return BedrockGenerationParameters(
        maxTokens = settings.maxOutputTokens.coerceIn(1, 128_000),
        samplingMode = samplingMode,
        temperature = if (samplingMode == BedrockSamplingMode.TEMPERATURE) {
            settings.temperature.coerceIn(0f, 1f).toDouble()
        } else {
            null
        },
        topP = if (samplingMode == BedrockSamplingMode.TOP_P) {
            settings.topP.coerceIn(0f, 1f).toDouble()
        } else {
            null
        },
        topK = if (settings.bedrockTopKEnabled) {
            settings.topK.coerceIn(0, 500)
        } else {
            null
        }
    )
}

internal fun buildBedrockMessages(
    history: List<ChatMessage>,
    pendingUserMessage: ChatMessage
): List<BedrockChatMessage> {
    val sourceMessages = sanitizeChatHistoryForAiContext(history)
        .asSequence()
        .filter { it.id != pendingUserMessage.id && it.text.isNotBlank() }
        .map { BedrockChatMessage(isUser = it.isUser, text = it.text.trim()) }
        .toMutableList()

    sourceMessages += BedrockChatMessage(
        isUser = true,
        text = pendingUserMessage.text.trim()
    )

    return sourceMessages.fold(mutableListOf<BedrockChatMessage>()) { result, message ->
        val previous = result.lastOrNull()
        if (previous?.isUser == message.isUser) {
            result[result.lastIndex] = previous.copy(text = "${previous.text}\n\n${message.text}")
        } else {
            result += message
        }
        result
    }
}

internal fun parseBedrockConverseResponse(responseBody: String): BedrockConverseResult {
    val json = Json.parseToJsonElement(responseBody).jsonObject
    val stopReason = json["stopReason"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val usage = json["usage"] as? JsonObject
    val inputTokens = usage
        ?.get("inputTokens")
        ?.jsonPrimitive
        ?.contentOrNull
        ?.toLongOrNull()
        ?.coerceAtLeast(0)
    val outputTokens = usage
        ?.get("outputTokens")
        ?.jsonPrimitive
        ?.contentOrNull
        ?.toLongOrNull()
        ?.coerceAtLeast(0)

    when (stopReason) {
        "content_filtered" -> throw BedrockGenerationException("Bedrock filtered the response.")
        "guardrail_intervened" -> throw BedrockGenerationException("Bedrock guardrails blocked the response.")
        "max_tokens" -> throw BedrockGenerationException("Bedrock stopped because the response reached the max output token limit.")
        "model_context_window_exceeded" -> throw BedrockGenerationException("Bedrock stopped because the chat exceeded the model context window.")
        "malformed_model_output",
        "malformed_tool_use" -> throw BedrockGenerationException("Bedrock returned malformed model output.")
    }

    val content = (((json["output"] as? JsonObject)
        ?.get("message") as? JsonObject)
        ?.get("content") as? JsonArray)
        ?: JsonArray(emptyList())

    val text = buildString {
        content.forEach { element ->
            val contentBlock = element as? JsonObject ?: return@forEach
            val blockText = contentBlock["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (blockText.isNotEmpty()) append(blockText)
        }
    }.trim()

    if (text.isBlank()) {
        throw BedrockGenerationException("Bedrock returned an empty response.")
    }

    return BedrockConverseResult(
        text = text,
        stopReason = stopReason,
        inputTokens = inputTokens,
        outputTokens = outputTokens
    )
}
