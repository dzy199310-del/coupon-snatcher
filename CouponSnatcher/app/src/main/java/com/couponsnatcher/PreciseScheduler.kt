package com.couponsnatcher

import android.os.Process
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 高优先级定时触发器。
 *
 * 目标时间以「墙钟毫秒（含毫秒）」表示，触发精度可达毫秒级。
 *
 * 原理：在一条 URGENT_DISPLAY 优先级的线程里：
 *  - 距离目标 > 5ms：分段 sleep，减少 CPU 占用；
 *  - 最后 5ms：进入忙等（busy spin），避免 sleep 的调度抖动；
 *  - 到达目标时刻立即回调 onFire。
 *
 * 说明：Android 普通 Handler/Timer 受系统调度影响，误差通常在数毫秒~数十毫秒；
 * 本类通过「分段 sleep + 末段忙等」把触发误差压到毫秒内（实际落地还受系统
 * dispatchGesture 调度影响，详见 README）。
 */
class PreciseScheduler(
    private val targetEpochMillis: Long,
    private val onTick: (remainingMs: Long) -> Unit,
    private val onFire: () -> Unit
) {
    private val running = AtomicBoolean(false)
    private var thread: Thread? = null

    fun start() {
        if (running.get()) return
        running.set(true)
        thread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY)
            while (running.get()) {
                val now = System.currentTimeMillis()
                val remaining = targetEpochMillis - now
                if (remaining <= 0L) {
                    onFire()
                    running.set(false)
                    break
                }
                if (remaining > 5L) {
                    val sleep = (remaining / 2).coerceAtMost(4L)
                    try {
                        Thread.sleep(sleep)
                    } catch (_: InterruptedException) {
                        break
                    }
                    onTick(kotlin.math.max(0L, remaining - sleep))
                } else {
                    // 最后 5ms：忙等，保证毫秒级触发
                    while (running.get() && System.currentTimeMillis() < targetEpochMillis) {
                        // spin
                    }
                    if (running.get()) {
                        onFire()
                        running.set(false)
                    }
                    break
                }
            }
        }, "PreciseScheduler").apply { isDaemon = true; start() }
    }

    fun cancel() {
        running.set(false)
        thread?.interrupt()
    }

    fun isRunning(): Boolean = running.get()
}
