package dev.jem.offlinegit.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dev.jem.offlinegit.domain.WritableTokenStore
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidEncryptedTokenStore(
    context: Context,
    private val keyAlias: String = "offline_git_token",
) : WritableTokenStore {

    private val preferences = context.applicationContext.getSharedPreferences(
        "offline-git-auth",
        Context.MODE_PRIVATE,
    )

    override suspend fun getToken(): String? = try {
        val encryptedToken = preferences.getString(KEY_TOKEN, null) ?: return null
        val iv = preferences.getString(KEY_IV, null) ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(GCM_TAG_BITS, iv.decodeBase64()),
        )
        cipher.doFinal(encryptedToken.decodeBase64()).decodeToString()
    } catch (e: Exception) {
        clearToken()
        null
    }

    override suspend fun saveToken(token: String) {
        if (token.isBlank()) {
            clearToken()
            return
        }

        try {
            val iv = ByteArray(GCM_IV_BYTES).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.ENCRYPT_MODE,
                secretKey(),
                GCMParameterSpec(GCM_TAG_BITS, iv),
            )
            preferences.edit()
                .putString(KEY_TOKEN, cipher.doFinal(token.encodeToByteArray()).encodeBase64())
                .putString(KEY_IV, iv.encodeBase64())
                .apply()
        } catch (e: Exception) {
            clearToken()
        }
    }

    override suspend fun clearToken() {
        preferences.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_IV)
            .apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        if (keyStore.containsAlias(keyAlias)) {
            return (keyStore.getEntry(keyAlias, null) as KeyStore.SecretKeyEntry).secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return keyGenerator.generateKey()
    }

    private fun ByteArray.encodeBase64(): String = Base64.encodeToString(this, Base64.NO_WRAP)

    private fun String.decodeBase64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
        const val KEY_IV = "iv"
        const val KEY_TOKEN = "token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
