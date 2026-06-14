package com.example.rpapp3.data.model

enum class ModelRequestStatus {
    SENT,
    NOT_SENT
}

data class ModelRequestMessage(
    val role: String,
    val text: String
) {
    fun toMap(): Map<String, Any> = mapOf(
        "role" to role,
        "text" to text
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): ModelRequestMessage {
            return ModelRequestMessage(
                role = map["role"] as? String ?: "",
                text = map["text"] as? String ?: ""
            )
        }
    }
}

data class ModelRequestParameter(
    val name: String,
    val value: String
) {
    fun toMap(): Map<String, Any> = mapOf(
        "name" to name,
        "value" to value
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): ModelRequestParameter {
            return ModelRequestParameter(
                name = map["name"] as? String ?: "",
                value = map["value"] as? String ?: ""
            )
        }
    }
}

data class ModelRequestDetails(
    val chatId: String,
    val messageId: String,
    val usageRecordId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val status: ModelRequestStatus = ModelRequestStatus.SENT,
    val failureReason: String? = null,
    val provider: String,
    val modelId: String,
    val streaming: Boolean,
    val systemPrompt: String,
    val messages: List<ModelRequestMessage>,
    val parameters: List<ModelRequestParameter>,
    val safetySettings: List<ModelRequestParameter> = emptyList(),
    val endpoint: String? = null,
    val rawSnapshotLabel: String,
    val rawSnapshot: String
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "chatId" to chatId,
        "messageId" to messageId,
        "usageRecordId" to usageRecordId,
        "createdAt" to createdAt,
        "status" to status.name,
        "failureReason" to failureReason,
        "provider" to provider,
        "modelId" to modelId,
        "streaming" to streaming,
        "systemPrompt" to systemPrompt,
        "messages" to messages.map { it.toMap() },
        "parameters" to parameters.map { it.toMap() },
        "safetySettings" to safetySettings.map { it.toMap() },
        "endpoint" to endpoint,
        "rawSnapshotLabel" to rawSnapshotLabel,
        "rawSnapshot" to rawSnapshot
    )

    fun toCopyText(): String = buildString {
        appendLine("Status: ${status.name}")
        failureReason?.let { appendLine("Failure reason: $it") }
        appendLine("Provider: $provider")
        appendLine("Model: $modelId")
        appendLine("Streaming: $streaming")
        endpoint?.let { appendLine("Endpoint: $it") }
        appendLine()
        appendLine("SYSTEM PROMPT")
        appendLine(systemPrompt.ifBlank { "(empty)" })
        appendLine()
        appendLine("MESSAGES")
        messages.forEachIndexed { index, message ->
            appendLine("${index + 1}. ${message.role}")
            appendLine(message.text)
        }
        appendLine()
        appendLine("PARAMETERS")
        parameters.forEach { appendLine("${it.name}: ${it.value}") }
        if (safetySettings.isNotEmpty()) {
            appendLine()
            appendLine("SAFETY SETTINGS")
            safetySettings.forEach { appendLine("${it.name}: ${it.value}") }
        }
        appendLine()
        appendLine(rawSnapshotLabel.uppercase())
        append(rawSnapshot.ifBlank { "(not available)" })
    }

    companion object {
        fun fromMap(map: Map<String, Any?>): ModelRequestDetails {
            return ModelRequestDetails(
                chatId = map["chatId"] as? String ?: "",
                messageId = map["messageId"] as? String ?: "",
                usageRecordId = map["usageRecordId"] as? String,
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L,
                status = runCatching {
                    ModelRequestStatus.valueOf(map["status"] as? String ?: "")
                }.getOrDefault(ModelRequestStatus.SENT),
                failureReason = map["failureReason"] as? String,
                provider = map["provider"] as? String ?: "",
                modelId = map["modelId"] as? String ?: "",
                streaming = map["streaming"] as? Boolean ?: false,
                systemPrompt = map["systemPrompt"] as? String ?: "",
                messages = mapList(map["messages"], ModelRequestMessage::fromMap),
                parameters = mapList(map["parameters"], ModelRequestParameter::fromMap),
                safetySettings = mapList(map["safetySettings"], ModelRequestParameter::fromMap),
                endpoint = map["endpoint"] as? String,
                rawSnapshotLabel = map["rawSnapshotLabel"] as? String ?: "Raw snapshot",
                rawSnapshot = map["rawSnapshot"] as? String ?: ""
            )
        }

        private fun <T> mapList(
            value: Any?,
            mapper: (Map<String, Any?>) -> T
        ): List<T> {
            return (value as? List<*>)
                .orEmpty()
                .mapNotNull { item ->
                    @Suppress("UNCHECKED_CAST")
                    (item as? Map<String, Any?>)?.let(mapper)
                }
        }
    }
}

data class ModelRequestDetailsWithUsage(
    val details: ModelRequestDetails,
    val usage: ChatUsageRecord?
)

internal fun resolveModelRequestUsage(
    details: ModelRequestDetails,
    exactUsage: ChatUsageRecord?,
    legacyUsage: ChatUsageRecord?
): ChatUsageRecord? {
    if (details.status != ModelRequestStatus.SENT) return null

    val usage = if (details.usageRecordId != null) exactUsage else legacyUsage
    return usage?.takeIf { record ->
        record.chatId == details.chatId &&
            record.messageId == details.messageId &&
            record.modelId == details.modelId &&
            (details.usageRecordId == null || record.id == details.usageRecordId)
    }
}
