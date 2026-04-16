package com.coreguard.app

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.UserManager
import android.util.Log

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        val um = getSystemService(UserManager::class.java) ?: return
        if (!um.isManagedProfile) return

        // Рабочий профиль: гарантируем что MainActivityLauncher включён,
        // иначе иконка с портфелем не появится в лаунчере.
        // Это единственное место, которое ГАРАНТИРОВАННО выполняется при каждом
        // запуске процесса (broadcast, service, activity) — в отличие от BootReceiver.
        val pm = packageManager
        val cn = ComponentName(packageName, "$packageName.ui.MainActivityLauncher")
        val state = pm.getComponentEnabledSetting(cn)

        // Debug: пишем в SharedPreferences для диагностики через run-as
        val debug = getSharedPreferences("debug", MODE_PRIVATE).edit()
        debug.putLong("app_oncreate_time", System.currentTimeMillis())
        debug.putBoolean("app_is_managed", true)
        debug.putInt("app_launcher_state_before", state)

        if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
            || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED
        ) {
            try {
                // Сначала DEFAULT, потом ENABLED — двойной подход для MIUI
                pm.setComponentEnabledSetting(cn,
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                    PackageManager.DONT_KILL_APP)
                pm.setComponentEnabledSetting(cn,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP)
                val after = pm.getComponentEnabledSetting(cn)
                debug.putInt("app_launcher_state_after", after)
                debug.putString("app_fix_result", "OK")
                Log.e("AM_App", "MainActivityLauncher re-enabled: $state -> $after")
            } catch (e: Exception) {
                debug.putString("app_fix_result", "ERROR: ${e.message}")
                Log.e("AM_App", "Cannot re-enable MainActivityLauncher", e)
            }
        } else {
            debug.putString("app_fix_result", "SKIP state=$state (already enabled)")
        }
        debug.apply()
    }
}
