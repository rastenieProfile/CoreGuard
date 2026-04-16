package com.coreguard.app.trigger

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.UserManager
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AM_Boot"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.LOCKED_BOOT_COMPLETED" -> {
                val um = context.getSystemService(UserManager::class.java)
                if (um?.isManagedProfile == true) {
                    Log.i(TAG, "Boot in work profile — starting TriggerMonitorService")
                    ensureMainActivityEnabled(context)
                    try {
                        context.startForegroundService(Intent(context, TriggerMonitorService::class.java))
                    } catch (e: Exception) {
                        Log.e(TAG, "Cannot start foreground service from boot", e)
                    }
                } else {
                    Log.i(TAG, "Boot in main profile")
                }
            }
            Intent.ACTION_MANAGED_PROFILE_ADDED -> {
                // Рабочий профиль создан — иконку в основном профиле НЕ скрываем,
                // пользователю она нужна для управления
                Log.i(TAG, "Work profile added")
            }
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                // APK обновлён — срабатывает в каждом установленном профиле (вкл. рабочий).
                val um = context.getSystemService(UserManager::class.java)
                if (um?.isManagedProfile == true) {
                    // Рабочий профиль: восстанавливаем MainActivity + launcher alias + запускаем сервис
                    Log.i(TAG, "APK replaced in work profile — restoring MainActivity + starting service")
                    ensureMainActivityEnabled(context)
                    try {
                        context.startForegroundService(Intent(context, TriggerMonitorService::class.java))
                    } catch (e: Exception) {
                        Log.e(TAG, "Cannot start foreground service from package replaced", e)
                    }
                } else {
                    // Основной профиль: иконку НЕ скрываем — пользователю нужна для управления
                    Log.i(TAG, "APK replaced in main profile")
                }
            }
        }
    }

    private fun hideLauncherAlias(context: Context) {
        try {
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context.packageName,
                    "${context.packageName}.ui.MainActivityLauncher"),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            Log.i(TAG, "Launcher alias hidden in main profile")
        } catch (e: Exception) {
            Log.w(TAG, "Cannot hide launcher alias", e)
        }
    }

    private fun ensureMainActivityEnabled(context: Context) {
        try {
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context.packageName,
                    "${context.packageName}.ui.MainActivity"),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            // Включаем также launcher alias — иначе иконка в рабочем профиле останется скрытой
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context.packageName,
                    "${context.packageName}.ui.MainActivityLauncher"),
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                PackageManager.DONT_KILL_APP
            )
            Log.e(TAG, "MainActivity + MainActivityLauncher re-enabled in work profile")
        } catch (e: Exception) {
            Log.e(TAG, "Cannot re-enable MainActivity", e)
        }
    }
}
