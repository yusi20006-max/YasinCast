package com.yasin.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioLibraryRulesTest {
    @Test
    fun acceptsAudioMimeTypes() {
        assertTrue(AudioLibraryRules.isAudio("audio/mpeg", "recording.bin"))
        assertTrue(AudioLibraryRules.isAudio("audio/flac", "recording"))
    }

    @Test
    fun acceptsKnownAudioExtensionsWhenProviderOmitsMimeType() {
        listOf("episode.mp3", "show.M4A", "track.aac", "voice.ogg", "lossless.flac", "sample.wav")
            .forEach { assertTrue("$it should be recognized", AudioLibraryRules.isAudio(null, it)) }
    }

    @Test
    fun rejectsUnrelatedDocuments() {
        assertFalse(AudioLibraryRules.isAudio("application/pdf", "notes.pdf"))
        assertFalse(AudioLibraryRules.isAudio(null, "photo.jpg"))
        assertFalse(AudioLibraryRules.isAudio(null, "README"))
    }

    @Test
    fun formatsUnknownAndSmallSizes() {
        assertEquals("حجم نامشخص", AudioLibraryRules.formatSize(null))
        assertEquals("512 بایت", AudioLibraryRules.formatSize(512))
    }
}
