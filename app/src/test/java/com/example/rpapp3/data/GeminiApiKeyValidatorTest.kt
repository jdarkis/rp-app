package com.example.rpapp3.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.HttpURLConnection

class GeminiApiKeyValidatorTest {

    @Test
    fun successfulResponseIsActive() {
        assertEquals(
            GeminiKeyStatus.Active,
            GeminiApiKeyValidator.classifyResponse(HttpURLConnection.HTTP_OK, "{}")
        )
    }

    @Test
    fun invalidApiKeyResponseIsInvalidOrBlocked() {
        assertEquals(
            GeminiKeyStatus.InvalidOrBlocked,
            GeminiApiKeyValidator.classifyResponse(
                HttpURLConnection.HTTP_BAD_REQUEST,
                """{"error":{"status":"INVALID_ARGUMENT","message":"API key not valid."}}"""
            )
        )
    }

    @Test
    fun leakedApiKeyResponseIsInvalidOrBlocked() {
        assertEquals(
            GeminiKeyStatus.InvalidOrBlocked,
            GeminiApiKeyValidator.classifyResponse(
                HttpURLConnection.HTTP_BAD_REQUEST,
                """{"error":{"message":"Your API key was reported as leaked."}}"""
            )
        )
    }

    @Test
    fun permissionDeniedIsInvalidOrBlocked() {
        assertEquals(
            GeminiKeyStatus.InvalidOrBlocked,
            GeminiApiKeyValidator.classifyResponse(
                HttpURLConnection.HTTP_FORBIDDEN,
                """{"error":{"status":"PERMISSION_DENIED"}}"""
            )
        )
    }

    @Test
    fun unauthorizedIsInvalidOrBlocked() {
        assertEquals(
            GeminiKeyStatus.InvalidOrBlocked,
            GeminiApiKeyValidator.classifyResponse(
                HttpURLConnection.HTTP_UNAUTHORIZED,
                """{"error":{"status":"UNAUTHENTICATED"}}"""
            )
        )
    }

    @Test
    fun rateLimitIsUnableToVerifyRatherThanInvalid() {
        assertEquals(
            GeminiKeyStatus.UnableToVerify,
            GeminiApiKeyValidator.classifyResponse(
                429,
                """{"error":{"status":"RESOURCE_EXHAUSTED"}}"""
            )
        )
    }

    @Test
    fun serverFailureIsUnableToVerifyRatherThanInvalid() {
        assertEquals(
            GeminiKeyStatus.UnableToVerify,
            GeminiApiKeyValidator.classifyResponse(
                HttpURLConnection.HTTP_UNAVAILABLE,
                """{"error":{"status":"UNAVAILABLE"}}"""
            )
        )
    }

    @Test
    fun networkFailureIsUnableToVerifyRatherThanInvalid() = runBlocking {
        val validator = GeminiApiKeyValidator {
            throw IOException("Network unavailable")
        }

        assertEquals(
            GeminiKeyStatus.UnableToVerify,
            validator.validate("test-key")
        )
    }
}
