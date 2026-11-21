package io.qzz.xandll.morgo.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.qzz.xandll.morgo.data.datasource.RadioBrowser
import io.qzz.xandll.morgo.domain.model.*
import io.qzz.xandll.morgo.domain.model.AlarmFace
import io.qzz.xandll.morgo.domain.update.UpdateOffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.LocalTime

/**
 * Einstellungen. Zugangsdaten liegen verschlüsselt.
 * Schlägt die Entschlüsselung fehl, wird die Datei nicht gelöscht.
 */
class SettingsRepository private constructor(
    private val regularPrefs: SharedPreferences,
    private val encryptedPrefs: SharedPreferences,
    secureOpenError: String?
) {
    constructor(context: Context) : this(open(context))

    private constructor(loaded: LoadedPrefs) : this(
        loaded.regular,
        loaded.encrypted,
        loaded.error
    )

    private val lessonCache = LessonCache(regularPrefs)
    private val _secureStorageBroken = MutableStateFlow(secureOpenError != null)
    val secureStorageBroken: StateFlow<Boolean> = _secureStorageBroken.asStateFlow()
    val secureStorageMessage: String? = secureOpenError

    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<AppSettings> = _settingsFlow.asStateFlow()

    private val _updateOffer = MutableStateFlow(readUpdateOffer())
    val updateOffer: StateFlow<UpdateOffer?> = _updateOffer.asStateFlow()

    fun loadUpdateOffer(): UpdateOffer? = _updateOffer.value

    fun saveUpdateOffer(offer: UpdateOffer?) {
        regularPrefs.edit().apply {
            if (offer == null) {
                remove(KEY_OFFER_TAG)
                remove(KEY_OFFER_NOTES)
                remove(KEY_OFFER_APK)
                remove(KEY_OFFER_SHA)
                remove(KEY_OFFER_DISMISSED)
            } else {
                putString(KEY_OFFER_TAG, offer.tag)
                putString(KEY_OFFER_NOTES, offer.notes)
                putString(KEY_OFFER_APK, offer.apkUrl)
                putString(KEY_OFFER_SHA, offer.sha256Url.orEmpty())
                putBoolean(KEY_OFFER_DISMISSED, offer.dismissed)
            }
            apply()
        }
        _updateOffer.value = offer
    }

    fun dismissUpdateOffer() {
        val current = _updateOffer.value ?: return
        saveUpdateOffer(current.copy(dismissed = true))
    }

    fun lastNotifiedUpdateTag(): String = regularPrefs.getString(KEY_UPDATE_NOTIFIED, "") ?: ""

    fun rememberNotifiedUpdate(tag: String) {
        regularPrefs.edit().putString(KEY_UPDATE_NOTIFIED, tag).apply()
    }

    fun rememberUpdateDownload(id: Long) {
        regularPrefs.edit().putLong(KEY_UPDATE_DOWNLOAD, id).apply()
    }

    fun updateDownloadId(): Long = regularPrefs.getLong(KEY_UPDATE_DOWNLOAD, -1L)

    private fun readUpdateOffer(): UpdateOffer? {
        val tag = regularPrefs.getString(KEY_OFFER_TAG, "") ?: ""
        val apk = regularPrefs.getString(KEY_OFFER_APK, "") ?: ""
        if (tag.isBlank() || apk.isBlank()) return null
        val sha = regularPrefs.getString(KEY_OFFER_SHA, "")?.takeIf { it.isNotBlank() }
        return UpdateOffer(
            tag = tag,
            notes = regularPrefs.getString(KEY_OFFER_NOTES, "") ?: "",
            apkUrl = apk,
            sha256Url = sha,
            dismissed = regularPrefs.getBoolean(KEY_OFFER_DISMISSED, false)
        )
    }

    fun loadSettings(): AppSettings {
        val overrideDate = encString(KEY_OVERRIDE_DATE).takeIf { it.isNotBlank() }
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val today = LocalDate.now()
        val override = if (overrideDate != null && !overrideDate.isBefore(today)) {
            val mode = runCatching { DayOverrideMode.valueOf(encString(KEY_OVERRIDE_MODE)) }.getOrNull()
            if (mode == null) null else DayOverride(
                date = overrideDate,
                mode = mode,
                wakeTime = timeOrNull(encString(KEY_OVERRIDE_WAKE))
            )
        } else {
            null
        }
        return AppSettings(
            webUntis = WebUntisSettings(
                server = encString(KEY_WEBUNTIS_SERVER).ifBlank {
                    regularPrefs.getString(KEY_WEBUNTIS_SERVER, "") ?: ""
                },
                schoolName = encString(KEY_WEBUNTIS_SCHOOL).ifBlank {
                    regularPrefs.getString(KEY_WEBUNTIS_SCHOOL, "") ?: ""
                },
                username = encString(KEY_WEBUNTIS_USERNAME),
                password = encString(KEY_WEBUNTIS_PASSWORD),
                useSecret = encBool(KEY_WEBUNTIS_USE_SECRET),
                secret = encString(KEY_WEBUNTIS_SECRET)
            ),
            train = TrainSettings(
                startStation = loadStation(KEY_TRAIN_START_STATION_ID, KEY_TRAIN_START_STATION_NAME),
                destinationStation = loadStation(KEY_TRAIN_DEST_STATION_ID, KEY_TRAIN_DEST_STATION_NAME),
                walkingTimeMinutes = regularPrefs.getInt(KEY_TRAIN_WALKING_TIME, 10),
                preparationTimeMinutes = regularPrefs.getInt(KEY_TRAIN_PREP_TIME, 30)
            ),
            homeAssistant = HomeAssistantSettings(
                url = regularPrefs.getString(KEY_HA_URL, "") ?: "",
                token = encString(KEY_HA_TOKEN),
                wakeUpActions = regularPrefs.getStringSet(KEY_HA_WAKE_ACTIONS, emptySet())?.toList() ?: emptyList(),
                preWake = loadPreWake()
            ),
            alarm = AlarmSettings(
                enabled = regularPrefs.getBoolean(KEY_ALARM_ENABLED, true),
                defaultWakeTime = LocalTime.of(
                    regularPrefs.getInt(KEY_ALARM_DEFAULT_HOUR, 7),
                    regularPrefs.getInt(KEY_ALARM_DEFAULT_MINUTE, 0)
                ),
                snoozeMinutes = regularPrefs.getInt(KEY_ALARM_SNOOZE_MINUTES, 5),
                wakeTimeRules = LessonGrid.decode(regularPrefs.getString(KEY_WAKE_RULES, "") ?: ""),
                planBEnabled = regularPrefs.getBoolean(KEY_PLAN_B, true),
                maxEarlierMinutes = regularPrefs.getInt(KEY_MAX_EARLIER, 30),
                earliestWakeTime = LocalTime.of(
                    regularPrefs.getInt(KEY_EARLIEST_HOUR, 5),
                    regularPrefs.getInt(KEY_EARLIEST_MINUTE, 0)
                ),
                eveningCheckEnabled = regularPrefs.getBoolean(KEY_EVENING_ENABLED, true),
                eveningCheckTime = LocalTime.of(
                    regularPrefs.getInt(KEY_EVENING_HOUR, 20),
                    regularPrefs.getInt(KEY_EVENING_MINUTE, 0)
                ),
                followUpEnabled = regularPrefs.getBoolean(KEY_FOLLOW_ENABLED, false),
                followUpMinutes = regularPrefs.getInt(KEY_FOLLOW_MINUTES, 5),
                followUpEvenIfAwake = regularPrefs.getBoolean(KEY_FOLLOW_AWAKE, false),
                wakeSound = runCatching {
                    WakeSound.valueOf(regularPrefs.getString(KEY_WAKE_SOUND, WakeSound.RINGTONE.name) ?: "")
                }.getOrDefault(WakeSound.RINGTONE),
                music = loadMusic(),
                extraAlarms = ExtraAlarmCodec.decode(regularPrefs.getString(KEY_EXTRA_ALARMS, "") ?: ""),
                dayOverride = override
            ),
            useDynamicColor = regularPrefs.getBoolean(KEY_DYNAMIC_COLOR, false),
            onboardingComplete = regularPrefs.getBoolean(KEY_ONBOARDING, false),
            update = UpdateSettings(
                enabled = regularPrefs.getBoolean(KEY_UPDATE_ENABLED, true),
                owner = regularPrefs.getString(KEY_UPDATE_OWNER, "Xandll")?.ifBlank { "Xandll" } ?: "Xandll",
                repo = regularPrefs.getString(KEY_UPDATE_REPO, "morgo")?.ifBlank { "morgo" } ?: "morgo"
            )
        )
    }

    fun saveSettings(settings: AppSettings) {
        if (!_secureStorageBroken.value) {
            try {
                encryptedPrefs.edit().apply {
                    putString(KEY_WEBUNTIS_SERVER, settings.webUntis.server)
                    putString(KEY_WEBUNTIS_SCHOOL, settings.webUntis.schoolName)
                    putString(KEY_WEBUNTIS_USERNAME, settings.webUntis.username)
                    putString(KEY_WEBUNTIS_PASSWORD, settings.webUntis.password)
                    putBoolean(KEY_WEBUNTIS_USE_SECRET, settings.webUntis.useSecret)
                    putString(KEY_WEBUNTIS_SECRET, settings.webUntis.secret)
                    putString(KEY_HA_TOKEN, settings.homeAssistant.token)
                    val override = settings.alarm.dayOverride
                    if (override != null) {
                        putString(KEY_OVERRIDE_DATE, override.date.toString())
                        putString(KEY_OVERRIDE_MODE, override.mode.name)
                        putString(KEY_OVERRIDE_WAKE, override.wakeTime?.toString().orEmpty())
                    } else {
                        remove(KEY_OVERRIDE_DATE)
                        remove(KEY_OVERRIDE_MODE)
                        remove(KEY_OVERRIDE_WAKE)
                    }
                    apply()
                }
            } catch (e: Exception) {
                _secureStorageBroken.value = true
            }
        }

        regularPrefs.edit().apply {
            putString(KEY_WEBUNTIS_SERVER, settings.webUntis.server)
            putString(KEY_WEBUNTIS_SCHOOL, settings.webUntis.schoolName)
            settings.train.startStation?.let {
                putString(KEY_TRAIN_START_STATION_ID, it.id)
                putString(KEY_TRAIN_START_STATION_NAME, it.name)
            } ?: run {
                remove(KEY_TRAIN_START_STATION_ID)
                remove(KEY_TRAIN_START_STATION_NAME)
            }
            settings.train.destinationStation?.let {
                putString(KEY_TRAIN_DEST_STATION_ID, it.id)
                putString(KEY_TRAIN_DEST_STATION_NAME, it.name)
            } ?: run {
                remove(KEY_TRAIN_DEST_STATION_ID)
                remove(KEY_TRAIN_DEST_STATION_NAME)
            }
            putInt(KEY_TRAIN_WALKING_TIME, settings.train.walkingTimeMinutes)
            putInt(KEY_TRAIN_PREP_TIME, settings.train.preparationTimeMinutes)
            putString(KEY_HA_URL, settings.homeAssistant.url)
            putStringSet(KEY_HA_WAKE_ACTIONS, settings.homeAssistant.wakeUpActions.toSet())
            val preWake = settings.homeAssistant.preWake
            putBoolean(KEY_PREWAKE_ENABLED, preWake.enabled)
            putString(KEY_PREWAKE_ENTITIES, preWake.entityIds.joinToString("\n"))
            putInt(KEY_PREWAKE_LEAD, preWake.leadMinutes)
            putInt(KEY_PREWAKE_TOGGLES, preWake.toggleCount)
            putInt(KEY_PREWAKE_INTERVAL, preWake.intervalAmount)
            putString(KEY_PREWAKE_UNIT, preWake.intervalUnit.name)
            putInt(KEY_PREWAKE_BRIGHTNESS, preWake.brightness ?: -1)
            putBoolean(KEY_LEAVE_ENABLED, preWake.leaveOffEnabled)
            putInt(KEY_LEAVE_OFFSET, preWake.leaveOffOffsetMinutes)
            putString(KEY_LEAVE_CHOICES, LeaveLightPlan.encode(preWake.leaveOff))
            putBoolean(KEY_ALARM_ENABLED, settings.alarm.enabled)
            putInt(KEY_ALARM_DEFAULT_HOUR, settings.alarm.defaultWakeTime.hour)
            putInt(KEY_ALARM_DEFAULT_MINUTE, settings.alarm.defaultWakeTime.minute)
            putInt(KEY_ALARM_SNOOZE_MINUTES, settings.alarm.snoozeMinutes)
            putString(KEY_WAKE_RULES, LessonGrid.encode(settings.alarm.wakeTimeRules))
            putBoolean(KEY_PLAN_B, settings.alarm.planBEnabled)
            putInt(KEY_MAX_EARLIER, settings.alarm.maxEarlierMinutes)
            putInt(KEY_EARLIEST_HOUR, settings.alarm.earliestWakeTime.hour)
            putInt(KEY_EARLIEST_MINUTE, settings.alarm.earliestWakeTime.minute)
            putBoolean(KEY_EVENING_ENABLED, settings.alarm.eveningCheckEnabled)
            putInt(KEY_EVENING_HOUR, settings.alarm.eveningCheckTime.hour)
            putInt(KEY_EVENING_MINUTE, settings.alarm.eveningCheckTime.minute)
            putBoolean(KEY_FOLLOW_ENABLED, settings.alarm.followUpEnabled)
            putInt(KEY_FOLLOW_MINUTES, settings.alarm.followUpMinutes)
            putBoolean(KEY_FOLLOW_AWAKE, settings.alarm.followUpEvenIfAwake)
            putString(KEY_WAKE_SOUND, settings.alarm.wakeSound.name)
            val music = settings.alarm.music
            putString(KEY_MUSIC_KIND, music.kind.name)
            putString(KEY_MUSIC_SPOTIFY, music.spotifyInput)
            putString(KEY_MUSIC_STREAM, music.streamUrl)
            putString(KEY_MUSIC_STREAM_TITLE, music.streamTitle)
            putString(KEY_MUSIC_PACKAGE, music.appPackage)
            putString(KEY_MUSIC_APP, music.appLabel)
            putString(KEY_MUSIC_QUERY, music.searchQuery)
            putInt(KEY_MUSIC_RAMP, music.rampSeconds)
            putInt(KEY_MUSIC_FALLBACK, music.fallbackSeconds)
            putInt(KEY_MUSIC_VOLUME, music.volumePercent)
            putBoolean(KEY_MUSIC_SHUFFLE, music.shuffle)
            putString(KEY_RADIO_HOST, music.radioBrowserHost)
            putString(KEY_EXTRA_ALARMS, ExtraAlarmCodec.encode(settings.alarm.extraAlarms))
            putBoolean(KEY_DYNAMIC_COLOR, settings.useDynamicColor)
            putBoolean(KEY_ONBOARDING, settings.onboardingComplete)
            putBoolean(KEY_UPDATE_ENABLED, settings.update.enabled)
            putString(KEY_UPDATE_OWNER, settings.update.owner.trim())
            putString(KEY_UPDATE_REPO, settings.update.repo.trim())
            apply()
        }
        _settingsFlow.value = settings
    }

    fun alarmTitle(): String {
        return regularPrefs.getString(KEY_ALARM_TITLE, AlarmFace.DEFAULT_TITLE)?.trim()
            .orEmpty()
            .ifBlank { AlarmFace.DEFAULT_TITLE }
    }

    fun setAlarmTitle(title: String) {
        val next = title.trim().ifBlank { AlarmFace.DEFAULT_TITLE }
        regularPrefs.edit().putString(KEY_ALARM_TITLE, next).apply()
    }

    fun saveLessonCache(date: LocalDate, lessons: List<Lesson>) = lessonCache.save(date, lessons)

    fun readLessonCache(date: LocalDate): List<Lesson>? = lessonCache.read(date)

    private fun loadMusic(): MusicAlarmSettings {
        val kind = runCatching {
            MusicKind.valueOf(regularPrefs.getString(KEY_MUSIC_KIND, MusicKind.STREAM.name) ?: "")
        }.getOrDefault(MusicKind.STREAM)
        return MusicAlarmSettings(
            kind = kind,
            spotifyInput = regularPrefs.getString(KEY_MUSIC_SPOTIFY, "") ?: "",
            streamUrl = regularPrefs.getString(KEY_MUSIC_STREAM, "") ?: "",
            streamTitle = regularPrefs.getString(KEY_MUSIC_STREAM_TITLE, "") ?: "",
            appPackage = regularPrefs.getString(KEY_MUSIC_PACKAGE, "") ?: "",
            appLabel = regularPrefs.getString(KEY_MUSIC_APP, "") ?: "",
            searchQuery = regularPrefs.getString(KEY_MUSIC_QUERY, "") ?: "",
            rampSeconds = regularPrefs.getInt(KEY_MUSIC_RAMP, 30),
            fallbackSeconds = regularPrefs.getInt(KEY_MUSIC_FALLBACK, 20),
            volumePercent = regularPrefs.getInt(KEY_MUSIC_VOLUME, 70),
            radioBrowserHost = migratedRadioHost(),
            shuffle = regularPrefs.getBoolean(KEY_MUSIC_SHUFFLE, true)
        )
    }

    private fun migratedRadioHost(): String {
        val stored = regularPrefs.getString(KEY_RADIO_HOST, null)
        val host = RadioBrowser.migrateHost(stored)
        if (!stored.isNullOrBlank() && stored != host) {
            regularPrefs.edit().putString(KEY_RADIO_HOST, host).apply()
        }
        return host
    }

    private fun loadPreWake(): PreWakeLightSettings {
        val unitName = regularPrefs.getString(KEY_PREWAKE_UNIT, LightIntervalUnit.SECONDS.name)
        val unit = runCatching { LightIntervalUnit.valueOf(unitName ?: "") }
            .getOrDefault(LightIntervalUnit.SECONDS)
        val brightness = regularPrefs.getInt(KEY_PREWAKE_BRIGHTNESS, -1)
        return PreWakeLightSettings(
            enabled = regularPrefs.getBoolean(KEY_PREWAKE_ENABLED, false),
            entityIds = (regularPrefs.getString(KEY_PREWAKE_ENTITIES, "") ?: "")
                .split('\n')
                .filter { it.isNotBlank() },
            leadMinutes = regularPrefs.getInt(KEY_PREWAKE_LEAD, 10),
            toggleCount = regularPrefs.getInt(KEY_PREWAKE_TOGGLES, 3),
            intervalAmount = regularPrefs.getInt(KEY_PREWAKE_INTERVAL, 15),
            intervalUnit = unit,
            brightness = brightness.takeIf { it in 1..255 },
            leaveOffEnabled = regularPrefs.getBoolean(KEY_LEAVE_ENABLED, false),
            leaveOffOffsetMinutes = regularPrefs.getInt(KEY_LEAVE_OFFSET, 0),
            leaveOff = LeaveLightPlan.decode(
                regularPrefs.getString(KEY_LEAVE_CHOICES, null)
            )
        )
    }

    private fun loadStation(idKey: String, nameKey: String): Station? {
        val id = regularPrefs.getString(idKey, null)
        val name = regularPrefs.getString(nameKey, null)
        return if (id != null && name != null) Station(id = id, name = name) else null
    }

    private fun encString(key: String): String {
        return try {
            encryptedPrefs.getString(key, "") ?: ""
        } catch (e: Exception) {
            _secureStorageBroken.value = true
            ""
        }
    }

    private fun encBool(key: String): Boolean {
        return try {
            encryptedPrefs.getBoolean(key, false)
        } catch (e: Exception) {
            _secureStorageBroken.value = true
            false
        }
    }

    private fun timeOrNull(raw: String): LocalTime? {
        if (raw.isBlank()) return null
        return runCatching { LocalTime.parse(raw) }.getOrNull()
    }

    private class LoadedPrefs(
        val regular: SharedPreferences,
        val encrypted: SharedPreferences,
        val error: String?
    )

    companion object {
        internal fun forPrefs(
            regular: SharedPreferences,
            encrypted: SharedPreferences,
            secureOpenError: String? = null
        ) = SettingsRepository(regular, encrypted, secureOpenError)

        private fun open(context: Context): LoadedPrefs {
            val regular = context.getSharedPreferences("morgo_prefs", Context.MODE_PRIVATE)
            return try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                val encrypted = EncryptedSharedPreferences.create(
                    context,
                    "morgo_secure_prefs",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
                LoadedPrefs(regular, encrypted, null)
            } catch (e: Exception) {
                LoadedPrefs(
                    regular,
                    ThrowingPrefs(e),
                    "Zugangsdaten konnten nicht gelesen werden. Die gespeicherten Daten wurden nicht gelöscht."
                )
            }
        }

        private const val KEY_WEBUNTIS_SERVER = "webuntis_server"
        private const val KEY_WEBUNTIS_SCHOOL = "webuntis_school"
        private const val KEY_WEBUNTIS_USERNAME = "webuntis_username"
        private const val KEY_WEBUNTIS_PASSWORD = "webuntis_password"
        private const val KEY_WEBUNTIS_USE_SECRET = "webuntis_use_secret"
        private const val KEY_WEBUNTIS_SECRET = "webuntis_secret"
        private const val KEY_TRAIN_START_STATION_ID = "train_start_station_id"
        private const val KEY_TRAIN_START_STATION_NAME = "train_start_station_name"
        private const val KEY_TRAIN_DEST_STATION_ID = "train_dest_station_id"
        private const val KEY_TRAIN_DEST_STATION_NAME = "train_dest_station_name"
        private const val KEY_TRAIN_WALKING_TIME = "train_walking_time"
        private const val KEY_TRAIN_PREP_TIME = "train_prep_time"
        private const val KEY_HA_URL = "ha_url"
        private const val KEY_HA_TOKEN = "ha_token"
        private const val KEY_HA_WAKE_ACTIONS = "ha_wake_actions"
        private const val KEY_PREWAKE_ENABLED = "prewake_enabled"
        private const val KEY_PREWAKE_ENTITIES = "prewake_entities"
        private const val KEY_PREWAKE_LEAD = "prewake_lead"
        private const val KEY_PREWAKE_TOGGLES = "prewake_toggles"
        private const val KEY_PREWAKE_INTERVAL = "prewake_interval"
        private const val KEY_PREWAKE_UNIT = "prewake_unit"
        private const val KEY_PREWAKE_BRIGHTNESS = "prewake_brightness"
        private const val KEY_LEAVE_ENABLED = "leave_light_enabled"
        private const val KEY_LEAVE_OFFSET = "leave_light_offset"
        private const val KEY_LEAVE_CHOICES = "leave_light_choices"
        private const val KEY_ALARM_TITLE = "alarm_face_title"
        private const val KEY_ALARM_ENABLED = "alarm_enabled"
        private const val KEY_ALARM_DEFAULT_HOUR = "alarm_default_hour"
        private const val KEY_ALARM_DEFAULT_MINUTE = "alarm_default_minute"
        private const val KEY_ALARM_SNOOZE_MINUTES = "alarm_snooze_minutes"
        private const val KEY_WAKE_RULES = "alarm_wake_rules"
        private const val KEY_PLAN_B = "alarm_plan_b"
        private const val KEY_MAX_EARLIER = "alarm_max_earlier"
        private const val KEY_EARLIEST_HOUR = "alarm_earliest_hour"
        private const val KEY_EARLIEST_MINUTE = "alarm_earliest_minute"
        private const val KEY_EVENING_ENABLED = "evening_check_enabled"
        private const val KEY_EVENING_HOUR = "evening_check_hour"
        private const val KEY_EVENING_MINUTE = "evening_check_minute"
        private const val KEY_FOLLOW_ENABLED = "follow_up_enabled"
        private const val KEY_FOLLOW_MINUTES = "follow_up_minutes"
        private const val KEY_FOLLOW_AWAKE = "follow_up_even_awake"
        private const val KEY_WAKE_SOUND = "wake_sound"
        private const val KEY_MUSIC_KIND = "music_kind"
        private const val KEY_MUSIC_SPOTIFY = "music_spotify"
        private const val KEY_MUSIC_STREAM = "music_stream"
        private const val KEY_MUSIC_STREAM_TITLE = "music_stream_title"
        private const val KEY_MUSIC_PACKAGE = "music_app_package"
        private const val KEY_MUSIC_APP = "music_app_label"
        private const val KEY_MUSIC_QUERY = "music_query"
        private const val KEY_MUSIC_RAMP = "music_ramp_seconds"
        private const val KEY_MUSIC_FALLBACK = "music_fallback_seconds"
        private const val KEY_MUSIC_VOLUME = "music_volume_percent"
        private const val KEY_MUSIC_SHUFFLE = "music_shuffle"
        private const val KEY_RADIO_HOST = "radio_browser_host"
        private const val KEY_EXTRA_ALARMS = "extra_alarms"
        private const val KEY_OVERRIDE_DATE = "day_override_date"
        private const val KEY_OVERRIDE_MODE = "day_override_mode"
        private const val KEY_OVERRIDE_WAKE = "day_override_wake"
        private const val KEY_DYNAMIC_COLOR = "use_dynamic_color"
        private const val KEY_ONBOARDING = "onboarding_complete"
        private const val KEY_UPDATE_ENABLED = "update_enabled"
        private const val KEY_UPDATE_OWNER = "update_owner"
        private const val KEY_UPDATE_REPO = "update_repo"
        private const val KEY_OFFER_TAG = "update_offer_tag"
        private const val KEY_OFFER_NOTES = "update_offer_notes"
        private const val KEY_OFFER_APK = "update_offer_apk"
        private const val KEY_OFFER_SHA = "update_offer_sha"
        private const val KEY_OFFER_DISMISSED = "update_offer_dismissed"
        private const val KEY_UPDATE_NOTIFIED = "update_notified_tag"
        private const val KEY_UPDATE_DOWNLOAD = "update_download_id"
    }
}

/** Liefert keine Werte und schreibt nichts. Die echte Datei bleibt unangetastet. */
private class ThrowingPrefs(private val cause: Exception) : SharedPreferences {
    override fun getAll(): MutableMap<String, *> = mutableMapOf<String, Any>()
    override fun getString(key: String?, defValue: String?): String? = throw cause
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = throw cause
    override fun getInt(key: String?, defValue: Int): Int = throw cause
    override fun getLong(key: String?, defValue: Long): Long = throw cause
    override fun getFloat(key: String?, defValue: Float): Float = throw cause
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = throw cause
    override fun contains(key: String?): Boolean = false
    override fun edit(): SharedPreferences.Editor = throw cause
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
}
