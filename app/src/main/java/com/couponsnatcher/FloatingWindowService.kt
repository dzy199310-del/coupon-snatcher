package com.couponsnatcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * 悬浮窗前台服务。
 *
 * 职责：
 *  - 用 WindowManager 在任意 App 之上显示可拖拽控制面板（TYPE_APPLICATION_OVERLAY）；
 *  - 让用户在面板里设置「坐标 X/Y」「目标日期时间（含毫秒）」「重复次数」；
 *  - 用 PreciseScheduler 在精确时刻触发点击；
 *  - 点击通过 TapAccessibilityService（无障碍，无需 root）执行，root 设备额外兜底 input tap；
 *  - 提供「选坐标」：全屏透明捕获层，点一下屏幕即拾取坐标。
 */
class FloatingWindowService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var params: WindowManager.LayoutParams
    private var pickerView: View? = null
    private var scheduler: PreciseScheduler? = null

    private var lastX = 0
    private var lastY = 0
    private var repeatLeft = 1

    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val CHANNEL_ID = "coupon_snatcher_channel"
        private const val NOTIF_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!::floatingView.isInitialized) {
            showFloatingWindow()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scheduler?.cancel()
        scheduler = null
        if (::floatingView.isInitialized && floatingView.isAttachedToWindow) {
            windowManager.removeView(floatingView)
        }
        pickerView?.let {
            if (it.isAttachedToWindow) windowManager.removeView(it)
        }
        pickerView = null
        super.onDestroy()
    }

    // region 通知（前台服务必须）
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                CHANNEL_ID, "抢券精灵", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(chan)
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("抢券精灵运行中")
            .setContentText("悬浮窗已启动，等待定时点击")
            .setSmallIcon(R.drawable.ic_launcher)
            .build()
    }
    // endregion

    // region 悬浮窗
    private fun showFloatingWindow() {
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        floatingView = inflater.inflate(R.layout.floating_window, null)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 200

        windowManager.addView(floatingView, params)
        bindControls(floatingView)
    }

    private fun bindControls(view: View) {
        val dragHandle = view.findViewById<View>(R.id.dragHandle)
        val tvCountdown = view.findViewById<TextView>(R.id.tvCountdown)
        val etX = view.findViewById<EditText>(R.id.etX)
        val etY = view.findViewById<EditText>(R.id.etY)
        val etDate = view.findViewById<EditText>(R.id.etDate)
        val etTime = view.findViewById<EditText>(R.id.etTime)
        val etMs = view.findViewById<EditText>(R.id.etMs)
        val etRepeat = view.findViewById<EditText>(R.id.etRepeat)
        val btnPick = view.findViewById<Button>(R.id.btnPick)
        val btnStart = view.findViewById<Button>(R.id.btnStart)
        val btnStop = view.findViewById<Button>(R.id.btnStop)
        val btnTest = view.findViewById<Button>(R.id.btnTest)

        // 拖动悬浮窗
        dragHandle.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingView, params)
                    }
                }
                return true
            }
        })

        // 输入框聚焦时临时让窗口可聚焦，以便弹出软键盘
        val focusListener = View.OnFocusChangeListener { v, hasFocus ->
            setFocusable(hasFocus)
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            if (hasFocus) {
                imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
            } else {
                imm.hideSoftInputFromWindow(v.windowToken, 0)
            }
        }
        etX.onFocusChangeListener = focusListener
        etY.onFocusChangeListener = focusListener
        etDate.onFocusChangeListener = focusListener
        etTime.onFocusChangeListener = focusListener
        etMs.onFocusChangeListener = focusListener
        etRepeat.onFocusChangeListener = focusListener

        btnPick.setOnClickListener { startCoordinatePicker(etX, etY) }

        btnTest.setOnClickListener {
            val x = etX.text.toString().toIntOrNull()
            val y = etY.text.toString().toIntOrNull()
            if (x == null || y == null) {
                toast(getString(R.string.toast_input_invalid))
                return@setOnClickListener
            }
            doTap(x, y)
            toast(getString(R.string.toast_fired))
        }

        btnStart.setOnClickListener {
            try {
                val x = etX.text.toString().toIntOrNull()
                    ?: throw IllegalArgumentException()
                val y = etY.text.toString().toIntOrNull()
                    ?: throw IllegalArgumentException()
                val ms = (etMs.text.toString().toIntOrNull() ?: 0).coerceIn(0, 999)
                val target = parseTarget(
                    etDate.text.toString(),
                    etTime.text.toString(),
                    ms
                )
                if (target <= System.currentTimeMillis()) {
                    toast(getString(R.string.toast_time_past))
                    return@setOnClickListener
                }
                lastX = x
                lastY = y
                repeatLeft = (etRepeat.text.toString().toIntOrNull() ?: 1).coerceAtLeast(1)
                startScheduler(target, tvCountdown, btnStart, btnStop)
            } catch (e: DateTimeParseException) {
                toast(getString(R.string.toast_input_invalid))
            } catch (e: IllegalArgumentException) {
                toast(getString(R.string.toast_input_invalid))
            }
        }

        btnStop.setOnClickListener {
            scheduler?.cancel()
            scheduler = null
            tvCountdown.text = getString(R.string.countdown_idle)
            btnStart.isEnabled = true
            btnStop.isEnabled = false
        }
    }

    private fun setFocusable(focusable: Boolean) {
        if (!::params.isInitialized) return
        params.flags = if (focusable) {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        if (::floatingView.isInitialized && floatingView.isAttachedToWindow) {
            windowManager.updateViewLayout(floatingView, params)
        }
    }

    private fun parseTarget(date: String, time: String, ms: Int): Long {
        val dt = LocalDateTime.parse(
            "$date $time",
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        )
        val withMs = dt.withNano(ms * 1_000_000)
        return withMs.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun startScheduler(
        target: Long,
        tvCountdown: TextView,
        btnStart: Button,
        btnStop: Button
    ) {
        scheduler?.cancel()
        scheduler = PreciseScheduler(
            targetEpochMillis = target,
            onTick = { remaining ->
                mainHandler.post { tvCountdown.text = formatRemaining(remaining) }
            },
            onFire = {
                // 全部切到主线程执行，避免后台线程直接改 UI
                mainHandler.post {
                    // 按设定次数连点（首次在精确时刻触发，后续间隔 50ms）
                    var count = repeatLeft
                    fun tapOnce() {
                        doTap(lastX, lastY)
                        count -= 1
                        if (count > 0) {
                            mainHandler.postDelayed({ tapOnce() }, 50)
                        } else {
                            tvCountdown.text = getString(R.string.countdown_idle)
                            btnStart.isEnabled = true
                            btnStop.isEnabled = false
                            toast(getString(R.string.toast_fired))
                        }
                    }
                    tapOnce()
                }
            }
        )
        scheduler!!.start()
        btnStart.isEnabled = false
        btnStop.isEnabled = true
    }

    private fun doTap(x: Int, y: Int) {
        val a11y = TapAccessibilityService.instance
        if (a11y != null && a11y.performTap(x, y)) {
            return
        }
        // 兜底：已 root 的设备用 input tap
        try {
            Runtime.getRuntime().exec(arrayOf("su", "-c", "input tap $x $y"))
        } catch (_: Exception) {
            toast(getString(R.string.toast_no_a11y_root))
        }
    }

    private fun startCoordinatePicker(etX: EditText, etY: EditText) {
        if (pickerView?.isAttachedToWindow == true) return
        val picker = View(this).apply {
            setBackgroundColor(Color.argb(70, 0, 0, 0))
        }
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        )
        pickerView = picker
        windowManager.addView(picker, p)
        toast(getString(R.string.toast_pick_hint))
        picker.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val x = event.rawX.toInt()
                val y = event.rawY.toInt()
                etX.setText(x.toString())
                etY.setText(y.toString())
                if (picker.isAttachedToWindow) windowManager.removeView(picker)
                pickerView = null
                toast(getString(R.string.pick_done, x, y))
                true
            } else {
                false
            }
        }
    }

    private fun formatRemaining(ms: Long): String {
        val total = ms.coerceAtLeast(0)
        val m = total / 60000
        val s = (total % 60000) / 1000
        val milli = total % 1000
        return String.format("%02d:%02d.%03d", m, s, milli)
    }
    // endregion

    private fun toast(msg: String) {
        mainHandler.post {
            Toast.makeText(this@FloatingWindowService, msg, Toast.LENGTH_SHORT).show()
        }
    }
}
