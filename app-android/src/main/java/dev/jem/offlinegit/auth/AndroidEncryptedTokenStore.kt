package dev.jem.offlinegit.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import dev.jem.offlinegit.domain.AccountTokenStore
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * One token entry per account, all encrypted with the same Keystore key (the
 * alias does not need to vary per account). The pre-multi-account build stored
 * a single token under un-suffixed keys; it is exposed as the legacy token
 * until sign-in migration succeeds.
 */
class AndroidEncryptedTokenStore(
    context: Context,
    private val keyAlias: String = "offline_git_token",
) : AccountTokenStore {

    private val preferences = context.applicationContext.getSharedPreferences(
        "offline-git-auth",
        Context.MODE_PRIVATE,
    )

    override suspend fun getToken(accountId: Long): String? = decryptToken(
        tokenKey = "$KEY_TOKEN:$accountId",
        ivKey = "$KEY_IV:$accountId",
    )

    override suspend fun saveToken(
        accountId: Long,
        token: String,
    ) {
        if (token.isBlank()) {
            clearToken(accountId)
            return
        }

        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            // AndroidKeyStore GCM keys require randomized encryption, so the
            // Keystore must generate the IV itself — passing a caller-supplied
            // IV to ENCRYPT_MODE throws. Read the generated IV back off the cipher.
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val encryptedToken = cipher.doFinal(token.encodeToByteArray())
            preferences.edit()
                .putString("$KEY_TOKEN:$accountId", encryptedToken.encodeBase64())
                .putString("$KEY_IV:$accountId", cipher.iv.encodeBase64())
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "Unable to encrypt token for storage", e)
            clearToken(accountId)
        }
    }

    override suspend fun clearToken(accountId: Long) {
        preferences.edit()
            .remove("$KEY_TOKEN:$accountId")
            .remove("$KEY_IV:$accountId")
            .apply()
    }

    override suspend fun getLegacyToken(): String? = decryptToken(
        tokenKey = KEY_TOKEN,
        ivKey = KEY_IV,
    )

    override suspend fun clearLegacyToken() {
        preferences.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_IV)
            .apply()
    }

    private fun decryptToken(
        tokenKey: String,
        ivKey: String,
    ): String? = try {
        val encryptedToken = preferences.getString(tokenKey, null)
        val iv = preferences.getString(ivKey, null)
        if (encryptedToken == null || iv == null) {
            null
        } else {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(GCM_TAG_BITS, iv.decodeBase64()),
            )
            cipher.doFinal(encryptedToken.decodeBase64()).decodeToString()
        }
    } catch (e: Exception) {
        Log.w(TAG, "Unable to decrypt stored token; clearing it", e)
        preferences.edit()
            .remove(tokenKey)
            .remove(ivKey)
            .apply()
        null
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
        const val TAG = "TokenStore"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val GCM_TAG_BITS = 128
        const val KEY_IV = "iv"
        const val KEY_TOKEN = "token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
