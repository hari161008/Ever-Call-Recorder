// SPDX-License-Identifier: GPL-3.0-or-later
package com.coolappstore.evercallrecorder.by.svhp.core

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val ALIAS = "callrec.stt.v1"
private const val PROVIDER = "AndroidKeyStore"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val IV_BYTES = 12
private const val TAG_BITS = 128

object CryptoBox {

    private val lock = Any()

    @Volatile
    private var cachedKey: SecretKey? = null

    /**
     * Encrypt [plain] with an app-private AES-256/GCM key kept in the
     * Android Keystore. Returns base64(iv || ciphertext || tag).
     *
     * The key is non-extractable; reinstalling the app or restoring from a
     * different device wipes it (recordings are local anyway).
     */
    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val out = ByteArray(iv.size + body.size)
        System.arraycopy(iv, 0, out, 0, iv.size)
        System.arraycopy(body, 0, out, iv.size, body.size)
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    /**
     * Decrypt a payload previously produced by [encrypt]. If [payload] looks
     * like a legacy plaintext (decrypt throws), returns it unchanged so older
     * users keep working until the next save re-encrypts.
     */
    fun decryptOrPassthrough(payload: String): String {
        return try {
            val raw = Base64.decode(payload, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, raw, 0, IV_BYTES))
            String(cipher.doFinal(raw, IV_BYTES, raw.size - IV_BYTES), Charsets.UTF_8)
        } catch (t: Throwable) {
            // Legacy plaintext keys saved before encryption was introduced should
            // still work; any decrypt failure means we treat the payload as
            // already plain. Wide catch is intentional — Base64, Keystore,
            // BadPadding and AEAD-tag mismatches all surface here.
            payload
        }
    }

    private fun key(): SecretKey {
        cachedKey?.let { return it }
        synchronized(lock) {
            cachedKey?.let { return it }
            val ks = KeyStore.getInstance(PROVIDER).apply { load(null) }
            val existing = (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            val k = existing ?: generate()
            cachedKey = k
            return k
        }
    }

    private fun generate(): SecretKey {
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return gen.generateKey()
    }
}
