package com.example.rpapp3.data

import com.example.rpapp3.data.repository.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemPromptSettingsTest {

    @Test
    fun systemPromptIsEnabledByDefault() {
        assertTrue(ChatSettings().systemPromptEnabled)
    }

    @Test
    fun defaultSystemPromptPreservesExistingInstruction() {
        assertEquals(
            "You are a roleplay AI assistant. You will be playing one or more characters in a collaborative story.",
            SettingsRepository.DEFAULT_SYSTEM_PROMPT
        )
    }
}
