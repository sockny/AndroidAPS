package app.aaps.persistentNotification

import app.aaps.aod.AodReading
import app.aaps.core.data.model.TrendArrow

/** Immutable display data. Colours are not therapy thresholds and never drive a treatment action. */
data class GlucoseNotificationData(
    val value: String,
    val units: String,
    val timestamp: Long?,
    val trend: TrendArrow?,
    val state: State
) {
    enum class State { LOW, IN_RANGE, HIGH, UNKNOWN_RANGE, STALE, MISSING }
    val fresh get() = state != State.STALE && state != State.MISSING
    // A stale status-bar icon must not keep looking like a current glucose value.
    val iconText get() = if (fresh) value else "?"
    val arrowText get() = if (fresh) AodReading.arrowText(trend ?: TrendArrow.NONE) else ""
    val expiresAt get() = timestamp?.takeIf { fresh }?.plus(AodReading.STALE_AFTER_MS)

    companion object {
        fun classify(valueMgdl: Double?, timestamp: Long?, filledGap: Boolean, now: Long, lowMgdl: Double, highMgdl: Double): State = when {
            valueMgdl == null || !valueMgdl.isFinite() || valueMgdl <= 0 || filledGap -> State.MISSING
            timestamp == null || timestamp <= 0 || timestamp > now + 60_000 -> State.MISSING
            now - timestamp >= AodReading.STALE_AFTER_MS -> State.STALE
            !lowMgdl.isFinite() || !highMgdl.isFinite() || lowMgdl >= highMgdl -> State.UNKNOWN_RANGE
            valueMgdl < lowMgdl -> State.LOW
            valueMgdl > highMgdl -> State.HIGH
            else -> State.IN_RANGE
        }
    }
}
