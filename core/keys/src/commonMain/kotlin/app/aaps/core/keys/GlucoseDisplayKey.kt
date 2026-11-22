package app.aaps.core.keys

import app.aaps.core.keys.interfaces.AppPlatform
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.IntNonPreferenceKey
import app.aaps.core.keys.interfaces.TextRef

/** Device-local presentation settings. Stored IDs are retained across the initial AOD builds. */
enum class AodBooleanKey : BooleanPreferenceKey {
    Enabled;
    override val key = "aod_enabled"
    override val defaultValue = false
    override val title = KeysStrings.aod_enable
    override val summary = KeysStrings.aod_enable_summary
    override val preferenceType = PreferenceType.SWITCH
    override val exportable = false
    override val platforms = setOf(AppPlatform.Android)
}

enum class GlucoseNotificationKey : BooleanPreferenceKey {
    Enabled;
    override val key = "notification_glucose_display"
    override val defaultValue = true
    override val title = KeysStrings.notification_glucose_enable
    override val summary = KeysStrings.notification_glucose_summary
    override val preferenceType = PreferenceType.SWITCH
    override val exportable = false
    override val platforms = setOf(AppPlatform.Android)
}

enum class AodIntKey(
    override val key: String,
    override val defaultValue: Int,
    override val min: Int,
    override val max: Int,
    override val title: TextRef,
    override val summary: TextRef? = null
) : IntPreferenceKey {
    Size("aod_size", 48, 24, 96, KeysStrings.aod_size, KeysStrings.aod_size_summary),
    X("aod_x", 50, 0, 100, KeysStrings.aod_x, KeysStrings.aod_position_summary),
    Y("aod_y", 70, 0, 100, KeysStrings.aod_y, KeysStrings.aod_position_summary);

    override val exportable = false
    override val platforms = setOf(AppPlatform.Android)
}

/** Ignore the prototype colour setting while keeping old stored values out of exports. */
enum class AodLegacyIntKey : IntNonPreferenceKey {
    Colour;
    override val key = "aod_colour"
    override val defaultValue = 0
    override val exportable = false
}
