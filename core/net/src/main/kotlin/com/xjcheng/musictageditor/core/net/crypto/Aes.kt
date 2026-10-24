/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.core.net.crypto

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Thin AES helpers.
 *
 * Kept separate from [Weapi]/[MusicKey] so the primitives can be checked against the
 * NIST SP 800-38A vectors (which use no padding) while the protocol code always uses
 * PKCS#5 padding.
 */
internal object Aes {

    private const val TRANSFORMATION_CBC = "AES/CBC/PKCS5Padding"
    private const val TRANSFORMATION_CBC_RAW = "AES/CBC/NoPadding"
    private const val TRANSFORMATION_ECB = "AES/ECB/PKCS5Padding"
    private const val TRANSFORMATION_ECB_RAW = "AES/ECB/NoPadding"

    fun cbcEncrypt(plain: ByteArray, key: ByteArray, iv: ByteArray, padding: Boolean = true): ByteArray =
        run(if (padding) TRANSFORMATION_CBC else TRANSFORMATION_CBC_RAW, Cipher.ENCRYPT_MODE, key, iv, plain)

    fun cbcDecrypt(cipher: ByteArray, key: ByteArray, iv: ByteArray, padding: Boolean = true): ByteArray =
        run(if (padding) TRANSFORMATION_CBC else TRANSFORMATION_CBC_RAW, Cipher.DECRYPT_MODE, key, iv, cipher)

    fun ecbEncrypt(plain: ByteArray, key: ByteArray, padding: Boolean = true): ByteArray =
        run(if (padding) TRANSFORMATION_ECB else TRANSFORMATION_ECB_RAW, Cipher.ENCRYPT_MODE, key, null, plain)

    fun ecbDecrypt(cipher: ByteArray, key: ByteArray, padding: Boolean = true): ByteArray =
        run(if (padding) TRANSFORMATION_ECB else TRANSFORMATION_ECB_RAW, Cipher.DECRYPT_MODE, key, null, cipher)

    private fun run(
        transformation: String,
        mode: Int,
        key: ByteArray,
        iv: ByteArray?,
        input: ByteArray,
    ): ByteArray {
        val cipher = Cipher.getInstance(transformation)
        val keySpec = SecretKeySpec(key, "AES")
        if (iv == null) {
            cipher.init(mode, keySpec)
        } else {
            cipher.init(mode, keySpec, IvParameterSpec(iv))
        }
        return cipher.doFinal(input)
    }
}
