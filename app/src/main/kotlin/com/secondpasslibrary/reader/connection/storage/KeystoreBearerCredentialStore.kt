package com.secondpasslibrary.reader.connection.storage

import android.annotation.SuppressLint
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.secondpasslibrary.client.BearerCredential
import com.secondpasslibrary.reader.connection.BearerCredentialStore
import com.secondpasslibrary.reader.connection.ConnectionProfile
import com.secondpasslibrary.reader.connection.CredentialStorageException
import com.secondpasslibrary.reader.connection.StoredCredential
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class KeystoreBearerCredentialStore
@Inject
constructor(@ApplicationContext context: Context) :
    BearerCredentialStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override suspend fun read(): StoredCredential? = withContext(Dispatchers.IO) {
        val encodedCiphertext = preferences.getString(CIPHERTEXT_KEY, null)
        val encodedIv = preferences.getString(IV_KEY, null)
        if (encodedCiphertext == null && encodedIv == null) return@withContext null
        if (encodedCiphertext == null || encodedIv == null) {
            throw CredentialStorageException("Stored credential is incomplete.")
        }
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                loadKey(),
                GCMParameterSpec(GCM_TAG_BITS, Base64.decode(encodedIv, Base64.NO_WRAP))
            )
            cipher.updateAAD(ASSOCIATED_DATA)
            val plaintext = cipher.doFinal(Base64.decode(encodedCiphertext, Base64.NO_WRAP))
            try {
                CredentialEnvelopeCodec.decode(plaintext)
            } finally {
                plaintext.fill(0)
            }
        } catch (failure: CredentialStorageException) {
            throw failure
        } catch (failure: GeneralSecurityException) {
            throw CredentialStorageException(
                "Stored credential could not be decrypted.",
                failure
            )
        } catch (failure: IOException) {
            throw CredentialStorageException(
                "Stored credential could not be decrypted.",
                failure
            )
        } catch (failure: IllegalArgumentException) {
            throw CredentialStorageException(
                "Stored credential could not be decrypted.",
                failure
            )
        } catch (failure: IllegalStateException) {
            throw CredentialStorageException(
                "Stored credential could not be decrypted.",
                failure
            )
        }
    }

    override suspend fun write(credential: BearerCredential, recoveryProfile: ConnectionProfile) {
        writeEnvelope(credential, recoveryProfile)
    }

    override suspend fun markProfileCommitted() {
        val stored =
            read()
                ?: throw CredentialStorageException("Stored credential disappeared before commit.")
        writeEnvelope(stored.credential, null)
    }

    @SuppressLint("UseKtx") // KTX edit(commit = true) discards the durability result.
    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            if (!preferences.edit().clear().commit()) {
                throw CredentialStorageException("Credential storage could not be cleared.")
            }
            runCatching {
                val keyStore = loadKeyStore()
                if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
            }.getOrElse {
                throw CredentialStorageException("Credential key could not be cleared.", it)
            }
        }
    }

    private suspend fun writeEnvelope(
        credential: BearerCredential,
        recoveryProfile: ConnectionProfile?
    ) = withContext(Dispatchers.IO) {
        val plaintext = CredentialEnvelopeCodec.encode(credential, recoveryProfile)
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            cipher.updateAAD(ASSOCIATED_DATA)
            val ciphertext = cipher.doFinal(plaintext)
            val committed =
                preferences
                    .edit()
                    .putString(CIPHERTEXT_KEY, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                    .putString(IV_KEY, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                    .commit()
            ciphertext.fill(0)
            if (!committed) {
                throw CredentialStorageException(
                    "Credential could not be stored durably."
                )
            }
        } catch (failure: CredentialStorageException) {
            throw failure
        } catch (failure: GeneralSecurityException) {
            throw CredentialStorageException(
                "Credential could not be encrypted and stored.",
                failure
            )
        } catch (failure: IOException) {
            throw CredentialStorageException(
                "Credential could not be encrypted and stored.",
                failure
            )
        } finally {
            plaintext.fill(0)
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = loadKeyStore()
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_SIZE_BITS)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            generateKey()
        }
    }

    private fun loadKey(): SecretKey = loadKeyStore().getKey(KEY_ALIAS, null) as? SecretKey
        ?: throw CredentialStorageException("Credential key is missing.")

    private fun loadKeyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "second_pass_reader_bearer_v1"
        const val PREFERENCES_NAME = "second_pass_secure_credential"
        const val CIPHERTEXT_KEY = "ciphertext"
        const val IV_KEY = "iv"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val KEY_SIZE_BITS = 256
        val ASSOCIATED_DATA = "com.secondpasslibrary.reader:credential:v1".toByteArray()
    }
}
