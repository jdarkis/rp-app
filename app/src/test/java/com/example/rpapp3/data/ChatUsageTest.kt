package com.example.rpapp3.data

import com.example.rpapp3.data.model.Chat
import com.example.rpapp3.data.model.ChatUsagePricing
import com.example.rpapp3.data.model.ChatUsageRecord
import com.example.rpapp3.data.model.ChatUsageSummary
import com.example.rpapp3.data.model.ModelRequestDetails
import com.example.rpapp3.data.model.ModelRequestStatus
import com.example.rpapp3.data.model.geminiTokenUsage
import com.example.rpapp3.data.model.resolveModelRequestUsage
import com.example.rpapp3.data.repository.selectLatestUsageRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatUsageTest {

    @Test
    fun chatAndUsageRecordRoundTripPreserveUsage() {
        val record = ChatUsageRecord.create(
            id = "usage-1",
            chatId = "chat-1",
            messageId = "message-1",
            provider = AiProvider.GEMINI.name,
            modelId = "gemini-2.5-flash",
            inputTokens = 1_000,
            outputTokens = 500,
            createdAt = 1234
        )
        val chat = Chat(
            id = "chat-1",
            usage = ChatUsageSummary().plus(record)
        )

        assertEquals(chat, Chat.fromMap(chat.toMap()))
        assertEquals(record, ChatUsageRecord.fromMap(record.toMap()))
    }

    @Test
    fun missingAndMalformedUsageFallsBackSafely() {
        val legacyChat = Chat.fromMap(mapOf("id" to "legacy"))
        val malformed = ChatUsageSummary.fromMap(
            mapOf(
                "inputTokens" to -50,
                "outputTokens" to "invalid",
                "inputCostNanodollars" to -1L,
                "trackedCallCount" to -10
            )
        )

        assertEquals(ChatUsageSummary(), legacyChat.usage)
        assertEquals(ChatUsageSummary(), malformed)
        assertNull(ChatUsageRecord.fromMap(mapOf("chatId" to "chat-1")))
    }

    @Test
    fun standardPricesProduceExactNanodollarCosts() {
        val expectedRates = listOf(
            Triple("gemini-3-flash-preview", 500_000_000L, 3_000_000_000L),
            Triple("gemini-3-pro-preview", 4_000_000_000L, 18_000_000_000L),
            Triple("gemini-2.5-flash", 300_000_000L, 2_500_000_000L),
            Triple("gemini-2.5-flash-lite", 100_000_000L, 400_000_000L),
            Triple("gemini-2.5-pro", 2_500_000_000L, 15_000_000_000L),
            Triple(
                ChatSettingsManager.DEFAULT_BEDROCK_MODEL_ID,
                5_000_000_000L,
                25_000_000_000L
            )
        )

        expectedRates.forEach { (modelId, expectedInputCost, expectedOutputCost) ->
            val quote = ChatUsagePricing.quote(
                modelId = modelId,
                inputTokens = 1_000_000,
                outputTokens = 1_000_000
            )!!
            assertEquals(modelId, expectedInputCost, quote.inputCostNanodollars)
            assertEquals(modelId, expectedOutputCost, quote.outputCostNanodollars)
        }
        assertNull(ChatUsagePricing.quote("unknown-model", 100, 100))
    }

    @Test
    fun tieredPricingSwitchesOnlyAboveTwoHundredThousandInputTokens() {
        val atBoundary = ChatUsagePricing.quote(
            modelId = "gemini-3-pro-preview",
            inputTokens = 200_000,
            outputTokens = 1
        )!!
        val aboveBoundary = ChatUsagePricing.quote(
            modelId = "gemini-3-pro-preview",
            inputTokens = 200_001,
            outputTokens = 1
        )!!
        val pro25AboveBoundary = ChatUsagePricing.quote(
            modelId = "gemini-2.5-pro",
            inputTokens = 200_001,
            outputTokens = 1
        )!!

        assertEquals(400_000_000L, atBoundary.inputCostNanodollars)
        assertEquals(12_000L, atBoundary.outputCostNanodollars)
        assertEquals(800_004_000L, aboveBoundary.inputCostNanodollars)
        assertEquals(18_000L, aboveBoundary.outputCostNanodollars)
        assertEquals(500_002_500L, pro25AboveBoundary.inputCostNanodollars)
        assertEquals(15_000L, pro25AboveBoundary.outputCostNanodollars)
    }

    @Test
    fun geminiUsageIncludesThinkingTokensInOutput() {
        val withThinking = geminiTokenUsage(
            promptTokenCount = 100,
            candidatesTokenCount = 40,
            totalTokenCount = 175
        )
        val inconsistentTotal = geminiTokenUsage(
            promptTokenCount = 100,
            candidatesTokenCount = 40,
            totalTokenCount = 120
        )

        assertEquals(100L, withThinking.inputTokens)
        assertEquals(75L, withThinking.outputTokens)
        assertEquals(40L, inconsistentTotal.outputTokens)
    }

    @Test
    fun aggregateKeepsKnownValuesAndFlagsIncompleteOrUnpricedCalls() {
        val complete = ChatUsageRecord.create(
            id = "complete",
            chatId = "chat",
            messageId = "message",
            provider = AiProvider.GEMINI.name,
            modelId = "gemini-2.5-flash-lite",
            inputTokens = 100,
            outputTokens = 50
        )
        val missing = ChatUsageRecord.create(
            id = "missing",
            chatId = "chat",
            messageId = "message-2",
            provider = AiProvider.GEMINI.name,
            modelId = "gemini-2.5-flash-lite",
            inputTokens = null,
            outputTokens = null
        )
        val unpriced = ChatUsageRecord.create(
            id = "unpriced",
            chatId = "chat",
            messageId = "message-3",
            provider = AiProvider.GEMINI.name,
            modelId = "future-model",
            inputTokens = 20,
            outputTokens = 10
        )

        val summary = ChatUsageSummary()
            .plus(complete)
            .plus(missing)
            .plus(unpriced)

        assertEquals(120L, summary.inputTokens)
        assertEquals(60L, summary.outputTokens)
        assertEquals(3L, summary.trackedCallCount)
        assertEquals(1L, summary.missingUsageCallCount)
        assertEquals(1L, summary.unpricedCallCount)
        assertTrue(complete.hasCompleteUsage)
        assertTrue(complete.hasCompletePricing)
        assertFalse(missing.hasCompleteUsage)
        assertFalse(unpriced.hasCompletePricing)
    }

    @Test
    fun legacyUsageSelectionUsesNewestMatchingMessageAndModel() {
        val records = listOf(
            usageRecord(id = "older", messageId = "message", modelId = "model", createdAt = 10),
            usageRecord(id = "newer", messageId = "message", modelId = "model", createdAt = 20),
            usageRecord(id = "wrong-model", messageId = "message", modelId = "other", createdAt = 30),
            usageRecord(id = "wrong-message", messageId = "other", modelId = "model", createdAt = 40)
        )

        assertEquals(
            "newer",
            selectLatestUsageRecord(records, messageId = "message", modelId = "model")?.id
        )
    }

    @Test
    fun requestUsagePrefersExactRecordAndSkipsNotSentRequests() {
        val exact = usageRecord(id = "exact", messageId = "message", modelId = "model", createdAt = 10)
        val legacy = usageRecord(id = "legacy", messageId = "message", modelId = "model", createdAt = 20)
        val exactDetails = requestDetails(usageRecordId = "exact")

        assertEquals(exact, resolveModelRequestUsage(exactDetails, exact, legacy))
        assertEquals(
            legacy,
            resolveModelRequestUsage(requestDetails(usageRecordId = null), exact, legacy)
        )
        assertNull(
            resolveModelRequestUsage(
                requestDetails(
                    usageRecordId = null,
                    status = ModelRequestStatus.NOT_SENT
                ),
                exact,
                legacy
            )
        )
    }

    private fun usageRecord(
        id: String,
        messageId: String,
        modelId: String,
        createdAt: Long
    ) = ChatUsageRecord(
        id = id,
        chatId = "chat",
        messageId = messageId,
        provider = AiProvider.GEMINI.name,
        modelId = modelId,
        createdAt = createdAt,
        inputTokens = 100,
        outputTokens = 50,
        inputCostNanodollars = 30_000,
        outputCostNanodollars = 125_000
    )

    private fun requestDetails(
        usageRecordId: String?,
        status: ModelRequestStatus = ModelRequestStatus.SENT
    ) = ModelRequestDetails(
        chatId = "chat",
        messageId = "message",
        usageRecordId = usageRecordId,
        status = status,
        provider = "Gemini",
        modelId = "model",
        streaming = false,
        systemPrompt = "",
        messages = emptyList(),
        parameters = emptyList(),
        rawSnapshotLabel = "Snapshot",
        rawSnapshot = "{}"
    )
}
