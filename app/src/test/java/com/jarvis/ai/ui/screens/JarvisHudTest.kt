package com.jarvis.ai.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class JarvisHudTest {

    private fun phase(
        acting: Boolean = false,
        loading: Boolean = false,
        speaking: Boolean = false,
        listening: Boolean = false,
        handsFree: Boolean = false
    ) = hudPhase(acting, loading, speaking, listening, handsFree)

    @Test fun idleWhenNothingActive() = assertEquals(HudPhase.IDLE, phase())

    @Test fun actingOutranksEverything() =
        assertEquals(
            HudPhase.WORKING,
            phase(acting = true, loading = true, speaking = true, listening = true)
        )

    @Test fun loadingOutranksSpeakingAndListening() =
        assertEquals(HudPhase.THINKING, phase(loading = true, speaking = true, listening = true))

    @Test fun speakingOutranksListening() =
        assertEquals(HudPhase.SPEAKING, phase(speaking = true, listening = true))

    @Test fun handsFreeAloneReadsAsListening() =
        assertEquals(HudPhase.LISTENING, phase(handsFree = true))

    @Test fun statusLinesMatchPhases() {
        assertEquals("Working on it…", hudStatusLine(HudPhase.WORKING))
        assertEquals("Thinking…", hudStatusLine(HudPhase.THINKING))
        assertEquals("Speaking…", hudStatusLine(HudPhase.SPEAKING))
        assertEquals("Listening…", hudStatusLine(HudPhase.LISTENING))
        assertEquals("How can I assist you today?", hudStatusLine(HudPhase.IDLE))
    }
}
