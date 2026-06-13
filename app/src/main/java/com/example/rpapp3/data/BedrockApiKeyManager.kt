package com.example.rpapp3.data

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.bedrockApiKeyDataStore: DataStore<Preferences> by preferencesDataStore(name = "bedrock_api_key")

data class BedrockApiKeyState(
    val hasKey: Boolean = false,
    val maskedKey: String = ""
)

class BedrockApiKeyManager private constructor(
    private val context: Context,
    private val secretStore: AndroidKeyStoreSecretStore = AndroidKeyStoreSecretStore()
) {
    companion object {
        private val ENCRYPTED_KEY = stringPreferencesKey("encrypted_bedrock_api_key")
        private val IV_KEY = stringPreferencesKey("bedrock_api_key_iv")

        @Volatile
        private var INSTANCE: BedrockApiKeyManager? = null

        fun getInstance(context: Context): BedrockApiKeyManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BedrockApiKeyManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    val apiKeyState: Flow<BedrockApiKeyState> = context.bedrockApiKeyDataStore.data.map { preferences ->
        val key = decryptKey(preferences)
        if (key.isNullOrBlank()) {
            BedrockApiKeyState()
        } else {
            BedrockApiKeyState(
                hasKey = true,
                maskedKey = maskBedrockApiKey(key)
            )
        }
    }

    suspend fun getApiKey(): String? {
        return decryptKey(context.bedrockApiKeyDataStore.data.first())
    }

    suspend fun saveApiKey(key: String) {
        val trimmedKey = key.trim()
        if (trimmedKey.isBlank()) return

        val encryptedValue = secretStore.encrypt(trimmedKey)
        context.bedrockApiKeyDataStore.edit { preferences ->
            preferences[ENCRYPTED_KEY] = encryptedValue.cipherText
            preferences[IV_KEY] = encryptedValue.iv
        }
    }

    suspend fun clearApiKey() {
        context.bedrockApiKeyDataStore.edit { preferences ->
            preferences.remove(ENCRYPTED_KEY)
            preferences.remove(IV_KEY)
        }
    }

    private fun decryptKey(preferences: Preferences): String? {
        val cipherText = preferences[ENCRYPTED_KEY] ?: return null
        val iv = preferences[IV_KEY] ?: return null
        return runCatching {
            secretStore.decrypt(EncryptedValue(cipherText = cipherText, iv = iv))
        }.getOrNull()
    }
}

internal data class EncryptedValue(
    val cipherText: String,
    val iv: String
)

internal class AndroidKeyStoreSecretStore(
    private val keyAlias: String = "rpapp3_bedrock_api_key"
) {
    private val keyStore: KeyStore
        get() = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    fun encrypt(value: String): EncryptedValue {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val cipherBytes = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return EncryptedValue(
            cipherText = Base64.encodeToString(cipherBytes, Base64.NO_WRAP),
            iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        )
    }

    fun decrypt(value: EncryptedValue): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = Base64.decode(value.iv, Base64.NO_WRAP)
        val cipherBytes = Base64.decode(value.cipherText, Base64.NO_WRAP)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val existingKey = (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.secretKey
        if (existingKey != null) return existingKey

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        val builder = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setUnlockedDeviceRequired(false)
        }

        generator.init(builder.build())
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
    }
}

internal fun maskBedrockApiKey(key: String): String {
    return if (key.length > 14) {
        "${key.take(7)}...${key.takeLast(7)}"
    } else {
        "Saved key"
    }
}
