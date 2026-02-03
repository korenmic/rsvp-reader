package com.rsvpreader

import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.LinearLayout
import kotlin.math.roundToInt

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var speedIndicator: View? = null

    private var minWps = 3
    private var maxWps = 45
    private var initialY = 0f
    private var overlayHeight = 0

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "overlay_service_channel"
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        minWps = intent?.getIntExtra("minWps", 3) ?: 3
        maxWps = intent?.getIntExtra("maxWps", 45) ?: 45
        
        if (intent?.action == "UPDATE_SPEED_RANGE") {
            return START_STICKY
        }
        
        if (overlayView == null) {
            createOverlay()
        }
        
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.overlay_notification_title))
                .setContentText(getString(R.string.overlay_notification_text))
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(pendingIntent)
                .build()
        } else {
            Notification.Builder(this)
                .setContentTitle(getString(R.string.overlay_notification_title))
                .setContentText(getString(R.string.overlay_notification_text))
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(pendingIntent)
                .build()
        }
    }

    private fun createOverlay() {
        try {
            val layoutInflater = LayoutInflater.from(this)
            overlayView = layoutInflater.inflate(R.layout.overlay_view, null)
            speedIndicator = overlayView?.findViewById(R.id.speedIndicator)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )

            params.gravity = Gravity.END or Gravity.CENTER_VERTICAL
            params.x = 0
            params.y = 0

            windowManager.addView(overlayView, params)

            overlayView?.post {
                overlayHeight = overlayView?.height ?: 300
            }

            setupTouchListener(params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupTouchListener(params: WindowManager.LayoutParams) {
        overlayView?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialY = event.rawY
                    RSVPEngine.handleTouchStart()
                    return@setOnTouchListener true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaY = initialY - event.rawY
                    val speed = calculateDiscreteSpeed(deltaY)
                    RSVPEngine.setSpeed(speed)
                    updateSpeedIndicator(speed)
                    return@setOnTouchListener true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    RSVPEngine.handleTouchEnd()
                    resetSpeedIndicator()
                    return@setOnTouchListener true
                }
            }
            false
        }
    }

    private fun calculateDiscreteSpeed(deltaY: Float): Int {
        val maxDelta = overlayHeight / 2f
        val normalizedDelta = (deltaY / maxDelta).coerceIn(-1f, 1f)
        
        return when {
            normalizedDelta < -0.55f -> -5
            normalizedDelta < -0.15f -> -1
            normalizedDelta < 0.15f -> 0
            else -> {
                val forwardNormalized = ((normalizedDelta - 0.15f) / 0.85f).coerceIn(0f, 1f)
                val range = maxWps - minWps
                (minWps + forwardNormalized * range).roundToInt()
            }
        }
    }

    private fun updateSpeedIndicator(speed: Int) {
        speedIndicator?.apply {
            val color = when {
                speed < 0 -> getColor(R.color.red)
                speed == 0 -> getColor(R.color.light_gray)
                else -> getColor(R.color.green)
            }
            setBackgroundColor(color)

            val heightPercent = when {
                speed == -5 -> 0.9f
                speed == -1 -> 0.4f
                speed == 0 -> 0.15f
                else -> {
                    val forwardNormalized = (speed - minWps).toFloat() / (maxWps - minWps)
                    (0.15f + forwardNormalized * 0.85f).coerceIn(0.15f, 1f)
                }
            }

            try {
                val layoutParams = this.layoutParams as? LinearLayout.LayoutParams
                layoutParams?.let {
                    it.weight = heightPercent
                    this.layoutParams = it
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun resetSpeedIndicator() {
        speedIndicator?.apply {
            setBackgroundColor(getColor(R.color.green))
            
            try {
                val layoutParams = this.layoutParams as? LinearLayout.LayoutParams
                layoutParams?.let {
                    it.weight = 0.5f
                    this.layoutParams = it
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            overlayView?.let {
                windowManager.removeView(it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
