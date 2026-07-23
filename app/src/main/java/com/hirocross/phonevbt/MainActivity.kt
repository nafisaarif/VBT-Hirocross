package com.hirocross.phonevbt

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.SystemClock
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class RepResult(
    val number: Int,
    val meanVelocity: Double,
    val peakVelocity: Double,
    val rom: Double,
    val duration: Double,
    val velocityLoss: Double
)

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private lateinit var status: TextView
    private lateinit var velocityText: TextView
    private lateinit var directionText: TextView
    private lateinit var repText: TextView
    private lateinit var meanText: TextView
    private lateinit var peakText: TextView
    private lateinit var romText: TextView
    private lateinit var lossText: TextView
    private lateinit var historyText: TextView
    private lateinit var chart: VelocityChartView

    private var calibrated = false
    private var running = false
    private var calibrating = false
    private val calibrationSamples = mutableListOf<FloatArray>()
    private val bias = FloatArray(3)

    private var lastTimestampNs = 0L
    private var filteredA = 0.0
    private var velocity = 0.0
    private var position = 0.0
    private var stillSinceMs = 0L

    private var phase = "idle"
    private var repStartMs = 0L
    private val repVelocities = mutableListOf<Double>()
    private val repPositions = mutableListOf<Double>()
    private val reps = mutableListOf<RepResult>()

    private val alpha = 0.25
    private val startThreshold = 0.45
    private val stillThreshold = 0.18
    private val minRepMs = 450L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.textStatus)
        velocityText = findViewById(R.id.textVelocity)
        directionText = findViewById(R.id.textDirection)
        repText = findViewById(R.id.textRep)
        meanText = findViewById(R.id.textMean)
        peakText = findViewById(R.id.textPeak)
        romText = findViewById(R.id.textRom)
        lossText = findViewById(R.id.textLoss)
        historyText = findViewById(R.id.textHistory)
        chart = findViewById(R.id.chart)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        findViewById<Button>(R.id.btnCalibrate).setOnClickListener { startCalibration() }
        findViewById<Button>(R.id.btnStart).setOnClickListener { startSet() }
        findViewById<Button>(R.id.btnStop).setOnClickListener { stopSet() }
        findViewById<Button>(R.id.btnReset).setOnClickListener { resetAll() }
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    private fun startCalibration() {
        calibrationSamples.clear()
        calibrating = true
        calibrated = false
        status.text = "Status: KALIBRASI — tahan HP diam dan vertikal"
    }

    private fun finishCalibration() {
        if (calibrationSamples.isEmpty()) return
        for (axis in 0..2) {
            bias[axis] = calibrationSamples.map { it[axis] }.average().toFloat()
        }
        calibrating = false
        calibrated = true
        status.text = "Status: KALIBRASI SELESAI"
    }

    private fun startSet() {
        if (!calibrated) {
            Toast.makeText(this, "Kalibrasi terlebih dahulu.", Toast.LENGTH_SHORT).show()
            return
        }
        running = true
        lastTimestampNs = 0L
        filteredA = 0.0
        velocity = 0.0
        position = 0.0
        phase = "idle"
        repVelocities.clear()
        repPositions.clear()
        status.text = "Status: RUNNING"
    }

    private fun stopSet() {
        running = false
        velocity = 0.0
        position = 0.0
        phase = "idle"
        velocityText.text = "0.00"
        directionText.text = "Arah: DIAM"
        status.text = "Status: FINISHED"
    }

    private fun resetAll() {
        stopSet()
        reps.clear()
        chart.clear()
        repText.text = "Rep: 0"
        meanText.text = "Mean: 0.00 m/s"
        peakText.text = "Peak: 0.00 m/s"
        romText.text = "ROM: 0.00 m"
        lossText.text = "Loss: 0.0%"
        historyText.text = "Riwayat rep akan muncul di sini."
    }

    override fun onSensorChanged(event: SensorEvent) {
        val values = event.values.copyOf()

        if (calibrating) {
            calibrationSamples.add(values)
            status.text = "Status: KALIBRASI ${calibrationSamples.size}/150"
            if (calibrationSamples.size >= 150) finishCalibration()
            return
        }

        if (!running) {
            lastTimestampNs = event.timestamp
            return
        }

        if (lastTimestampNs == 0L) {
            lastTimestampNs = event.timestamp
            return
        }

        val dt = (event.timestamp - lastTimestampNs) / 1_000_000_000.0
        lastTimestampNs = event.timestamp
        if (dt <= 0.0 || dt > 0.1) return

        // Smartphone is expected to be vertical; Y is treated as main movement axis.
        val rawA = values[1] - bias[1]
        filteredA = alpha * rawA + (1.0 - alpha) * filteredA

        val nowMs = SystemClock.elapsedRealtime()
        if (abs(filteredA) < stillThreshold) {
            if (stillSinceMs == 0L) stillSinceMs = nowMs
            if (nowMs - stillSinceMs > 180L) {
                velocity *= 0.4
                if (abs(velocity) < 0.03) velocity = 0.0
            }
        } else {
            stillSinceMs = 0L
        }

        velocity += filteredA * dt
        velocity = max(-3.5, min(3.5, velocity))
        position += velocity * dt

        velocityText.text = "%.2f".format(abs(velocity))
        directionText.text = when {
            velocity > 0.05 -> "Arah: NAIK"
            velocity < -0.05 -> "Arah: TURUN"
            else -> "Arah: DIAM"
        }

        chart.addValue(abs(velocity).toFloat())
        detectRep(nowMs)
    }

    private fun detectRep(nowMs: Long) {
        if (phase == "idle" && abs(filteredA) >= startThreshold) {
            phase = if (velocity >= 0) "up" else "down"
            repStartMs = nowMs
            repVelocities.clear()
            repPositions.clear()
            repPositions.add(position)
        }

        if (phase == "down") {
            repPositions.add(position)
            if (velocity > 0.05) {
                phase = "up"
                repVelocities.clear()
            }
        }

        if (phase == "up") {
            repVelocities.add(max(0.0, velocity))
            repPositions.add(position)
            val duration = nowMs - repStartMs
            if (abs(filteredA) < stillThreshold && abs(velocity) < 0.08 && duration >= minRepMs) {
                finalizeRep(nowMs)
            }
        }
    }

    private fun finalizeRep(nowMs: Long) {
        val positives = repVelocities.filter { it > 0.0 }
        if (positives.isEmpty()) {
            phase = "idle"
            return
        }

        val mean = positives.average()
        val peak = positives.maxOrNull() ?: 0.0
        val rom = if (repPositions.isNotEmpty()) {
            (repPositions.maxOrNull()!! - repPositions.minOrNull()!!).let { abs(it) }
        } else 0.0
        val duration = (nowMs - repStartMs) / 1000.0
        val first = reps.firstOrNull()?.meanVelocity ?: mean
        val loss = max(0.0, (first - mean) / first * 100.0)

        val rep = RepResult(reps.size + 1, mean, peak, rom, duration, loss)
        reps.add(rep)

        repText.text = "Rep: ${reps.size}"
        meanText.text = "Mean: %.2f m/s".format(mean)
        peakText.text = "Peak: %.2f m/s".format(peak)
        romText.text = "ROM: %.2f m".format(rom)
        lossText.text = "Loss: %.1f%%".format(loss)

        historyText.text = reps.joinToString("\n") {
            "Rep ${it.number} — Mean %.2f | Peak %.2f | ROM %.2f | Loss %.1f%%"
                .format(it.meanVelocity, it.peakVelocity, it.rom, it.velocityLoss)
        }

        phase = "idle"
        velocity = 0.0
        position = 0.0
        repVelocities.clear()
        repPositions.clear()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
