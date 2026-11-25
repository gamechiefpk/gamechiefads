package com.gcl.gamechiefads

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock

/**
 * DEBUG BUILDS ONLY. While the app is in front, shaking the phone opens the debug panel: the dev calls nothing. It is created only for a debuggable
 * app, so release builds never register a sensor. Firebase can switch it off with the boolean `debugShakeEnabled` (default true) and the config can with
 * `.debugShake(false)`.
 */
internal class ShakeWatcher(
    context: Context,
    private val allowed: () -> Boolean,
    private val onShake: () -> Unit,
) : SensorEventListener {
    private val sensors = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val detector = ShakeDetector()
    private var on = false

    /** The app came to the front. */
    fun start() {
        if (on || sensors == null || accelerometer == null) return
        on = sensors.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        AdLog.d("shake") { "listening: shake the phone to open the debug panel (Firebase $KEY=false turns it off)" }
    }

    /** The app went to the background: no sensor, no battery. */
    fun stop() {
        if (!on) return
        on = false
        runCatching { sensors?.unregisterListener(this) }
    }

    private var lastLogged = 0L

    override fun onSensorChanged(event: SensorEvent) {
        // QA: a strong reading is written to the log (at most 1 per second) so a "why did my shake not open it" is easy to answer
        val now = SystemClock.elapsedRealtime()
        val g = kotlin.math.sqrt((event.values[0] * event.values[0] + event.values[1] * event.values[1] + event.values[2] * event.values[2]).toDouble()) / 9.81
        if (g > 1.6 && now - lastLogged > 1000) { lastLogged = now; AdLog.d("shake") { "strong reading: %.1f g".format(g) } }
        if (!detector.onAcceleration(event.values[0], event.values[1], event.values[2], SystemClock.elapsedRealtime())) return
        if (!allowed()) { AdLog.d("shake") { "shake ignored (switched off in the config or in Firebase)" }; return }
        AdLog.d("shake") { "shake: opening the debug panel" }
        runCatching(onShake)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        /** The Firebase Remote Config boolean that switches the shake off (default true = on, in debug builds). */
        const val KEY = "debugShakeEnabled"
    }
}
