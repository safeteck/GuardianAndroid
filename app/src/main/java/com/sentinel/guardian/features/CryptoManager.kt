package com.sentinel.guardian.features

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AesGcmKeyManager
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import java.io.IOException
import java.security.GeneralSecurityException

class CryptoManager(context: Context) {

    private val keysetName = "master_keyset"
    private val preferenceFile = "master_key_preference"
    private val masterKeyUri = "android-keystore://master_key"
    private lateinit var keysetHandle: KeysetHandle
    private lateinit var aead: Aead

    init {
        try {
            AeadConfig.register()
            keysetHandle = AndroidKeysetManager.Builder()
                .withKeyTemplate(AesGcmKeyManager.aes256GcmTemplate())
                .withSharedPref(context, keysetName, preferenceFile)
                .withMasterKeyUri(masterKeyUri)
                .build()
                .keysetHandle
            aead = keysetHandle.getPrimitive(Aead::class.java)
        } catch (e: GeneralSecurityException) {
            throw RuntimeException(e)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    fun encrypt(plainText: String): ByteArray {
        return aead.encrypt(plainText.toByteArray(Charsets.UTF_8), null)
    }

    fun decrypt(cipherText: ByteArray): String {
        return String(aead.decrypt(cipherText, null), Charsets.UTF_8)
    }
}