package app.aaps.glucoseDisplay

import android.content.Context
import android.content.ComponentCallbacks
import android.content.res.Configuration
import android.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.UiMode
import app.aaps.core.ui.compose.DarkGeneralColors
import app.aaps.core.ui.compose.LightGeneralColors
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose

/** The overview's display marks and exact AapsTheme palette, shared by notification and AOD. */
@Inject
class GlucoseDisplayColours(private val context: Context, private val preferences: Preferences) {
    val lowMgdl get() = preferences.getRaw(UnitDoubleKey.OverviewLowMark)
    val highMgdl get() = preferences.getRaw(UnitDoubleKey.OverviewHighMark)
    val changes get() = combine(
        preferences.observe(UnitDoubleKey.OverviewLowMark),
        preferences.observe(UnitDoubleKey.OverviewHighMark),
        preferences.observe(StringKey.GeneralDarkMode),
        configurationChanges()
    ) { _, _, _, _ -> Unit }

    private fun configurationChanges() = callbackFlow {
        val callbacks = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) { trySend(Unit) }
            override fun onLowMemory() = Unit
        }
        val application = context.applicationContext
        application.registerComponentCallbacks(callbacks)
        trySend(Unit)
        awaitClose { application.unregisterComponentCallbacks(callbacks) }
    }

    fun colour(valueMgdl: Double?): Int {
        val low = lowMgdl
        val high = highMgdl
        if (valueMgdl == null || !valueMgdl.isFinite() || valueMgdl <= 0 || !low.isFinite() || !high.isFinite() || low >= high)
            return Color.GRAY
        // Same preference and SYSTEM fallback as AapsTheme, rather than the notification background.
        val dark = when (UiMode.fromString(preferences.get(StringKey.GeneralDarkMode))) {
            UiMode.LIGHT -> false
            UiMode.DARK -> true
            UiMode.SYSTEM -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        }
        val colours = if (dark) DarkGeneralColors else LightGeneralColors
        return when {
            valueMgdl < low -> colours.bgLow
            valueMgdl > high -> colours.bgHigh
            else -> colours.bgInRange
        }.toArgb()
    }
}
