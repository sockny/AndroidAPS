package app.aaps.persistentNotification

import android.app.Application
import android.app.Notification
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.graphics.ColorUtils
import app.aaps.R
import app.aaps.aod.AodReading
import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.concurrent.AapsLock
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.utils.TrendCalculator
import app.aaps.core.keys.GlucoseNotificationKey
import app.aaps.core.keys.StringKey
import app.aaps.glucoseDisplay.GlucoseDisplayColours
import app.aaps.core.ui.compose.LightGeneralColors
import androidx.compose.ui.graphics.toArgb
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35])
class GlucoseNotificationRenderingTest {
    private val now = 1_800_000_000_000L
    private val line1 = "5.6 → +0.1 • 0.80 U/h"
    private val line2 = "IOB: 1.2 U • COB: 20 g"
    private val line3 = "Default profile"
    private lateinit var context: Application
    private lateinit var ads: AutosensDataStore
    private lateinit var presenter: GlucoseNotificationPresenter
    private lateinit var preferences: Preferences

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        preferences = mock()
        whenever(preferences.get(GlucoseNotificationKey.Enabled)).thenReturn(true)
        whenever(preferences.get(StringKey.GeneralDarkMode)).thenReturn("light")
        whenever(preferences.getRaw(UnitDoubleKey.OverviewLowMark)).thenReturn(70.0)
        whenever(preferences.getRaw(UnitDoubleKey.OverviewHighMark)).thenReturn(180.0)
        ads = mock()
        whenever(ads.dataLock).thenReturn(AapsLock())
        whenever(ads.lastBg()).thenReturn(InMemoryGlucoseValue(timestamp = now, value = 100.0))
        val calculator: IobCobCalculator = mock()
        whenever(calculator.ads).thenReturn(ads)
        val profile: ProfileUtil = mock()
        whenever(profile.units).thenReturn(GlucoseUnit.MMOL)
        whenever(profile.fromMgdlToStringInUnits(100.0, GlucoseUnit.MMOL)).thenReturn("5.6")
        val trend: TrendCalculator = mock()
        whenever(trend.getTrendArrow(ads)).thenReturn(TrendArrow.FLAT)
        presenter = GlucoseNotificationPresenter(context, GlucoseNotificationSettings(preferences), calculator, profile, trend, GlucoseDisplayColours(context, preferences))
    }

    @Test fun numericIconAndLayoutsRenderWithoutLosingExistingNotificationInformation() = runBlocking {
        val prepared = presenter.prepare(now, line1, line2, line3)
        val builder = NotificationCompat.Builder(context, "test")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true).setOnlyAlertOnce(true)
            .setContentTitle(line1).setContentText(line2).setSubText(line3)
        presenter.apply(builder, prepared)
        val notification = builder.build()
        assertNotNull(notification.smallIcon)
        assertEquals(line1, notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(line2, notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(line3, notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT).toString())
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)

        val icon = requireNotNull(prepared.icon.bitmap)
        var pixels = 0
        for (x in 0 until icon.width) for (y in 0 until icon.height) if (Color.alpha(icon.getPixel(x, y)) > 0) pixels++
        assertTrue("The numeric icon must contain visible glyphs", pixels > 100)
        for (x in 0 until icon.width) {
            assertEquals(0, Color.alpha(icon.getPixel(x, 0)))
            assertEquals(0, Color.alpha(icon.getPixel(x, icon.height - 1)))
        }
        for (dark in listOf(false, true)) {
            val configuration = Configuration(context.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            val themed = ContextThemeWrapper(context.createConfigurationContext(configuration),
                if (dark) android.R.style.Theme_Material_NoActionBar else android.R.style.Theme_Material_Light_NoActionBar)
            val compact = prepared.compact.apply(themed, FrameLayout(themed))
            val expanded = prepared.expanded.apply(themed, FrameLayout(themed))
            assertEquals("5.6", compact.findViewById<TextView>(R.id.notification_glucose_value).text.toString())
            assertEquals(prepared.colour, compact.findViewById<TextView>(R.id.notification_glucose_value).currentTextColor)
            assertEquals("→", compact.findViewById<TextView>(R.id.notification_glucose_trend).text.toString())
            assertEquals(line1, expanded.findViewById<TextView>(R.id.notification_glucose_full_status).text.toString())
            assertEquals(line2, expanded.findViewById<TextView>(R.id.notification_glucose_info).text.toString())
            assertEquals(line3, expanded.findViewById<TextView>(R.id.notification_glucose_profile).text.toString())
            val background = if (dark) Color.rgb(32, 32, 32) else Color.WHITE
            for (view in listOf(compact, expanded)) {
                for (id in listOf(R.id.notification_glucose_status, R.id.notification_glucose_info))
                    assertTrue("Small text needs readable contrast in both themes", ColorUtils.calculateContrast(view.findViewById<TextView>(id).currentTextColor, background) >= 4.5)
            }
            preview(compact, "compact-${if (dark) "dark" else "light"}", dark)
            preview(expanded, "expanded-${if (dark) "dark" else "light"}", dark)
        }
    }

    @Test fun numberTrendAndStatusIconColoursFollowGlucoseAndOverviewDisplayMarks() = runBlocking {
        // The same 100 mg/dL reading must change colour when the display marks change.
        val cases = listOf(
            Triple(110.0, 180.0, LightGeneralColors.bgLow.toArgb()),
            Triple(70.0, 180.0, LightGeneralColors.bgInRange.toArgb()),
            Triple(70.0, 90.0, LightGeneralColors.bgHigh.toArgb())
        )
        for ((low, high, expected) in cases) {
            whenever(preferences.getRaw(UnitDoubleKey.OverviewLowMark)).thenReturn(low)
            whenever(preferences.getRaw(UnitDoubleKey.OverviewHighMark)).thenReturn(high)
            val prepared = presenter.prepare(now, line1, line2, line3)
            assertEquals(expected, prepared.colour)
            for (layout in listOf(prepared.compact, prepared.expanded)) {
                val view = layout.apply(context, FrameLayout(context))
                assertEquals(expected, view.findViewById<TextView>(R.id.notification_glucose_value).currentTextColor)
                assertEquals(expected, view.findViewById<TextView>(R.id.notification_glucose_trend).currentTextColor)
            }
            val bitmap = requireNotNull(prepared.icon.bitmap)
            val colours = (0 until bitmap.width).flatMap { x -> (0 until bitmap.height).map { y -> bitmap.getPixel(x, y) } }
            assertTrue(colours.any { Color.alpha(it) == 255 && it == expected })
        }
    }

    @Test fun oldMeasurementHasNoCurrentNumericIconOrTrend() = runBlocking {
        whenever(ads.lastBg()).thenReturn(InMemoryGlucoseValue(timestamp = now - AodReading.STALE_AFTER_MS, value = 100.0))
        val prepared = presenter.prepare(now, line1, line2, line3)
        assertEquals("?", prepared.data.iconText)
        val view = prepared.compact.apply(context, FrameLayout(context))
        assertTrue(view.findViewById<TextView>(R.id.notification_glucose_value).paintFlags and Paint.STRIKE_THRU_TEXT_FLAG != 0)
        assertEquals(View.GONE, view.findViewById<View>(R.id.notification_glucose_trend).visibility)
        assertTrue(view.findViewById<TextView>(R.id.notification_glucose_status).text.toString().contains("STALE"))
        val expanded = prepared.expanded.apply(context, FrameLayout(context))
        assertEquals("0.80 U/h", expanded.findViewById<TextView>(R.id.notification_glucose_full_status).text.toString())
        assertEquals(line2, expanded.findViewById<TextView>(R.id.notification_glucose_info).text.toString())
        assertEquals(line3, expanded.findViewById<TextView>(R.id.notification_glucose_profile).text.toString())
    }

    @Test fun missingProfileWarningRemainsVisibleInCompactNotification() = runBlocking {
        val prepared = presenter.prepare(now, "No profile set", null, null)
        val view = prepared.compact.apply(context, FrameLayout(context))
        assertEquals("No profile set", view.findViewById<TextView>(R.id.notification_glucose_info).text.toString())
    }

    private fun preview(view: View, name: String, dark: Boolean) {
        val width = (360 * context.resources.displayMetrics.density).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        val bitmap = Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(if (dark) Color.rgb(32, 32, 32) else Color.WHITE)
        view.draw(canvas)
        val file = File("build/reports/glucose-notification/$name.png")
        requireNotNull(file.parentFile).mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
