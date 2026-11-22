# Glucose display: always-on display and coloured notification

## Reference prototype and contribution status

This branch preserves the locally tested prototype based on upstream commit
`f77abd9b82` (AAPS 4.0.0). Its implementation was generated with OpenAI Codex in
collaboration with the fork owner. It is published transparently as reference material,
not as a merge request against current `dev`.

The fork owner reports using the AOD and coloured notification on a Samsung Galaxy
S23 Ultra with One UI and that they work well in ongoing use. This is user-reported
experience; no precise battery measurements or multi-device compatibility results
are claimed. The build/test results below were recorded on 2026-11-08, not rerun or
relabelled as new results at publication on 2026-11-22.

AAPS CONTRIBUTING.md asks for AI findings in an Issue and leaves the actual upstream
implementation to maintainers. Accordingly, the upstream contribution is a feature
proposal and architectural context, with this prototype available as optional reference.
See [upstream contribution guidance](https://github.com/nightscout/AndroidAPS/blob/dev/CONTRIBUTING.md).

## Behaviour and scope

This Android presentation feature reads AAPS processed CGM data directly from
`IobCobCalculator.ads`, using the existing `ProfileUtil` and `TrendCalculator`.
It does not use Nightscout, a second app, a notification listener or screen scraping.
The service and presenter receive no pump or command interfaces. No dosing algorithm,
constraint, pump driver, treatment record or safety feature is changed.

The notification enhances the existing ongoing AAPS notification, preserving its ID,
channel, ongoing/only-alert-once flags, tap action, Android Auto conversation, title/text
metadata and expanded IOB/COB/basal/profile details. Its value, arrow and bitmap numeric
small icon use the exact `LightGeneralColors` / `DarkGeneralColors` blood-glucose colours
from `AapsTheme`, selected with the same GeneralDarkMode preference and system fallback.
AOD uses the same shared `GlucoseDisplayColours` resolver. The overview low/high marks
select the range, including the same inclusive boundaries. These are display marks, not therapy thresholds.
Changing the display marks, app theme or Android configuration refreshes both displays
without waiting for CGM. Configuration callbacks are registered only while their flow
is collected and unregistered on cancellation; no timer or wakeup is added.
Android can override the small icon tint. Switching the mode off restores the existing
notification and supported live/Now Bar templates; the custom style cannot share those
templates because they replace its RemoteViews.

Missing, invalid, non-finite, non-positive, future-dated or gap-filled readings are not
presented as current. At `Constants.OLD_BG_MINUTES` (currently nine minutes), old values
are crossed out and labelled, trends are hidden (including repeated legacy detail text),
and the numeric notification icon becomes `?`. Sample time remains visible. A single
cancellable coroutine deadline requests a debounced notification refresh at expiry.
There are no polling alarms or wake locks; deep sleep may delay this refresh.

## Settings and translations

Settings → **Glucose display** uses the same collapsible card, search provider,
Material theme and adaptive preference controls as the other built-in AAPS settings.
**Display settings** opens inside the existing settings navigation, with **General**
and **Advanced** tabs. General contains the independent notification/AOD switches and
an Android accessibility-settings link with status refreshed on resume. Advanced contains
24–96 sp AOD text size, horizontal/vertical position (0–100 percent), display/energy explanations. Each tab retains its own scroll position.
The prototype native Activity and its manifest/DI registrations have been removed.

`core:keys/GlucoseDisplayKey.kt` defines Android-only typed preference metadata. Existing
stored IDs and defaults are retained: AOD is opt-in (false); the explicitly requested
numeric notification mode retains its initial true default. The five active display settings
remain non-exportable and unsynced. The removed prototype `aod_colour` preference is
retained only as a non-exportable, ignored legacy key; no selector or colour override
uses it. Old locally stored values need no migration.

English is the source language. Text lives in the existing canonical `strings.xml` files:

- `core/keys/src/androidMain/res/values/strings.xml`: preference titles and summaries.
- `ui/src/androidMain/res/values/strings.xml`: tabs, settings, permission status and explanations.
- `app/src/main/res/values/strings.xml`: service metadata and glucose age/missing labels.

All three files already appear in `crowdin.yml`; generated `KeysStrings`/`UiStrings` and
Android resource resolution follow the selected app language and fall back to English.
Translators use the normal matching `values-<locale>/strings.xml` files, preserving format
placeholders. No locale is forced and no handwritten string registry is introduced.
The separate prototype Hungarian/string files were removed. iOS and desktop return no
settings definition because this overlay and ongoing notification are Android facilities.

## Device compatibility and power

The overlay uses Android `TYPE_ACCESSIBILITY_OVERLAY`, `DisplayManager`, `PowerManager`
and `KeyguardManager`, with no manufacturer/model check or Samsung API. `AodDisplayPolicy`
permits the Android OFF/DOZE/DOZE_SUSPEND states and a noninteractive ON transition, only
while enabled and locked. Wake, unlock, disable, interrupt, unbind and destruction remove
it and cancel updates. Unknown/missing displays never qualify. The service requests no
accessibility event types, window contents or gestures. It is exported only behind
`BIND_ACCESSIBILITY_SERVICE`; it does not intercept touches or focus.

This is device-independent Android code, not a guarantee that every manufacturer renders
overlays on its ambient display. The device must have an enabled, compatible AOD. AAPS
does not turn on a powered-off panel or bypass OEM restrictions to force visibility.
See [Android AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)
and [Android display states](https://developer.android.com/reference/android/view/Display#STATE_DOZE).

A one-minute non-wakeup coroutine ages and shifts visible AOD text in both axes. A
margin allows movement even at the position limits. Moderate text size, device AOD brightness,
movement and the device AOD schedule reduce power use and OLED wear; they cannot eliminate
it. Deep sleep may delay updates and movement. Hardware visibility, screen wake/unlock,
fingerprint/clock overlap, battery impact and long-term OLED behaviour require real-device
acceptance checks. This feature never replaces AAPS alarms.

## Source and licence

Adapted from [GlucoDataHandler](https://github.com/pachi81/GlucoDataHandler), commit
`215b2f071f86942a5b9714cbf88808a71708a33f`: `AODAccessibilityService`, `widget/AodWidget`,
`notification/PermanentNotification` and `BitmapUtils` informed the overlay lifecycle,
screen-state handling, pixel shifting and numeric-icon/RemoteViews technique. GDH-specific
data models, services, bitmap pools, fonts and preference infrastructure are replaced by
AAPS equivalents. No GDH library/runtime dependency is added.

GDH is MIT, copyright (c) 2023 Michael Pach. The complete notice is packaged as
`app/src/main/assets/licenses/GlucoDataHandler-MIT.txt` and credited in adapted source.
AAPS retains its existing AGPL-3.0 licence. Future GDH changes are not automatically imported.

## Build and checks

Use the existing SDK, Gradle wrapper and Android Studio JDK (21+):

```powershell
./gradlew.bat :app:assembleFullRelease :app:testFullDebugUnitTest `
    --tests 'app.aaps.aod.*' `
    --tests 'app.aaps.glucoseDisplay.*' `
    --tests 'app.aaps.persistentNotification.*' `
    --tests 'app.aaps.keys.PreferenceKeySnapshotTest' `
    --tests 'app.aaps.keys.PreferenceKeyRoundTripTest'
./gradlew.bat :ui:testAndroidHostTest `
    --tests 'app.aaps.ui.compose.preferences.GlucoseDisplaySettingsScreenTest'
```

Tests use immutable/mock CGM data and no MainApp/therapy startup. They cover display-state
eligibility, data freshness, trends/units, notification colours across all ranges, visible
numeric glyphs, light/dark layouts, metadata and IOB/COB/profile preservation, and settings
tabs/toggle writes. Notification test renders are under
`app/build/reports/glucose-notification/`. Existing preference-schema and round-trip tests
check stored IDs and exportability. The original raw-store access scan also passed during
the initial integration; this feature continues to use only typed Preferences.

The local build uses `4.0.0-rc-aod` with the existing version code to identify a custom build
and satisfy the modified-stable-source build guard. `-rc` preserves the actual
`ConfigImpl.isDev() == false` feature gates, checked by a regression test. The upstream PR
should use the maintainers' chosen version, rather than including this local branding
change in `buildSrc/src/main/kotlin/Versions.kt`. No feature-gate implementation is altered.
Use your existing signing key to update an installed release; a debug key cannot update it.

## Phone acceptance steps

1. Install an APK signed with the existing AAPS signing key, without uninstalling AAPS.
2. Open Settings → Glucose display. Check notification value, trend and colour against
   overview, including low/in-range/high display marks and a new CGM sample. Expand the
   notification and check IOB, COB, basal and profile. Toggle off to restore standard style.
3. Open Display settings → General. Enable the phone's own AOD, the AAPS AOD switch and the
   AOD accessibility service via Open accessibility settings. If Android blocks access,
   use phone Settings → Apps → AAPS → menu → Allow restricted settings, then return to
   accessibility. See [Android Help](https://support.google.com/android/answer/12623953).
4. Lock the phone; verify AOD text and CGM updates, then wake/unlock and check immediate
   overlay removal and normal touch/fingerprint/lock-screen operation.
5. Adjust size and position in Advanced; confirm both displays match the overview glucose
   colour in light, dark and system theme modes. Check movement and clock/fingerprint
   overlap. Check another supported AOD device separately; do not assume OEM equivalence.
6. Check stale/missing rendering in a test environment without changing your live therapy
   or stopping required CGM collection. Inspect deep-sleep, schedule and battery behaviour.

## Validation for the settings integration (before the colour follow-up)

The two native Compose UI tests passed (`:ui:testAndroidHostTest`), including separate
tabs, returning to General and writing only the AOD display switch. Reviewed the
generated General/Advanced PNGs in `ui/build/reports/glucose-display-settings/`.
Final checks on 2026-11-08:

- `:app:assembleFullRelease` and all 31 selected app tests: **BUILD SUCCESSFUL** (9 minutes).
- Both native Compose UI tests: **BUILD SUCCESSFUL**; total **33 tests passed**, none failed/skipped.
- `git diff --check`, UTF-8 decoding and changed XML parsing passed.
- Inspected the unsigned release APK with `aapt2`: package `info.nightscout.androidaps`,
  version `4.0.0-rc-aod`, version code `1500`, non-debuggable.
- APK alignment check passed and the GDH MIT licence is packaged.
- Output: `app/build/outputs/apk/full/release/app-full-release-unsigned.apk`.
  This run does not generate a newly signed release; sign with the existing Android Studio key.
- Logs: `glucose-display-native-settings-final.log`, `glucose-display-settings-ui-tests.log`.
- Real-device/OEM AOD visibility and status-bar tint remain acceptance checks.


## Modified and new files

- `_docs/AOD-integration.md`
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/assets/licenses/GlucoDataHandler-MIT.txt`
- `app/src/main/kotlin/app/aaps/MainApp.kt`
- `app/src/main/kotlin/app/aaps/aod/AodAccessibilityService.kt`
- `app/src/main/kotlin/app/aaps/aod/AodDataSource.kt`
- `app/src/main/kotlin/app/aaps/aod/AodDisplayPolicy.kt`
- `app/src/main/kotlin/app/aaps/aod/AodReading.kt`
- `app/src/main/kotlin/app/aaps/aod/AodSettings.kt`
- `app/src/main/kotlin/app/aaps/di/metro/AppReceiversBindings.kt`
- `app/src/main/kotlin/app/aaps/glucoseDisplay/GlucoseDisplayColours.kt`
- `app/src/main/kotlin/app/aaps/persistentNotification/GlucoseNotificationData.kt`
- `app/src/main/kotlin/app/aaps/persistentNotification/GlucoseNotificationPresenter.kt`
- `app/src/main/kotlin/app/aaps/persistentNotification/GlucoseNotificationSettings.kt`
- `app/src/main/kotlin/app/aaps/persistentNotification/PersistentNotificationPlugin.kt`
- `app/src/main/res/layout/notification_glucose_compact.xml`
- `app/src/main/res/layout/notification_glucose_expanded.xml`
- `app/src/main/res/values-night/glucose_notification_colors.xml`
- `app/src/main/res/values/glucose_notification_colors.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/xml/aod_accessibility_service.xml`
- `app/src/test/kotlin/app/aaps/aod/AodBuildModeTest.kt`
- `app/src/test/kotlin/app/aaps/aod/AodDataSourceTest.kt`
- `app/src/test/kotlin/app/aaps/aod/AodDisplayPolicyTest.kt`
- `app/src/test/kotlin/app/aaps/aod/AodReadingTest.kt`
- `app/src/test/kotlin/app/aaps/glucoseDisplay/GlucoseDisplayColoursTest.kt`
- `app/src/test/kotlin/app/aaps/persistentNotification/GlucoseNotificationDataTest.kt`
- `app/src/test/kotlin/app/aaps/persistentNotification/GlucoseNotificationRenderingTest.kt`
- `app/src/test/kotlin/app/aaps/persistentNotification/PersistentNotificationPluginTest.kt`
- `app/src/testFull/resources/prefs-schema.txt`
- `buildSrc/src/main/kotlin/Versions.kt`
- `core/keys/src/androidMain/res/values/strings.xml`
- `core/keys/src/commonMain/kotlin/app/aaps/core/keys/GlucoseDisplayKey.kt`
- `ui/src/androidHostTest/kotlin/app/aaps/ui/compose/preferences/GlucoseDisplaySettingsScreenTest.kt`
- `ui/src/androidMain/kotlin/app/aaps/ui/compose/preferences/GlucoseDisplaySettingsScreen.kt`
- `ui/src/androidMain/kotlin/app/aaps/ui/compose/preferences/PlatformGlucoseDisplayPreferences.android.kt`
- `ui/src/androidMain/res/values/strings.xml`
- `ui/src/commonMain/kotlin/app/aaps/ui/compose/preferences/AllPreferencesScreen.kt`
- `ui/src/commonMain/kotlin/app/aaps/ui/compose/preferences/PlatformGlucoseDisplayPreferences.kt`
- `ui/src/commonMain/kotlin/app/aaps/ui/search/BuiltInSearchables.kt`
- `ui/src/iosMain/kotlin/app/aaps/ui/compose/preferences/PlatformGlucoseDisplayPreferences.ios.kt`
- `ui/src/jvmMain/kotlin/app/aaps/ui/compose/preferences/PlatformGlucoseDisplayPreferences.jvm.kt`

## Shared overview-colour follow-up

The notification and AOD now share the exact AAPS overview palette. The independent
AOD colour selector and its strings are removed. Old readings keep their range colour,
like the overview; the strike-through, stale label, hidden trend and question-mark icon
still distinguish age. Missing/invalid data remains neutral. Four additional resolver
tests cover light/dark/system mode, exact palette equality, boundaries and invalid data.

Follow-up validation (2026-11-08):

- `:app:assembleFullRelease` and 35 selected app tests: **BUILD SUCCESSFUL** (13m 35s).
- `:ui:testAndroidHostTest`: both settings tests passed, including the absent AOD colour selector.
- Total **37 tests passed**, no failures, errors or skipped tests.
- Reviewed the regenerated notification render: the value and arrow use the overview green.
- `git diff --check` and APK alignment checks passed. APK metadata remains package
  `info.nightscout.androidaps`, version `4.0.0-rc-aod`, version code `1500`, non-debuggable.
- Output remains the unsigned `app/build/outputs/apk/full/release/app-full-release-unsigned.apk`;
  no newly signed APK is generated by these CLI checks.
- Logs: `glucose-display-overview-colours.log` and `glucose-display-overview-colours-ui.log`.
- The operating system can still override a small status-bar icon tint; device acceptance
  checks remain necessary for OEM AOD visibility.
