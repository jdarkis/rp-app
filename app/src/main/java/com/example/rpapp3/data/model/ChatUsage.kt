package com.example.rpapp3.data.model

import com.example.rpapp3.data.ChatSettingsManager

private const val NANODOLLARS_PER_DOLLAR = 1_000_000_000L
private const val LARGE_PROMPT_THRESHOLD = 200_000L

data class ChatUsageSummary(
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val inputCostNanodollars: Long = 0,
    val outputCostNanodollars: Long = 0,
    val trackedCallCount: Long = 0,
    val missingUsageCallCount: Long = 0,
    val unpricedCallCount: Long = 0
) {
    val totalCostNanodollars: Long
        get() = safeAdd(inputCostNanodollars, outputCostNanodollars)

    fun plus(record: ChatUsageRecord): ChatUsageSummary = ChatUsageSummary(
        inputTokens = safeAdd(inputTokens, record.inputTokens ?: 0),
        outputTokens = safeAdd(outputTokens, record.outputTokens ?: 0),
        inputCostNanodollars = safeAdd(
            inputCostNanodollars,
            record.inputCostNanodollars ?: 0
        ),
        outputCostNanodollars = safeAdd(
            outputCostNanodollars,
            record.outputCostNanodollars ?: 0
        ),
        trackedCallCount = safeAdd(trackedCallCount, 1),
        missingUsageCallCount = safeAdd(
            missingUsageCallCount,
            if (record.hasCompleteUsage) 0 else 1
        ),
        unpricedCallCount = safeAdd(
            unpricedCallCount,
            if (record.hasCompleteUsage && !record.hasCompletePricing) 1 else 0
        )
    )

    fun toMap(): Map<String, Any> = mapOf(
        "inputTokens" to inputTokens,
        "outputTokens" to outputTokens,
        "inputCostNanodollars" to inputCostNanodollars,
        "outputCostNanodollars" to outputCostNanodollars,
        "trackedCallCount" to trackedCallCount,
        "missingUsageCallCount" to missingUsageCallCount,
        "unpricedCallCount" to unpricedCallCount
    )

    companion object {
        fun fromMap(value: Any?): ChatUsageSummary {
            val map = value.asStringKeyMap() ?: return ChatUsageSummary()
            return ChatUsageSummary(
                inputTokens = map.nonNegativeLong("inputTokens"),
                outputTokens = map.nonNegativeLong("outputTokens"),
                inputCostNanodollars = map.nonNegativeLong("inputCostNanodollars"),
                outputCostNanodollars = map.nonNegativeLong("outputCostNanodollars"),
                trackedCallCount = map.nonNegativeLong("trackedCallCount"),
                missingUsageCallCount = map.nonNegativeLong("missingUsageCallCount"),
                unpricedCallCount = map.nonNegativeLong("unpricedCallCount")
            )
        }
    }
}

data class ChatUsageRecord(
    val id: String,
    val chatId: String,
    val messageId: String,
    val provider: String,
    val modelId: String,
    val createdAt: Long,
    val inputTokens: Long?,
    val outputTokens: Long?,
    val inputCostNanodollars: Long?,
    val outputCostNanodollars: Long?
) {
    val hasCompleteUsage: Boolean
        get() = inputTokens != null && outputTokens != null

    val hasCompletePricing: Boolean
        get() = inputCostNanodollars != null && outputCostNanodollars != null

    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "chatId" to chatId,
        "messageId" to messageId,
        "provider" to provider,
        "modelId" to modelId,
        "createdAt" to createdAt,
        "inputTokens" to inputTokens,
        "outputTokens" to outputTokens,
        "inputCostNanodollars" to inputCostNanodollars,
        "outputCostNanodollars" to outputCostNanodollars
    )

    companion object {
        fun create(
            id: String,
            chatId: String,
            messageId: String,
            provider: String,
            modelId: String,
            inputTokens: Long?,
            outputTokens: Long?,
            createdAt: Long = System.currentTimeMillis()
        ): ChatUsageRecord {
            val safeInputTokens = inputTokens?.coerceAtLeast(0)
            val safeOutputTokens = outputTokens?.coerceAtLeast(0)
            val quote = if (safeInputTokens != null && safeOutputTokens != null) {
                ChatUsagePricing.quote(modelId, safeInputTokens, safeOutputTokens)
            } else {
                null
            }

            return ChatUsageRecord(
                id = id,
                chatId = chatId,
                messageId = messageId,
                provider = provider,
                modelId = modelId,
                createdAt = createdAt,
                inputTokens = safeInputTokens,
                outputTokens = safeOutputTokens,
                inputCostNanodollars = quote?.inputCostNanodollars,
                outputCostNanodollars = quote?.outputCostNanodollars
            )
        }

        fun fromMap(value: Any?): ChatUsageRecord? {
            val map = value.asStringKeyMap() ?: return null
            val id = map["id"] as? String ?: return null
            return ChatUsageRecord(
                id = id,
                chatId = map["chatId"] as? String ?: "",
                messageId = map["messageId"] as? String ?: "",
                provider = map["provider"] as? String ?: "",
                modelId = map["modelId"] as? String ?: "",
                createdAt = map.nonNegativeLong("createdAt"),
                inputTokens = map.nullableNonNegativeLong("inputTokens"),
                outputTokens = map.nullableNonNegativeLong("outputTokens"),
                inputCostNanodollars = map.nullableNonNegativeLong("inputCostNanodollars"),
                outputCostNanodollars = map.nullableNonNegativeLong("outputCostNanodollars")
            )
        }
    }
}

data class ChatUsageQuote(
    val inputCostNanodollars: Long,
    val outputCostNanodollars: Long
)

object ChatUsagePricing {
    fun quote(
        modelId: String,
        inputTokens: Long,
        outputTokens: Long
    ): ChatUsageQuote? {
        val safeInputTokens = inputTokens.coerceAtLeast(0)
        val safeOutputTokens = outputTokens.coerceAtLeast(0)
        val largePrompt = safeInputTokens > LARGE_PROMPT_THRESHOLD
        val rates = when (modelId) {
            "gemini-3-flash-preview" -> Rates(500, 3_000)
            "gemini-3-pro-preview" -> if (largePrompt) {
                Rates(4_000, 18_000)
            } else {
                Rates(2_000, 12_000)
            }
            "gemini-2.5-flash" -> Rates(300, 2_500)
            "gemini-2.5-flash-lite" -> Rates(100, 400)
            "gemini-2.5-pro" -> if (largePrompt) {
                Rates(2_500, 15_000)
            } else {
                Rates(1_250, 10_000)
            }
            ChatSettingsManager.DEFAULT_BEDROCK_MODEL_ID -> Rates(5_000, 25_000)
            else -> return null
        }

        return ChatUsageQuote(
            inputCostNanodollars = safeMultiply(safeInputTokens, rates.inputNanodollarsPerToken),
            outputCostNanodollars = safeMultiply(safeOutputTokens, rates.outputNanodollarsPerToken)
        )
    }

    fun nanodollarsToUsd(nanodollars: Long): Double =
        nanodollars.coerceAtLeast(0).toDouble() / NANODOLLARS_PER_DOLLAR

    private data class Rates(
        val inputNanodollarsPerToken: Long,
        val outputNanodollarsPerToken: Long
    )
}

data class ProviderTokenUsage(
    val inputTokens: Long?,
    val outputTokens: Long?
)

fun geminiTokenUsage(
    promptTokenCount: Int,
    candidatesTokenCount: Int,
    totalTokenCount: Int
): ProviderTokenUsage {
    val input = promptTokenCount.coerceAtLeast(0).toLong()
    val candidates = candidatesTokenCount.coerceAtLeast(0).toLong()
    val totalOutput = (totalTokenCount.toLong() - input).coerceAtLeast(0)
    return ProviderTokenUsage(
        inputTokens = input,
        outputTokens = maxOf(candidates, totalOutput)
    )
}

private fun Any?.asStringKeyMap(): Map<String, Any?>? {
    val rawMap = this as? Map<*, *> ?: return null
    return rawMap.entries.mapNotNull { (key, value) ->
        (key as? String)?.let { it to value }
    }.toMap()
}

private fun Map<String, Any?>.nonNegativeLong(key: String): Long =
    nullableNonNegativeLong(key) ?: 0

private fun Map<String, Any?>.nullableNonNegativeLong(key: String): Long? =
    (this[key] as? Number)?.toLong()?.coerceAtLeast(0)

private fun safeAdd(left: Long, right: Long): Long =
    if (right > 0 && left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right

private fun safeMultiply(value: Long, multiplier: Long): Long =
    if (value > 0 && multiplier > Long.MAX_VALUE / value) {
        Long.MAX_VALUE
    } else {
        value * multiplier
    }
