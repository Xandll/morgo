package io.qzz.xandll.morgo.domain.model

object AlarmKinds {
    const val MAIN = "main"
    const val FOLLOW_UP = "follow_up"
    const val EXTRA = "extra"
}

enum class AlarmCommand {
    STOP,
    SNOOZE
}

data class AlarmCommandEffect(
    val stopSound: Boolean,
    val confirmAwake: Boolean,
    val snooze: Boolean,
    val closeScreen: Boolean,
    val runWakeActions: Boolean
)

/**
 * Was der Wecker zeigt und welche Aktion Stopp oder Schlummern auslöst.
 * Die Benachrichtigung bleibt auf dem Telefon und geht an die Uhr mit.
 */
data class AlarmAlertSpec(
    val title: String,
    val body: String,
    val showSnooze: Boolean,
    val fullScreen: Boolean,
    val localOnly: Boolean = false,
    val ongoing: Boolean = true
) {
    val actions: List<String> = buildList {
        if (showSnooze) add(SNOOZE)
        add(STOP)
    }

    companion object {
        const val SNOOZE = "Schlummern"
        const val STOP = "Stopp"
    }
}

object AlarmFace {
    const val DEFAULT_TITLE = "Weckruf"

    fun title(kind: String, label: String, storedTitle: String): String {
        return when (kind) {
            AlarmKinds.FOLLOW_UP -> "Zweiter Wecker"
            AlarmKinds.EXTRA -> label.trim().ifBlank { "Extra-Wecker" }
            else -> storedTitle.trim().ifBlank { DEFAULT_TITLE }
        }
    }

    /** Schlummern nur, wenn eine Dauer gesetzt ist und der Wecker wirklich klingelt. */
    fun showSnooze(kind: String, snoozeMinutes: Int, withSound: Boolean): Boolean {
        if (snoozeMinutes <= 0 || !withSound) return false
        return kind == AlarmKinds.MAIN || kind == AlarmKinds.FOLLOW_UP || kind == AlarmKinds.EXTRA
    }

    /** Entsperrt und in Benutzung: nur die Benachrichtigung. Sonst das Vollbild. */
    fun deviceInUse(interactive: Boolean, keyguardLocked: Boolean): Boolean {
        return interactive && !keyguardLocked
    }

    fun alert(
        kind: String,
        label: String,
        storedTitle: String,
        snoozeMinutes: Int,
        withSound: Boolean,
        interactive: Boolean,
        keyguardLocked: Boolean,
        clock: String
    ): AlarmAlertSpec {
        val snooze = showSnooze(kind, snoozeMinutes, withSound)
        return AlarmAlertSpec(
            title = title(kind, label, storedTitle),
            body = clock,
            showSnooze = snooze,
            fullScreen = !deviceInUse(interactive, keyguardLocked),
            localOnly = false,
            ongoing = true
        )
    }

    fun effect(command: AlarmCommand): AlarmCommandEffect {
        return when (command) {
            AlarmCommand.STOP -> AlarmCommandEffect(
                stopSound = true,
                confirmAwake = true,
                snooze = false,
                closeScreen = true,
                runWakeActions = false
            )
            AlarmCommand.SNOOZE -> AlarmCommandEffect(
                stopSound = true,
                confirmAwake = false,
                snooze = true,
                closeScreen = true,
                runWakeActions = false
            )
        }
    }
}
