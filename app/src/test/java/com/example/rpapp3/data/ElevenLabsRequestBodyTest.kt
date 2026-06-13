package com.example.rpapp3.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ElevenLabsRequestBodyTest {

    @Test
    fun requestBodyEscapesTextAndDoesNotForceLanguage() {
        val body = buildElevenLabsRequestBody(
            text = "She said \"hello\".\\Path\nNext line\tTabbed",
            modelId = "eleven_v3"
        )

        assertEquals(
            """{"text":"She said \"hello\".\\Path\nNext line\tTabbed","model_id":"eleven_v3"}""",
            body
        )
        assertFalse(body.contains("language_code"))
    }
}
