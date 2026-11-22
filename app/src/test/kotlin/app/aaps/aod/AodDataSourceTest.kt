package app.aaps.aod

import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.concurrent.AapsLock
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.TrendCalculator
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AodDataSourceTest {
    private val ads: AutosensDataStore = mock {
        on { dataLock }.thenReturn(AapsLock())
    }
    private val calculator: IobCobCalculator = mock { on { ads }.thenReturn(this@AodDataSourceTest.ads) }
    private val profile: ProfileUtil = mock()
    private val trend: TrendCalculator = mock()
    private val bus: RxBus = mock()
    private val source = AodDataSource(calculator, profile, trend, bus)

    @Test fun `uses processed glucose and AAPS units and calculated trend without changing the reading`() = runTest {
        val bg = InMemoryGlucoseValue(timestamp = 1_800_000_000_000, value = 110.0, smoothed = 100.0, trendArrow = TrendArrow.SINGLE_DOWN)
        val original = bg.copy()
        whenever(ads.lastBg()).thenReturn(bg)
        whenever(profile.units).thenReturn(GlucoseUnit.MMOL)
        whenever(profile.fromMgdlToStringInUnits(100.0)).thenReturn("5.6")
        whenever(trend.getTrendArrow(ads)).thenReturn(TrendArrow.FLAT)

        assertEquals(AodReading("5.6", "mmol/L", bg.timestamp, TrendArrow.FLAT, 100.0), source.read())
        assertEquals(original, bg)
    }

    @Test fun `empty internal data does not produce a glucose display`() = runTest {
        whenever(ads.lastBg()).thenReturn(null)
        assertNull(source.read())
    }

    @Test fun `gap-filled readings are never presented as a measured value`() = runTest {
        whenever(ads.lastBg()).thenReturn(InMemoryGlucoseValue(value = 100.0, filledGap = true))
        assertNull(source.read())
    }

    @Test fun `invalid values are never presented as glucose`() = runTest {
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.0, -10.0)) {
            whenever(ads.lastBg()).thenReturn(InMemoryGlucoseValue(value = value))
            assertNull(source.read())
        }
    }
}
