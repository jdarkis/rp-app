package com.example.rpapp3.data

import com.example.rpapp3.data.model.Chat
import com.example.rpapp3.data.model.ChatUsageSummary
import com.example.rpapp3.data.repository.SummarizerPrompts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSettingsPersistenceTest {

    @Test
    fun chatRoundTripPreservesEveryRegularChatSetting() {
        val settings = ChatSettings(
            filterMode = MessageFilterMode.AFTER_DELIMITER,
            customDelimiter = "###",
            customDelimiters = listOf("###", "---"),
            paragraphCount = 7,
            streamingEnabled = true,
            temperature = 0.7f,
            topP = 0.8f,
            topK = 42,
            maxOutputTokens = 8_192,
            presencePenalty = 0.4f,
            frequencyPenalty = -0.3f,
            thinkingEnabled = true,
            bedrockSamplingMode = BedrockSamplingMode.TOP_P,
            bedrockTemperature = 0.6f,
            bedrockTopP = 0.91f,
            bedrockTopK = 250,
            bedrockTopKEnabled = true,
            bedrockMaxOutputTokens = 64_000,
            safetyHarassment = SafetyThreshold.BLOCK_NONE,
            safetyHateSpeech = SafetyThreshold.BLOCK_ONLY_HIGH,
            safetySexuallyExplicit = SafetyThreshold.BLOCK_LOW_AND_ABOVE,
            safetyDangerousContent = SafetyThreshold.BLOCK_MEDIUM_AND_ABOVE,
            separateCharacterDialogue = false,
            provideChoicesEnabled = false,
            responseLength = ResponseLength.VERY_LONG,
            ttsEnabled = true,
            autoTtsEnabled = true,
            ttsAudioTagsEnabled = true,
            narratorVoiceId = "voice-123",
            ttsModelId = "inworld-tts-1.5-max",
            unlockPromptEnabled = true,
            systemPromptEnabled = false,
            narratorLanguage = "lt",
            aiModelId = ChatSettingsManager.DEFAULT_BEDROCK_MODEL_ID
        )
        val original = Chat(
            id = "chat-a",
            worldId = "world-a",
            title = "Persistent chat",
            settings = settings
        )

        val restored = Chat.fromMap(original.toMap())

        assertEquals(original, restored)
    }

    @Test
    fun missingSettingsUseHardCodedDefaults() {
        val restored = Chat.fromMap(
            mapOf(
                "id" to "legacy-chat",
                "worldId" to "world-a",
                "title" to "Legacy"
            )
        )

        assertEquals(ChatSettings(), restored.settings)
    }

    @Test
    fun malformedSettingsAreBoundedAndUnknownEnumsFallBack() {
        val restored = ChatSettings.fromMap(
            mapOf(
                "filterMode" to "UNKNOWN_FILTER",
                "paragraphCount" to 100L,
                "temperature" to -5.0,
                "topP" to 4.0,
                "topK" to 0L,
                "maxOutputTokens" to 999_999L,
                "presencePenalty" to -20.0,
                "frequencyPenalty" to 20.0,
                "bedrockSamplingMode" to "UNKNOWN_MODE",
                "bedrockTemperature" to 8.0,
                "bedrockTopP" to -2.0,
                "bedrockTopK" to 999L,
                "bedrockMaxOutputTokens" to 999_999L,
                "safetyHarassment" to "UNKNOWN_THRESHOLD",
                "responseLength" to "UNKNOWN_LENGTH",
                "ttsModelId" to "",
                "narratorLanguage" to "",
                "aiModelId" to "unknown-model"
            )
        )

        assertEquals(MessageFilterMode.OFF, restored.filterMode)
        assertEquals(20, restored.paragraphCount)
        assertEquals(0f, restored.temperature)
        assertEquals(1f, restored.topP)
        assertEquals(1, restored.topK)
        assertEquals(65_536, restored.maxOutputTokens)
        assertEquals(-2f, restored.presencePenalty)
        assertEquals(2f, restored.frequencyPenalty)
        assertEquals(BedrockSamplingMode.TEMPERATURE, restored.bedrockSamplingMode)
        assertEquals(1f, restored.bedrockTemperature)
        assertEquals(0f, restored.bedrockTopP)
        assertEquals(500, restored.bedrockTopK)
        assertEquals(128_000, restored.bedrockMaxOutputTokens)
        assertEquals(SafetyThreshold.BLOCK_MEDIUM_AND_ABOVE, restored.safetyHarassment)
        assertEquals(ResponseLength.SHORT, restored.responseLength)
        assertEquals(ChatSettingsManager.DEFAULT_TTS_MODEL_ID, restored.ttsModelId)
        assertEquals("en", restored.narratorLanguage)
        assertEquals(ChatSettingsManager.DEFAULT_AI_MODEL_ID, restored.aiModelId)
    }

    @Test
    fun geminiAndBedrockProfilesRemainIndependent() {
        val settings = ChatSettings(
            temperature = 1.7f,
            topP = 0.72f,
            topK = 33,
            maxOutputTokens = 4_096,
            bedrockSamplingMode = BedrockSamplingMode.TOP_P,
            bedrockTemperature = 0.3f,
            bedrockTopP = 0.94f,
            bedrockTopK = 275,
            bedrockTopKEnabled = true,
            bedrockMaxOutputTokens = 32_000,
            thinkingEnabled = true,
            streamingEnabled = true,
            aiModelId = ChatSettingsManager.DEFAULT_BEDROCK_MODEL_ID
        )

        val bedrock = settings.effectiveForSelectedProvider()
        val gemini = settings.copy(
            aiModelId = ChatSettingsManager.DEFAULT_AI_MODEL_ID
        ).effectiveForSelectedProvider()

        assertEquals(0.3f, bedrock.temperature)
        assertEquals(0.94f, bedrock.topP)
        assertEquals(275, bedrock.topK)
        assertEquals(32_000, bedrock.maxOutputTokens)
        assertFalse(bedrock.streamingEnabled)
        assertFalse(bedrock.thinkingEnabled)
        assertEquals(1.7f, gemini.temperature)
        assertEquals(0.72f, gemini.topP)
        assertEquals(33, gemini.topK)
        assertEquals(4_096, gemini.maxOutputTokens)
        assertTrue(gemini.streamingEnabled)
        assertTrue(gemini.thinkingEnabled)
    }

    @Test
    fun newAndExtendedChatsUseDefaultsWhileDuplicatesCopySettings() {
        val source = Chat(
            id = "source",
            settings = ChatSettings(
                temperature = 0.2f,
                ttsEnabled = true
            ),
            usage = ChatUsageSummary(inputTokens = 500, outputTokens = 250)
        )
        val duplicate = source.copy(
            id = "duplicate",
            title = "Copy",
            usage = ChatUsageSummary()
        )
        val newChat = Chat(id = "new")
        val extendedChat = Chat(id = "extended")

        assertEquals(source.settings, duplicate.settings)
        assertEquals(ChatUsageSummary(), duplicate.usage)
        assertEquals(ChatSettings(), newChat.settings)
        assertEquals(ChatSettings(), extendedChat.settings)
        assertEquals(ChatSettings(), source.copy(settings = ChatSettings()).settings)
    }

    @Test
    fun changingOneChatDoesNotChangeAnotherAndSurvivesReload() {
        val chatA = Chat(id = "a")
        val chatB = Chat(id = "b")
        val changedA = chatA.copy(
            settings = chatA.settings.copy(
                responseLength = ResponseLength.LONG,
                ttsEnabled = true
            )
        )
        val reloadedA = Chat.fromMap(changedA.toMap())

        assertEquals(ResponseLength.LONG, reloadedA.settings.responseLength)
        assertTrue(reloadedA.settings.ttsEnabled)
        assertEquals(ChatSettings(), chatB.settings)
    }

    @Test
    fun privateAndSummarizerDefaultsRemainUnchanged() {
        val privateSettings = PrivateChatSettings()
        val summarizerPrompts = SummarizerPrompts()

        assertFalse(privateSettings.ttsEnabled)
        assertFalse(privateSettings.thinkingEnabled)
        assertEquals("", privateSettings.conversationStylePrompt)
        assertEquals("", summarizerPrompts.highDetailSummaryPrompt)
        assertEquals("", summarizerPrompts.worldDescriptionPrompt)
    }
}
