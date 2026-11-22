/*
 * Numeric bitmap icons and coloured RemoteViews adapted from GlucoDataHandler's
 * PermanentNotification and BitmapUtils. Copyright (c) 2023 Michael Pach, MIT.
 * Full notice: assets/licenses/GlucoDataHandler-MIT.txt.
 * Upstream commit: 215b2f071f86942a5b9714cbf88808a71708a33f.
 */
package app.aaps.persistentNotification

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import app.aaps.R
import app.aaps.glucoseDisplay.GlucoseDisplayColours
import app.aaps.core.interfaces.concurrent.withLock
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.utils.TrendCalculator
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.combine
import java.util.Date

/** Reads CGM display snapshots and prepares a notification; has no pump or command interfaces. */
@Inject
class GlucoseNotificationPresenter(
    private val context: Context,
    private val settings: GlucoseNotificationSettings,
    private val calculator: IobCobCalculator,
    private val profileUtil: ProfileUtil,
    private val trendCalculator: TrendCalculator,
    private val colours: GlucoseDisplayColours
) {
    val enabled get() = settings.enabled
    val changes get() = combine(settings.changes, colours.changes) { _, _ -> Unit }

    data class Prepared(
        val data: GlucoseNotificationData,
        val icon: IconCompat,
        val colour: Int,
        val compact: RemoteViews,
        val expanded: RemoteViews
    )

    suspend fun prepare(now: Long, line1: String?, line2: String?, line3: String?): Prepared = withContext(Dispatchers.Default) {
        val ads = calculator.ads
        val snapshot = ads.dataLock.withLock {
            ads.lastBg()?.copy()?.let { it to trendCalculator.getTrendArrow(ads) }
        }
        val bg = snapshot?.first
        val units = profileUtil.units
        val state = GlucoseNotificationData.classify(bg?.recalculated, bg?.timestamp, bg?.filledGap == true, now, colours.lowMgdl, colours.highMgdl)
        val data = GlucoseNotificationData(
            if (state == GlucoseNotificationData.State.MISSING) "—" else profileUtil.fromMgdlToStringInUnits(bg!!.recalculated, units),
            units.displayLabel, bg?.timestamp, snapshot?.second, state
        )
        val colour = colours.colour(bg?.recalculated.takeUnless { state == GlucoseNotificationData.State.MISSING })
        val iconBitmap = numberBitmap(data.iconText, colour)
        Prepared(
            data, IconCompat.createWithBitmap(iconBitmap), colour,
            views(R.layout.notification_glucose_compact, data, colour, line1, line2, line3),
            views(R.layout.notification_glucose_expanded, data, colour, line1, line2, line3)
        )
    }

    fun apply(builder: NotificationCompat.Builder, prepared: Prepared) {
        builder.setSmallIcon(prepared.icon)
            .setColor(prepared.colour)
            .setColorized(false)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(prepared.compact)
            .setCustomBigContentView(prepared.expanded)
    }

    private fun views(layout: Int, data: GlucoseNotificationData, colour: Int, line1: String?, line2: String?, line3: String?): RemoteViews =
        RemoteViews(context.packageName, layout).apply {
            // The legacy first line starts with BG/trend/delta, then " • " and the current basal.
            // Do not repeat that stale trend below the explicitly stale glucose presentation.
            val detailLine = if (data.fresh) line1.orEmpty() else line1?.substringAfter(" • ", line1).orEmpty()
            setTextViewText(R.id.notification_glucose_value, data.value)
            setTextColor(R.id.notification_glucose_value, colour)
            setInt(R.id.notification_glucose_value, "setPaintFlags", Paint.ANTI_ALIAS_FLAG or
                if (data.state == GlucoseNotificationData.State.STALE) Paint.STRIKE_THRU_TEXT_FLAG else 0)
            setContentDescription(R.id.notification_glucose_value, "${data.value} ${data.units}")
            setTextViewText(R.id.notification_glucose_trend, data.arrowText)
            setTextColor(R.id.notification_glucose_trend, colour)
            setViewVisibility(R.id.notification_glucose_trend, if (data.arrowText.isEmpty()) View.GONE else View.VISIBLE)
            val time = data.timestamp?.let { android.text.format.DateFormat.getTimeFormat(context).format(Date(it)) }
            val status = when (data.state) {
                GlucoseNotificationData.State.MISSING -> context.getString(R.string.aod_no_data)
                GlucoseNotificationData.State.STALE -> context.getString(R.string.aod_stale, data.units, time)
                else -> context.getString(R.string.aod_age, data.units, time)
            }
            setTextViewText(R.id.notification_glucose_status, status)
            setTextViewText(R.id.notification_glucose_info, line2?.takeUnless { it.isBlank() } ?: detailLine)
            if (layout == R.layout.notification_glucose_expanded) {
                setTextViewText(R.id.notification_glucose_full_status, detailLine)
                setTextViewText(R.id.notification_glucose_profile, line3.orEmpty())
                setViewVisibility(R.id.notification_glucose_profile, if (line3.isNullOrBlank()) View.GONE else View.VISIBLE)
            }
        }

    /** Transparent, coloured numeric icon. System UI may apply its own monochrome tint. */
    private fun numberBitmap(text: String, colour: Int): Bitmap {
        val side = 128
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colour
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            textSize = side.toFloat()
        }
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        paint.textSize *= minOf((side - 6f) / bounds.width().coerceAtLeast(1), (side - 6f) / bounds.height().coerceAtLeast(1))
        paint.getTextBounds(text, 0, text.length, bounds)
        Canvas(bitmap).drawText(text, (side - bounds.width()) / 2f - bounds.left, (side + bounds.height()) / 2f - bounds.bottom, paint)
        return bitmap
    }
}
