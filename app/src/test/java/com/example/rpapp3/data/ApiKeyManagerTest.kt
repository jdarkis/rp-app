package com.example.rpapp3.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ApiKeyManagerTest {

    @Test
    fun syncSkipsBundledKeysThatWereDeletedByUser() {
        val bundledKeys = listOf("default-1", "default-2", "default-3")
        val currentKeys = listOf("default-1", "custom-key")
        val removedBundledKeys = listOf("default-2")

        assertEquals(
            listOf("default-3"),
            geminiDefaultKeysToSync(
                currentKeys = currentKeys,
                removedDefaultKeys = removedBundledKeys,
                defaultKeys = bundledKeys
            )
        )
    }

    @Test
    fun removedBundledKeyTombstoneIsClearedWhenKeyIsAddedAgain() {
        val bundledKeys = listOf("default-1", "default-2")
        val removedKeys = markGeminiDefaultKeyRemoved(
            key = "default-2",
            removedDefaultKeys = emptySet(),
            defaultKeys = bundledKeys
        )

        assertEquals(setOf("default-2"), removedKeys)
        assertEquals(
            emptySet<String>(),
            unmarkGeminiDefaultKeyRemoved("default-2", removedKeys)
        )
    }

    @Test
    fun customKeyDeletionDoesNotCreateBundledKeyTombstone() {
        val removedKeys = markGeminiDefaultKeyRemoved(
            key = "custom-key",
            removedDefaultKeys = emptySet(),
            defaultKeys = listOf("default-1", "default-2")
        )

        assertEquals(emptySet<String>(), removedKeys)
    }

    @Test
    fun parseAndSerializeApiKeyListsIgnoreEmptySegments() {
        val keys = parseGeminiApiKeyList("key-1|||key-2||||||key-3")

        assertEquals(listOf("key-1", "key-2", "key-3"), keys)
        assertEquals("key-1|||key-2|||key-3", serializeGeminiApiKeyList(keys))
    }
}
