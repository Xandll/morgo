package io.qzz.xandll.morgo.domain

import io.qzz.xandll.morgo.domain.model.AlarmAlertSpec
import io.qzz.xandll.morgo.domain.model.AlarmCommand
import io.qzz.xandll.morgo.domain.model.AlarmFace
import io.qzz.xandll.morgo.domain.model.AlarmKinds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmFaceTest {

    @Test
    fun `titles stay short on the lock screen`() {
        assertEquals("Weckruf", AlarmFace.title(AlarmKinds.MAIN, "", ""))
        assertEquals("Plan B", AlarmFace.title(AlarmKinds.MAIN, "", "Plan B"))
        assertEquals("Zweiter Wecker", AlarmFace.title(AlarmKinds.FOLLOW_UP, "egal", "Plan B"))
        assertEquals("Turnzeug", AlarmFace.title(AlarmKinds.EXTRA, "Turnzeug", "Weckruf"))
        assertEquals("Extra-Wecker", AlarmFace.title(AlarmKinds.EXTRA, "  ", "Weckruf"))
    }

    @Test
    fun `snooze shows for a ringing alarm including music and hides when it is off`() {
        assertTrue(AlarmFace.showSnooze(AlarmKinds.MAIN, snoozeMinutes = 5, withSound = true))
        assertTrue(AlarmFace.showSnooze(AlarmKinds.FOLLOW_UP, snoozeMinutes = 5, withSound = true))
        assertTrue(AlarmFace.showSnooze(AlarmKinds.EXTRA, snoozeMinutes = 5, withSound = true))
        assertFalse(AlarmFace.showSnooze(AlarmKinds.MAIN, snoozeMinutes = 0, withSound = true))
        assertFalse(AlarmFace.showSnooze(AlarmKinds.EXTRA, snoozeMinutes = 5, withSound = false))
    }

    @Test
    fun `locked phone opens the full screen and an unlocked phone stays a heads-up`() {
        val locked = AlarmFace.alert(
            AlarmKinds.MAIN, "", "Weckruf", 5, true,
            interactive = false, keyguardLocked = true, clock = "06:15"
        )
        val inUse = AlarmFace.alert(
            AlarmKinds.MAIN, "", "Plan B", 5, true,
            interactive = true, keyguardLocked = false, clock = "06:15"
        )
        assertTrue(locked.fullScreen)
        assertFalse(inUse.fullScreen)
        assertEquals("Plan B", inUse.title)
        assertEquals(listOf("Schlummern", "Stopp"), inUse.actions)
        assertFalse(locked.localOnly)
        assertFalse(inUse.localOnly)
        assertTrue(locked.ongoing)
        assertEquals(AlarmAlertSpec.SNOOZE, locked.actions.first())
    }

    @Test
    fun `stop confirms awake and snooze only reschedules`() {
        val stop = AlarmFace.effect(AlarmCommand.STOP)
        val snooze = AlarmFace.effect(AlarmCommand.SNOOZE)
        assertTrue(stop.confirmAwake)
        assertTrue(stop.stopSound)
        assertTrue(stop.closeScreen)
        assertFalse(stop.snooze)
        assertFalse(stop.runWakeActions)
        assertFalse(snooze.confirmAwake)
        assertTrue(snooze.snooze)
        assertTrue(snooze.stopSound)
        assertTrue(snooze.closeScreen)
        assertFalse(snooze.runWakeActions)
    }

    @Test
    fun `device in use means unlocked and interactive`() {
        assertFalse(AlarmFace.deviceInUse(interactive = false, keyguardLocked = true))
        assertFalse(AlarmFace.deviceInUse(interactive = true, keyguardLocked = true))
        assertTrue(AlarmFace.deviceInUse(interactive = true, keyguardLocked = false))
    }
}
