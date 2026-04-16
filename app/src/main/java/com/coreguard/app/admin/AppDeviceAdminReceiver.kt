package com.coreguard.app.admin

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserHandle
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * DPC Receiver — точка входа для управления Work Profile.
 *
 * Этот компонент получает системные события:
 * - Активация Device Admin
 * - Завершение provisioning рабочего профиля
 * - Деактивация (удаление профиля)
 */
class AppDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "AM_DPC"

        fun getComponentName(context: Context): ComponentName =
            ComponentName(context.applicationContext, AppDeviceAdminReceiver::class.java)
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        Log.i(TAG, "Work Profile provisioning complete")

        // Только быстрые DPM-вызовы — на IO-потоке через goAsync().
        // Тяжёлые операции (applyAllPermissions, allowAllCrossProfileWidgets)
        // выполняются в TriggerMonitorService, чтобы не блокировать провиженинг.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val dpm = context.getSystemService(DevicePolicyManager::class.java)
                val componentName = getComponentName(context)

                // 1. Включаем рабочий профиль
                dpm.setProfileEnabled(componentName)

                // 2. Выдаём себе POST_NOTIFICATIONS (Android 13+), иначе startForeground
                //    уведомление подавляется ("Suppressing notification by user request")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    dpm.setPermissionGrantState(
                        componentName,
                        context.packageName,
                        android.Manifest.permission.POST_NOTIFICATIONS,
                        DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
                    )
                    Log.i(TAG, "POST_NOTIFICATIONS granted by DPC")
                }

                // 3. Применяем дефолтные политики безопасности
                applyDefaultCrossProfilePolicies(context, dpm, componentName)

                // 4. Разрешаем установку APK из неизвестных источников
                val pm = ProfileManager(context)
                pm.setUnknownSourcesAllowed(true)

                // 5. Иконку в лаунчере рабочего профиля НЕ скрываем — пользователю
                // нужен доступ к настройкам DPC через иконку с портфельчиком.
                Log.i(TAG, "Launcher alias kept visible in work profile")

                Log.i(TAG, "Work Profile enabled with default policies")

                // 6. Автоматически открыть приложение в рабочем профиле
                try {
                    val launchIntent = Intent(context, com.coreguard.app.ui.MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(launchIntent)
                    Log.i(TAG, "Auto-launched MainActivity in work profile")
                } catch (e: Exception) {
                    Log.w(TAG, "Cannot auto-launch MainActivity", e)
                }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onEnabled(context: Context, intent: Intent) {
        Log.i(TAG, "Device Admin enabled")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Log.i(TAG, "Device Admin disabled")
    }

    /**
     * Дефолтные политики обмена между профилями:
     * - Буфер обмена: РАЗРЕШЁН (по запросу пользователя)
     * - Контакты: ЗАПРЕЩЕНЫ
     * - Передача файлов (Intents): ЗАПРЕЩЕНА
     */
    private fun applyDefaultCrossProfilePolicies(
        context: Context,
        dpm: DevicePolicyManager,
        admin: ComponentName
    ) {
        // Буфер обмена — разрешён по умолчанию
        @Suppress("DEPRECATION")
        dpm.setCrossProfileCallerIdDisabled(admin, true)   // скрыть Caller ID
        @Suppress("DEPRECATION")
        dpm.setCrossProfileContactsSearchDisabled(admin, true)  // скрыть контакты

        // Запретить share/intents между профилями: убираем все фильтры
        dpm.clearCrossProfileIntentFilters(admin)

        Log.i(TAG, "Default cross-profile policies applied: clipboard=ON, contacts=OFF, share=OFF")
    }
}
