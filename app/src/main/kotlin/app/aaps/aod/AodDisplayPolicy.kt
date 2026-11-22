package app.aaps.aod

import android.view.Display

/** Android display states, without manufacturer checks or a request to illuminate the panel. */
internal object AodDisplayPolicy {
    fun mayShow(enabled: Boolean, interactive: Boolean, locked: Boolean, displayState: Int?): Boolean =
        enabled && !interactive && locked && displayState in setOf(
            Display.STATE_OFF, Display.STATE_DOZE, Display.STATE_DOZE_SUSPEND, Display.STATE_ON
        )
}
