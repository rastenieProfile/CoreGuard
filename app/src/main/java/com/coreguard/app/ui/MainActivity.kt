package com.coreguard.app.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Outline
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import java.util.HashSet
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.coreguard.app.R
import com.coreguard.app.admin.AppPermOverride
import com.coreguard.app.admin.AppPermOverrides
import com.coreguard.app.admin.PermState
import com.coreguard.app.admin.ProfileManager
import com.coreguard.app.databinding.ActivityMainBinding
import com.coreguard.app.trigger.TriggerMonitorService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var profileManager: ProfileManager

    private val appCheckBoxes = mutableListOf<Pair<CheckBox, String>>()
    private val selectedPackages = mutableSetOf<String>()
    private var isListLoading = false
    private var appListDirty = true   // перестраивать список только при необходимости

    // Кешированные результаты IPC-запросов к DPM — выставляются один раз на IO-потоке в onCreate().
    // DPM занят во время активации рабочего профиля → вызовы на UI-потоке → ANR.
    private var cachedIsOwner = false
    private var cachedCanProvision = false
    private var uiInitialized = false   // true после завершения initUi()

    // в”Ђв”Ђв”Ђ Activity result launchers в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ

    private val provisioningLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            cachedCanProvision = false   // профиль создан — provisioning больше невозможен
            getSharedPreferences("app_state", MODE_PRIVATE).edit()
                .putBoolean("our_profile_created", true).apply()
            Toast.makeText(this, R.string.provision_success, Toast.LENGTH_LONG).show()

            // Иконку в основном профиле НЕ скрываем — DPC скроет её в рабочем профиле.
            // Скрытие компонента в основном профиле вызывает MIUI Security Center → kill процесса.

            // Попытка перекинуть пользователя в рабочий профиль (может не сработать
            // пока DPC не вызвал setProfileEnabled — это нормально)
            tryLaunchWorkProfile()
            finish()
        }
    }

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d("AM_Main", "vpnPermission result=${result.resultCode}")
        if (result.resultCode == Activity.RESULT_OK) startMonitorService()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d("AM_Main", "notificationPerm granted=$granted")
        if (!granted) {
            Toast.makeText(this, R.string.notification_permission_denied, Toast.LENGTH_LONG).show()
        }
        // Продолжаем запрос VPN в любом случае — уведомления желательны, но не обязательны
        val vpnIntent = VpnService.prepare(this)
        Log.d("AM_Main", "VpnService.prepare (after notif)=${if (vpnIntent != null) "needs permission" else "already granted"}")
        if (vpnIntent != null) vpnPermissionLauncher.launch(vpnIntent) else startMonitorService()
    }

    // в”Ђв”Ђв”Ђ Lifecycle в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        profileManager = ProfileManager(this)

        // Binder IPC к DPM блокирует главный поток во время активации рабочего профиля → ANR.
        // Скрываем UI и выполняем ВСЕ IPC-запросы на IO-потоке.
        binding.root.visibility = View.INVISIBLE
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                cachedIsOwner = profileManager.isProfileOwner
                cachedCanProvision = profileManager.canProvision()
            }
            if (isFinishing) return@launch

            if (!cachedIsOwner) {
                // В основном профиле: проверить, не создан ли уже рабочий профиль
                val provisioned = withContext(Dispatchers.IO) { isWorkProfileAlreadyProvisioned() }
                if (isFinishing) return@launch
                if (provisioned) {
                    // Пробуем перекинуть пользователя в рабочий профиль
                    val launched = tryLaunchWorkProfile()
                    if (launched) {
                        // Иконку в основном профиле НЕ скрываем — MIUI Security Center убьёт процесс.
                        // Иконка в рабочем профиле скрыта DPC receiver.
                        finish()
                        return@launch
                    }
                    // Рабочий профиль существует, но запустить не удалось —
                    // показываем кнопку повторной настройки, флаг НЕ сбрасываем.
                }
            }

            binding.root.visibility = View.VISIBLE
            initUi()
        }
    }

    private fun initUi() {
        binding.btnProvision.setOnClickListener { startProvisioning() }
        binding.btnSelectAll.setOnClickListener {
            appCheckBoxes.forEach { (cb, _) -> cb.isChecked = true }
        }
        binding.btnDeselectAll.setOnClickListener {
            appCheckBoxes.forEach { (cb, _) -> cb.isChecked = false }
        }
        binding.btnSaveApps.setOnClickListener { saveAppSelection() }
        binding.btnCloseBanner.setOnClickListener { dismissInfoBanner() }
        binding.cbShowSystem.setOnCheckedChangeListener { _, _ ->
            if (cachedIsOwner) { appListDirty = true; buildAppList() }
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_connection -> true
                R.id.nav_settings -> { openSettings(); true }
                else -> false
            }
        }

        if (cachedIsOwner) {
            appListDirty = true
            buildAppList()
            autoStartMonitorService()
            // Разрешить виджеты новых приложений — в фоне, чтобы не блокировать UI
            lifecycleScope.launch(Dispatchers.IO) {
                profileManager.allowAllCrossProfileWidgets()
            }
        }
        updateUi()
        maybeShowExistingProfileWarning()
        maybeShowFirstLaunch()
        uiInitialized = true
    }

    override fun onResume() {
        super.onResume()
        // Первый onResume() срабатывает раньше завершения async-инициализации в onCreate() —
        // пропускаем, т.к. UI пока невидим и все нужные вызовы делаются из initUi().
        if (!uiInitialized) return
        if (cachedIsOwner && appListDirty) buildAppList()
        updateUi()
        binding.bottomNav.selectedItemId = R.id.nav_connection

        // VPN-триггер включён по умолчанию. При каждом открытии проверяем:
        // 1) Сервис не запущен → запрашиваем VPN и стартуем
        // 2) Сервис запущен (напр. BootReceiver), но VPN не разрешён → запрашиваем
        if (cachedIsOwner) {
            val vpnNeeded = VpnService.prepare(this) != null
            Log.d("AM_Main", "onResume: isRunning=${TriggerMonitorService.isRunning} vpnNeeded=$vpnNeeded")
            if (!TriggerMonitorService.isRunning || vpnNeeded) {
                requestVpnAndStart()
            }
        }
    }

    /** Автоматически запускает мониторинг при входе в рабочий профиль */
    private fun autoStartMonitorService() {
        Log.d("AM_Main", "autoStart: isRunning=${TriggerMonitorService.isRunning}")
        if (!TriggerMonitorService.isRunning) {
            requestVpnAndStart()
        }
        requestBatteryOptimizationExemption()
    }

    /** Запрашивает исключение из оптимизации батареи — программный эквивалент
     *  «Автозапуска» в MIUI. Работает из контекста рабочего профиля (user 11),
     *  там эта настройка независима от основного профиля. */
    private fun requestBatteryOptimizationExemption() {
        val pm = getSystemService(android.os.PowerManager::class.java) ?: return
        if (pm.isIgnoringBatteryOptimizations(packageName)) return  // уже выдано
        try {
            startActivity(
                android.content.Intent(
                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    android.net.Uri.parse("package:$packageName")
                )
            )
        } catch (e: Exception) {
            Log.w("AM_Main", "Cannot request battery optimization exemption", e)
        }
    }

    // ─── First launch & privacy ──────────────────────────────────────────────────

    private fun maybeShowFirstLaunch() {
        val prefs = getSharedPreferences("app_state", MODE_PRIVATE)
        if (prefs.getBoolean("first_launch_shown", false)
            && prefs.getBoolean("privacy_warning_shown", false)
            && (!cachedIsOwner || prefs.getBoolean("wp_onboarding_shown", false))
            && prefs.getBoolean("oem_warning_shown", false)
        ) return

        val ourProfileCreated = prefs.getBoolean("our_profile_created", false)

        startActivity(Intent(this, OnboardingActivity::class.java).apply {
            putExtra("is_profile_owner", cachedIsOwner)
        })
    }

    private fun maybeShowPrivacyWarning() {
        // Handled by wizard in maybeShowFirstLaunch()
    }

    private fun maybeShowWorkProfileOnboarding() {
        // Handled by wizard in maybeShowFirstLaunch()
    }

    private fun maybeShowExistingProfileWarning() {
        // Деструктивный диалог «удалите существующий профиль» убран.
        // Приложение просто отображает кнопку «Настроить» и позволяет Android
        // самому сообщить пользователю об ограничениях (если они есть).
    }

    private fun getOemProfileRemovalPath(): String {
        val mfr = Build.MANUFACTURER.lowercase()
        val res = when {
            mfr in listOf("xiaomi", "redmi", "poco") -> R.string.existing_profile_path_xiaomi
            mfr in listOf("huawei", "honor")         -> R.string.existing_profile_path_huawei
            mfr == "samsung"                         -> R.string.existing_profile_path_samsung
            mfr in listOf("oppo", "realme", "oneplus", "vivo") -> R.string.existing_profile_path_oppo
            else                                     -> R.string.existing_profile_path_generic
        }
        return getString(res)
    }

    private fun updateUi() {
        // Всегда показываем кнопку настройки если не являемся DPC —
        // не блокируем пользователя диалогом «удалите существующий профиль».
        // Android сам объяснит если создать ещё один профиль нельзя.
        binding.provisionSection.visibility =
            if (!cachedIsOwner) View.VISIBLE else View.GONE
        binding.profileCreatedSection.visibility = View.GONE
        binding.controlSection.visibility =
            if (cachedIsOwner) View.VISIBLE else View.GONE

        if (cachedIsOwner) {
            val isRunning = TriggerMonitorService.isRunning
            val isTriggered = TriggerMonitorService.isTriggered

            binding.statusLabel.text = when {
                isTriggered -> getString(R.string.status_triggered)
                isRunning -> getString(R.string.status_active)
                else -> getString(R.string.status_inactive)
            }

            // Показать баннер если ещё не закрыт
            val prefs = getSharedPreferences("app_state", MODE_PRIVATE)
            binding.infoBanner.visibility =
                if (!prefs.getBoolean("info_banner_dismissed", false)) View.VISIBLE else View.GONE
        }
    }

    private fun dismissInfoBanner() {
        binding.infoBanner.visibility = View.GONE
        getSharedPreferences("app_state", MODE_PRIVATE).edit()
            .putBoolean("info_banner_dismissed", true).apply()
    }

    // в”Ђв”Ђв”Ђ App list (inline on main page) в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ

    private fun buildAppList() {
        if (isListLoading) return
        isListLoading = true

        // Читаем состояние View на main-потоке до перехода на IO
        val showSystem = binding.cbShowSystem.isChecked

        // Показать индикатор загрузки
        binding.appListContainer.removeAllViews()
        val progress = ProgressBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER_HORIZONTAL }
        }
        binding.appListContainer.addView(progress)

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { loadAppData(showSystem) }
            renderAppList(result)
            isListLoading = false
            appListDirty = false
        }
    }

    private data class AppEntry(
        val label: String,
        val packageName: String,
        val icon: android.graphics.drawable.Drawable
    )

    private fun loadAppData(showSystem: Boolean): List<AppEntry> {

        val allApps = packageManager
            .getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.packageName != packageName }

        val filtered = if (showSystem) {
            allApps
        } else {
            // Только установленные пользователем (не системные)
            allApps.filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
        }

        return filtered
            .map { AppEntry(it.loadLabel(packageManager).toString(), it.packageName, it.loadIcon(packageManager)) }
            .sortedBy { it.label.lowercase() }
    }

    private fun renderAppList(apps: List<AppEntry>) {
        val prefs = getSharedPreferences("trigger_config", MODE_PRIVATE)
        val migrated = prefs.getBoolean("apps_migrated_v1", false)
        val savedPkgs = if (migrated) prefs.getStringSet("managed_packages", null)?.toMutableSet() else null

        val installedPkgNames = apps.map { it.packageName }.toSet()

        if (savedPkgs == null) {
            selectedPackages.clear()
            prefs.edit()
                .putStringSet("managed_packages", emptySet())
                .putBoolean("apps_migrated_v1", true)
                .apply()
        } else {
            selectedPackages.clear()
            selectedPackages.addAll(savedPkgs.filter { it in installedPkgNames })
        }

        binding.appListContainer.removeAllViews()
        appCheckBoxes.clear()

        val density = resources.displayMetrics.density
        val iconSizePx = (40 * density).toInt()
        val iconMarginPx = (12 * density).toInt()
        val rowPaddingV = (6 * density).toInt()

        for (app in apps) {
            val label = app.label
            val pkg = app.packageName
            val isSelected = selectedPackages.contains(pkg)
            val marker = if (isSelected) "\uD83D\uDD34" else "\uD83D\uDFE2"

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, rowPaddingV, 0, rowPaddingV)
            }

            val iconView = ImageView(this).apply {
                setImageDrawable(app.icon)
                layoutParams = LinearLayout.LayoutParams(iconSizePx, iconSizePx).apply {
                    marginEnd = iconMarginPx
                }
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setOval(0, 0, view.width, view.height)
                    }
                }
                clipToOutline = true
            }

            val cb = CheckBox(this).apply {
                text = "$marker $label"
                isChecked = isSelected
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selectedPackages.add(pkg)
                    else selectedPackages.remove(pkg)
                    val m = if (checked) "\uD83D\uDD34" else "\uD83D\uDFE2"
                    text = "$m $label"
                }
            }

            row.addView(iconView)
            row.addView(cb)
            row.setOnClickListener { showAppRestrictionsMenu(pkg, label) }
            appCheckBoxes.add(Pair(cb, pkg))
            binding.appListContainer.addView(row)
        }
    }

    private fun showAppRestrictionsMenu(pkg: String, label: String) {
        val override = AppPermOverrides.getForPackage(this, pkg)
        val isFrozen = selectedPackages.contains(pkg)

        val items = arrayOf(
            getString(R.string.restriction_freeze),
            getString(R.string.restriction_hide_from_apps),
            getString(R.string.restriction_hide_imei),
            getString(R.string.restriction_block_camera),
            getString(R.string.restriction_block_mic),
            getString(R.string.restriction_block_location),
            getString(R.string.restriction_block_contacts),
            getString(R.string.restriction_block_sms)
        )

        val checked = booleanArrayOf(
            isFrozen,
            override.queryAllPackages == PermState.DENY,
            override.readPhoneState == PermState.DENY,
            override.camera == PermState.DENY,
            override.recordAudio == PermState.DENY,
            override.fineLocation == PermState.DENY,
            override.readContacts == PermState.DENY,
            override.sms == PermState.DENY
        )

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.restriction_menu_title, label))
            .setMultiChoiceItems(items, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton(R.string.btn_save) { _, _ ->
                // Freeze toggle
                if (checked[0]) selectedPackages.add(pkg) else selectedPackages.remove(pkg)
                appCheckBoxes.firstOrNull { it.second == pkg }?.let { (cb, _) ->
                    cb.isChecked = checked[0]
                }
                saveAppSelection()

                // Permissions — сохраняем только изменённые, остальные поля без изменений
                fun resolve(newChecked: Boolean, old: PermState): PermState = when {
                    newChecked -> PermState.DENY
                    old == PermState.DENY -> PermState.AUTO
                    else -> old
                }

                AppPermOverrides.setForPackage(this, pkg, override.copy(
                    queryAllPackages = resolve(checked[1], override.queryAllPackages),
                    readPhoneState   = resolve(checked[2], override.readPhoneState),
                    camera           = resolve(checked[3], override.camera),
                    recordAudio      = resolve(checked[4], override.recordAudio),
                    fineLocation     = resolve(checked[5], override.fineLocation),
                    readContacts     = resolve(checked[6], override.readContacts),
                    sms              = resolve(checked[7], override.sms)
                ))
                lifecycleScope.launch(Dispatchers.IO) {
                    profileManager.applyPermissionsForNewApp(pkg)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun saveAppSelection() {
        getSharedPreferences("trigger_config", MODE_PRIVATE).edit()
            .putStringSet("managed_packages", selectedPackages.toSet())
            .apply()
        Toast.makeText(this, R.string.apps_saved, Toast.LENGTH_SHORT).show()
        // Перезагрузить конфиг сервиса мониторинга (если запущен)
        if (TriggerMonitorService.isRunning) {
            startForegroundService(Intent(this, TriggerMonitorService::class.java))
        }
    }

    // в”Ђв”Ђв”Ђ Protection actions в”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђв”Ђ

    private fun startProvisioning() {
        // Пробуем провижнинг напрямую, даже если canProvision() вернул false:
        // Android 12+ может поддерживать несколько рабочих профилей,
        // а система сама объяснит пользователю если это невозможно.
        provisioningLauncher.launch(profileManager.createProvisioningIntent())
    }

    private fun requestVpnAndStart() {
        Log.d("AM_Main", "requestVpnAndStart")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d("AM_Main", "requesting POST_NOTIFICATIONS")
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        val vpnIntent = VpnService.prepare(this)
        Log.d("AM_Main", "VpnService.prepare=${if (vpnIntent != null) "needs permission" else "already granted"}")
        if (vpnIntent != null) vpnPermissionLauncher.launch(vpnIntent) else startMonitorService()
    }

    private fun startMonitorService() {
        startForegroundService(Intent(this, TriggerMonitorService::class.java))
        updateUi()
    }

    private fun stopMonitorService() {
        stopService(Intent(this, TriggerMonitorService::class.java))
        updateUi()
    }

    private fun openSettings() = startActivity(Intent(this, SettingsActivity::class.java))
    private fun openAddApps()  = startActivity(Intent(this, AddAppsActivity::class.java))

    /**
     * Пытается запустить наше приложение в рабочем профиле через LauncherApps.
     */
    private fun isWorkProfileAlreadyProvisioned(): Boolean {
        val prefs = getSharedPreferences("app_state", MODE_PRIVATE)
        val userManager = getSystemService(UserManager::class.java) ?: return false
        val myHandle = android.os.Process.myUserHandle()
        val otherProfiles = userManager.userProfiles.filter { it != myHandle }

        if (otherProfiles.isEmpty()) {
            prefs.edit().putBoolean("our_profile_created", false).apply()
            return false
        }

        // Проверяем через LauncherApps — есть ли наше приложение в каком-либо
        // рабочем профиле. Это работает даже после ADB-провижнинга и переустановки,
        // не зависит от флага our_profile_created.
        val launcherApps = getSystemService(LauncherApps::class.java)
        if (launcherApps != null) {
            for (profile in otherProfiles) {
                val activities = try {
                    launcherApps.getActivityList(packageName, profile)
                } catch (_: Exception) { emptyList() }
                if (activities.isNotEmpty()) {
                    prefs.edit().putBoolean("our_profile_created", true).apply()
                    return true
                }
            }
        }

        // Наше приложение не найдено ни в одном профиле
        prefs.edit().putBoolean("our_profile_created", false).apply()
        return false
    }

    private fun launchWorkProfileApp() {
        tryLaunchWorkProfile()
    }

    /**
     * Пробует запустить наше приложение в рабочем профиле.
     * @return true если удалось найти и запустить Activity.
     */
    private fun tryLaunchWorkProfile(): Boolean {
        try {
            val launcherApps = getSystemService(LauncherApps::class.java) ?: return false
            val userManager = getSystemService(UserManager::class.java) ?: return false
            val profiles = userManager.userProfiles
            for (profile in profiles) {
                if (profile == android.os.Process.myUserHandle()) continue
                // Это другой профиль — пробуем запустить наше приложение там
                val activities = launcherApps.getActivityList(packageName, profile)
                if (activities.isNotEmpty()) {
                    launcherApps.startMainActivity(
                        activities[0].componentName,
                        profile,
                        null,
                        null
                    )
                    return true
                }
            }
        } catch (e: Exception) {
            // Если не удалось — пользователь откроет вручную (Toast уже показан)
        }
        return false
    }


}
