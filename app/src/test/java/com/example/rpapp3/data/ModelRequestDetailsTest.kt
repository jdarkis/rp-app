package com.example.rpapp3.data

import com.example.rpapp3.data.model.ChatMessage
import com.example.rpapp3.data.model.ModelRequestDetails
import com.example.rpapp3.data.model.ModelRequestStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelRequestDetailsTest {

    @Test
    fun geminiSnapshotOmitsSystemInstructionWhenPromptIsDisabled() {
        val details = buildGeminiRequestDetails(
            chatId = "chat-1",
            userMessage = ChatMessage(
                id = "message-1",
                chatId = "chat-1",
                text = "Hello",
                isUser = true
            ),
            history = emptyList(),
            systemPrompt = "",
            settings = ChatSettings()
        )

        val raw = Json.parseToJsonElement(details.rawSnapshot).jsonObject

        assertTrue(details.systemPrompt.isEmpty())
        assertFalse(raw.containsKey("systemInstruction"))
    }

    @Test
    fun firestoreRoundTripPreservesNestedRequestDetails() {
        val pending = ChatMessage(
            id = "message-1",
            chatId = "chat-1",
            text = "Continue",
            isUser = true
        )
        val original = buildGeminiRequestDetails(
            chatId = "chat-1",
            userMessage = pending,
            history = listOf(
                ChatMessage(text = "Hello", isUser = true),
                ChatMessage(text = "Hi", isUser = false)
            ),
            systemPrompt = "Stay in character.",
            settings = ChatSettings(
                aiModelId = "gemini-2.5-flash",
                streamingEnabled = true
            )
        )

        val restored = ModelRequestDetails.fromMap(original.toMap())

        assertEquals(original, restored)
    }

    @Test
    fun usageRecordIdRoundTripsAndLegacyMapsRemainCompatible() {
        val details = buildGeminiRequestDetails(
            chatId = "chat-1",
            userMessage = ChatMessage(
                id = "message-1",
                chatId = "chat-1",
                text = "Continue",
                isUser = true
            ),
            history = emptyList(),
            systemPrompt = "",
            settings = ChatSettings(aiModelId = "gemini-2.5-flash"),
            usageRecordId = "usage-1"
        )

        assertEquals("usage-1", ModelRequestDetails.fromMap(details.toMap()).usageRecordId)
        assertNull(ModelRequestDetails.fromMap(details.toMap() - "usageRecordId").usageRecordId)
    }

    @Test
    fun geminiSnapshotContainsExactSemanticHistoryAndSettings() {
        val pending = ChatMessage(
            id = "pending",
            chatId = "chat",
            text = "Continue",
            isUser = true
        )
        val details = buildGeminiRequestDetails(
            chatId = "chat",
            userMessage = pending,
            history = listOf(
                ChatMessage(text = "Hello", isUser = true),
                ChatMessage(
                    text = "Hi there\n[ACTIONS]\n1. I wave.\n[DIALOGUE]\na. \"Hello\"",
                    isUser = false
                )
            ),
            systemPrompt = "System instructions",
            settings = ChatSettings(
                aiModelId = "gemini-3-flash-preview",
                streamingEnabled = true,
                temperature = 0.7f,
                topP = 0.8f,
                topK = 32,
                maxOutputTokens = 4096,
                safetyHarassment = SafetyThreshold.BLOCK_NONE
            )
        )

        val raw = Json.parseToJsonElement(details.rawSnapshot).jsonObject
        val contents = raw.getValue("contents").jsonArray
        val generation = raw.getValue("generationConfig").jsonObject

        assertEquals(listOf("user", "model", "user"), details.messages.map { it.role })
        assertEquals("Hi there", details.messages[1].text)
        assertEquals("Continue", details.messages.last().text)
        assertEquals(3, contents.size)
        assertEquals("user", contents.last().jsonObject.getValue("role").jsonPrimitive.content)
        assertEquals(32, generation.getValue("topK").jsonPrimitive.content.toInt())
        assertEquals(4096, generation.getValue("maxOutputTokens").jsonPrimitive.content.toInt())
        assertTrue(details.streaming)
        assertTrue(details.safetySettings.any { it.name == "HARASSMENT" && it.value == "NONE" })
        assertTrue(details.rawSnapshotLabel.contains("SDK-equivalent"))
        assertFalse(details.rawSnapshot.contains("[ACTIONS]"))
        assertFalse(details.rawSnapshot.contains("[DIALOGUE]"))
    }

    @Test
    fun bedrockSnapshotUsesExactGeneratedBodyAndNormalizedParameters() {
        val pending = ChatMessage(
            id = "pending",
            chatId = "chat",
            text = "Continue",
            isUser = true
        )
        val request = BedrockConverseRequest(
            modelId = ChatSettingsManager.DEFAULT_BEDROCK_MODEL_ID,
            systemPrompt = "Stay in character.",
            messages = listOf(
                BedrockChatMessage(isUser = true, text = "Hello"),
                BedrockChatMessage(isUser = false, text = "Hi"),
                BedrockChatMessage(isUser = true, text = "Continue")
            ),
            settings = ChatSettings(
                aiModelId = ChatSettingsManager.DEFAULT_BEDROCK_MODEL_ID,
                topP = 0.8f,
                topK = 700,
                maxOutputTokens = 200_000,
                bedrockSamplingMode = BedrockSamplingMode.TOP_P,
                bedrockTopKEnabled = true
            )
        )

        val details = buildBedrockRequestDetails(
            chatId = "chat",
            userMessage = pending,
            request = request
        )

        assertEquals(buildBedrockConverseRequestBody(request), details.rawSnapshot)
        assertEquals("Amazon Bedrock", details.provider)
        assertFalse(details.streaming)
        assertTrue(details.endpoint!!.endsWith("/${request.modelId}/converse"))
        assertTrue(details.parameters.any { it.name == "maxTokens" && it.value == "128000" })
        assertTrue(details.parameters.any { it.name == "topK" && it.value == "500" })
        assertFalse(details.parameters.any { it.name == "temperature" })
    }

    @Test
    fun notSentStatusAndFailureReasonArePersisted() {
        val pending = ChatMessage(id = "pending", chatId = "chat", text = "Hello")
        val details = buildGeminiRequestDetails(
            chatId = "chat",
            userMessage = pending,
            history = emptyList(),
            systemPrompt = "",
            settings = ChatSettings(),
            status = ModelRequestStatus.NOT_SENT,
            failureReason = "No API key configured"
        )

        val restored = ModelRequestDetails.fromMap(details.toMap())

        assertEquals(ModelRequestStatus.NOT_SENT, restored.status)
        assertEquals("No API key configured", restored.failureReason)
    }

    @Test
    fun snapshotsNeverContainCredentialsOrAuthorizationHeaders() {
        val knownSecret = "AIzaSyExampleSecretThatMustNeverBeStored"
        val pending = ChatMessage(id = "pending", chatId = "chat", text = "Hello")
        val gemini = buildGeminiRequestDetails(
            chatId = "chat",
            userMessage = pending,
            history = emptyList(),
            systemPrompt = "System",
            settings = ChatSettings()
        )
        val bedrock = buildBedrockRequestDetails(
            chatId = "chat",
            userMessage = pending,
            request = BedrockConverseRequest(
                modelId = ChatSettingsManager.DEFAULT_BEDROCK_MODEL_ID,
                systemPrompt = "System",
                messages = listOf(BedrockChatMessage(true, "Hello")),
                settings = ChatSettings(aiModelId = ChatSettingsManager.DEFAULT_BEDROCK_MODEL_ID)
            )
        )

        listOf(gemini, bedrock).forEach { details ->
            val serialized = details.toMap().toString()
            assertFalse(serialized.contains(knownSecret))
            assertFalse(serialized.contains("Authorization", ignoreCase = true))
            assertFalse(serialized.contains("Bearer ", ignoreCase = true))
            assertFalse(serialized.contains("apiKey", ignoreCase = true))
        }
    }
}
