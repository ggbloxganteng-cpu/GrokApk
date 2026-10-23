package com.mercury3.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import com.mercury3.assistant.MainActivity
import com.mercury3.assistant.R
import com.mercury3.assistant.data.SecurePrefs
import kotlin.math.abs

class FloatingBubbleService : Service() {

    private lateinit var wm: WindowManager
    private var bubble: FrameLayout? = null
    private lateinit var prefs: SecurePrefs

    companion object {
        const val CH = "grok_bubble"
        const val NID = 1001
        fun start(ctx: Context) {
            val i = Intent(ctx, FloatingBubbleService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
            else ctx.startService(i)
        }
        fun stop(ctx: Context) = ctx.stopService(Intent(ctx, FloatingBubbleService::class.java))
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = SecurePrefs(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CH, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
        startForeground(NID, notif())
        addBubble()
    }

    private fun notif(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CH)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    private fun addBubble() {
        val size = (56 * resources.displayMetrics.density).toInt()
        val params = WindowManager.LayoutParams(
            size, size,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (prefs.bubbleX >= 0) prefs.bubbleX else resources.displayMetrics.widthPixels - size - 24
            y = if (prefs.bubbleY >= 0) prefs.bubbleY else resources.displayMetrics.heightPixels / 3
        }

        val view = FrameLayout(this)
        val iv = ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher)
            setBackgroundResource(R.drawable.bubble_bg)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(10, 10, 10, 10)
            alpha = 0.92f
        }
        view.addView(iv, FrameLayout.LayoutParams(size, size))

        var ix = 0; var iy = 0; var itx = 0f; var ity = 0f; var drag = false
        view.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    ix = params.x; iy = params.y
                    itx = e.rawX; ity = e.rawY; drag = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - itx).toInt()
                    val dy = (e.rawY - ity).toInt()
                    if (abs(dx) > 8 || abs(dy) > 8) drag = true
                    params.x = ix + dx; params.y = iy + dy
                    wm.updateViewLayout(view, params); true
                }
                MotionEvent.ACTION_UP -> {
                    if (!drag) {
                        startActivity(Intent(this, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        })
                    } else {
                        val sw = resources.displayMetrics.widthPixels
                        params.x = if (params.x + size / 2 < sw / 2) 8 else sw - size - 8
                        wm.updateViewLayout(view, params)
                        prefs.bubbleX = params.x; prefs.bubbleY = params.y
                    }
                    true
                }
                else -> false
            }
        }
        bubble = view
        wm.addView(view, params)
    }

    override fun onDestroy() {
        bubble?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        bubble = null
        super.onDestroy()
    }
}
