package com.margelo.nitro.nitrotts

import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class TtsPlaybackStoreTest {
    private fun sample(index: Int) = "text$index" + "x".repeat(1795)
    private lateinit var engine: TextToSpeech
    private val spoken = mutableListOf<String>()
    private val ids = mutableListOf<String>()

    private fun set(name: String, value: Any?) {
        TtsPlaybackStore::class.java.getDeclaredField(name).apply {
            isAccessible = true
            set(TtsPlaybackStore, value)
        }
    }

    private fun listener(): UtteranceProgressListener =
        TtsPlaybackStore::class.java.getDeclaredField("progressListener").run {
            isAccessible = true
            get(TtsPlaybackStore) as UtteranceProgressListener
        }

    @Before fun setup() {
        engine = mock(TextToSpeech::class.java)
        `when`(engine.speak(anyString(), anyInt(), any(Bundle::class.java), anyString()))
            .thenAnswer {
                assertEquals(TextToSpeech.QUEUE_ADD, it.getArgument<Int>(1))
                spoken.add(it.getArgument(0))
                ids.add(it.getArgument(3))
                TextToSpeech.SUCCESS
            }
        set("engine", engine)
        set("isReady", true)
        set("boundEngineName", null)
        set("settings", TtsSettings(null, null, 1.0, 1.0))
        set("paragraphs", (0..5).map { TtsParagraph("p$it", sample(it)) })
        set("currentIndex", 0)
        set("state", TtsPlaybackState.PAUSED)
        set("hasAudioFocus", true)
        set("applicationContext", null)
        set("audioManager", mock(AudioManager::class.java))
        spoken.clear()
        ids.clear()
    }

    @Test fun preloadsBeforeFirstParagraphFinishes() {
        TtsPlaybackStore.play()
        assertEquals((0..2).map(::sample), spoken)
        assertEquals(0.0, TtsPlaybackStore.snapshot().progress!!.index, 0.0)
    }

    @Test fun advancesWithoutStoppingAndRefillsLookahead() {
        TtsPlaybackStore.play()
        clearInvocations(engine)
        listener().onDone(ids[0])
        verify(engine, never()).stop()
        assertEquals((0..3).map(::sample), spoken)
        assertEquals(1.0, TtsPlaybackStore.snapshot().progress!!.index, 0.0)
    }

    @Test fun pauseAndSeekIgnoreOldCallbacks() {
        TtsPlaybackStore.play()
        val old = ids.toList()
        TtsPlaybackStore.pause()
        old.forEach { listener().onDone(it) }
        assertEquals(TtsPlaybackState.PAUSED, TtsPlaybackStore.snapshot().state)
        set("hasAudioFocus", true)
        TtsPlaybackStore.seekTo(4)
        old.forEach { listener().onDone(it) }
        assertEquals(4.0, TtsPlaybackStore.snapshot().progress!!.index, 0.0)
        assertEquals((4..5).map(::sample), spoken.takeLast(2))
    }

    @Test fun settingsChangeRebuildsQueueAndIgnoresPreviousGeneration() {
        TtsPlaybackStore.play()
        val old = ids.toList()
        TtsPlaybackStore.updateSettings(TtsSettings(null, null, 1.5, 0.9))
        old.forEach { listener().onDone(it) }
        verify(engine).setSpeechRate(1.5f)
        verify(engine).setPitch(0.9f)
        assertEquals(0.0, TtsPlaybackStore.snapshot().progress!!.index, 0.0)
        assertEquals((0..2).map(::sample), spoken.takeLast(3))
    }

    @Test fun stopDiscardsEntireLookahead() {
        TtsPlaybackStore.play()
        val old = ids.toList()
        TtsPlaybackStore.stop()
        old.forEach { listener().onDone(it) }
        assertEquals(TtsPlaybackState.IDLE, TtsPlaybackStore.snapshot().state)
        assertNull(TtsPlaybackStore.snapshot().progress)
        assertEquals(3, spoken.size)
    }

    @Test fun joinsRealParagraphsAndRemovesLineBreaksBeforeSpeaking() {
        set("paragraphs", listOf(
            TtsParagraph("a", "First.\n\nSecond."),
            TtsParagraph("b", "Third.\r\n\r\nFourth."),
        ))
        TtsPlaybackStore.play()
        assertEquals(listOf("First. Second. Third. Fourth."), spoken)
        assertEquals(0.0, TtsPlaybackStore.snapshot().progress!!.index, 0.0)
        listener().onDone(ids.single())
        assertEquals(1.0, TtsPlaybackStore.snapshot().progress!!.index, 0.0)
        assertEquals(TtsPlaybackState.COMPLETED, TtsPlaybackStore.snapshot().state)
    }

    @Test fun rangeCallbacksRetainOriginalParagraphSeekAndResume() {
        set("paragraphs", listOf(TtsParagraph("a", "First."), TtsParagraph("b", "Second.")))
        TtsPlaybackStore.play()
        val old = ids.single()
        listener().onRangeStart(old, 7, 14, 0)
        assertEquals(1.0, TtsPlaybackStore.snapshot().progress!!.index, 0.0)
        TtsPlaybackStore.pause()
        set("hasAudioFocus", true)
        TtsPlaybackStore.play()
        assertEquals("Second.", spoken.last())
        listener().onRangeStart(old, 0, 6, 0)
        assertEquals(1.0, TtsPlaybackStore.snapshot().progress!!.index, 0.0)
    }

    @Test fun completesExactlyOnceAtEnd() {
        TtsPlaybackStore.play()
        for (i in 0..5) listener().onDone(ids[i])
        assertEquals(TtsPlaybackState.COMPLETED, TtsPlaybackStore.snapshot().state)
        assertEquals(6, spoken.size)
        listener().onDone(ids.last())
        assertEquals(6, spoken.size)
    }

    @Test fun queuedFailureStopsAndInvalidatesRemainingCallbacks() {
        TtsPlaybackStore.play()
        listener().onError(ids[2], TextToSpeech.ERROR_SYNTHESIS)
        assertEquals(TtsPlaybackState.ERROR, TtsPlaybackStore.snapshot().state)
        listener().onDone(ids[0])
        assertEquals(0.0, TtsPlaybackStore.snapshot().progress!!.index, 0.0)
        verify(engine, atLeastOnce()).stop()
    }
}
