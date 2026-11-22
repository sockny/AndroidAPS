package app.aaps.aod

import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.configuration.Constants

/** An immutable display snapshot. No therapy objects escape the data adapter. */
data class AodReading(val value: String, val units: String, val timestamp: Long, val arrow: TrendArrow, val glucoseMgdl: Double) {
    fun state(now: Long): State = when {
        timestamp <= 0 || timestamp > now + 60_000 -> State.MISSING
        now - timestamp >= STALE_AFTER_MS -> State.STALE
        else -> State.FRESH
    }

    enum class State { FRESH, STALE, MISSING }

    companion object {
        const val STALE_AFTER_MS = Constants.OLD_BG_MINUTES * 60_000L

        fun arrowText(arrow: TrendArrow): String = when (arrow) {
            TrendArrow.NONE -> ""
            TrendArrow.TRIPLE_UP -> "↑↑↑"
            TrendArrow.TRIPLE_DOWN -> "↓↓↓"
            else -> arrow.symbol
        }
    }
}
