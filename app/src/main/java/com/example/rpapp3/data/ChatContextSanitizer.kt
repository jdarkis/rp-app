package com.example.rpapp3.data

import com.example.rpapp3.data.model.ChatMessage

private val GENERATED_CHOICES_MARKER =
    Regex("""(?m)^[ \t]*\[(?:ACTIONS|DIALOGUE)](?:[ \t]*\r?)?$""")

internal fun sanitizeChatMessageForAiContext(message: ChatMessage): ChatMessage? {
    if (message.isUser) return message

    val marker = GENERATED_CHOICES_MARKER.find(message.text)
    val cleanedText = if (marker == null) {
        message.text.trim()
    } else {
        message.text.substring(0, marker.range.first).trim()
    }

    return cleanedText
        .takeIf { it.isNotEmpty() }
        ?.let { message.copy(text = it) }
}

internal fun sanitizeChatHistoryForAiContext(
    messages: Iterable<ChatMessage>
): List<ChatMessage> = messages.mapNotNull(::sanitizeChatMessageForAiContext)
