package com.example.rpapp3.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

enum class GeminiKeyStatus {
    Checking,
    Active,
    InvalidOrBlocked,
    UnableToVerify
}

class GeminiApiKeyValidator(
    private val connectionFactory: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    }
) {
    companion object {
        private const val MODELS_ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models?pageSize=1"

        internal fun classifyResponse(
            responseCode: Int,
            responseBody: String
        ): GeminiKeyStatus {
            val normalizedBody = responseBody.lowercase()
            val indicatesInvalidKey =
                normalizedBody.contains("api_key_invalid") ||
                    normalizedBody.contains("api key not valid") ||
                    normalizedBody.contains("invalid api key") ||
                    (normalizedBody.contains("api key") &&
                        (normalizedBody.contains("leaked") ||
                            normalizedBody.contains("blocked")))

            return when {
                responseCode in 200..299 -> GeminiKeyStatus.Active
                responseCode == HttpURLConnection.HTTP_UNAUTHORIZED ||
                    responseCode == HttpURLConnection.HTTP_FORBIDDEN ||
                    indicatesInvalidKey -> GeminiKeyStatus.InvalidOrBlocked
                else -> GeminiKeyStatus.UnableToVerify
            }
        }
    }

    suspend fun validate(key: String): GeminiKeyStatus = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null

        try {
            connection = connectionFactory(URL(MODELS_ENDPOINT)).apply {
                requestMethod = "GET"
                setRequestProperty("x-goog-api-key", key)
                setRequestProperty("Accept", "application/json")
                connectTimeout = 10_000
                readTimeout = 10_000
            }

            val responseCode = connection.responseCode
            val responseBody = runCatching {
                val stream = if (responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }
                stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }.getOrDefault("")

            classifyResponse(responseCode, responseBody)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            GeminiKeyStatus.UnableToVerify
        } finally {
            connection?.disconnect()
        }
    }
}
