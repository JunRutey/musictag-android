/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.core.net.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicKeyTest {

    private val musicJson =
        """{"musicId":1234567,"musicName":"测试歌曲","artist":[["歌手",1]],"album":"专辑","albumPic":"https://p1.music.126.net/abc.jpg","bitrate":320000,"duration":261}"""

    @Test
    fun roundTripsThroughTheMarker() {
        val encoded = MusicKey.encrypt(musicJson)
        assertTrue(encoded.startsWith(MusicKey.PREFIX))
        assertEquals(musicJson, MusicKey.decrypt(encoded))
    }

    @Test
    fun producedValueDecryptsWithTheDocumentedKey() {
        // Independent check of the key bytes: decrypting with the raw AES-128-ECB key from
        // the original library must reproduce the JSON.
        val encoded = MusicKey.encrypt(musicJson).removePrefix(MusicKey.PREFIX)
        val key = "#14ljk_!\\]&0U<('".toByteArray(Charsets.US_ASCII)
        val plain = Aes.ecbDecrypt(java.util.Base64.getDecoder().decode(encoded), key)
        assertEquals(musicJson, String(plain, Charsets.UTF_8))
    }

    @Test
    fun rejectsForeignComments() {
        assertNull(MusicKey.decrypt("just a comment"))
        assertNull(MusicKey.decrypt(MusicKey.PREFIX + "not-base64!!"))
        assertFalse(MusicKey.isMusicKey("just a comment"))
        assertTrue(MusicKey.isMusicKey(MusicKey.encrypt("{}")))
    }

    @Test
    fun handlesEmptyPayload() {
        assertNull(MusicKey.decrypt(MusicKey.PREFIX))
    }
}
