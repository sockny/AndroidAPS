/*
 * Adapted from GlucoDataHandler AODAccessibilityService by Michael Pach.
 * Copyright (c) 2023 Michael Pach. MIT license: assets/licenses/GlucoDataHandler-MIT.txt.
 * Upstream: pachi81/GlucoDataHandler, commit 215b2f071f86942a5b9714cbf88808a71708a33f.
 */
package app.aaps.aod

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.TextView
import app.aaps.R
import app.aaps.glucoseDisplay.GlucoseDisplayColours
import app.aaps.core.interfaces.di.MetroMemberInjector
import app.aaps.settingsstore.AndroidStorage
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Date

/** No window content, gestures, notification access, foreground notification or wake locks. */
class AodAccessibilityService : AccessibilityService() {
    @Inject lateinit var dataSource: AodDataSource
    @Inject lateinit var settings: AodSettings
    @Inject lateinit var colours: GlucoseDisplayColours

    private val handler = Handler(Looper.getMainLooper())
    private var scope: CoroutineScope? = null
    private var updates: Job? = null
    private var refresh: Job? = null
    private var overlay: LinearLayout? = null
    private val windows get() = getSystemService(WindowManager::class.java)
    private val displays get() = getSystemService(DisplayManager::class.java)
    private var registered = false
    private var receiverRegistered = false
    private var displayListenerRegistered = false
    private var interrupted = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            reconcile()
        }
    }
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = reconcile()
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) reconcile()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (registered || AndroidStorage.failure != null) return
        try {
            if (!(application as MetroMemberInjector).injectMembers(this)) return
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            registerReceiver(screenReceiver, IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_TIME_CHANGED)
            }, Context.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
            displays.registerDisplayListener(displayListener, handler)
            displayListenerRegistered = true
            registered = true
            interrupted = false
            reconcile()
            scope?.launch {
                combine(settings.changes(), colours.changes) { _, _ -> Unit }.drop(1).collectLatest {
                    reconcile()
                    if (mayShow()) requestRefresh()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "AOD service could not connect", e)
            cleanup()
        }
    }

    private fun mayShow(): Boolean {
        if (interrupted || !registered || AndroidStorage.failure != null) return false
        // Panel state may change independently of interactivity during AOD transitions.
        // Even STATE_ON is eligible only with the locked/noninteractive gates above.
        return AodDisplayPolicy.mayShow(
            enabled = settings.enabled,
            interactive = getSystemService(PowerManager::class.java).isInteractive,
            locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked,
            displayState = displays.getDisplay(Display.DEFAULT_DISPLAY)?.state
        )
    }

    /** Hide synchronously on wake; recheck after the device has finished the screen-off transition. */
    private fun reconcile() {
        if (!mayShow()) {
            updates?.cancel()
            updates = null
            refresh?.cancel()
            refresh = null
            removeOverlay()
            return
        }
        if (updates != null) return
        updates = scope?.launch {
            delay(1000)
            requestRefresh()
            launch {
                dataSource.changes().collectLatest {
                    // A single CGM update can emit several pipeline events; draw once after the burst.
                    delay(250)
                    requestRefresh()
                }
            }
            // Handler/coroutine delays do not wake a sleeping CPU. Age and pixel shift catch up on resume.
            while (true) {
                delay(60_000)
                requestRefresh()
            }
        }
    }

    private fun requestRefresh() {
        refresh?.cancel()
        refresh = scope?.launch {
            try {
                val reading = dataSource.read()
                if (mayShow()) render(reading) else removeOverlay()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Never leave a previously fresh value on screen after an update failure.
                removeOverlay()
                Log.w(TAG, "AOD refresh failed", e)
            }
        }
    }

    private fun render(reading: AodReading?) {
        val now = System.currentTimeMillis()
        val state = reading?.state(now) ?: AodReading.State.MISSING
        val fresh = state == AodReading.State.FRESH
        val missing = state == AodReading.State.MISSING
        val textColour = colours.colour(if (missing) null else reading?.glucoseMgdl)
        val value = TextView(this).apply {
            text = if (missing) "—" else reading!!.value + if (fresh) " " + AodReading.arrowText(reading.arrow) else ""
            textSize = settings.size.toFloat()
            setTextColor(textColour)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            if (state == AodReading.State.STALE) paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        }
        val details = TextView(this).apply {
            val sampleTime = reading?.let { android.text.format.DateFormat.getTimeFormat(this@AodAccessibilityService).format(Date(it.timestamp)) }
            text = when (state) {
                AodReading.State.MISSING -> getString(R.string.aod_no_data)
                AodReading.State.STALE -> getString(R.string.aod_stale, reading!!.units, sampleTime)
                AodReading.State.FRESH -> getString(R.string.aod_age, reading!!.units, sampleTime)
            }
            textSize = 14f
            setTextColor(textColour)
            gravity = Gravity.CENTER
        }
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(value)
            addView(details)
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val bounds = windows.currentWindowMetrics.bounds
        val margin = (16 * resources.displayMetrics.density).toInt().coerceAtLeast(12)
        val availableWidth = (bounds.width() - 2 * margin).coerceAtLeast(1)
        val availableHeight = (bounds.height() - 2 * margin).coerceAtLeast(1)
        // Fit even the largest value/trend setting to the actual display bounds.
        view.measure(android.view.View.MeasureSpec.UNSPECIFIED, android.view.View.MeasureSpec.UNSPECIFIED)
        if (view.measuredWidth > availableWidth) value.textSize = settings.size * availableWidth.toFloat() / view.measuredWidth
        view.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(availableWidth, android.view.View.MeasureSpec.AT_MOST),
            android.view.View.MeasureSpec.makeMeasureSpec(availableHeight, android.view.View.MeasureSpec.AT_MOST)
        )
        val minute = now / 60_000
        val shiftX = ((minute % 21) - 10).toInt()
        val shiftY = (((minute / 21) % 21) - 10).toInt()
        val maxX = (availableWidth - view.measuredWidth).coerceAtLeast(0)
        val maxY = (availableHeight - view.measuredHeight).coerceAtLeast(0)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            // The reserved margin leaves room for movement even at either end of the sliders.
            x = margin + maxX * settings.x / 100 + shiftX
            y = margin + maxY * settings.y / 100 + shiftY
        }
        removeOverlay()
        windows.addView(view, params)
        overlay = view
    }

    private fun removeOverlay() {
        overlay?.let { view ->
            try { windows.removeViewImmediate(view) }
            catch (e: Exception) { Log.w(TAG, "AOD overlay removal failed", e) }
        }
        overlay = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() {
        interrupted = true
        reconcile()
    }
    override fun onUnbind(intent: Intent?): Boolean {
        cleanup()
        return super.onUnbind(intent)
    }
    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }
    private fun cleanup() {
        scope?.cancel()
        scope = null
        updates = null
        refresh = null
        handler.removeCallbacksAndMessages(null)
        if (receiverRegistered) {
            unregisterReceiver(screenReceiver)
            receiverRegistered = false
        }
        if (displayListenerRegistered) {
            displays.unregisterDisplayListener(displayListener)
            displayListenerRegistered = false
        }
        registered = false
        removeOverlay()
    }

    companion object { private const val TAG = "AAPS.AOD" }
}
