package com.example.rpapp3.data

import com.example.rpapp3.data.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatContextSanitizerTest {

    @Test
    fun removesStandardGeneratedChoicesFromAssistantMessage() {
        val message = assistant(
            """
                Snow gathered on the windowsill.
                [Landlady]:"The office is closed."

                [ACTIONS]
                1. I wait.
                2. I leave.
                3. I ask why.
                [DIALOGUE]
                a. "When does it open?"
            """.trimIndent()
        )

        assertEquals(
            """
                Snow gathered on the windowsill.
                [Landlady]:"The office is closed."
            """.trimIndent(),
            sanitizeChatMessageForAiContext(message)?.text
        )
    }

    @Test
    fun removesFromEarliestStandaloneMarkerAndPreservesInlineMentions() {
        val inline = assistant("The clerk pointed to the [ACTIONS] heading on the form.")
        val dialogueOnly = assistant("The bell rang.\r\n  [DIALOGUE]  \r\na. \"Hello\"")

        assertEquals(inline.text, sanitizeChatMessageForAiContext(inline)?.text)
        assertEquals("The bell rang.", sanitizeChatMessageForAiContext(dialogueOnly)?.text)
    }

    @Test
    fun leavesUserMessagesUnchangedAndOmitsChoiceOnlyAssistantMessages() {
        val user = ChatMessage(
            text = "I quote the marker:\n[ACTIONS]\nThen continue.",
            isUser = true
        )
        val choiceOnly = assistant("[ACTIONS]\n1. I wait.")

        assertEquals(user, sanitizeChatMessageForAiContext(user))
        assertNull(sanitizeChatMessageForAiContext(choiceOnly))
    }

    @Test
    fun storyAndCharacterExtractionContextsUseSanitizedAssistantText() {
        val messages = listOf(
            ChatMessage(text = "I enter the inn.", isUser = true),
            assistant(
                """
                    Marta locked the door.
                    [ACTIONS]
                    1. I knock.
                    [DIALOGUE]
                    a. "Let me in."
                """.trimIndent(),
                characterName = "Narrator"
            )
        )

        val storyContext = buildStoryChatContent(messages)
        val extractionContext = buildCharacterExtractionUserPrompt(
            worldDescription = null,
            aiInstructions = null,
            chatMessages = messages,
            additionalPrompt = null
        )

        listOf(storyContext, extractionContext).forEach { context ->
            assertTrue(context.contains("Marta locked the door."))
            assertFalse(context.contains("[ACTIONS]"))
            assertFalse(context.contains("[DIALOGUE]"))
            assertFalse(context.contains("I knock."))
        }
    }

    private fun assistant(
        text: String,
        characterName: String? = null
    ) = ChatMessage(
        text = text,
        isUser = false,
        characterName = characterName
    )
}
