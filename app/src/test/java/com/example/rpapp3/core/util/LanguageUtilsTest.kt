package com.example.rpapp3.core.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageUtilsTest {

    @Test
    fun allEnglishChatDoesNotRequireLanguageInstructions() {
        assertFalse(
            LanguageUtils.requiresExplicitLanguageInstructions(
                narratorLanguage = "en",
                characterLanguages = listOf("en", "EN", "en-US")
            )
        )
    }

    @Test
    fun nonEnglishNarratorRequiresLanguageInstructions() {
        assertTrue(
            LanguageUtils.requiresExplicitLanguageInstructions(
                narratorLanguage = "lt",
                characterLanguages = listOf("en")
            )
        )
    }

    @Test
    fun nonEnglishCharacterRequiresLanguageInstructions() {
        assertTrue(
            LanguageUtils.requiresExplicitLanguageInstructions(
                narratorLanguage = "en",
                characterLanguages = listOf("en", "ru")
            )
        )
    }
}
