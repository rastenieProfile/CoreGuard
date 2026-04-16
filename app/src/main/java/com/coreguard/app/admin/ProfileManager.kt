package com.coreguard.app.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager
import android.util.Log
import com.coreguard.app.admin.PermState

/**
 * Управляет созданием и настройкой Work Profile.
 */
class ProfileManager(private val context: Context) {

    companion object {
        private const val TAG = "AM_Profile"
    }

    private val dpm: DevicePolicyManager =
        context.getSystemService(DevicePolicyManager::class.java)

    private val adminComponent: ComponentName =
        AppDeviceAdminReceiver.getComponentName(context)

    /** Проверяет, является ли текущее приложение Profile Owner */
    val isProfileOwner: Boolean
        get() = dpm.isProfileOwnerApp(context.packageName)

    /** Проверяет, существует ли уже управляемый профиль */
    val isManagedProfileExists: Boolean
        get() = dpm.isProvisioningAllowed(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE).not()
                && isProfileOwner

    /**
     * Создаёт Intent для запуска provisioning рабочего профиля.
     * Пользователь увидит системный диалог подтверждения.
     */
    fun createProvisioningIntent(): Intent {
        return Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE).apply {
            putExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                adminComponent
            )
        }
    }

    /** Можно ли создать Work Profile на этом устройстве */
    fun canProvision(): Boolean {
        return dpm.isProvisioningAllowed(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)
    }

    // ─── Управление приложениями в рабочем профиле ───

    /**
     * Замораживает (suspend) указанные пакеты.
     * Приложения остаются видимыми, но неактивными (серая иконка).
     */
    fun suspendPackages(packageNames: List<String>): List<String> {
        if (!isProfileOwner) return packageNames

        val failed = dpm.setPackagesSuspended(
            adminComponent,
            packageNames.toTypedArray(),
            true // suspended
        )
        Log.i(TAG, "Suspended ${packageNames.size - failed.size}/${packageNames.size} packages")
        return failed.toList()
    }

    /**
     * Размораживает (unsuspend) указанные пакеты.
     */
    fun unsuspendPackages(packageNames: List<String>): List<String> {
        if (!isProfileOwner) return packageNames

        val failed = dpm.setPackagesSuspended(
            adminComponent,
            packageNames.toTypedArray(),
            false // не suspended
        )
        Log.i(TAG, "Unsuspended ${packageNames.size - failed.size}/${packageNames.size} packages")
        return failed.toList()
    }

    /**
     * Полностью скрывает приложение (исчезает с экрана).
     */
    fun hidePackage(packageName: String, hidden: Boolean) {
        if (!isProfileOwner) return
        dpm.setApplicationHidden(adminComponent, packageName, hidden)
        Log.i(TAG, "Package $packageName hidden=$hidden")
    }

    /**
     * Автоматически выдаёт runtime-разрешение приложению через DPC.
     * Не показывает диалог пользователю.
     */
    fun grantSelfPermission(permission: String): Boolean {
        if (!isProfileOwner) return false
        return try {
            val granted = dpm.setPermissionGrantState(
                adminComponent,
                context.packageName,
                permission,
                DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
            )
            Log.i(TAG, "Auto-grant $permission: $granted")
            granted
        } catch (e: Exception) {
            Log.w(TAG, "Cannot auto-grant $permission", e)
            false
        }
    }

    /**
     * Включает Location Services через DPC (требуется для чтения Wi-Fi SSID на Android 10+).
     */
    fun ensureLocationEnabled() {
        if (!isProfileOwner) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                dpm.setLocationEnabled(adminComponent, true)
                Log.i(TAG, "Location enabled via DPC")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cannot enable location via DPC", e)
        }
    }

    /**
     * Снимает принудительный Always-On VPN в рабочем профиле,
     * чтобы приложения ходили в сеть напрямую.
     */
    fun clearWorkProfileVpn() {
        if (!isProfileOwner) return
        try {
            dpm.setAlwaysOnVpnPackage(adminComponent, null, false)
            Log.i(TAG, "Cleared always-on VPN for work profile")
        } catch (e: Exception) {
            Log.w(TAG, "Cannot clear always-on VPN", e)
        }
    }

    // ─── Установка из неизвестных источников ───

    /**
     * Разрешает или запрещает установку APK из неизвестных источников
     * в рабочем профиле.
     */
    fun setUnknownSourcesAllowed(allowed: Boolean) {
        if (!isProfileOwner) return
        if (allowed) {
            dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
        } else {
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
        }
        Log.i(TAG, "Unknown sources allowed: $allowed")
    }

    // ─── Cross-profile виджеты ───

    /**
     * Разрешает виджеты всех установленных приложений рабочего профиля
     * для отображения на домашнем экране основного профиля.
     */
    fun allowAllCrossProfileWidgets() {
        if (!isProfileOwner) return
        val pm = context.packageManager
        val already = dpm.getCrossProfileWidgetProviders(adminComponent).toSet()
        val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        var added = 0
        for (app in installed) {
            if (app.packageName !in already) {
                dpm.addCrossProfileWidgetProvider(adminComponent, app.packageName)
                added++
            }
        }
        Log.i(TAG, "Cross-profile widgets: allowed $added new providers (total ${already.size + added})")
    }

    // ─── Политики обмена между профилями ───

    fun setClipboardSharing(enabled: Boolean) {
        if (!isProfileOwner) return
        if (!enabled) {
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_CROSS_PROFILE_COPY_PASTE)
        } else {
            dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_CROSS_PROFILE_COPY_PASTE)
        }
        Log.i(TAG, "Clipboard sharing: $enabled")
    }

    fun setContactsSharing(enabled: Boolean) {
        if (!isProfileOwner) return
        @Suppress("DEPRECATION")
        dpm.setCrossProfileCallerIdDisabled(adminComponent, !enabled)
        @Suppress("DEPRECATION")
        dpm.setCrossProfileContactsSearchDisabled(adminComponent, !enabled)
        Log.i(TAG, "Contacts sharing: $enabled")
    }

    // ─── Защита от слежки ───

    /**
     * Запрещает всем приложениям в рабочем профиле делать скриншоты / записывать экран.
     *
     * Блокирует сторонние снимки экрана (приложения не смогут сфотографировать
     * другие приложения в фоне). На функциональность приложений не влияет.
     */
    fun setWorkProfileScreenCaptureDisabled(disabled: Boolean) {
        if (!isProfileOwner) return
        try {
            dpm.setScreenCaptureDisabled(adminComponent, disabled)
            Log.i(TAG, "Screen capture disabled=$disabled")
        } catch (e: Exception) {
            Log.w(TAG, "Cannot set screen capture disabled", e)
        }
    }

    /**
     * Устанавливает глобальную политику READ_PHONE_STATE.
     * Per-app overrides сохраняют свои исключения — затрагиваются только приложения на AUTO.
     */
    @Synchronized
    fun restrictPhoneStatePermission(restricted: Boolean) {
        if (!isProfileOwner) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val overrides = AppPermOverrides.load(context)
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        var count = 0
        for (app in installed) {
            if (app.packageName == context.packageName) continue
            val ovr = overrides[app.packageName] ?: AppPermOverride()
            if (ovr.readPhoneState != PermState.AUTO) continue // не трогаем переопределённые
            val state = if (restricted)
                DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED
            else
                DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT
            try {
                dpm.setPermissionGrantState(adminComponent, app.packageName,
                    "android.permission.READ_PHONE_STATE", state)
                count++
            } catch (_: Exception) {}
        }
        Log.i(TAG, "READ_PHONE_STATE restricted=$restricted for $count apps (AUTO only)")
    }

    // ─── Скрытие иконки из лаунчера ───

    /**
     * Скрывает/показывает иконку CoreGuard в лаунчере рабочего профиля.
     *
     * При hidden=true иконка исчезает — открыть приложение можно только
     * через tap на foreground-уведомление сервиса. Сервис при этом
     * продолжает работать без изменений.
     *
     * Состояние сохраняем в SharedPreferences чтобы переживало перезапуски.
     */
    fun hideLauncherIcon(hidden: Boolean) {
        val state = if (hidden)
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        else
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        try {
            // Отключаем только alias (MainActivityLauncher), а не саму MainActivity.
            // Иначе PendingIntent уведомлений перестаёт работать.
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context.packageName, "${context.packageName}.ui.MainActivityLauncher"),
                state,
                android.content.pm.PackageManager.DONT_KILL_APP
            )
            Log.e(TAG, "Launcher icon hidden=$hidden state=$state")
        } catch (e: Exception) {
            Log.e(TAG, "Cannot toggle launcher icon", e)
        }
    }

    // ─── Скрытие от других приложений в рабочем профиле ───

    /**
     * Запрещает QUERY_ALL_PACKAGES всем приложениям в рабочем профиле.
     *
     * Без этого разрешения приложение не может вызвать getInstalledPackages()
     * и обнаружить наш пакет через PackageManager (Android 11+, API 30+).
     *
     * ВАЖНО: работает для приложений, целевой SDK которых ≥ 30.
     * getProfileOwner() всё равно расскажет о нас — это unavoidable системный API.
     */
    fun denyPackageEnumerationToManagedApps() {
        applyAllPermissions()
    }

    // ─── Глобальные настройки разрешений (data-class для чистого API) ───────────

    private data class GlobalPerms(
        val phoneState: Boolean,
        val location: Boolean,
        val camera: Boolean,
        val microphone: Boolean,
        val contacts: Boolean,
        val sms: Boolean,
        val callLog: Boolean,
        val accounts: Boolean,
        val bluetoothScan: Boolean,
    ) {
        companion object {
            fun fromPrefs(prefs: android.content.SharedPreferences) = GlobalPerms(
                phoneState    = prefs.getBoolean("pref_restrict_phone_state",    false),
                location      = prefs.getBoolean("pref_restrict_location",       false),
                camera        = prefs.getBoolean("pref_restrict_camera",         false),
                microphone    = prefs.getBoolean("pref_restrict_microphone",     false),
                contacts      = prefs.getBoolean("pref_restrict_contacts",       false),
                sms           = prefs.getBoolean("pref_restrict_sms",            false),
                callLog       = prefs.getBoolean("pref_restrict_call_log",       false),
                accounts      = prefs.getBoolean("pref_restrict_accounts",       false),
                bluetoothScan = prefs.getBoolean("pref_restrict_bluetooth_scan", false),
            )
        }
    }

    // ─── Единая точка применения политик разрешений ───

    /**
     * Применяет все permission-политики ко всем управляемым приложениям.
     * Вызывается при старте сервиса, provisioning и изменении любой настройки.
     */
    @Synchronized
    fun applyAllPermissions() {
        if (!isProfileOwner) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

        // Подавляем системные уведомления PermissionController об автовыдаче разрешений:
        // при AUTO_GRANT система не показывает пользователю уведомления.
        try {
            dpm.setPermissionPolicy(adminComponent, DevicePolicyManager.PERMISSION_POLICY_AUTO_GRANT)
        } catch (e: Exception) {
            Log.w(TAG, "setPermissionPolicy failed", e)
        }

        val prefs = context.getSharedPreferences("trigger_config", Context.MODE_PRIVATE)
        val globals = GlobalPerms.fromPrefs(prefs)
        val overrides = AppPermOverrides.load(context)
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        var applied = 0
        for (app in installed) {
            if (app.packageName == context.packageName) continue
            val ovr = overrides[app.packageName] ?: AppPermOverride()
            applyForPackage(app.packageName, ovr, globals)
            applied++
        }
        Log.i(TAG, "applyAllPermissions: $applied apps")

        // Запрещаем личному профилю читать уведомления рабочего через NotificationListenerService.
        // Метод доступен с API 33 (Android 13). Внутри рабочего профиля слушатели
        // и так требуют ручного включения в Настройках, которое DPC может ограничить.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                dpm.setPermittedCrossProfileNotificationListeners(
                    adminComponent, listOf(context.packageName)
                )
            } catch (e: Exception) {
                Log.w(TAG, "setPermittedCrossProfileNotificationListeners failed", e)
            }
        }
    }

    /**
     * Применяет политики для одного приложения — при установке или изменении per-app override.
     */
    fun applyPermissionsForNewApp(pkg: String) {
        if (!isProfileOwner) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val prefs = context.getSharedPreferences("trigger_config", Context.MODE_PRIVATE)
        val globals = GlobalPerms.fromPrefs(prefs)
        val ovr = AppPermOverrides.getForPackage(context, pkg)
        applyForPackage(pkg, ovr, globals)
        Log.i(TAG, "applyPermissionsForNewApp: $pkg")
    }

    private fun applyForPackage(pkg: String, ovr: AppPermOverride, globals: GlobalPerms) {
        val qapState = when (ovr.queryAllPackages) {
            PermState.ALLOW -> DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT
            else            -> DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED
        }
        val rpsState = when (ovr.readPhoneState) {
            PermState.ALLOW -> DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT
            PermState.DENY  -> DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED
            PermState.AUTO  -> if (globals.phoneState) DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED
                               else DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT
        }
        fun ps(s: PermState, globalRestricted: Boolean): Int = when (s) {
            PermState.DENY  -> DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED
            PermState.ALLOW -> DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
            PermState.AUTO  -> if (globalRestricted) DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED
                               else DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT
        }
        val permsMap = mutableMapOf(
            "android.permission.QUERY_ALL_PACKAGES"         to qapState,
            "android.permission.READ_PHONE_STATE"           to rpsState,
            "android.permission.CAMERA"                     to ps(ovr.camera, globals.camera),
            "android.permission.RECORD_AUDIO"               to ps(ovr.recordAudio, globals.microphone),
            "android.permission.ACCESS_FINE_LOCATION"       to ps(ovr.fineLocation, globals.location),
            "android.permission.ACCESS_BACKGROUND_LOCATION" to ps(ovr.backgroundLocation, globals.location),
            "android.permission.READ_CONTACTS"              to ps(ovr.readContacts, globals.contacts),
            "android.permission.READ_SMS"                   to ps(ovr.sms, globals.sms),
            "android.permission.SEND_SMS"                   to ps(ovr.sms, globals.sms),
            "android.permission.READ_CALL_LOG"              to ps(ovr.callLog, globals.callLog),
            "android.permission.GET_ACCOUNTS"               to ps(ovr.accounts, globals.accounts),
        )
        // BLUETOOTH_SCAN добавлен в API 31 (Android 12)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permsMap["android.permission.BLUETOOTH_SCAN"] = ps(ovr.bluetoothScan, globals.bluetoothScan)
        }
        permsMap.forEach { (perm, state) ->
            try {
                dpm.setPermissionGrantState(adminComponent, pkg, perm, state)
            } catch (e: SecurityException) {
                Log.w(TAG, "Cannot set $perm for $pkg on API ${Build.VERSION.SDK_INT}: ${e.message}")
            } catch (_: Exception) {}
        }
    }

    fun setIntentSharing(enabled: Boolean) {
        if (!isProfileOwner) return
        dpm.clearCrossProfileIntentFilters(adminComponent)
        if (enabled) {
            // Разрешить основные share-intents между профилями
            val filter = android.content.IntentFilter(Intent.ACTION_SEND).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addDataType("*/*")
            }
            dpm.addCrossProfileIntentFilter(
                adminComponent,
                filter,
                DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED
                        or DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT
            )
        }
        Log.i(TAG, "Intent/file sharing: $enabled")
    }

    // ─── BT / NFC / Uninstall ────────────────────────────────────────────────

    /**
     * Запрещает/разрешает передачу файлов через Bluetooth из рабочего профиля.
     * Работает для Profile Owner. Не запрещает сам BT — только file sharing.
     */
    fun setBluetoothSharingRestricted(restricted: Boolean) {
        if (!isProfileOwner) return
        if (restricted) {
            dpm.addUserRestriction(adminComponent, android.os.UserManager.DISALLOW_BLUETOOTH_SHARING)
        } else {
            dpm.clearUserRestriction(adminComponent, android.os.UserManager.DISALLOW_BLUETOOTH_SHARING)
        }
        Log.i(TAG, "Bluetooth sharing restricted: $restricted")
    }

    /**
     * Запрещает/разрешает NFC Beam (отправка данных через NFC прикосновением).
     */
    fun setNfcBeamRestricted(restricted: Boolean) {
        if (!isProfileOwner) return
        if (restricted) {
            dpm.addUserRestriction(adminComponent, android.os.UserManager.DISALLOW_OUTGOING_BEAM)
        } else {
            dpm.clearUserRestriction(adminComponent, android.os.UserManager.DISALLOW_OUTGOING_BEAM)
        }
        Log.i(TAG, "NFC beam restricted: $restricted")
    }

    /**
     * Запрещает/разрешает удаление всех установленных приложений в рабочем профиле.
     * Применяется ко всем приложениям кроме самого CoreGuard.
     */
    fun setUninstallBlockedForAllApps(blocked: Boolean) {
        if (!isProfileOwner) return
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        var count = 0
        for (app in installed) {
            if (app.packageName == context.packageName) continue
            try {
                dpm.setUninstallBlocked(adminComponent, app.packageName, blocked)
                count++
            } catch (e: Exception) {
                Log.w(TAG, "Cannot set uninstall blocked for ${app.packageName}", e)
            }
        }
        Log.i(TAG, "Uninstall blocked=$blocked for $count apps")
    }
}
