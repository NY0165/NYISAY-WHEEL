package mz.nyisay.virtualracingwheel

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import kotlin.math.atan2
import kotlin.math.pow

/**
 * Accelerometer / gravity steering with filter, calibration, deadzone and curve.
 */
class SteeringSensor(
    context: Context,
    private val onSteer: (Float) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val sensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    var filteredDeg: Float = 0f
        private set
    var zeroDeg: Float = 0f
    var maxAngleDeg: Float = 45f
    var filterAlpha: Float = 0.18f
    var deadzone: Float = 0.04f
    var curveExp: Float = 1.3f
    var invert: Boolean = false

    private var running = false

    fun start() {
        if (running || sensor == null) return
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        running = true
    }

    fun stop() {
        if (!running) return
        sensorManager.unregisterListener(this)
        running = false
    }

    fun calibrate() {
        zeroDeg = filteredDeg
    }

    override fun onSensorChanged(event: SensorEvent) {
        val ax = event.values[0]
        val ay = event.values[1]

        val rotation = windowManager.defaultDisplay.rotation
        val (sx, sy) = when (rotation) {
            Surface.ROTATION_90 -> Pair(ay, -ax)
            Surface.ROTATION_270 -> Pair(-ay, ax)
            Surface.ROTATION_180 -> Pair(-ax, -ay)
            else -> Pair(ax, ay)
        }

        val rawDeg = Math.toDegrees(atan2(sx.toDouble(), sy.toDouble())).toFloat()
        filteredDeg += filterAlpha * (rawDeg - filteredDeg)

        var delta = filteredDeg - zeroDeg
        while (delta > 180f) delta -= 360f
        while (delta < -180f) delta += 360f

        val shaped = shapeSteer(delta / maxAngleDeg)
        val out = if (invert) -shaped else shaped
        onSteer(out.coerceIn(-1f, 1f))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun shapeSteer(n: Float): Float {
        val a = n.coerceIn(-1.5f, 1.5f)
        val sign = if (a >= 0f) 1f else -1f
        val abs = kotlin.math.abs(a)
        if (abs < deadzone) return 0f
        val scaled = ((abs - deadzone) / (1f - deadzone)).coerceIn(0f, 1f)
        return sign * scaled.pow(curveExp)
    }
}
