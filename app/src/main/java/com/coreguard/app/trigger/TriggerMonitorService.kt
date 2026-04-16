package com.coreguard.app.trigger

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.coreguard.app.R
import com.coreguard.app.admin.ProfileManager
import com.coreguard.app.vpn.LocalVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Foreground-сервис: мониторинг триггеров (VPN, Wi-Fi, Mobile, Оператор).
 *
 * Логика простая:
 * - Любой триггер активен → block network + freeze apps
 * - Ни одного триггера → unfreeze apps + unblock network
 */
class TriggerMonitorService : Service() {

    companion object {
        private const val TAG = "AM_Monitor"
        private const val CHANNEL_ID = "AM_Monitor_v2"
        private const val NOTIFICATION_ID = 1

        @Volatile
        var isRunning = false
            private set

        /** true когда триггер сработал и защита активна */
        @Volatile
        var isTriggered = false
            private set
    }

    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var wifiManager: WifiManager
    private lateinit var profileManager: ProfileManager
    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var currentConfig = TriggerConfig()
    private var managedPackages = listOf<String>()

    // Уровни защиты (от слабого к строгому): самый строгий активный триггер побеждает.
    // При уходе строгого триггера — автоматический переход на следующий по строгости.
    private enum class ProtectionLevel {
        NONE,           // всё разморожено, VPN выключен
        NORMAL,         // freeze managed apps
        KILL_SWITCH,    // freeze managed + VPN kill switch
        CORPORATE       // freeze ALL apps + VPN kill switch (строжайший)
    }

    private var currentLevel = ProtectionLevel.NONE
    private var freezeJob: kotlinx.coroutines.Job? = null
    private var reevaluatePending = false
    private val protectionLock = Any()
    private val reevaluateRunnable = Runnable {
        reevaluatePending = false
        doReevaluate()
    }

    // Периодический опрос — NetworkCallback не видит VPN из основного профиля,
    // поэтому раз в 5 сек проверяем через NetworkInterface.
    private val periodicReevaluateRunnable = object : Runnable {
        override fun run() {
            doReevaluate()
            handler.postDelayed(this, 5_000L)
        }
    }

    // Слушатель изменений настроек — перезагружает конфигурацию триггеров при любом изменении
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null && (key.startsWith("trigger_") || key == "kill_switch" ||
                    key == "wifi_ssid_list" || key == "operator_list" ||
                    key == "wifi_force_kill_switch" || key == "corporate_triggers" ||
                    key == "corporate_wifi_ssids" || key == "corporate_operators" ||
                    key == "managed_packages")) {
            Log.d(TAG, "Config key changed: $key → reloading")
            loadConfig()
            scheduleReevaluate()
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            scheduleReevaluate()
        }

        override fun onAvailable(network: Network) {
            scheduleReevaluate()
        }

        override fun onLost(network: Network) {
            scheduleReevaluate()
        }
    }

    private val vpnNetworkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            scheduleReevaluate()
        }

        override fun onLost(network: Network) {
            scheduleReevaluate()
        }
    }

    override fun onCreate() {
        super.onCreate()
        connectivityManager = getSystemService(ConnectivityManager::class.java)
        wifiManager = applicationContext.getSystemService(WifiManager::class.java)
        profileManager = ProfileManager(this)

        // Profile Owner: DPC-грант разрешений для чтения SSID и показа уведомлений.
        // - POST_NOTIFICATIONS (API 33+) — без него уведомление подавляется
        // - NEARBY_WIFI_DEVICES (API 33+) — NOT sensor permission, DPC может грантить
        // - ACCESS_FINE_LOCATION (API < 31) — на старых API DPC может, на 31+ уже нет (sensor)
        if (profileManager.isProfileOwner) {
            try {
                val dpm = getSystemService(android.app.admin.DevicePolicyManager::class.java)
                val admin = com.coreguard.app.admin.AppDeviceAdminReceiver.getComponentName(this)
                val grant = android.app.admin.DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    dpm.setPermissionGrantState(admin, packageName, Manifest.permission.POST_NOTIFICATIONS, grant)
                    dpm.setPermissionGrantState(admin, packageName, Manifest.permission.NEARBY_WIFI_DEVICES, grant)
                    Log.d(TAG, "DPC-granted POST_NOTIFICATIONS + NEARBY_WIFI_DEVICES")
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    dpm.setPermissionGrantState(admin, packageName, Manifest.permission.ACCESS_FINE_LOCATION, grant)
                    dpm.setPermissionGrantState(admin, packageName, Manifest.permission.ACCESS_COARSE_LOCATION, grant)
                    dpm.setPermissionGrantState(admin, packageName, Manifest.permission.ACCESS_BACKGROUND_LOCATION, grant)
                    Log.d(TAG, "DPC-granted FINE/COARSE/BACKGROUND_LOCATION (API < 31)")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cannot self-grant permissions via DPC", e)
            }
        }

        createNotificationChannel()
        try {
            startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.notification_monitoring)))
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
            stopSelf()
            return
        }
        registerNetworkCallback()

        // Подписка на изменения настроек — триггеры подхватываются «на лету»
        getSharedPreferences("trigger_config", MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(prefsListener)

        isRunning = true
        Log.i(TAG, "TriggerMonitorService started")

        // Периодический опрос VPN-интерфейсов (NetworkCallback не видит VPN из основного профиля)
        handler.postDelayed(periodicReevaluateRunnable, 5_000L)

        serviceScope.launch {
            // Принудительно включаем MainActivity (могла быть отключена старым багом hideLauncherIcon).
            // Без этого PendingIntent уведомления не может открыть приложение.
            ensureMainActivityEnabled()

            // Разрешаем cross-profile виджеты (тяжёлая операция, вынесена из DPC ресивера)
            profileManager.allowAllCrossProfileWidgets()

            // Применяем политики разрешений (тяжёлая операция, вынесена из DPC ресивера)
            profileManager.applyAllPermissions()

            // Запрет скриншотов в рабочем профиле (если включено)
            val settingsPrefs = getSharedPreferences("trigger_config", MODE_PRIVATE)
            if (settingsPrefs.getBoolean("pref_screen_capture_disabled", false)) {
                profileManager.setWorkProfileScreenCaptureDisabled(true)
            }
        }
    }

    override fun onDestroy() {
        isRunning = false
        isTriggered = false
        serviceScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        handler.removeCallbacksAndMessages(null)
        try {
            getSharedPreferences("trigger_config", MODE_PRIVATE)
                .unregisterOnSharedPreferenceChangeListener(prefsListener)
        } catch (_: Exception) {}
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering callback", e)
        }
        try {
            connectivityManager.unregisterNetworkCallback(vpnNetworkCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering VPN callback", e)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        loadConfig()
        // Восстанавливаем состояние при рестарте через START_STICKY
        val prefs = getSharedPreferences("trigger_state", MODE_PRIVATE)
        val wasTriggered = prefs.getBoolean("is_triggered", false)
        if (wasTriggered && currentLevel == ProtectionLevel.NONE) {
            Log.i(TAG, "Restoring triggered state after service restart")
        }
        doReevaluate()
        // Исправляем состояние компонентов при каждом запуске/рестарте сервиса.
        // MainActivity могла быть отключена старым багом hideLauncherIcon.
        serviceScope.launch { ensureMainActivityEnabled() }
        return START_STICKY
    }

    private fun ensureMainActivityEnabled() {
        try {
            packageManager.setComponentEnabledSetting(
                android.content.ComponentName(packageName, "${packageName}.ui.MainActivity"),
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                android.content.pm.PackageManager.DONT_KILL_APP
            )
        } catch (_: Exception) {}
    }



    private fun loadConfig() {
        val prefs = getSharedPreferences("trigger_config", MODE_PRIVATE)
        val wifiSsidRaw = prefs.getString("wifi_ssid_list", "") ?: ""

        currentConfig = TriggerConfig(
            triggerOnVpn = prefs.getBoolean("trigger_vpn", true),
            triggerOnWifi = prefs.getBoolean("trigger_wifi", false),
            wifiSsidList = wifiSsidRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
            triggerOnMobile = prefs.getBoolean("trigger_mobile", true),
            triggerOnOperator = prefs.getBoolean("trigger_operator", false),
            operatorList = (prefs.getString("operator_list", "") ?: "")
                .split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
            wifiForceKillSwitch = prefs.getBoolean("wifi_force_kill_switch", false),
            killSwitch = prefs.getBoolean("kill_switch", false),
            corporateTriggers = prefs.getStringSet("corporate_triggers", emptySet()) ?: emptySet(),
            corporateWifiSsids = prefs.getStringSet("corporate_wifi_ssids", emptySet()) ?: emptySet(),
            corporateOperators = prefs.getStringSet("corporate_operators", emptySet()) ?: emptySet()
        )

        managedPackages = prefs.getStringSet("managed_packages", emptySet())
            ?.toList() ?: emptyList()

        Log.d(TAG, "Config loaded: $currentConfig, packages: ${managedPackages.size}")
    }

    private fun getCurrentNetworkState(): NetworkState {
        // WiFi/Cellular определяем по АКТИВНОЙ (default) сети.
        // allNetworks содержит ВСЕ зарегистрированные сети, включая неактивный CELL при WiFi,
        // что приводило к ложному срабатыванию триггера мобильной сети.
        val activeNetwork = connectivityManager.activeNetwork
        val activeCaps = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
        val isWifi = activeCaps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isCellular = activeCaps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        Log.d(TAG, "Active network: wifi=$isWifi cellular=$isCellular")

        // VPN ищем по ВСЕМ сетям — VPN может не быть default route
        val allNetworks = connectivityManager.allNetworks
        var hasExternalVpn = false
        var vpnTransportCount = 0

        for (network in allNetworks) {
            val caps = connectivityManager.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                vpnTransportCount++
                val ourVpnActive = LocalVpnService.currentMode != LocalVpnService.Mode.OFF
                if (!ourVpnActive) {
                    hasExternalVpn = true
                }
            }
        }

        // Если наш VPN активен, но VPN-транспортов больше одного — есть внешний
        val ourVpnActive = LocalVpnService.currentMode != LocalVpnService.Mode.OFF
        if (ourVpnActive && vpnTransportCount > 1) {
            hasExternalVpn = true
        }
        Log.d(TAG, "VPN detection: ourVpnActive=$ourVpnActive vpnTransportCount=$vpnTransportCount hasExternalVpn=$hasExternalVpn")

        // Fallback: ConnectivityManager в рабочем профиле может не видеть VPN из основного.
        // Проверяем сетевые интерфейсы (tun/ppp/tap) — они видны на уровне ядра из любого профиля.
        if (!hasExternalVpn) {
            val vpnByInterface = detectVpnByInterface()
            if (vpnByInterface) {
                hasExternalVpn = true
                Log.d(TAG, "VPN detected via NetworkInterface fallback")
            }
        }

        val ssid = if (isWifi) getWifiSsid() else null
        val operator = getOperatorName()

        return NetworkState(
            isVpnActive = hasExternalVpn,
            isWifiConnected = isWifi,
            wifiSsid = ssid,
            isCellularConnected = isCellular,
            operatorName = operator
        )
    }

    private fun getWifiSsid(): String? {
        // 1. ConnectivityManager transportInfo (API 29+, основной путь)
        try {
            val caps = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            val wifiInfo = caps?.transportInfo as? android.net.wifi.WifiInfo
            val ssid = wifiInfo?.ssid?.removeSurrounding("\"")
            if (!ssid.isNullOrEmpty() && ssid != "<unknown ssid>" && ssid != "0x") {
                Log.d(TAG, "SSID via CM transportInfo: $ssid")
                return ssid
            }
        } catch (e: Exception) {
            Log.w(TAG, "CM transportInfo SSID error", e)
        }

        // 2. WifiManager connectionInfo (legacy fallback)
        try {
            @Suppress("DEPRECATION")
            val info = wifiManager.connectionInfo
            val ssid = info?.ssid?.removeSurrounding("\"")
            if (!ssid.isNullOrEmpty() && ssid != "<unknown ssid>" && ssid != "0x") {
                Log.d(TAG, "SSID via WifiManager: $ssid")
                return ssid
            }
        } catch (e: Exception) {
            Log.w(TAG, "WifiManager SSID error", e)
        }

        // Ни один метод не вернул SSID — логируем состояние пермишенов для диагностики
        val hasFine = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasNearby = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(this, "android.permission.NEARBY_WIFI_DEVICES") == PackageManager.PERMISSION_GRANTED
        Log.w(TAG, "Cannot read SSID (fine=$hasFine nearby=$hasNearby)")
        return null
    }

    private fun getOperatorName(): String? {
        val tm = getSystemService(TelephonyManager::class.java) ?: return null
        val name = tm.networkOperatorName
        return if (name.isNullOrBlank()) null else name
    }

    /**
     * Fallback VPN-детекция через сетевые интерфейсы.
     * ConnectivityManager в рабочем профиле может не видеть VPN из основного,
     * но TUN/PPP/TAP интерфейсы видны на уровне ядра из любого профиля.
     * Исключаем наш собственный LocalVpnService.
     */
    private fun detectVpnByInterface(): Boolean {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces() ?: return false
            val vpnPrefixes = listOf("tun", "ppp", "tap", "utun", "ipsec", "wg")
            val found = mutableListOf<String>()
            for (iface in interfaces) {
                val name = iface.name ?: continue
                if (!iface.isUp) continue
                if (vpnPrefixes.any { name.startsWith(it) }) {
                    found += name
                }
            }
            if (found.isNotEmpty()) {
                Log.d(TAG, "VPN interfaces found: ${found.joinToString()}")
                // Если наш LocalVpnService активен, он тоже создаёт tun.
                // Если найден ровно 1 tun и наш VPN активен — это наш.
                val ourVpnActive = LocalVpnService.currentMode != LocalVpnService.Mode.OFF
                if (ourVpnActive && found.size == 1 && found[0].startsWith("tun")) {
                    Log.d(TAG, "Single TUN interface with our VPN active — ignoring (our own)")
                    return false
                }
                // Если наш VPN НЕ активен, или интерфейсов > 1 — есть внешний VPN
                return true
            }
        } catch (e: Exception) {
            Log.w(TAG, "detectVpnByInterface error", e)
        }
        return false
    }

    private fun scheduleReevaluate() {
        handler.removeCallbacks(reevaluateRunnable)
        handler.postDelayed(reevaluateRunnable, 200L)
    }

    private fun doReevaluate() {
        val state = getCurrentNetworkState()
        val result = TriggerEvaluator.evaluate(state, currentConfig)
        val desiredLevel = computeProtectionLevel(result)

        synchronized(protectionLock) {
            transitionProtection(desiredLevel)
        }
    }

    /** Определяет строжайший уровень защиты по результату оценки триггеров */
    private fun computeProtectionLevel(result: TriggerEvaluator.Result): ProtectionLevel {
        if (!result.shouldActivate) return ProtectionLevel.NONE
        if (result.forceFreezeAll) return ProtectionLevel.CORPORATE
        if (result.forceKillSwitch || currentConfig.killSwitch) return ProtectionLevel.KILL_SWITCH
        return ProtectionLevel.NORMAL
    }

    /**
     * Единая точка переключения уровня защиты.
     * Сравнивает текущий и желаемый уровень, выполняет только нужные действия.
     * Строжайший триггер всегда побеждает; при его уходе — понижение до следующего.
     */
    private fun transitionProtection(newLevel: ProtectionLevel) {
        val oldLevel = currentLevel
        if (oldLevel == newLevel) return

        Log.i(TAG, ">>> Protection: $oldLevel → $newLevel <<<")
        currentLevel = newLevel
        isTriggered = newLevel != ProtectionLevel.NONE
        getSharedPreferences("trigger_state", MODE_PRIVATE).edit()
            .putBoolean("is_triggered", isTriggered).apply()

        // ── VPN Kill Switch ──
        val needsVpn = newLevel >= ProtectionLevel.KILL_SWITCH
        if (needsVpn && !LocalVpnService.isBlocking) {
            startVpnBlocking()
        } else if (!needsVpn && LocalVpnService.isBlocking) {
            stopVpnBlocking()
        }

        // ── Freeze / Unfreeze ──
        freezeJob?.cancel()
        freezeJob = serviceScope.launch {
            if (!needsVpn) profileManager.clearWorkProfileVpn()

            when {
                // Полная деактивация
                newLevel == ProtectionLevel.NONE && oldLevel == ProtectionLevel.CORPORATE -> {
                    unfreezeAllPackages()
                }
                newLevel == ProtectionLevel.NONE -> {
                    unfreezePackages()
                }

                // Повышение до корпоративного: заморозить ВСЕ
                newLevel == ProtectionLevel.CORPORATE -> {
                    freezeAllPackages()
                }

                // Понижение С корпоративного: разморозить все, потом заморозить только managed
                oldLevel == ProtectionLevel.CORPORATE -> {
                    unfreezeAllPackages()
                    freezePackages()
                }

                // Первичная активация (было NONE): заморозить managed
                oldLevel == ProtectionLevel.NONE -> {
                    freezePackages()
                }

                // NORMAL ↔ KILL_SWITCH: только VPN меняется, freeze тот же
            }
        }

        // ── Уведомление ──
        val text = if (newLevel == ProtectionLevel.NONE)
            getString(R.string.notification_monitoring)
        else getString(R.string.notification_triggered)
        updateNotification(text)
    }

    private fun startVpnBlocking() {
        val intent = Intent(this, LocalVpnService::class.java).apply {
            action = LocalVpnService.ACTION_START_BLOCKING
        }
        startService(intent)
    }

    private fun stopVpnBlocking() {
        val intent = Intent(this, LocalVpnService::class.java).apply {
            action = LocalVpnService.ACTION_STOP_BLOCKING
        }
        startService(intent)
    }

    private fun freezePackages() {
        if (managedPackages.isNotEmpty()) {
            val failed = profileManager.suspendPackages(managedPackages)
            if (failed.isNotEmpty()) {
                Log.w(TAG, "Failed to suspend: $failed")
            }
        }
    }

    /** Корпоративный режим: заморозить ВСЕ приложения рабочего профиля (кроме нашего) */
    private fun freezeAllPackages() {
        val pm = packageManager
        @Suppress("DEPRECATION")
        val allPkgs = pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
            .map { it.packageName }
            .filter { it != packageName }
        Log.i(TAG, "Corporate freeze ALL: ${allPkgs.size} packages")
        val failed = profileManager.suspendPackages(allPkgs)
        if (failed.isNotEmpty()) {
            Log.w(TAG, "Failed to suspend (corporate): $failed")
        }
    }

    private fun unfreezePackages() {
        if (managedPackages.isNotEmpty()) {
            profileManager.unsuspendPackages(managedPackages)
        }
    }

    /** Разморозить ВСЕ пакеты (после корпоративного режима) */
    private fun unfreezeAllPackages() {
        @Suppress("DEPRECATION")
        val allPkgs = packageManager.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
            .map { it.packageName }
            .filter { it != packageName }
        Log.i(TAG, "Unfreezing ALL packages (post-corporate): ${allPkgs.size}")
        profileManager.unsuspendPackages(allPkgs)
    }

    private fun registerNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
        // Дополнительный callback для VPN-сетей (они могут не иметь INTERNET capability)
        val vpnRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        connectivityManager.registerNetworkCallback(vpnRequest, vpnNetworkCallback)
    }

    private fun createNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        // Удаляем старые каналы — Android сохраняет user-locked настройки при пересоздании с тем же ID
        nm.deleteNotificationChannel("AM_Monitor")
        nm.deleteNotificationChannel("bo_monitor")
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_monitor),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, com.coreguard.app.ui.MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setSilent(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.foregroundServiceBehavior = NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
        }
        return builder.build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }
}
