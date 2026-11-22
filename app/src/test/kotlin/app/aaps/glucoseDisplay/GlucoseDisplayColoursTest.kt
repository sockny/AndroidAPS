package app.aaps.glucoseDisplay

import android.content.res.Configuration
import android.app.Application
import android.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.DarkGeneralColors
import app.aaps.core.ui.compose.LightGeneralColors
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class GlucoseDisplayColoursTest {
    private val preferences: Preferences = mock()

    @Before fun setup() {
        whenever(preferences.getRaw(UnitDoubleKey.OverviewLowMark)).thenReturn(70.0)
        whenever(preferences.getRaw(UnitDoubleKey.OverviewHighMark)).thenReturn(180.0)
    }

    private fun colours(mode: String, night: Boolean): GlucoseDisplayColours {
        whenever(preferences.get(StringKey.GeneralDarkMode)).thenReturn(mode)
        val app = RuntimeEnvironment.getApplication()
        val config = Configuration(app.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        return GlucoseDisplayColours(app.createConfigurationContext(config), preferences)
    }

    @Test fun lightAppUsesTheExactOverviewPaletteEvenOnADarkPhone() {
        val colours = colours("light", true)
        assertEquals(LightGeneralColors.bgLow.toArgb(), colours.colour(60.0))
        assertEquals(LightGeneralColors.bgInRange.toArgb(), colours.colour(100.0))
        assertEquals(LightGeneralColors.bgHigh.toArgb(), colours.colour(200.0))
    }

    @Test fun darkAppUsesTheExactOverviewPaletteEvenOnALightPhone() {
        val colours = colours("dark", false)
        assertEquals(DarkGeneralColors.bgLow.toArgb(), colours.colour(60.0))
        assertEquals(DarkGeneralColors.bgInRange.toArgb(), colours.colour(100.0))
        assertEquals(DarkGeneralColors.bgHigh.toArgb(), colours.colour(200.0))
    }

    @Test fun systemModeFollowsThePhoneTheme() {
        assertEquals(LightGeneralColors.bgHigh.toArgb(), colours("system", false).colour(200.0))
        assertEquals(DarkGeneralColors.bgHigh.toArgb(), colours("system", true).colour(200.0))
    }

    @Test fun overviewRangeBoundariesAndMissingOrInvalidValuesAreHandled() {
        val colours = colours("light", false)
        assertEquals(LightGeneralColors.bgInRange.toArgb(), colours.colour(70.0))
        assertEquals(LightGeneralColors.bgInRange.toArgb(), colours.colour(180.0))
        for (value in listOf(null, Double.NaN, Double.POSITIVE_INFINITY, -1.0, 0.0))
            assertEquals(Color.GRAY, colours.colour(value))
        whenever(preferences.getRaw(UnitDoubleKey.OverviewHighMark)).thenReturn(60.0)
        assertEquals(Color.GRAY, colours.colour(100.0))
    }
}
