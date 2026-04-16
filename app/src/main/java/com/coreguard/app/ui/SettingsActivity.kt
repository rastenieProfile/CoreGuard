package com.coreguard.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.MotionEvent
import android.widget.EditText
import android.widget.Toast
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroupAdapter
import androidx.preference.PreferenceScreen
import androidx.preference.PreferenceViewHolder
import androidx.preference.SwitchPreferenceCompat
import androidx.recyclerview.widget.RecyclerView
import com.coreguard.app.R
import com.coreguard.app.admin.ProfileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        private lateinit var profileManager: ProfileManager

        @Suppress("DEPRECATION")
        override fun onCreateAdapter(preferenceScreen: PreferenceScreen): RecyclerView.Adapter<*> {
            return object : PreferenceGroupAdapter(preferenceScreen) {
                override fun onBindViewHolder(holder: PreferenceViewHolder, position: Int) {
                    super.onBindViewHolder(holder, position)
                    holder.itemView.findViewById<TextView>(android.R.id.summary)?.maxLines = Int.MAX_VALUE
                }
            }
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            preferenceManager.sharedPreferencesName = "trigger_config"
            setPreferencesFromResource(R.xml.preferences, rootKey)
            profileManager = ProfileManager(requireContext())

            // isProfileOwner — IPC к DPM, выполняем на IO-потоке.
            // Все DPM-вызовы из listeners тоже переносим на IO-поток.
            lifecycleScope.launch {
                val isOwner = withContext(Dispatchers.IO) { profileManager.isProfileOwner }

                findPreference<SwitchPreferenceCompat>("pref_clipboard")?.apply {
                    isEnabled = isOwner
                    onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, v ->
                        lifecycleScope.launch(Dispatchers.IO) { profileManager.setClipboardSharing(v as Boolean) }; true
                    }
                }

                findPreference<SwitchPreferenceCompat>("pref_contacts")?.apply {
                    isEnabled = isOwner
                    onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, v ->
                        lifecycleScope.launch(Dispatchers.IO) { profileManager.setContactsSharing(v as Boolean) }; true
                    }
                }

                findPreference<SwitchPreferenceCompat>("pref_gallery")?.apply {
                    isEnabled = isOwner
                    onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, v ->
                        lifecycleScope.launch(Dispatchers.IO) { profileManager.setIntentSharing(v as Boolean) }; true
                    }
                }

                findPreference<SwitchPreferenceCompat>("pref_unknown_sources")?.apply {
                    isEnabled = isOwner
                    onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, v ->
                        lifecycleScope.launch(Dispatchers.IO) { profileManager.setUnknownSourcesAllowed(v as Boolean) }; true
                    }
                }

                findPreference<SwitchPreferenceCompat>("pref_screen_capture_disabled")?.apply {
                    isEnabled = isOwner
                    onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, v ->
                        lifecycleScope.launch(Dispatchers.IO) { profileManager.setWorkProfileScreenCaptureDisabled(v as Boolean) }; true
                    }
                }

                findPreference<SwitchPreferenceCompat>("pref_restrict_phone_state")?.apply {
                    isEnabled = isOwner
                    onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, _ ->
                        lifecycleScope.launch(Dispatchers.IO) { profileManager.applyAllPermissions() }; true
                    }
                }

                findPreference<SwitchPreferenceCompat>("pref_restrict_location")?.apply {
                    isEnabled = isOwner
                    onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, _ ->
                        lifecycleScope.launch(Dispatchers.IO) { profileManager.applyAllPermissions() }; true
                    }
                }

                findPreference<SwitchPreferenceCompat>("pref_restrict_camera")?.apply {
                    isEnabled = isOwner
                    onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, _ ->
                        lifecycleScope.launch(Dispatchers.IO) { profileManager.applyAllPermissions() }; true
                    }
                }

                findPreference<SwitchPreferenceCompat>("pref_restrict_microphone")?.apply {
                    isEnabled = isOwner
                    onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, _ ->
                        lifecycleScope.launch(Dispatchers.IO) { profileManager.applyAllPermissions() }; true
                    }
                }

                findPreference<SwitchPreferenceCompat>("pref_restrict_contacts")?.apply {
                    isEnabled = isOwner
                    onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, _ ->
                        lifecycleScope.launch(Dispatchers.IO) { profileManager.applyAllPermissions() }; true
                    }
                }

                findPreference<SwitchPreferenceCompat>("pref_restrict_sms")?.apply {
                    isEnabled = isOwner
                    onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, _ ->
                        lifecycleScope.launch(Dispatchers.IO) { profileManager.applyAllPermissions() }; true
                    }
                }

                findPreference<SwitchPreferenceCompat>("pref_restrict_call_log")?.apply {
                    isEnabled = isOwner
                    onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, _ ->
                        lifecycleScope.launch(Dispatchers.IO) { profileManager.applyAllPermissions() }; true
                    }
                }

                findPreference<SwitchPreferenceCompat>("pref_restrict_accounts")?.apply {
                    isEnabled = isOwner
                    onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, _ ->
                        lifecycleScope.launch(Dispatchers.IO) { profileManager.applyAllPermissions() }; true
                    }
                }

                findPreference<SwitchPreferenceCompat>("pref_restrict_bluetooth_scan")?.apply {
                    isEnabled = isOwner
                    onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, _ ->
                        lifecycleScope.launch(Dispatchers.IO) { profileManager.applyAllPermissions() }; true
                    }
                }

                findPreference<SwitchPreferenceCompat>("pref_restrict_bluetooth")?.apply {
                    isEnabled = isOwner
                    onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, v ->
                        lifecycleScope.launch(Dispatchers.IO) { profileManager.setBluetoothSharingRestricted(v as Boolean) }; true
                    }
                }

                findPreference<SwitchPreferenceCompat>("pref_restrict_nfc")?.apply {
                    isEnabled = isOwner
                    onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, v ->
                        lifecycleScope.launch(Dispatchers.IO) { profileManager.setNfcBeamRestricted(v as Boolean) }; true
                    }
                }

                findPreference<SwitchPreferenceCompat>("pref_uninstall_blocked")?.apply {
                    isEnabled = isOwner
                    onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, v ->
                        lifecycleScope.launch(Dispatchers.IO) { profileManager.setUninstallBlockedForAllApps(v as Boolean) }; true
                    }
                }

                findPreference<Preference>("pref_app_permissions")?.apply {
                    isEnabled = isOwner
                    setOnPreferenceClickListener {
                        startActivity(android.content.Intent(requireContext(), AppPermissionsActivity::class.java))
                        true
                    }
                }

            }  // end lifecycleScope.launch

            // Wi-Fi network picker
            findPreference<Preference>("wifi_ssid_list")?.also { pref ->
                refreshSummary(pref, "wifi_ssid_list")
                pref.setOnPreferenceClickListener { showWifiPicker(); true }
            }

            // Operator picker
            findPreference<Preference>("operator_list")?.also { pref ->
                refreshSummary(pref, "operator_list")
                pref.setOnPreferenceClickListener { showOperatorPicker(); true }
            }

            // Corporate trigger dialog — при включении VPN/Mobile спрашиваем «корпоративная сеть?»
            // Wi-Fi и оператор — корпоративность привязана к конкретной сети/оператору (per-SSID / per-operator)
            val triggerKeys = listOf("trigger_vpn", "trigger_mobile")
            for (key in triggerKeys) {
                findPreference<SwitchPreferenceCompat>(key)?.onPreferenceChangeListener =
                    Preference.OnPreferenceChangeListener { _, v ->
                        if (v == true) showCorporateTriggerDialog(key)
                        else removeCorporateTrigger(key)
                        true
                    }
            }

            // Easter egg: tap 7 times
            findPreference<Preference>("easter_egg")?.setOnPreferenceClickListener {
                recordTap()
                true
            }

            // Донат
            findPreference<Preference>("donate_open")?.setOnPreferenceClickListener {
                startActivity(Intent(requireContext(), DonateActivity::class.java))
                true
            }

            // Cross-profile sync warning
            findPreference<Preference>("cross_profile_sync_warning")?.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setOnPreferenceClickListener {
                        try {
                            startActivity(Intent("android.settings.MANAGE_CROSS_PROFILE_ACCESS"))
                        } catch (_: Exception) {
                            try {
                                startActivity(Intent(Settings.ACTION_SETTINGS))
                            } catch (_: Exception) {}
                        }
                        true
                    }
                } else {
                    summary = getString(R.string.cross_profile_sync_summary_no_link)
                    isSelectable = false
                }
            }

            // Стабильная работа: ссылка на системные настройки приложения
            findPreference<Preference>("stable_work_app_settings")?.setOnPreferenceClickListener {
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${requireContext().packageName}")
                    }
                    startActivity(intent)
                } catch (_: Exception) {
                    try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: Exception) {}
                }
                true
            }

            // Стабильная работа: OEM-специфичные рекомендации
            findPreference<Preference>("stable_work_guide")?.setOnPreferenceClickListener {
                val mfr = Build.MANUFACTURER.lowercase()
                val msgRes = when {
                    mfr in listOf("xiaomi", "redmi", "poco") -> R.string.oem_warning_xiaomi
                    mfr in listOf("huawei", "honor")         -> R.string.oem_warning_huawei
                    mfr == "samsung"                         -> R.string.oem_warning_samsung
                    mfr in listOf("oppo", "realme", "oneplus", "vivo") -> R.string.oem_warning_oppo
                    else                                     -> R.string.oem_warning_generic
                }
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.oem_warning_title)
                    .setMessage(msgRes)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                true
            }
        }



        // ── Easter egg: 7 тапов + секретное удержание 20с на табличке ──────────────

        private val eggHandler = Handler(Looper.getMainLooper())
        private var eggHoldRunnable: Runnable? = null

        private var tapCount = 0
        private var lastTapTime = 0L

        private fun recordTap() {
            val now = SystemClock.elapsedRealtime()
            // Сброс если прошло больше 3 секунд с последнего тапа
            if (now - lastTapTime > 3000L) tapCount = 0
            lastTapTime = now
            tapCount++

            if (tapCount >= 7) {
                tapCount = 0
                val dialog = AlertDialog.Builder(requireContext())
                    .setMessage("Пишите мне до востребования\n\nСебастьян Парейро\nТорговец чёрным деревом")
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                // Секретная пасхалка: держи палец на тексте диалога 20 секунд
                val msgView = dialog.findViewById<android.widget.TextView>(android.R.id.message)
                msgView?.setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            eggHoldRunnable?.let { eggHandler.removeCallbacks(it) }
                            eggHoldRunnable = Runnable {
                                if (dialog.isShowing)
                                    msgView.text = "Сарынь на кичку 🏴\u200D☠️"
                            }
                            eggHandler.postDelayed(eggHoldRunnable!!, 20_000L)
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            eggHoldRunnable?.let { eggHandler.removeCallbacks(it) }
                            eggHoldRunnable = null
                        }
                    }
                    false
                }
            }
        }

        private fun showCorporateTriggerDialog(triggerKey: String) {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.corporate_trigger_title)
                .setMessage(R.string.corporate_trigger_message)
                .setPositiveButton(R.string.corporate_trigger_yes) { _, _ ->
                    addCorporateTrigger(triggerKey)
                }
                .setNegativeButton(R.string.corporate_trigger_no) { _, _ ->
                    removeCorporateTrigger(triggerKey)
                }
                .setCancelable(false)
                .show()
        }

        private fun addCorporateTrigger(key: String) {
            val prefs = triggerPrefs()
            val set = prefs.getStringSet("corporate_triggers", emptySet())?.toMutableSet() ?: mutableSetOf()
            set.add(key)
            prefs.edit().putStringSet("corporate_triggers", set).apply()
        }

        private fun removeCorporateTrigger(key: String) {
            val prefs = triggerPrefs()
            val set = prefs.getStringSet("corporate_triggers", emptySet())?.toMutableSet() ?: mutableSetOf()
            set.remove(key)
            prefs.edit().putStringSet("corporate_triggers", set).apply()
        }

        private fun triggerPrefs() =
            requireContext().getSharedPreferences("trigger_config", Context.MODE_PRIVATE)

        private fun savedList(key: String): Set<String> =
            (triggerPrefs().getString(key, "") ?: "")
                .split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

        private fun refreshSummary(pref: Preference, key: String) {
            val items = savedList(key)
            if (items.isEmpty()) {
                pref.summary = getString(R.string.wifi_not_set)
                return
            }
            val corpKey = when (key) {
                "wifi_ssid_list" -> "corporate_wifi_ssids"
                "operator_list" -> "corporate_operators"
                else -> null
            }
            val corpSet = corpKey?.let {
                triggerPrefs().getStringSet(it, emptySet()) ?: emptySet()
            } ?: emptySet()
            pref.summary = items.joinToString(", ") { name ->
                if (name in corpSet) "🏢 $name" else name
            }
        }

        // в”Ђв”Ђ Wi-Fi picker в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ

        private var pendingWifiPickerAfterLocation = false

        // Runtime-запрос FINE_LOCATION для API 31-32:
        // DPC не может грантить sensor-пермишены, а NEARBY_WIFI_DEVICES ещё нет.
        private val locationPermLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            Log.d("WifiPicker", "locationPermLauncher granted=$granted")
            if (granted) showWifiPicker()
        }

        private fun showWifiPicker() {
            val TAG = "WifiPicker"
            val ctx = requireContext()

            // Выдаём разрешения через DPC
            var needsPermWait = false
            fun grantIfNeeded(perm: String) {
                val had = androidx.core.content.ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED
                if (!had) {
                    profileManager.grantSelfPermission(perm)
                    needsPermWait = true
                }
                Log.d(TAG, "perm ${perm.substringAfterLast('.')} had=$had")
            }
            // На Android 12+ DPC не может грантить sensor-пермишены (FINE/COARSE_LOCATION).
            // На Android 13+ используем NEARBY_WIFI_DEVICES с neverForLocation — FINE_LOCATION не нужен.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                grantIfNeeded(Manifest.permission.ACCESS_FINE_LOCATION)
                grantIfNeeded(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            grantIfNeeded(Manifest.permission.ACCESS_WIFI_STATE)
            grantIfNeeded(Manifest.permission.CHANGE_WIFI_STATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                grantIfNeeded(Manifest.permission.NEARBY_WIFI_DEVICES)
            }

            // API 31+: DPC не может грантить FINE_LOCATION (sensor permission).
            // На API 33+ NEARBY_WIFI_DEVICES даёт scan results, но SSID текущей сети
            // не виден без FINE_LOCATION при neverForLocation. Запрашиваем у пользователя.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(ctx,
                    Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (!hasFine) {
                    Log.d(TAG, "API ${Build.VERSION.SDK_INT}: requesting FINE_LOCATION via runtime permission")
                    locationPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    return
                }
            }

            profileManager.ensureLocationEnabled()

            // DPC-грант применяется асинхронно (~200мс) — ждём пока он вступит в силу
            val delay = if (needsPermWait) 1000L else 0L
            if (delay > 0) {
                Log.d(TAG, "waiting ${delay}ms for DPC permission grant to take effect")
            }
            Handler(Looper.getMainLooper()).postDelayed({ doWifiScan(TAG) }, delay)
        }

        private fun doWifiScan(TAG: String) {
            if (!isAdded) return
            val ctx = requireContext()

            val lm = ctx.getSystemService(android.location.LocationManager::class.java)
            val locationOn = lm.isLocationEnabled
            val wifiManager = ctx.applicationContext.getSystemService(WifiManager::class.java)
            val cm = ctx.getSystemService(android.net.ConnectivityManager::class.java)

            // Проверяем реальное состояние разрешений после ожидания
            val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(ctx,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "locationEnabled=$locationOn wifiEnabled=${wifiManager.isWifiEnabled} hasFineLocation=$hasFine")

            val saved = savedList("wifi_ssid_list")
            val networks = mutableSetOf<String>()
            networks.addAll(saved)

            // Читаем кэшированные результаты
            collectSsidsSync(wifiManager, cm, networks, TAG)
            Log.d(TAG, "cached nets=${networks.size} saved=${saved.size}: $networks")

            if (networks.size > saved.size) {
                showWifiPickerDialog(networks, saved)
                return
            }

            if (!locationOn) {
                pendingWifiPickerAfterLocation = true
                AlertDialog.Builder(ctx)
                    .setTitle("Геолокация отключена")
                    .setMessage("Для автоматического определения Wi-Fi сетей необходимо включить геолокацию. Включите её в настройках и вернитесь.\n\nТакже можно ввести имя сети вручную.")
                    .setPositiveButton("Открыть настройки") { _, _ ->
                        try { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                        catch (_: Exception) { startActivity(Intent(Settings.ACTION_SETTINGS)) }
                    }
                    .setNeutralButton("Ввести вручную") { _, _ ->
                        pendingWifiPickerAfterLocation = false
                        showManualSsidInput(saved.toMutableSet())
                    }
                    .setNegativeButton(android.R.string.cancel) { _, _ ->
                        pendingWifiPickerAfterLocation = false
                    }
                    .show()
                return
            }

            // Прогресс-диалог
            val progressRow = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                setPadding(64, 48, 64, 32)
                gravity = android.view.Gravity.CENTER_VERTICAL
                addView(android.widget.ProgressBar(ctx))
                addView(android.widget.TextView(ctx).apply {
                    text = getString(R.string.wifi_scanning)
                    setPadding(32, 0, 0, 0)
                    textSize = 15f
                })
            }

            val handler = Handler(Looper.getMainLooper())
            var done = false
            var scanReceiver: android.content.BroadcastReceiver? = null

            val loadingDialog = AlertDialog.Builder(ctx)
                .setView(progressRow)
                .setNeutralButton(getString(R.string.wifi_add_manual), null)
                .setCancelable(true)
                .create()

            fun cleanup() {
                handler.removeCallbacksAndMessages(TAG)
                try { scanReceiver?.let { ctx.unregisterReceiver(it) } } catch (_: Exception) {}
            }

            fun finish(nets: Set<String>) {
                if (done) return
                done = true
                cleanup()
                try { if (loadingDialog.isShowing) loadingDialog.dismiss() } catch (_: Exception) {}
                showWifiPickerDialog(nets, saved)
            }

            loadingDialog.setOnShowListener {
                loadingDialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                    done = true; cleanup()
                    loadingDialog.dismiss()
                    showManualSsidInput(saved.toMutableSet())
                }
            }
            loadingDialog.setOnCancelListener { done = true; cleanup() }
            loadingDialog.show()

            // Регистрируем приёмник ДО startScan
            scanReceiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(c: android.content.Context, i: android.content.Intent) {
                    val updated = i.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                    try { ctx.unregisterReceiver(this) } catch (_: Exception) {}
                    collectScanResults(wifiManager, networks, TAG)
                    Log.d(TAG, "broadcast updated=$updated nets=${networks.size}: $networks")
                    finish(networks)
                }
            }
            try {
                @Suppress("DEPRECATION")
                ctx.registerReceiver(scanReceiver, android.content.IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
            } catch (_: Exception) {}

            val scanOk = try { @Suppress("DEPRECATION") wifiManager.startScan() } catch (_: Exception) { false }
            Log.d(TAG, "startScan=$scanOk")

            // Таймаут 8 сек
            handler.postDelayed({
                collectSsidsSync(wifiManager, cm, networks, TAG)
                Log.d(TAG, "timeout nets=${networks.size}: $networks")
                finish(networks)
            }, TAG, 8000)
        }

        override fun onResume() {
            super.onResume()
            if (pendingWifiPickerAfterLocation) {
                pendingWifiPickerAfterLocation = false
                // Пользователь вернулся из настроек — пробуем снова
                view?.postDelayed({ showWifiPicker() }, 500)
            }
        }

        /** Синхронный сбор SSID из всех доступных источников */
        private fun collectSsidsSync(
            wifiManager: WifiManager,
            cm: android.net.ConnectivityManager,
            out: MutableSet<String>,
            tag: String
        ) {
            // 1. Текущая сеть через ConnectivityManager
            try {
                val caps = cm.getNetworkCapabilities(cm.activeNetwork)
                val wifiInfo = caps?.transportInfo as? android.net.wifi.WifiInfo
                wifiInfo?.ssid?.removeSurrounding("\"")
                    ?.takeIf { it.isNotEmpty() && it != "<unknown ssid>" && it != "0x" }
                    ?.let { out.add(it); Log.d(tag, "[CM] ssid=$it") }
            } catch (_: Exception) {}

            // 2. Текущая сеть через WifiManager (legacy)
            try {
                @Suppress("DEPRECATION")
                wifiManager.connectionInfo?.ssid?.removeSurrounding("\"")
                    ?.takeIf { it.isNotEmpty() && it != "<unknown ssid>" && it != "0x" }
                    ?.let { out.add(it); Log.d(tag, "[WM] ssid=$it") }
            } catch (_: Exception) {}

            // 3. Сохранённые на устройстве Wi-Fi сети (работает до Android 10)
            try {
                @Suppress("DEPRECATION")
                wifiManager.configuredNetworks?.forEach { config ->
                    config.SSID?.removeSurrounding("\"")
                        ?.takeIf { it.isNotEmpty() && it != "<unknown ssid>" && it != "0x" }
                        ?.let { out.add(it) }
                }
            } catch (_: Exception) {}

            // 4. Результаты сканирования
            collectScanResults(wifiManager, out, tag)
        }

        /** Извлекает SSID из scanResults */
        private fun collectScanResults(
            wifiManager: WifiManager,
            out: MutableSet<String>,
            tag: String
        ) {
            try {
                val scans = wifiManager.scanResults
                Log.d(tag, "[scan] count=${scans?.size ?: "null"}")
                scans?.forEach { result ->
                    val ssid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        result.wifiSsid?.toString()?.removeSurrounding("\"")
                    } else {
                        @Suppress("DEPRECATION") result.SSID?.removeSurrounding("\"")
                    }
                    ssid?.takeIf { it.isNotEmpty() && it != "<unknown ssid>" && it != "0x" }
                        ?.let { out.add(it) }
                }
            } catch (_: Exception) {}
        }

        /** Показывает диалог выбора WiFi-сетей (или ручной ввод если пусто) */
        private fun showWifiPickerDialog(networks: Set<String>, saved: Set<String>) {
            if (!isAdded) return
            val all = networks.toList().sorted()
            val selected = saved.toMutableSet()

            if (all.isEmpty()) {
                Toast.makeText(requireContext(), R.string.wifi_no_networks_found, Toast.LENGTH_LONG).show()
                showManualSsidInput(selected)
                return
            }

            val checked = BooleanArray(all.size) { all[it] in selected }

            AlertDialog.Builder(requireContext())
                .setTitle(R.string.wifi_networks_picker_title)
                .setMultiChoiceItems(all.toTypedArray(), checked) { _, i, on ->
                    if (on) selected.add(all[i]) else selected.remove(all[i])
                }
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    saveWifiList(selected)
                    if (selected.isNotEmpty()) showCorporateWifiDialog(selected)
                }
                .setNeutralButton(R.string.wifi_add_manual) { _, _ ->
                    saveWifiList(selected)
                    showManualSsidInput(selected)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        private fun showManualSsidInput(currentSelection: MutableSet<String>) {
            val input = EditText(requireContext()).apply {
                hint = "SSID"
                setPadding(60, 40, 60, 20)
            }
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.wifi_add_manual)
                .setView(input)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val ssid = input.text.toString().trim()
                    if (ssid.isNotEmpty()) {
                        currentSelection.add(ssid)
                        saveWifiList(currentSelection)
                        showCorporateWifiDialog(currentSelection)
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        private fun saveWifiList(selected: Set<String>) {
            val value = selected.joinToString(",")
            triggerPrefs().edit().putString("wifi_ssid_list", value).apply()
            findPreference<Preference>("wifi_ssid_list")
                ?.let { refreshSummary(it, "wifi_ssid_list") }
        }

        /** Диалог выбора корпоративных Wi-Fi сетей из списка выбранных SSID */
        private fun showCorporateWifiDialog(ssids: Set<String>) {
            if (!isAdded || ssids.isEmpty()) return
            val all = ssids.toList().sorted()
            val savedCorp = triggerPrefs().getStringSet("corporate_wifi_ssids", emptySet()) ?: emptySet()
            val selected = savedCorp.toMutableSet()
            val checked = BooleanArray(all.size) { all[it] in selected }

            AlertDialog.Builder(requireContext())
                .setTitle(R.string.corporate_wifi_title)
                .setMultiChoiceItems(all.toTypedArray(), checked) { _, i, on ->
                    if (on) selected.add(all[i]) else selected.remove(all[i])
                }
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val valid = selected.filter { it in ssids }.toSet()
                    triggerPrefs().edit().putStringSet("corporate_wifi_ssids", valid).apply()
                    findPreference<Preference>("wifi_ssid_list")
                        ?.let { refreshSummary(it, "wifi_ssid_list") }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        // ── Operator picker ─────────────────────────────────────

        private fun showOperatorPicker() {
            if (!isAdded) return
            val ctx = requireContext()
            val tm = ctx.getSystemService(android.telephony.TelephonyManager::class.java)
            val currentOperator = tm?.networkOperatorName?.takeIf { it.isNotBlank() }

            val saved = savedList("operator_list").toMutableSet()
            val operators = mutableSetOf<String>()
            operators.addAll(saved)
            if (currentOperator != null) operators.add(currentOperator)

            val all = operators.toList().sorted()

            if (all.isEmpty()) {
                showManualOperatorInput(saved)
                return
            }

            val checked = BooleanArray(all.size) { all[it] in saved }

            AlertDialog.Builder(ctx)
                .setTitle(R.string.operator_picker_title)
                .setMultiChoiceItems(all.toTypedArray(), checked) { _, i, on ->
                    if (on) saved.add(all[i]) else saved.remove(all[i])
                }
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    saveOperatorList(saved)
                    if (saved.isNotEmpty()) showCorporateOperatorDialog(saved)
                }
                .setNeutralButton(R.string.operator_add_manual) { _, _ ->
                    saveOperatorList(saved)
                    showManualOperatorInput(saved)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .apply {
                    if (currentOperator != null) {
                        setMessage(getString(R.string.operator_current, currentOperator))
                    }
                }
                .show()
        }

        private fun showManualOperatorInput(currentSelection: MutableSet<String>) {
            val input = EditText(requireContext()).apply {
                hint = "Operator name"
                setPadding(60, 40, 60, 20)
            }
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.operator_add_manual)
                .setView(input)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val name = input.text.toString().trim()
                    if (name.isNotEmpty()) {
                        currentSelection.add(name)
                        saveOperatorList(currentSelection)
                        showCorporateOperatorDialog(currentSelection)
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        private fun saveOperatorList(selected: Set<String>) {
            val value = selected.joinToString(",")
            triggerPrefs().edit().putString("operator_list", value).apply()
            findPreference<Preference>("operator_list")
                ?.let { refreshSummary(it, "operator_list") }
        }

        /** Диалог выбора корпоративных операторов из списка выбранных */
        private fun showCorporateOperatorDialog(operators: Set<String>) {
            if (!isAdded || operators.isEmpty()) return
            val all = operators.toList().sorted()
            val savedCorp = triggerPrefs().getStringSet("corporate_operators", emptySet()) ?: emptySet()
            val selected = savedCorp.toMutableSet()
            val checked = BooleanArray(all.size) { all[it] in selected }

            AlertDialog.Builder(requireContext())
                .setTitle(R.string.corporate_operator_title)
                .setMultiChoiceItems(all.toTypedArray(), checked) { _, i, on ->
                    if (on) selected.add(all[i]) else selected.remove(all[i])
                }
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val valid = selected.filter { it in operators }.toSet()
                    triggerPrefs().edit().putStringSet("corporate_operators", valid).apply()
                    findPreference<Preference>("operator_list")
                        ?.let { refreshSummary(it, "operator_list") }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }
}
