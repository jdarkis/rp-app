package com.example.rpapp3.data

import com.example.rpapp3.data.model.ChatMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BedrockServiceTest {

    @Test
    fun temperatureModeMapsRequestAndOmitsTopPAndDisabledTopK() {
        val body = buildBedrockConverseRequestBody(
            BedrockConverseRequest(
                modelId = ChatSettingsManager.DEFAULT_BEDROCK_MODEL_ID,
                systemPrompt = "Stay in character.",
                messages = listOf(
                    BedrockChatMessage(isUser = true, text = "Hello"),
                    BedrockChatMessage(isUser = false, text = "Hi there"),
                    BedrockChatMessage(isUser = true, text = "Continue")
                ),
                settings = ChatSettings(
                    temperature = 1.7f,
                    topP = 0.8f,
                    topK = 64,
                    maxOutputTokens = 4096,
                    bedrockSamplingMode = BedrockSamplingMode.TEMPERATURE,
                    bedrockTopKEnabled = false
                )
            )
        )

        val json = Json.parseToJsonElement(body).jsonObject
        val system = json.getValue("system").jsonArray
        val messages = json.getValue("messages").jsonArray
        val inferenceConfig = json.getValue("inferenceConfig").jsonObject

        assertEquals(
            "Stay in character.",
            system[0].jsonObject.getValue("text").jsonPrimitive.content
        )
        assertEquals(3, messages.size)
        assertEquals("user", messages[0].jsonObject.getValue("role").jsonPrimitive.content)
        assertEquals("assistant", messages[1].jsonObject.getValue("role").jsonPrimitive.content)
        assertEquals(
            "Continue",
            messages[2].jsonObject
                .getValue("content")
                .jsonArray[0]
                .jsonObject
                .getValue("text")
                .jsonPrimitive
                .content
        )
        assertEquals(4096, inferenceConfig.getValue("maxTokens").jsonPrimitive.content.toInt())
        assertEquals(
            1.0,
            inferenceConfig.getValue("temperature").jsonPrimitive.content.toDouble(),
            0.0
        )
        assertFalse(inferenceConfig.containsKey("topP"))
        assertFalse(json.containsKey("additionalModelRequestFields"))
    }

    @Test
    fun topPModeOmitsTemperatureAndIncludesClampedTopK() {
        val body = buildBedrockConverseRequestBody(
            BedrockConverseRequest(
                modelId = ChatSettingsManager.DEFAULT_BEDROCK_MODEL_ID,
                systemPrompt = "",
                messages = listOf(BedrockChatMessage(isUser = true, text = "Hello")),
                settings = ChatSettings(
                    temperature = 0.4f,
                    topP = 0.8f,
                    topK = 700,
                    maxOutputTokens = 200_000,
                    bedrockSamplingMode = BedrockSamplingMode.TOP_P,
                    bedrockTopKEnabled = true
                )
            )
        )

        val json = Json.parseToJsonElement(body).jsonObject
        val inferenceConfig = json.getValue("inferenceConfig").jsonObject
        val additionalFields = json.getValue("additionalModelRequestFields").jsonObject

        assertFalse(inferenceConfig.containsKey("temperature"))
        assertEquals(
            0.8,
            inferenceConfig.getValue("topP").jsonPrimitive.content.toDouble(),
            0.000_001
        )
        assertEquals(128_000, inferenceConfig.getValue("maxTokens").jsonPrimitive.content.toInt())
        assertEquals(500, additionalFields.getValue("top_k").jsonPrimitive.content.toInt())
    }

    @Test
    fun bedrockProfileKeepsGeminiValuesSeparateAndNormalizesStaleValues() {
        val gemini = ChatSettings(
            aiModelId = "gemini-2.5-pro",
            streamingEnabled = true,
            temperature = 0.35f,
            topP = 0.75f,
            topK = 32,
            maxOutputTokens = 8192,
            thinkingEnabled = true
        )

        val bedrock = applyBedrockGenerationProfile(
            settings = gemini.copy(aiModelId = ChatSettingsManager.DEFAULT_BEDROCK_MODEL_ID),
            profile = BedrockGenerationProfile(
                samplingMode = BedrockSamplingMode.TOP_P,
                temperature = 2f,
                topP = -1f,
                topK = 900,
                topKEnabled = true,
                maxOutputTokens = 200_000
            )
        )

        assertEquals(0.35f, gemini.temperature)
        assertEquals(32, gemini.topK)
        assertTrue(gemini.streamingEnabled)
        assertTrue(gemini.thinkingEnabled)

        assertEquals(1f, bedrock.temperature)
        assertEquals(0f, bedrock.topP)
        assertEquals(500, bedrock.topK)
        assertEquals(128_000, bedrock.maxOutputTokens)
        assertFalse(bedrock.streamingEnabled)
        assertFalse(bedrock.thinkingEnabled)
        assertEquals(BedrockSamplingMode.TOP_P, bedrock.bedrockSamplingMode)
        assertTrue(bedrock.bedrockTopKEnabled)
    }

    @Test
    fun bedrockProfileDefaultsMatchClaudeSettings() {
        val profile = BedrockGenerationProfile()

        assertEquals(BedrockSamplingMode.TEMPERATURE, profile.samplingMode)
        assertEquals(1f, profile.temperature)
        assertEquals(0.999f, profile.topP)
        assertFalse(profile.topKEnabled)
        assertEquals(16_384, profile.maxOutputTokens)
    }

    @Test
    fun responseParserCombinesTextBlocks() {
        val result = parseBedrockConverseResponse(
            """
                {
                  "output": {
                    "message": {
                      "content": [
                        {"text": "Hello "},
                        {"text": "there"}
                      ],
                      "role": "assistant"
                    }
                  },
                  "stopReason": "end_turn"
                }
            """.trimIndent()
        )

        assertEquals("Hello there", result.text)
        assertEquals("end_turn", result.stopReason)
    }

    @Test
    fun responseParserRejectsEmptyFilteredAndTokenLimitedResponses() {
        assertThrows(BedrockGenerationException::class.java) {
            parseBedrockConverseResponse("""{"output":{"message":{"content":[]}},"stopReason":"end_turn"}""")
        }

        val filtered = assertThrows(BedrockGenerationException::class.java) {
            parseBedrockConverseResponse("""{"stopReason":"content_filtered"}""")
        }
        assertTrue(filtered.message!!.contains("filtered"))

        val tokenLimit = assertThrows(BedrockGenerationException::class.java) {
            parseBedrockConverseResponse("""{"stopReason":"max_tokens"}""")
        }
        assertTrue(tokenLimit.message!!.contains("max output token"))
    }

    @Test
    fun messageAssemblyIncludesPendingUserOnceAndMergesAdjacentRoles() {
        val pending = ChatMessage(id = "pending", text = "Continue", isUser = true)
        val messages = buildBedrockMessages(
            history = listOf(
                ChatMessage(id = "first-user", text = "Hello", isUser = true),
                ChatMessage(id = "assistant-one", text = "First line", isUser = false),
                ChatMessage(id = "assistant-two", text = "Second line", isUser = false),
                pending
            ),
            pendingUserMessage = pending
        )

        assertEquals(3, messages.size)
        assertEquals("Hello", messages[0].text)
        assertEquals("First line\n\nSecond line", messages[1].text)
        assertEquals("Continue", messages[2].text)
        assertEquals(1, messages.count { it.text == "Continue" })
    }

    @Test
    fun providerSelectionKeepsGeminiAndRoutesOpusToBedrock() {
        assertEquals(AiProvider.GEMINI, ChatSettingsManager.aiProviderFor("gemini-3-flash-preview"))
        assertEquals(
            AiProvider.BEDROCK,
            ChatSettingsManager.aiProviderFor(ChatSettingsManager.DEFAULT_BEDROCK_MODEL_ID)
        )
        assertEquals(
            "https://bedrock-runtime.us-east-1.amazonaws.com/model/" +
                "us.anthropic.claude-opus-4-6-v1/converse",
            BedrockService.converseEndpoint(ChatSettingsManager.DEFAULT_BEDROCK_MODEL_ID)
        )
    }

    @Test
    fun retryClassifierMatchesBedrockThrottleErrorsOnly() {
        assertTrue(BedrockService.shouldRetry(408))
        assertTrue(BedrockService.shouldRetry(429))
        assertTrue(BedrockService.shouldRetry(503))
        assertEquals(false, BedrockService.shouldRetry(403))
    }

    @Test
    fun errorBodiesProduceUsefulMessages() {
        val permissionMessage = BedrockService.userFacingErrorMessage(
            BedrockHttpException(403, """{"message":"Access denied"}""")
        )
        assertTrue(permissionMessage.contains("403"))
        assertTrue(permissionMessage.contains("Access denied"))

        val throttleMessage = BedrockService.userFacingErrorMessage(
            BedrockHttpException(429, """{"message":"Too many requests"}""")
        )
        assertTrue(throttleMessage.contains("throttled"))
        assertTrue(throttleMessage.contains("Too many requests"))
    }

    @Test
    fun awsErrorParserExtractsJsonMessageWithoutDumpingTheBody() {
        assertEquals(
            "Model access is not enabled",
            BedrockService.extractAwsErrorMessage(
                """{"message":"Model access is not enabled","requestId":"secret-request-id"}"""
            )
        )
    }
}
