package app.aaps.aod

import app.aaps.core.data.model.TrendArrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class AodReadingTest {
    private val now = 1_800_000_000_000L
    private fun reading(timestamp: Long) = AodReading("5.6", "mmol/l", timestamp, TrendArrow.FLAT, 100.0)

    @Test fun `a fresh reading becomes stale even without a new CGM event`() {
        val reading = reading(now)
        assertEquals(AodReading.State.FRESH, reading.state(now))
        assertEquals(AodReading.State.FRESH, reading.state(now + AodReading.STALE_AFTER_MS - 1))
        assertEquals(AodReading.State.STALE, reading.state(now + AodReading.STALE_AFTER_MS))
        assertEquals(AodReading.State.STALE, reading.state(now + 86_400_000))
    }

    @Test fun `invalid and future timestamps must never look fresh`() {
        assertEquals(AodReading.State.MISSING, reading(0).state(now))
        assertEquals(AodReading.State.MISSING, reading(-1).state(now))
        assertEquals(AodReading.State.MISSING, reading(now + 60_001).state(now))
        assertEquals(AodReading.State.FRESH, reading(now + 60_000).state(now))
    }

    @Test fun `unknown and triple trends do not expose protocol placeholders`() {
        assertEquals("", AodReading.arrowText(TrendArrow.NONE))
        assertEquals("↑↑↑", AodReading.arrowText(TrendArrow.TRIPLE_UP))
        assertEquals("↓↓↓", AodReading.arrowText(TrendArrow.TRIPLE_DOWN))
        TrendArrow.entries.forEach {
            assertFalse(AodReading.arrowText(it).contains("X"))
            assertFalse(AodReading.arrowText(it).contains("?"))
        }
    }
}
