package app.aaps.aod

import app.aaps.core.keys.AodBooleanKey
import app.aaps.core.keys.AodIntKey
import app.aaps.core.keys.interfaces.Preferences
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.combine

@Inject
class AodSettings(private val preferences: Preferences) {
    val enabled get() = preferences.get(AodBooleanKey.Enabled)
    val size get() = preferences.get(AodIntKey.Size).coerceIn(24, 96)
    val x get() = preferences.get(AodIntKey.X).coerceIn(0, 100)
    val y get() = preferences.get(AodIntKey.Y).coerceIn(0, 100)
    fun changes() = combine(
        preferences.observe(AodBooleanKey.Enabled), preferences.observe(AodIntKey.Size),
        preferences.observe(AodIntKey.X), preferences.observe(AodIntKey.Y)
    ) { _, _, _, _ -> Unit }
}
