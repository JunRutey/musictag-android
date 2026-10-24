/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.core.net.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeapiTest {

    private val json = """{"s":"周杰伦","type":1,"limit":30,"offset":0}"""

    @Test
    fun encryptsBothLayersSoTheyCanBeUnwrapped() {
        val secret = "0123456789abcdef"
        val payload = Weapi.encrypt(json, secret)

        // Outer layer opens with the random secret, inner layer with the fixed key.
        val inner = Weapi.aesCbcDecryptBase64(payload.params, secret, Weapi.IV)
        val recovered = Weapi.aesCbcDecryptBase64(inner, Weapi.AES_KEY, Weapi.IV)
        assertEquals(json, recovered)
    }

    /**
     * Cross-checked against an independent implementation of the RSA step: the reversed
     * secret, left-padded to 128 bytes, raised to 0x10001 modulo the embedded modulus
     * (computed with Python's arbitrary-precision integers).
     */
    @Test
    fun encSecKeyMatchesIndependentComputation() {
        val expected =
            "35701388baf89fed412e11269b9c76625d095ecaf17f03fa018abe19ea2d38b949debf242ee39a71ca1f6cda71b1b86" +
                "a45aa909ee27f7e78e267d34e732f0de948206c3340a788d0003372183e2f753c1f78b66ac23d134ac1fc9b993156520" +
                "ea826b8aa89a962d4491b4b8d7e08738e1da9b07aa39bf4a7ef0b1c210728cd52"
        assertEquals(expected, Weapi.rsaEncryptSecret("0123456789abcdef"))
    }

    @Test
    fun encSecKeyIsAlways256HexCharacters() {
        val payload = Weapi.encrypt(json)
        assertEquals(256, payload.encSecKey.length)
        assertTrue(payload.encSecKey.all { it in "0123456789abcdef" })
    }

    @Test
    fun randomSecretIsSixteenCharactersFromTheAlphabet() {
        val secret = Weapi.randomSecret()
        assertEquals(16, secret.length)
        assertTrue(secret.all { it.isLetterOrDigit() })
        assertNotEquals(secret, Weapi.randomSecret())
    }

    @Test
    fun formBodyIsUrlEncoded() {
        val payload = Weapi.Payload(params = "a+b/c=", encSecKey = "ff00")
        assertEquals("params=a%2Bb%2Fc%3D&encSecKey=ff00", payload.asFormBody())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsWrongSecretLength() {
        Weapi.encrypt(json, secret = "short")
    }
}
