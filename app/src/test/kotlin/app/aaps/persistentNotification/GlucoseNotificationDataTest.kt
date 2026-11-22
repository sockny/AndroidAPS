package app.aaps.persistentNotification

import app.aaps.aod.AodReading
import app.aaps.core.data.model.TrendArrow
import app.aaps.persistentNotification.GlucoseNotificationData.State
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GlucoseNotificationDataTest {
    private val now = 1_800_000_000_000L
    private fun state(value: Double? = 100.0, time: Long? = now, gap: Boolean = false, low: Double = 70.0, high: Double = 180.0) =
        GlucoseNotificationData.classify(value, time, gap, now, low, high)

    @Test fun `colours use the overview display range including its boundaries`() {
        assertEquals(State.LOW, state(69.0))
        assertEquals(State.IN_RANGE, state(70.0))
        assertEquals(State.IN_RANGE, state(180.0))
        assertEquals(State.HIGH, state(181.0))
    }

    @Test fun `invalid or missing measurements cannot become a reassuring coloured number`() {
        for (value in listOf(null, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.0, -1.0))
            assertEquals(State.MISSING, state(value))
        assertEquals(State.MISSING, state(gap = true))
    }

    @Test fun `bad timestamps cannot be presented as current glucose`() {
        for (time in listOf(null, 0L, -1L, now + 60_001)) assertEquals(State.MISSING, state(time = time))
        assertEquals(State.IN_RANGE, state(time = now + 60_000))
    }

    @Test fun `expiry takes precedence over high or low colouring`() {
        assertEquals(State.IN_RANGE, state(time = now - AodReading.STALE_AFTER_MS + 1))
        assertEquals(State.STALE, state(50.0, now - AodReading.STALE_AFTER_MS))
        assertEquals(State.STALE, state(250.0, now - AodReading.STALE_AFTER_MS - 1))
    }

    @Test fun `invalid display marks do not produce an in-range colour`() {
        assertEquals(State.UNKNOWN_RANGE, state(low = Double.NaN))
        assertEquals(State.UNKNOWN_RANGE, state(high = Double.POSITIVE_INFINITY))
        assertEquals(State.UNKNOWN_RANGE, state(low = 180.0, high = 70.0))
        assertEquals(State.UNKNOWN_RANGE, state(low = 100.0, high = 100.0))
    }

    @Test fun `stale and missing status icons hide the previous number and trend`() {
        for (state in listOf(State.STALE, State.MISSING)) {
            val data = GlucoseNotificationData("5.6", "mmol/L", now, TrendArrow.DOUBLE_DOWN, state)
            assertEquals("?", data.iconText)
            assertEquals("", data.arrowText)
            assertFalse(data.fresh)
            assertNull(data.expiresAt)
        }
    }

    @Test fun `fresh numeric icons keep the formatted units and expire once`() {
        val data = GlucoseNotificationData("5,6", "mmol/L", now, TrendArrow.FLAT, State.IN_RANGE)
        assertEquals("5,6", data.iconText)
        assertEquals("→", data.arrowText)
        assertEquals(now + AodReading.STALE_AFTER_MS, data.expiresAt)
    }
}
