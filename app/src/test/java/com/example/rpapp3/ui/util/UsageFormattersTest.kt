package com.example.rpapp3.ui.util

import com.example.rpapp3.data.model.ChatUsageRecord
import com.example.rpapp3.data.model.ModelRequestDetails
import com.example.rpapp3.ui.components.buildModelRequestDetailsCopyText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageFormattersTest {

    @Test
    fun tokenAndUsdFormattingCoversZeroTinyAndNormalValues() {
        assertEquals("12,345", formatTokenCount(12_345))
        assertEquals("$0.00", formatUsd(0))
        assertEquals("<$0.000001", formatUsd(1))
        assertEquals("$0.001235", formatUsd(1_234_567))
    }

    @Test
    fun inputUsageFormattingDistinguishesMissingAndUnpricedValues() {
        assertEquals("Unavailable", formatInputTokenCount(null))
        assertEquals("Unavailable", formatInputTokenCost(null))

        val missing = usage(inputTokens = null, inputCostNanodollars = null)
        assertEquals("Unavailable", formatInputTokenCount(missing))
        assertEquals("Unavailable", formatInputTokenCost(missing))

        val unpriced = usage(inputTokens = 250, inputCostNanodollars = null)
        assertEquals("250", formatInputTokenCount(unpriced))
        assertEquals("Unavailable for this model", formatInputTokenCost(unpriced))

        val zeroCost = usage(inputTokens = 0, inputCostNanodollars = 0)
        assertEquals("0", formatInputTokenCount(zeroCost))
        assertEquals("$0.00", formatInputTokenCost(zeroCost))
    }

    @Test
    fun copiedRpDetailsIncludeInputUsage() {
        val text = buildModelRequestDetailsCopyText(
            details = ModelRequestDetails(
                chatId = "chat",
                messageId = "message",
                provider = "Gemini",
                modelId = "gemini-2.5-flash",
                streaming = false,
                systemPrompt = "",
                messages = emptyList(),
                parameters = emptyList(),
                rawSnapshotLabel = "Snapshot",
                rawSnapshot = "{}"
            ),
            inputUsage = usage(inputTokens = 1_000, inputCostNanodollars = 300_000),
            showInputUsage = true
        )

        assertTrue(text.contains("INPUT USAGE"))
        assertTrue(text.contains("Input tokens: 1,000"))
        assertTrue(text.contains("Estimated input cost: $0.0003"))
    }

    private fun usage(
        inputTokens: Long?,
        inputCostNanodollars: Long?
    ) = ChatUsageRecord(
        id = "usage",
        chatId = "chat",
        messageId = "message",
        provider = "GEMINI",
        modelId = "gemini-2.5-flash",
        createdAt = 1,
        inputTokens = inputTokens,
        outputTokens = 1,
        inputCostNanodollars = inputCostNanodollars,
        outputCostNanodollars = 1
    )
}
