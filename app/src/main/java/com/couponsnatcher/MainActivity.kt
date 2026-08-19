package com.couponsnatcher

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnStartFloat: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        btnStartFloat = findViewById(R.id.btnStartFloat)

        // 1) 悬浮窗权限
        findViewById<Button>(R.id.btnGrantOverlay).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.canDrawOverlays(this)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                } else {
                    toast("悬浮窗权限已授予")
                }
            } else {
                toast("Android 6.0 以下默认拥有该权限")
            }
        }

        // 2) 无障碍服务
        findViewById<Button>(R.id.btnOpenA11y).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        // 华为/鸿蒙 适配
        findViewById<Button>(R.id.btnHuawei).setOnClickListener {
            showHuaweiTips()
            requestIgnoreBatteryOptimization()
        }

        // 启动悬浮窗
        btnStartFloat.setOnClickListener {
            requestIgnoreBatteryOptimization() // 尽量保活，避免被系统回收
            val intent = Intent(this, FloatingWindowService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            moveTaskToBack(true) // 退到后台，悬浮窗继续工作
        }
    }

    private fun showHuaweiTips() {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_huawei_title)
            .setMessage(R.string.dialog_huawei_msg)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    /** 申请“忽略电池优化”，减少被华为后台管理杀掉的概率。 */
    private fun requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(POWER_SERVICE) as android.os.PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            .setData(Uri.parse("package:$packageName"))
                    )
                } catch (_: Exception) {
                    // 部分 ROM 没有该页面，退回到电池设置列表
                    try {
                        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    } catch (_: Exception) { }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val overlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
        val a11y = TapAccessibilityService.instance != null
        val ready = overlay && a11y
        tvStatus.text = if (ready) {
            getString(R.string.status_ready)
        } else {
            getString(R.string.status_not_ready)
        }
        btnStartFloat.isEnabled = ready
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
