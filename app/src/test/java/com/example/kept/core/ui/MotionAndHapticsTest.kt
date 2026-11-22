package com.example.kept.core.ui

import android.view.HapticFeedbackConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionAndHapticsTest {

    @Test fun `habit tick confirms from API 30`() {
        assertEquals(HapticFeedbackConstants.CONFIRM, Haptics.tickConstant(30))
        assertEquals(HapticFeedbackConstants.CONFIRM, Haptics.tickConstant(36))
    }

    @Test fun `habit tick falls back to keyboard tap below API 30`() {
        assertEquals(HapticFeedbackConstants.KEYBOARD_TAP, Haptics.tickConstant(29))
        assertEquals(HapticFeedbackConstants.KEYBOARD_TAP, Haptics.tickConstant(26))
    }

    @Test fun `animator scale zero is reduce motion`() {
        assertTrue(Motion.isReduced(0f))
        assertFalse(Motion.isReduced(0.5f))
        assertFalse(Motion.isReduced(1f))
    }
}
