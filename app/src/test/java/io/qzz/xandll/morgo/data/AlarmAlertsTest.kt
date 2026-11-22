package io.qzz.xandll.morgo.data

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import io.qzz.xandll.morgo.MorgoApplication
import io.qzz.xandll.morgo.data.service.AlarmAlerts
import io.qzz.xandll.morgo.data.service.AlarmLaunch
import io.qzz.xandll.morgo.data.service.FullScreenIntents
import io.qzz.xandll.morgo.domain.model.AlarmFace
import io.qzz.xandll.morgo.domain.model.AlarmKinds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class AlarmAlertsTest {

    @Test
    fun `foreground test opens the activity and a background alarm keeps the full screen intent`() {
        assertTrue(AlarmLaunch.openActivityDirectly(appInForeground = true))
        assertFalse(AlarmLaunch.openActivityDirectly(appInForeground = false))
        val context = RuntimeEnvironment.getApplication()
        val intent = AlarmLaunch.testIntent(context)
        assertEquals(true, intent.getBooleanExtra(AlarmLaunch.EXTRA_TEST, false))
        assertEquals("main", intent.getStringExtra("alarm_kind"))
    }

    @Test
    fun `full screen permission is required only from android 14 when it is denied`() {
        assertFalse(FullScreenIntents.needsPermission(33, canUseFullScreenIntent = false))
        assertFalse(FullScreenIntents.needsPermission(34, canUseFullScreenIntent = true))
        assertTrue(FullScreenIntents.needsPermission(34, canUseFullScreenIntent = false))
        val context = RuntimeEnvironment.getApplication()
        val settings = FullScreenIntents.settingsIntent(context)
        assertEquals(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, settings.action)
        assertEquals("package:${context.packageName}", settings.data.toString())
    }

    @Test
    fun `phone notification stays local and the watch copy is a normal alarm`() {
        val context = RuntimeEnvironment.getApplication()
        val locked = AlarmFace.alert(
            AlarmKinds.MAIN, "", "Weckruf", 5, true,
            interactive = false, keyguardLocked = true, clock = "06:15"
        )
        val notes = AlarmAlerts.notifications(context, locked, AlarmKinds.MAIN, "", 5, test = false)
        assertTrue(notes.phone.flags and Notification.FLAG_LOCAL_ONLY != 0)
        assertTrue(notes.phone.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertNotNull(notes.phone.fullScreenIntent)
        assertEquals(MorgoApplication.CHANNEL_ALARM, notes.phone.channelId)
        assertEquals(0, notes.wear.flags and Notification.FLAG_LOCAL_ONLY)
        assertEquals(0, notes.wear.flags and Notification.FLAG_ONGOING_EVENT)
        assertEquals(Notification.CATEGORY_ALARM, notes.wear.category)
        assertEquals(MorgoApplication.CHANNEL_ALARM_WEAR, notes.wear.channelId)
        assertNull(notes.wear.fullScreenIntent)
        assertEquals(
            listOf("Schlummern", "Stopp"),
            NotificationCompat.WearableExtender(notes.wear).actions.map { it.title.toString() }
        )

        val testNotes = AlarmAlerts.notifications(
            context,
            locked.copy(fullScreen = false, showSnooze = false, title = "Musik testen"),
            AlarmKinds.MAIN,
            "",
            5,
            test = true
        )
        assertNull(testNotes.phone.fullScreenIntent)
        assertTrue(testNotes.phone.flags and Notification.FLAG_LOCAL_ONLY != 0)
        assertEquals(0, testNotes.wear.flags and Notification.FLAG_ONGOING_EVENT)
        assertEquals(
            listOf("Stoppen"),
            NotificationCompat.WearableExtender(testNotes.wear).actions.map { it.title.toString() }
        )

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(AlarmAlerts.WEAR_NOTIFICATION_ID, notes.wear)
        manager.notify(AlarmAlerts.NOTIFICATION_ID, notes.phone)
        assertEquals(2, manager.activeNotifications.size)
        AlarmAlerts.cancelWear(context)
        assertEquals(1, manager.activeNotifications.size)
        assertEquals(AlarmAlerts.NOTIFICATION_ID, manager.activeNotifications.single().id)
    }
}
