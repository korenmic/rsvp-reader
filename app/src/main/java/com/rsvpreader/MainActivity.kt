package com.rsvpreader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var configLayout: View
    private lateinit var splitScreenPrompt: View
    private lateinit var rsvpWordText: TextView
    private lateinit var statusText: TextView
    private lateinit var speedText: TextView
    private lateinit var minWpsText: TextView
    private lateinit var maxWpsText: TextView
    private lateinit var btnPlay: Button
    private lateinit var btnPause: Button
    private lateinit var btnStop: Button
    private lateinit var btnToggleMode: Button
    private lateinit var btnEnableOverlay: Button
    private lateinit var btnEnableAccessibility: Button

    private var minWps = 3
    private var maxWps = 45
    private var rsvpMode = RSVPMode.NAIVE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
        checkPermissions()
        checkSplitScreenMode()
        updatePermissionUI()
        observeRSVPEngine()
    }

    private fun initViews() {
        configLayout = findViewById(R.id.configLayout)
        splitScreenPrompt = findViewById(R.id.splitScreenPrompt)
        rsvpWordText = findViewById(R.id.rsvpWordText)
        statusText = findViewById(R.id.statusText)
        speedText = findViewById(R.id.speedText)
        minWpsText = findViewById(R.id.minWpsText)
        maxWpsText = findViewById(R.id.maxWpsText)
        btnPlay = findViewById(R.id.btnPlay)
        btnPause = findViewById(R.id.btnPause)
        btnStop = findViewById(R.id.btnStop)
        btnToggleMode = findViewById(R.id.btnToggleMode)
        btnEnableOverlay = findViewById(R.id.btnEnableOverlay)
        btnEnableAccessibility = findViewById(R.id.btnEnableAccessibility)

        updateUI()
    }

    private fun setupListeners() {
        btnPlay.setOnClickListener {
            RSVPEngine.play()
            updatePlaybackButtons()
        }

        btnPause.setOnClickListener {
            RSVPEngine.pause()
            updatePlaybackButtons()
        }

        btnStop.setOnClickListener {
            RSVPEngine.stop()
            updatePlaybackButtons()
        }

        btnToggleMode.setOnClickListener {
            rsvpMode = if (rsvpMode == RSVPMode.NAIVE) RSVPMode.ORP else RSVPMode.NAIVE
            RSVPEngine.setMode(rsvpMode)
            updateUI()
        }

        findViewById<Button>(R.id.btnDecreaseMin).setOnClickListener {
            if (minWps > -5) {
                minWps--
                RSVPEngine.setSpeedRange(minWps, maxWps)
                updateUI()
                updateOverlayService()
            }
        }

        findViewById<Button>(R.id.btnIncreaseMin).setOnClickListener {
            if (minWps < maxWps) {
                minWps++
                RSVPEngine.setSpeedRange(minWps, maxWps)
                updateUI()
                updateOverlayService()
            }
        }

        findViewById<Button>(R.id.btnDecreaseMax).setOnClickListener {
            if (maxWps > minWps) {
                maxWps--
                RSVPEngine.setSpeedRange(minWps, maxWps)
                updateUI()
                updateOverlayService()
            }
        }


        btnEnableOverlay.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        btnEnableAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btnIncreaseMax).setOnClickListener {
            if (maxWps < 100) {
                maxWps++
                RSVPEngine.setSpeedRange(minWps, maxWps)
                updateUI()
                updateOverlayService()
            }
        }
    }

    private fun checkPermissions() {
        // Do not auto-redirect users into system Settings.
        // Instead, show explicit buttons in our UI.
        updatePermissionUI()
    }

    private fun updatePermissionUI() {
        val overlayOk = Settings.canDrawOverlays(this)
        val accOk = isAccessibilityServiceEnabled()

        btnEnableOverlay.visibility = if (overlayOk) View.GONE else View.VISIBLE
        btnEnableAccessibility.visibility = if (accOk) View.GONE else View.VISIBLE

        if (!overlayOk || !accOk) {
            statusText.text = getString(R.string.permissions_required)
            btnPlay.isEnabled = false
            btnPause.isEnabled = false
            btnStop.isEnabled = false
        } else {
            updatePlaybackButtons()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val services = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return services?.contains(packageName) == true
    }

    private fun checkSplitScreenMode() {
        if (isInMultiWindowMode) {
            splitScreenPrompt.visibility = View.GONE
            configLayout.visibility = View.VISIBLE
            startOverlayService()
        } else {
            splitScreenPrompt.visibility = View.VISIBLE
            configLayout.visibility = View.VISIBLE
        }
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean) {
        super.onMultiWindowModeChanged(isInMultiWindowMode)
        checkSplitScreenMode()
        updatePermissionUI()
    }

    private fun startOverlayService() {
        if (Settings.canDrawOverlays(this)) {
            val intent = Intent(this, OverlayService::class.java)
            intent.putExtra("minWps", minWps)
            intent.putExtra("maxWps", maxWps)
            startService(intent)
        }
    }

    private fun updateOverlayService() {
        if (Settings.canDrawOverlays(this)) {
            val intent = Intent(this, OverlayService::class.java)
            intent.putExtra("minWps", minWps)
            intent.putExtra("maxWps", maxWps)
            intent.action = "UPDATE_SPEED_RANGE"
            startService(intent)
        }
    }

    private fun observeRSVPEngine() {
        lifecycleScope.launch {
            RSVPEngine.currentWord.collectLatest { word ->
                if (word != null) {
                    rsvpWordText.text = word.text
                    rsvpWordText.visibility = View.VISIBLE
                    
                    if (word.isPaused) {
                        rsvpWordText.setTextColor(getColor(R.color.paused_gray))
                        configLayout.visibility = View.VISIBLE
                    } else {
                        rsvpWordText.setTextColor(
                            when {
                                word.speed < 0 -> getColor(R.color.red)
                                else -> getColor(R.color.black)
                            }
                        )
                        configLayout.visibility = View.GONE
                    }
                } else {
                    rsvpWordText.text = ""
                    rsvpWordText.visibility = View.GONE
                    configLayout.visibility = View.VISIBLE
                }
            }
        }

        lifecycleScope.launch {
            RSVPEngine.currentSpeed.collectLatest { speed ->
                speedText.text = getString(R.string.current_speed, speed)
            }
        }
    }

    private fun updateUI() {
        btnToggleMode.text = getString(
            R.string.rsvp_mode,
            if (rsvpMode == RSVPMode.NAIVE) getString(R.string.naive) else getString(R.string.orp)
        )
        minWpsText.text = minWps.toString()
        maxWpsText.text = maxWps.toString()
        updatePlaybackButtons()
    }

    private fun updatePlaybackButtons() {
        val state = RSVPEngine.getState()
        btnPlay.isEnabled = state != PlaybackState.PLAYING
        btnPause.isEnabled = state == PlaybackState.PLAYING
        btnStop.isEnabled = state != PlaybackState.STOPPED

        statusText.text = when (state) {
            PlaybackState.PLAYING -> "Playing"
            PlaybackState.PAUSED -> "Paused"
            PlaybackState.STOPPED -> "Ready"
        }
    }

    override fun onResume() {
        super.onResume()
        checkSplitScreenMode()
        updatePermissionUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopService(Intent(this, OverlayService::class.java))
    }
}
