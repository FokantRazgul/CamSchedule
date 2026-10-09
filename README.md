# Cam Planner

A personal time manager for a Cambridge student, drawn like an antique star atlas. It is a single-user,
fully offline Android app: no accounts, no backend, no API keys.

- **Timetable.** Import an `.ics` file (RRULE, EXDATE, RECURRENCE-ID and time zones are handled by
  biweekly). Re-importing replaces only the imported events. Add one-off engagements by hand, and
  rename, merge and mark subjects as academic.
- **Today and Week.** Today opens with a 24-hour astrolabe dial: real Cambridge sunrise and sunset, the
  moon phase, and the term week in Roman numerals. Below it the day's engagements are joined like a
  constellation.
- **Routes.** Each place is looked up once with the built-in Android Geocoder, and you can correct it.
  Route buttons open Google Maps from the previous engagement, or from home for the first one.
  Travel time is the straight-line distance × 1.3 at 5 km/h walking or 15 km/h cycling, plus a buffer.
- **Alarms.** A full-screen morning alarm N minutes before the first engagement, with Snooze and
  Dismiss. Also "Time to leave" alerts with a Route action, reminders, the evening review, and a
  midday nudge. Everything is recalculated whenever the timetable or settings change, and after a
  reboot, an update, or a clock or time-zone change.
- **Homework**, the **Evening Review** (reading pages, homework due within three days, exercise
  still owed), **Fitness** (push-ups and pull-ups, quiet exercises for a dorm room, runs with pace,
  rest days) and **Statistics** (reading, homework, exercise streaks and totals for the week and term).

## Install on your phone

The debug build is `app/build/outputs/apk/debug/app-debug.apk` (about 15 MB, Android 8.0 or later).

**Without a computer**
1. Copy `app-debug.apk` to the phone, for example by downloading it or from Google Drive.
2. Open it from the Files app. Android asks you to allow installs from that app: go to
   *Settings › Install unknown apps*, allow it for Files (or your browser), then go back and tap
   *Install*.
3. Play Protect may warn that the app is from an unknown developer. Choose *Install anyway*; the app
   is signed with a debug key rather than a Play Store key.

**With a computer and USB**
1. On the phone, enable *Developer options* (tap *Build number* seven times), then *USB debugging*.
2. Run `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

**First run**
1. The Permissions page opens. Allow notifications, exact alarms and full-screen alarms. Then open
   *Battery optimisation* and set Cam Planner to *Don't optimise* or *Unrestricted*, so alarms aren't
   delayed.
2. In *Index › Settings*, set your home address (*Find on the map*, or enter coordinates) and the
   Full Term dates.
3. In *Index › Import a Timetable*, choose your `.ics` file. Review the subjects and check
   *Index › The Gazetteer* for any place the geocoder couldn't find.

Looking places up needs a network connection once; everything else works offline. The Geocoder is
provided by Google Play services, so on a phone without them, enter coordinates by hand.

## Build

```bash
./gradlew test assembleDebug
```

You need JDK 17+ and the Android SDK (platform 37, build-tools 37). Gradle downloads everything else.

## Project layout

| Module | What lives there |
|---|---|
| `core:model` | Plain data types and the built-in exercise list |
| `core:domain` | Pure logic with unit tests: alarm planning, travel estimates, Maps URLs, almanac (sun, moon, term weeks), streaks and statistics, check-in plan |
| `core:ics` | `.ics` import on biweekly, with tests for recurrence, exceptions and the Europe/London clock changes |
| `core:data` | Room database, DataStore settings, repositories, geocoding worker |
| `core:alarms` | AlarmManager scheduling, receivers, ringing service, lock-screen alarm, permissions |
| `core:designsystem` | Colour and type tokens, bundled fonts (OFL), engraved components: astrolabe, orbit dials, moon phases, constellation |
| `app` | The single activity, navigation, ViewModels and screens |

The tests include an end-to-end smoke test (`AppSmokeTest`). It imports a timetable, schedules
alarms, opens every screen of the real app on Robolectric, and writes screenshots to
`app/build/smoke/`. The latest set is in `docs/screens/`.
