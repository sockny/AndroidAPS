package app.aaps.persistentNotification

import app.aaps.core.keys.GlucoseNotificationKey
import app.aaps.core.keys.interfaces.Preferences
import dev.zacsweers.metro.Inject

/** Read-only presenter settings. Edits use the standard adaptive preference controls. */
@Inject
class GlucoseNotificationSettings(private val preferences: Preferences) {
    val enabled get() = preferences.get(GlucoseNotificationKey.Enabled)
    val changes get() = preferences.observe(GlucoseNotificationKey.Enabled)
}
