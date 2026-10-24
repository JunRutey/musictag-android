/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.core.net.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** Hex helpers shared by the crypto tests. */
internal fun hex(value: String): ByteArray =
    value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

/**
 * The AES wiring is checked against NIST SP 800-38A (F.1.1 ECB, F.2.1 CBC) using the
 * no-padding variants, so the primitives are anchored to an external authority instead of
 * to this codebase's own output.
 */
class AesNistVectorsTest {

    private val key = hex("2b7e151628aed2a6abf7158809cf4f3c")
    private val iv = hex("000102030405060708090a0b0c0d0e0f")
    private val plaintext = hex(
        "6bc1bee22e409f96e93d7e117393172a" +
            "ae2d8a571e03ac9c9eb76fac45af8e51" +
            "30c81c46a35ce411e5fbc1191a0a52ef" +
            "f69f2445df4f9b17ad2b417be66c3710",
    )

    @Test
    fun cbcMatchesNistSp80038a() {
        val expected = hex(
            "7649abac8119b246cee98e9b12e9197d" +
                "5086cb9b507219ee95db113a917678b2" +
                "73bed6b8e3c1743b7116e69e22229516" +
                "3ff1caa1681fac09120eca307586e1a7",
        )
        val actual = Aes.cbcEncrypt(plaintext, key, iv, padding = false)
        assertArrayEquals(expected, actual)
        assertArrayEquals(plaintext, Aes.cbcDecrypt(actual, key, iv, padding = false))
    }

    @Test
    fun ecbMatchesNistSp80038a() {
        val expectedFirstBlock = hex("3ad77bb40d7a3660a89ecaf32466ef97")
        val actual = Aes.ecbEncrypt(plaintext.copyOfRange(0, 16), key, padding = false)
        assertArrayEquals(expectedFirstBlock, actual)
    }

    @Test
    fun paddedVariantsRoundTrip() {
        val message = "a message that is not block aligned".toByteArray()
        val encrypted = Aes.cbcEncrypt(message, key, iv)
        assertArrayEquals(message, Aes.cbcDecrypt(encrypted, key, iv))
        assertEquals(0, encrypted.size % 16)
    }
}
