package app.camplanner.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.camplanner.model.AppSettings
import app.camplanner.model.GeoPoint
import app.camplanner.model.TravelMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Summary of the last .ics import, shown on the Import screen. */
data class ImportInfo(val fileName: String, val importedAtUtc: Long, val eventCount: Int)

class SettingsStore(context: Context) {
    private val store = context.applicationContext.settingsDataStore

    private object K {
        val homeAddress = stringPreferencesKey("home_address")
        val homeLat = doublePreferencesKey("home_lat")
        val homeLng = doublePreferencesKey("home_lng")
        val travelMode = stringPreferencesKey("travel_mode")
        val buffer = intPreferencesKey("buffer_min")
        val morningEnabled = booleanPreferencesKey("morning_enabled")
        val morningLead = intPreferencesKey("morning_lead_min")
        val snooze = intPreferencesKey("snooze_min")
        val leaveEnabled = booleanPreferencesKey("leave_enabled")
        val reminderEnabled = booleanPreferencesKey("reminder_enabled")
        val reminderLead = intPreferencesKey("reminder_lead_min")
        val checkInEnabled = booleanPreferencesKey("checkin_enabled")
        val checkInDelay = intPreferencesKey("checkin_delay_min")
        val checkInFallback = intPreferencesKey("checkin_fallback_minute_of_day")
        val readingMin = intPreferencesKey("reading_min")
        val readingMax = intPreferencesKey("reading_max")
        val weeklyRunKm = doublePreferencesKey("weekly_run_km")
        val restDays = stringSetPreferencesKey("rest_days")
        val nudgeEnabled = booleanPreferencesKey("nudge_enabled")
        val nudgeTime = intPreferencesKey("nudge_minute_of_day")
        val termStart = longPreferencesKey("term_start_epoch_day")
        val termEnd = longPreferencesKey("term_end_epoch_day")
        val weekStart = stringPreferencesKey("week_start")
        val importFile = stringPreferencesKey("import_file")
        val importAt = longPreferencesKey("import_at")
        val importCount = intPreferencesKey("import_count")
        val permissionsSeen = booleanPreferencesKey("permissions_seen")
    }

    val settings: Flow<AppSettings> = store.data.map(::read)

    suspend fun current(): AppSettings = settings.first()

    val importInfo: Flow<ImportInfo?> = store.data.map { p ->
        val file = p[K.importFile] ?: return@map null
        ImportInfo(file, p[K.importAt] ?: 0, p[K.importCount] ?: 0)
    }

    val permissionsSeen: Flow<Boolean> = store.data.map { it[K.permissionsSeen] ?: false }

    suspend fun setPermissionsSeen() = store.edit { it[K.permissionsSeen] = true }

    suspend fun recordImport(info: ImportInfo) = store.edit {
        it[K.importFile] = info.fileName
        it[K.importAt] = info.importedAtUtc
        it[K.importCount] = info.eventCount
    }

    /** Applies [change] to the current settings and writes the result. */
    suspend fun update(change: (AppSettings) -> AppSettings) {
        store.edit { prefs -> write(prefs, change(read(prefs))) }
    }

    private fun read(p: Preferences): AppSettings {
        val d = AppSettings()
        val lat = p[K.homeLat]
        val lng = p[K.homeLng]
        return AppSettings(
            homeAddress = p[K.homeAddress] ?: d.homeAddress,
            home = if (lat != null && lng != null) GeoPoint(lat, lng) else null,
            defaultTravelMode = p[K.travelMode]?.let { runCatching { TravelMode.valueOf(it) }.getOrNull() } ?: d.defaultTravelMode,
            bufferMinutes = p[K.buffer] ?: d.bufferMinutes,
            morningAlarmEnabled = p[K.morningEnabled] ?: d.morningAlarmEnabled,
            morningAlarmLeadMinutes = p[K.morningLead] ?: d.morningAlarmLeadMinutes,
            snoozeMinutes = p[K.snooze] ?: d.snoozeMinutes,
            leaveAlertEnabled = p[K.leaveEnabled] ?: d.leaveAlertEnabled,
            reminderEnabled = p[K.reminderEnabled] ?: d.reminderEnabled,
            reminderLeadMinutes = p[K.reminderLead] ?: d.reminderLeadMinutes,
            checkInEnabled = p[K.checkInEnabled] ?: d.checkInEnabled,
            checkInDelayMinutes = p[K.checkInDelay] ?: d.checkInDelayMinutes,
            checkInFallback = p[K.checkInFallback]?.let { LocalTime.ofSecondOfDay(it * 60L) } ?: d.checkInFallback,
            readingMinPages = p[K.readingMin] ?: d.readingMinPages,
            readingMaxPages = p[K.readingMax] ?: d.readingMaxPages,
            weeklyRunTargetKm = p[K.weeklyRunKm] ?: d.weeklyRunTargetKm,
            restDays = p[K.restDays]?.mapNotNull { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }?.toSet() ?: d.restDays,
            middayNudgeEnabled = p[K.nudgeEnabled] ?: d.middayNudgeEnabled,
            middayNudgeTime = p[K.nudgeTime]?.let { LocalTime.ofSecondOfDay(it * 60L) } ?: d.middayNudgeTime,
            termStart = p[K.termStart]?.let(LocalDate::ofEpochDay),
            termEnd = p[K.termEnd]?.let(LocalDate::ofEpochDay),
            weekStart = p[K.weekStart]?.let { runCatching { DayOfWeek.valueOf(it) }.getOrNull() } ?: d.weekStart,
        )
    }

    private fun write(p: MutablePreferences, s: AppSettings) {
        p[K.homeAddress] = s.homeAddress
        if (s.home != null) {
            p[K.homeLat] = s.home!!.lat
            p[K.homeLng] = s.home!!.lng
        } else {
            p.remove(K.homeLat)
            p.remove(K.homeLng)
        }
        p[K.travelMode] = s.defaultTravelMode.name
        p[K.buffer] = s.bufferMinutes
        p[K.morningEnabled] = s.morningAlarmEnabled
        p[K.morningLead] = s.morningAlarmLeadMinutes
        p[K.snooze] = s.snoozeMinutes
        p[K.leaveEnabled] = s.leaveAlertEnabled
        p[K.reminderEnabled] = s.reminderEnabled
        p[K.reminderLead] = s.reminderLeadMinutes
        p[K.checkInEnabled] = s.checkInEnabled
        p[K.checkInDelay] = s.checkInDelayMinutes
        p[K.checkInFallback] = s.checkInFallback.toSecondOfDay() / 60
        p[K.readingMin] = s.readingMinPages
        p[K.readingMax] = s.readingMaxPages
        p[K.weeklyRunKm] = s.weeklyRunTargetKm
        p[K.restDays] = s.restDays.map { it.name }.toSet()
        p[K.nudgeEnabled] = s.middayNudgeEnabled
        p[K.nudgeTime] = s.middayNudgeTime.toSecondOfDay() / 60
        if (s.termStart != null) p[K.termStart] = s.termStart!!.toEpochDay() else p.remove(K.termStart)
        if (s.termEnd != null) p[K.termEnd] = s.termEnd!!.toEpochDay() else p.remove(K.termEnd)
        p[K.weekStart] = s.weekStart.name
    }
}
