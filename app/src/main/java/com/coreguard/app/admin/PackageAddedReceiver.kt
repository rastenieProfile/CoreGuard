package com.coreguard.app.admin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Перехватывает установку новых приложений в рабочем профиле.
 *
 * При каждой установке применяет политики разрешений с учётом
 * глобальных настроек и per-app overrides.
 */
class PackageAddedReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AM_PkgAdded"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_PACKAGE_ADDED) return
        val pkg = intent.data?.schemeSpecificPart ?: return
        if (pkg == context.packageName) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

        // Все IPC-вызовы к DPM — на IO-потоке через goAsync().
        // Иначе isProfileOwner и applyPermissionsForNewApp блокируют главный поток
        // во время каждой установки приложения → зависания.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val pm = ProfileManager(context)
                if (!pm.isProfileOwner) return@launch  // только в рабочем профиле
                pm.applyPermissionsForNewApp(pkg)
                Log.i(TAG, "Applied permission policies for newly installed $pkg")
            } finally {
                pending.finish()
            }
        }
    }
}
