/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.core.net.crypto

import java.math.BigInteger
import java.security.MessageDigest

/**
 * NetEase Cloud Music's `weapi` request signing.
 *
 * Reproduced from the original application's native `f(String)`: the JSON body is
 * encrypted twice with AES-128-CBC (first with a fixed key, then with a random 16
 * character secret) and the secret itself is RSA-encrypted with the published 1024-bit
 * key whose modulus the original ships as a decimal string in `.rodata`.
 *
 * Constants verified against `libmain.so` of 1.2.5.2:
 *   AES key `0CoJUm6Qyw8W8jud`, IV `0102030405060708`, exponent 0x10001.
 *
 * The secret is injectable so tests can be deterministic; production callers use the
 * default random secret.
 */
object Weapi {

    const val AES_KEY = "0CoJUm6Qyw8W8jud"
    const val IV = "0102030405060708"

    /** Decimal modulus exactly as embedded in the original library. */
    const val MODULUS_DECIMAL =
        "1577947502671315022124768178003454981218727833333897474240115310253662775352625" +
            "3991370180629076647918947753359785498960680319425397866032994198078607243280642" +
            "7833685472618792592200595694346872951301770580765135349259590167490536138082469" +
            "680638514416594216629258349130257685001248172188325316586707301643237607"

    const val EXPONENT = 0x10001

    private const val SECRET_ALPHABET =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

    data class Payload(val params: String, val encSecKey: String) {
        /** The form body the original sends: `params=…&encSecKey=…` (URL encoded). */
        fun asFormBody(): String =
            "params=${urlEncode(params)}&encSecKey=${urlEncode(encSecKey)}"
    }

    /** Signs [json] and returns both halves of the request body. */
    fun encrypt(json: String, secret: String = randomSecret()): Payload {
        require(secret.length == 16) { "weapi secret must be 16 characters, got ${secret.length}" }
        val firstPass = aesCbcBase64(json, AES_KEY, IV)
        val params = aesCbcBase64(firstPass, secret, IV)
        return Payload(params = params, encSecKey = rsaEncryptSecret(secret))
    }

    /**
     * `encSecKey`: the secret is reversed, left-padded to 128 bytes and encrypted with
     * the RSA public key; the result is the lowercase hex of exactly 128 bytes.
     */
    fun rsaEncryptSecret(secret: String): String {
        val reversed = secret.reversed().toByteArray(Charsets.US_ASCII)
        val padded = ByteArray(128 - reversed.size) + reversed
        val message = BigInteger(1, padded)
        val modulus = BigInteger(MODULUS_DECIMAL)
        val cipher = message.modPow(BigInteger.valueOf(EXPONENT.toLong()), modulus)
        return cipher.toString(16).padStart(256, '0')
    }

    /** AES-128-CBC with PKCS#5 padding, returned as standard base64. */
    internal fun aesCbcBase64(text: String, key: String, iv: String): String =
        java.util.Base64.getEncoder().encodeToString(
            Aes.cbcEncrypt(text.toByteArray(Charsets.UTF_8), key.toByteArray(Charsets.UTF_8), iv.toByteArray(Charsets.UTF_8)),
        )

    /** AES-128-CBC decryption counterpart, used by tests and diagnostics. */
    internal fun aesCbcDecryptBase64(payload: String, key: String, iv: String): String =
        String(
            Aes.cbcDecrypt(
                java.util.Base64.getDecoder().decode(payload),
                key.toByteArray(Charsets.UTF_8),
                iv.toByteArray(Charsets.UTF_8),
            ),
            Charsets.UTF_8,
        )

    fun randomSecret(): String {
        val random = java.security.SecureRandom()
        return buildString(16) {
            repeat(16) { append(SECRET_ALPHABET[random.nextInt(SECRET_ALPHABET.length)]) }
        }
    }

    private fun urlEncode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")

    /** Small helper kept next to the constants so callers can log a request fingerprint. */
    fun fingerprint(payload: Payload): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("${payload.params}:${payload.encSecKey}".toByteArray(Charsets.UTF_8))
        return digest.take(6).joinToString("") { "%02x".format(it) }
    }
}
