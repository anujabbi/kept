package com.example.kept.core.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SprigLookTest {

    @Test fun eachFormHasTheSpecStageAndOutfit() {
        val expected = mapOf(
            SprigForm.SPRIG to (0 to SprigOutfit.NONE),
            SprigForm.BUD to (1 to SprigOutfit.SCARF),
            SprigForm.BLOOM to (3 to SprigOutfit.SWEATER),
            SprigForm.THICKET to (3 to SprigOutfit.BOTH),
            SprigForm.GROVE to (3 to SprigOutfit.CROWN),
            SprigForm.ANCIENT to (3 to SprigOutfit.SAGE),
            SprigForm.CELESTIAL to (3 to SprigOutfit.STAR),
        )
        assertEquals("every form is covered", SprigForm.entries.toSet(), expected.keys)
        expected.forEach { (form, look) ->
            assertEquals("$form stage", look.first, SprigLook.stage(form))
            assertEquals("$form outfit", look.second, SprigLook.outfit(form))
        }
    }

    @Test fun allDoneCheers() = assertEquals(SprigPose.CHEER, SprigLook.pose(allDone = true))

    @Test fun wiltedDroops() = assertEquals(SprigPose.DROOP, SprigLook.pose(wilted = true))

    @Test fun wiltedBeatsAllDone() = assertEquals(SprigPose.DROOP, SprigLook.pose(wilted = true, allDone = true))

    @Test fun lockScreenGuards() {
        assertEquals(SprigPose.GUARD, SprigLook.pose(onLockScreen = true))
        assertEquals(SprigPose.GUARD, SprigLook.pose(onLockScreen = true, wilted = true))
    }

    @Test fun otherwiseIdle() = assertEquals(SprigPose.IDLE, SprigLook.pose())

    @Test fun talkBackReadsFormAndMood() {
        assertEquals("Sprig, Bloom, cheering", SprigLook.contentDescription(SprigForm.BLOOM, SprigPose.CHEER))
        assertEquals("Sprig, Sprig, asleep", SprigLook.contentDescription(SprigForm.SPRIG, SprigPose.SLEEP))
        assertEquals("Sprig, Grove, wilting", SprigLook.contentDescription(SprigForm.GROVE, SprigPose.DROOP, wilted = true))
    }
}
