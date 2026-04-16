package com.coreguard.app.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Локальный VPN-сервис: Kill Switch.
 *
 * BLOCK_ALL — полная блокировка трафика (чёрная дыра).
 * Активируется при срабатывании триггеров.
 */
class LocalVpnService : VpnService() {

    enum class Mode { OFF, BLOCK_ALL }

    companion object {
        private const val TAG = "AM_VPN"

        const val ACTION_START_BLOCKING   = "com.CoreGuard.vpn.START_BLOCKING"
        const val ACTION_STOP_BLOCKING    = "com.CoreGuard.vpn.STOP_BLOCKING"
        const val ACTION_STOP_SERVICE     = "com.CoreGuard.vpn.STOP_SERVICE"

        @Volatile var currentMode: Mode = Mode.OFF

        /** true когда Kill Switch активен (читается из TriggerMonitorService) */
        val isBlocking: Boolean get() = currentMode == Mode.BLOCK_ALL
    }

    private var tunInterface: ParcelFileDescriptor? = null
    private val isRunning = AtomicBoolean(false)
    private var workerThread: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_BLOCKING -> {
                stopCurrentMode()
                startBlockAll()
            }
            ACTION_STOP_BLOCKING -> {
                stopCurrentMode()
                stopSelf()
            }
            ACTION_STOP_SERVICE -> {
                stopCurrentMode()
                stopSelf()
            }
            else -> {
                // START_STICKY рестарт: intent == null — восстанавливаем режим
                val saved = getSharedPreferences("vpn_state", Context.MODE_PRIVATE)
                    .getString("desired_mode", "OFF")
                when (saved) {
                    "BLOCK_ALL" -> { stopCurrentMode(); startBlockAll() }
                    else -> { Log.w(TAG, "Unknown action: ${intent?.action}"); stopSelf() }
                }
            }
        }
        return START_STICKY
    }

    override fun onRevoke() {
        Log.i(TAG, "VPN revoked by user")
        stopCurrentMode()
        stopSelf()
    }

    override fun onDestroy() {
        stopCurrentMode()
        currentMode = Mode.OFF
        super.onDestroy()
    }

    // ─── KILL SWITCH (BLOCK_ALL) ─────────────────────────────────────────────

    private fun startBlockAll() {
        try {
            tunInterface = Builder()
                .setSession("Kill Switch")
                .addAddress("10.0.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0)
                .addDnsServer("10.0.0.1")
                .setBlocking(true)
                .establish()
            if (tunInterface == null) { Log.e(TAG, "TUN establish failed"); return }
            currentMode = Mode.BLOCK_ALL
            isRunning.set(true)
            getSharedPreferences("vpn_state", Context.MODE_PRIVATE).edit()
                .putString("desired_mode", "BLOCK_ALL").apply()
            workerThread = Thread({ blackHoleLoop() }, "AM-KillSwitch").apply { isDaemon = true; start() }
            Log.i(TAG, "Kill Switch ACTIVATED — all traffic blocked")
        } catch (e: Exception) {
            Log.e(TAG, "startBlockAll failed", e)
            stopCurrentMode()
        }
    }

    /** Чёрная дыра: все пакеты читаются и отбрасываются — трафик полностью заблокирован. */
    private fun blackHoleLoop() {
        val tun = tunInterface ?: return
        val buffer = ByteArray(32767)
        try {
            FileInputStream(tun.fileDescriptor).use { input ->
                while (isRunning.get() && !Thread.currentThread().isInterrupted) {
                    val n = input.read(buffer)
                    if (n <= 0) break
                }
            }
        } catch (e: Exception) {
            if (isRunning.get()) Log.e(TAG, "Kill switch loop error", e)
        }
        Log.d(TAG, "Kill switch loop ended")
    }

    // ─── Common ──────────────────────────────────────────────────────────────

    private fun stopCurrentMode() {
        isRunning.set(false)
        workerThread?.interrupt()
        workerThread = null
        try { tunInterface?.close() } catch (e: Exception) { Log.w(TAG, "TUN close error", e) }
        tunInterface = null
        val prev = currentMode
        currentMode = Mode.OFF
        getSharedPreferences("vpn_state", Context.MODE_PRIVATE).edit()
            .putString("desired_mode", "OFF").apply()
        if (prev != Mode.OFF) Log.i(TAG, "$prev DEACTIVATED")
    }
}
