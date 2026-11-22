package app.aaps.ui.compose.preferences

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import app.aaps.core.keys.AodBooleanKey
import app.aaps.core.keys.GlucoseNotificationKey
import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.AppPlatform
import app.aaps.core.keys.interfaces.IntentPreferenceKey
import app.aaps.core.ui.compose.ComposeScreenContent
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.ui.UiStrings

/** Same collapsible preference card and search provider as the other built-in settings. */
internal actual fun platformGlucoseDisplayPreferences(): PreferenceSubScreenDef? = PreferenceSubScreenDef(
    key = "glucose_display",
    title = UiStrings.glucose_display_title,
    items = listOf(GlucoseNotificationKey.Enabled, AodBooleanKey.Enabled, GlucoseDisplaySettingsIntent),
    icon = Icons.Default.Visibility
)

private object GlucoseDisplaySettingsIntent : IntentPreferenceKey {
    override val key = "glucose_display_settings"
    override val title = UiStrings.glucose_display_settings
    override val summary = UiStrings.glucose_display_summary
    override val platforms = setOf(AppPlatform.Android)
    override val preferenceType = PreferenceType.CLICK
    override val composeScreen = ComposeScreenContent { onBack -> GlucoseDisplaySettingsScreen(onBack) }
}
