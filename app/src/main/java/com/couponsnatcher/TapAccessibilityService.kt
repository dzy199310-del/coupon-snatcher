package com.couponsnatcher

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

/**
 * 无障碍点击服务。
 *
 * 通过 AccessibilityService.dispatchGesture 在「任意屏幕坐标」模拟一次点击，
 * 这是无需 root 在 Android 上注入全局点击的最可靠方式（需 API 24+，本项目 minSdk 26）。
 *
 * 服务连接后把自己保存到 companion.instance，供悬浮窗服务调用 performTap()。
 */
class TapAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: TapAccessibilityService? = null
    }

    override fun onServiceConnected() {
        instance = this
        super.onServiceConnected()
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 不需要处理事件
    }

    override fun onInterrupt() {
        // 不需要处理
    }

    /**
     * 在屏幕坐标 (x, y) 模拟一次点击（down+up，时长 1ms）。
     * @return 是否成功提交手势（不代表系统已执行完）
     */
    fun performTap(x: Int, y: Int): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 1L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }
}
