package com.example.rpapp3.data

data class TtsReplayAudioEntry(
    val generationId: Long,
    val audioData: ByteArray? = null,
    val localAudioUri: String? = null,
    val audioUrl: String? = null
) {
    val isPlayable: Boolean
        get() = audioData != null || !localAudioUri.isNullOrBlank() || !audioUrl.isNullOrBlank()
}

typealias TtsReplayAudioState = Map<String, Map<Int, TtsReplayAudioEntry>>

internal fun putTtsReplayAudioBytes(
    state: TtsReplayAudioState,
    messageId: String,
    segmentIndex: Int,
    generationId: Long,
    audioData: ByteArray,
    localAudioUri: String? = null
): TtsReplayAudioState {
    return state.withReplayAudioEntry(
        messageId = messageId,
        segmentIndex = segmentIndex,
        entry = TtsReplayAudioEntry(
            generationId = generationId,
            audioData = audioData,
            localAudioUri = localAudioUri
        )
    )
}

internal fun putTtsReplayAudioUrlIfCurrent(
    state: TtsReplayAudioState,
    messageId: String,
    segmentIndex: Int,
    generationId: Long,
    audioUrl: String
): TtsReplayAudioState {
    val currentEntry = state[messageId]?.get(segmentIndex) ?: return state
    if (currentEntry.generationId != generationId) return state

    return state.withReplayAudioEntry(
        messageId = messageId,
        segmentIndex = segmentIndex,
        entry = TtsReplayAudioEntry(
            generationId = generationId,
            localAudioUri = currentEntry.localAudioUri,
            audioUrl = audioUrl
        )
    )
}

internal fun mergeLocalTtsReplayAudioUris(
    state: TtsReplayAudioState,
    messageId: String,
    localAudioUris: Map<Int, String>
): TtsReplayAudioState {
    if (localAudioUris.isEmpty()) return state

    val messageAudio = state[messageId].orEmpty().toMutableMap()
    var changed = false

    localAudioUris.forEach { (segmentIndex, localAudioUri) ->
        val currentEntry = messageAudio[segmentIndex]
        if (localAudioUri.isNotBlank() && currentEntry == null) {
            messageAudio[segmentIndex] = TtsReplayAudioEntry(
                generationId = 0L,
                localAudioUri = localAudioUri
            )
            changed = true
        }
    }

    if (!changed) return state

    return state.toMutableMap().also { updatedState ->
        updatedState[messageId] = messageAudio
    }
}

internal fun mergePersistedTtsReplayAudioUrls(
    state: TtsReplayAudioState,
    messageId: String,
    audioUrls: Map<Int, String>
): TtsReplayAudioState {
    if (audioUrls.isEmpty()) return state

    val messageAudio = state[messageId].orEmpty().toMutableMap()
    var changed = false

    audioUrls.forEach { (segmentIndex, audioUrl) ->
        if (audioUrl.isNotBlank()) {
            val currentEntry = messageAudio[segmentIndex]
            if (currentEntry == null) {
                messageAudio[segmentIndex] = TtsReplayAudioEntry(
                    generationId = 0L,
                    audioUrl = audioUrl
                )
                changed = true
            } else if (
                currentEntry.generationId == 0L &&
                currentEntry.audioData == null &&
                currentEntry.audioUrl.isNullOrBlank()
            ) {
                messageAudio[segmentIndex] = currentEntry.copy(audioUrl = audioUrl)
                changed = true
            }
        }
    }

    if (!changed) return state

    return state.toMutableMap().also { updatedState ->
        updatedState[messageId] = messageAudio
    }
}

internal fun removeTtsReplayAudioForMessages(
    state: TtsReplayAudioState,
    messageIds: Collection<String>
): TtsReplayAudioState {
    if (messageIds.isEmpty()) return state
    return state.filterKeys { messageId -> messageId !in messageIds }
}

private fun TtsReplayAudioState.withReplayAudioEntry(
    messageId: String,
    segmentIndex: Int,
    entry: TtsReplayAudioEntry
): TtsReplayAudioState {
    val messageAudio = this[messageId].orEmpty().toMutableMap()
    messageAudio[segmentIndex] = entry

    return toMutableMap().also { updatedState ->
        updatedState[messageId] = messageAudio
    }
}
