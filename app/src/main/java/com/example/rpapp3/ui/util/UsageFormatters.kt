package com.example.rpapp3.ui.util

import com.example.rpapp3.data.model.ChatUsagePricing
import com.example.rpapp3.data.model.ChatUsageRecord
import java.text.NumberFormat
import java.util.Locale

internal fun formatTokenCount(value: Long): String =
    NumberFormat.getIntegerInstance(Locale.US).format(value.coerceAtLeast(0))

internal fun formatUsd(nanodollars: Long): String {
    if (nanodollars <= 0) return "$0.00"
    val dollars = ChatUsagePricing.nanodollarsToUsd(nanodollars)
    if (dollars < 0.000001) return "<$0.000001"

    return NumberFormat.getCurrencyInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 6
    }.format(dollars)
}

internal fun formatInputTokenCount(usage: ChatUsageRecord?): String =
    usage?.inputTokens?.let(::formatTokenCount) ?: "Unavailable"

internal fun formatInputTokenCost(usage: ChatUsageRecord?): String {
    if (usage?.inputTokens == null) return "Unavailable"
    return usage.inputCostNanodollars?.let(::formatUsd) ?: "Unavailable for this model"
}
