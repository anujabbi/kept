package com.example.kept.core.ui.sprig

import com.example.kept.core.domain.SprigOutfit
import com.example.kept.core.domain.SprigPose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The frame rules ported from `SprigA.dc.html`, without a frame clock. */
class SprigMotionTest {

    private fun SprigMotion.run(seconds: Float, dt: Float = 1f / 60f) {
        var left = seconds
        while (left > 0f) { step(minOf(dt, left)); left -= dt }
    }

    private fun frame(m: SprigMotion, pose: SprigPose = SprigPose.IDLE, tear: Boolean = false) =
        SprigFrame.of(pose, 3, SprigOutfit.NONE, tear, m)

    @Test fun tapHopsWithJoyAndDecaysInAboutSixTenths() {
        val m = SprigMotion { 0.5f }
        m.press(1_000)
        assertEquals(1f, m.hop, 0f)
        m.run(0.1f)
        val f = frame(m)
        assertTrue("rises", f.bodyDy < 0f)
        assertTrue("joy eyes", f.eyeJoy)
        assertTrue("arms up", f.armL > 30f)
        m.run(0.55f)
        assertEquals(0f, m.hop, 0f)
    }

    @Test fun threeTapsWithin450msTickle() {
        val m = SprigMotion()
        m.press(0); m.release()
        m.press(300); m.release()
        assertEquals(0f, m.tick, 0f)
        m.press(600)
        assertEquals(1f, m.tick, 0f)
    }

    @Test fun slowTapsDoNotTickle() {
        val m = SprigMotion()
        m.press(0); m.press(500); m.press(1_000)
        assertEquals(0f, m.tick, 0f)
    }

    @Test fun dragOver20pxPetsAndStopsTheTear() {
        val m = SprigMotion()
        m.run(0.5f) // tear phase visible
        assertTrue(frame(m, SprigPose.DROOP, tear = true).tearAlpha > 0f)
        m.press(0)
        m.move(0.5f, 0.5f, 12f)
        assertFalse(m.petting)
        m.move(0.55f, 0.5f, 12f)
        assertTrue(m.petting)
        m.run(0.5f)
        val f = frame(m, SprigPose.DROOP, tear = true)
        assertTrue("joy eyes", f.eyeJoy)
        assertTrue("stronger blush", f.blush > 0.9f)
        assertEquals("no tear", 0f, f.tearAlpha, 0f)
        assertFalse("not sad", f.brows)
    }

    @Test fun twelveSecondsUntouchedIdleSleepsAndATouchWakes() {
        val m = SprigMotion()
        m.run(11.9f)
        assertFalse(frame(m).zzz)
        m.run(0.2f)
        assertTrue(frame(m).zzz)
        assertTrue(frame(m).eyeShut)
        // Only idle falls asleep.
        assertFalse(frame(m, SprigPose.CHEER).zzz)
        m.press(0)
        assertFalse(frame(m).zzz)
    }

    @Test fun blinksEvery2To5AndAHalfSecondsFor130ms() {
        val blinks = mutableListOf<Float>()
        for (r in listOf(0f, 1f)) {
            val m = SprigMotion { r }
            var t = 0f; var was = false; val starts = mutableListOf<Float>()
            while (t < 12f) { m.step(0.01f); t += 0.01f; if (m.blink > 0f && !was) starts += t; was = m.blink > 0f }
            blinks += starts.zipWithNext { a, b -> b - a }
            assertEquals("first blink at 2.5s", 2.5f, starts.first(), 0.02f)
        }
        assertEquals(2f, blinks.first(), 0.05f)
        assertEquals(5.5f, blinks.last(), 0.05f)
    }

    @Test fun droopWithTearShowsSadBrowsAndAFallingTear() {
        val m = SprigMotion()
        m.run(0.5f)
        val f = frame(m, SprigPose.DROOP, tear = true)
        assertTrue(f.brows); assertTrue(f.mouthFrown); assertTrue(f.tearAlpha > 0f)
        assertEquals(0f, frame(m, SprigPose.DROOP, tear = false).tearAlpha, 0f)
    }

    @Test fun reduceMotionFreezesSwayHopAndWobbleButStillBlinks() {
        val m = SprigMotion { 0f }.apply { still = true }
        val before = frame(m)
        m.press(0); m.press(100); m.press(200)
        m.run(3f)
        val after = frame(m)
        assertEquals(0f, m.t, 0f)
        assertEquals(0f, m.hop, 0f)
        assertEquals(0f, m.tick, 0f)
        assertEquals(before.armL, after.armL, 0f)
        assertEquals(before.bodyDy, after.bodyDy, 0f)
        assertEquals(before.lean, after.lean, 0f)
        assertTrue("blinked", m.nextBlink < 2.5f)
    }

    @Test fun posesMatchTheReference() {
        val m = SprigMotion()
        frame(m, SprigPose.CHEER).let { assertTrue(it.eyeJoy && it.mouthOpen); assertEquals(150f, it.armL, 0.01f); assertEquals(-150f, it.armR, 0.01f) }
        frame(m, SprigPose.GUARD).let { assertTrue(it.mouthFlat && it.eyeOpen); assertEquals(8f, it.armL, 0f); assertEquals(-138f, it.armR, 0.01f) }
        frame(m, SprigPose.WAVE).let { assertTrue(it.mouthSmile); assertEquals(-140f, it.armR, 0.01f) }
        frame(m, SprigPose.DROOP).let { assertTrue(it.mouthFrown && it.brows); assertEquals(32f, it.sprout, 0.01f); assertEquals(4f, it.bodyDy, 0.01f) }
        frame(m, SprigPose.SLEEP).let { assertTrue(it.eyeShut && it.zzz); assertEquals(18f, it.sprout, 0.01f) }
        frame(m, SprigPose.IDLE).let { assertTrue(it.eyeOpen && it.mouthSmile && !it.zzz) }
    }

    @Test fun followEasesTowardTheFinger() {
        val m = SprigMotion()
        m.move(1f, 0.55f, 0f)
        assertEquals(1f, m.tx, 0f)
        m.step(1f / 60f)
        assertEquals(0.15f, m.lx, 0.01f) // ease min(1, dt * 9)
        m.run(1f)
        assertEquals(1f, m.lx, 0.01f)
        assertTrue(frame(m).eyesDx > 3f)
        m.release()
        assertEquals(0f, m.tx, 0f)
    }
}
