package com.sourav.runtrack

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var tvTimer: TextView
    private lateinit var tvDistance: TextView
    private lateinit var tvPace: TextView
    private lateinit var tvCalories: TextView
    private lateinit var btnToggle: Button

    private val handler = Handler(Looper.getMainLooper())
    private val userWeightKg = 80.0 // Calorie calculation parameter

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val fineGranted = perms[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        if (fineGranted) {
            toggleRun()
        } else {
            Toast.makeText(this, "Location permission required to track run!", Toast.LENGTH_SHORT).show()
        }
    }

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (TrackingService.isTracking) {
                val elapsedSec = (System.currentTimeMillis() - TrackingService.startTimeMillis) / 1000
                TrackingService.elapsedTimeSeconds = elapsedSec
                updateUI(elapsedSec, TrackingService.totalDistanceMeters)
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupUI()
        handler.post(updateRunnable)
    }

    private fun setupUI() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(48, 48, 48, 48)
        }

        val title = TextView(this).apply {
            text = "SOURAV RUNTRACK"
            textSize = 22f
            setTextColor(Color.parseColor("#FF5722"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        tvTimer = TextView(this).apply {
            text = "00:00:00"
            textSize = 48f
            setTextColor(Color.WHITE)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, 60, 0, 60)
        }

        tvDistance = createStatText("Distance: 0.00 km")
        tvPace = createStatText("Avg Pace: 0.00 min/km")
        tvCalories = createStatText("Calories: 0 kcal")

        btnToggle = Button(this).apply {
            text = if (TrackingService.isTracking) "STOP RUN" else "START RUN"
            textSize = 18f
            setTextColor(Color.WHITE)
            setBackgroundColor(if (TrackingService.isTracking) Color.RED else Color.parseColor("#FF5722"))
            setPadding(64, 32, 64, 32)
            setOnClickListener { checkPermissionsAndToggle() }
        }

        val spacer = TextView(this).apply { height = 80 }

        root.addView(title)
        root.addView(tvTimer)
        root.addView(tvDistance)
        root.addView(tvPace)
        root.addView(tvCalories)
        root.addView(spacer)
        root.addView(btnToggle)

        setContentView(root)
    }

    private fun createStatText(initial: String): TextView {
        return TextView(this).apply {
            text = initial
            textSize = 20f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 16)
        }
    }

    private fun checkPermissionsAndToggle() {
        val permsToRequest = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val allGranted = permsToRequest.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            toggleRun()
        } else {
            permissionLauncher.launch(permsToRequest.toTypedArray())
        }
    }

    private fun toggleRun() {
        val intent = Intent(this, TrackingService::class.java)
        if (!TrackingService.isTracking) {
            intent.action = TrackingService.ACTION_START
            ContextCompat.startForegroundService(this, intent)
            btnToggle.text = "STOP RUN"
            btnToggle.setBackgroundColor(Color.RED)
        } else {
            intent.action = TrackingService.ACTION_STOP
            startService(intent)
            btnToggle.text = "START RUN"
            btnToggle.setBackgroundColor(Color.parseColor("#FF5722"))
        }
    }

    private fun updateUI(seconds: Long, meters: Float) {
        val hrs = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        tvTimer.text = String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)

        val km = meters / 1000.0
        tvDistance.text = String.format(Locale.US, "Distance: %.2f km", km)

        val pace = if (km > 0.01) (seconds / 60.0) / km else 0.0
        tvPace.text = String.format(Locale.US, "Avg Pace: %.2f min/km", pace)

        // Running calorie formula: ~1.036 kcal per kg per km
        val calories = (km * userWeightKg * 1.036).toInt()
        tvCalories.text = "Calories: $calories kcal"
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)
    }
}
