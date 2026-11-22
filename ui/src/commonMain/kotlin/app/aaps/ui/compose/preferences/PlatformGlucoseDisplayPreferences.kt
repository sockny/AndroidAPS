package app.aaps.ui.compose.preferences

import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef

/** Android-only local display feature; unavailable on desktop and iOS. */
internal expect fun platformGlucoseDisplayPreferences(): PreferenceSubScreenDef?
