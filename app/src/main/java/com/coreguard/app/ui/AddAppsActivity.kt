package com.coreguard.app.ui

import android.app.admin.DevicePolicyManager
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.coreguard.app.R
import com.coreguard.app.admin.AppDeviceAdminReceiver

/**
 * Экран добавления приложений в рабочий профиль.
 *
 * Показывает системные приложения, доступные для включения через DPC,
 * и инструкцию по установке загруженных приложений через Play Маркет рабочего профиля.
 */
class AddAppsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AM_AddApps"
    }

    private val dpm by lazy { getSystemService(DevicePolicyManager::class.java) }
    private val adminComponent by lazy { AppDeviceAdminReceiver.getComponentName(this) }

    private val selectedPackages = mutableSetOf<String>()
    private val checkBoxes = mutableListOf<Pair<CheckBox, String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        // ─── Подсказка про загруженные приложения ───
        val infoCard = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 40 }
        }
        infoCard.addView(TextView(this).apply {
            text = getString(R.string.add_apps_userapp_hint)
            setPadding(40, 32, 40, 32)
            textSize = 14f
            setLineSpacing(4f, 1f)
        })
        rootLayout.addView(infoCard)

        // ─── Заголовок секции системных приложений ───
        rootLayout.addView(TextView(this).apply {
            text = getString(R.string.add_apps_system_title)
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 20)
        })

        // ─── Список системных приложений для включения ───
        val available = getSystemAppsAvailableToEnable()

        if (available.isEmpty()) {
            rootLayout.addView(TextView(this).apply {
                text = getString(R.string.add_apps_nothing_available)
                textSize = 14f
                setPadding(0, 16, 0, 16)
            })
        } else {
            for (app in available) {
                val label = try {
                    app.loadLabel(packageManager).toString()
                } catch (e: Exception) {
                    app.packageName
                }

                val cb = CheckBox(this).apply {
                    text = "$label\n${app.packageName}"
                    isChecked = false
                    setPadding(0, 10, 0, 10)
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) selectedPackages.add(app.packageName)
                        else selectedPackages.remove(app.packageName)
                    }
                }
                checkBoxes.add(Pair(cb, app.packageName))
                rootLayout.addView(cb)
            }

            rootLayout.addView(MaterialButton(this).apply {
                text = getString(R.string.add_apps_enable_button)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 40 }
                setOnClickListener { enableSelected() }
            })
        }

        setContentView(ScrollView(this).apply { addView(rootLayout) })

        supportActionBar?.apply {
            title = getString(R.string.add_apps_title)
            setDisplayHomeAsUpEnabled(true)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    /**
     * Возвращает системные приложения, доступные на устройстве,
     * но ещё не установленные (не включённые) в рабочем профиле.
     */
    private fun getSystemAppsAvailableToEnable(): List<ApplicationInfo> {
        return try {
            @Suppress("DEPRECATION")
            packageManager.getInstalledApplications(
                PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.GET_META_DATA
            ).filter { app ->
                (app.flags and ApplicationInfo.FLAG_INSTALLED) == 0 &&
                    (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                    app.packageName != packageName
            }.sortedBy {
                try { it.loadLabel(packageManager).toString().lowercase() }
                catch (e: Exception) { it.packageName }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get app list", e)
            emptyList()
        }
    }

    private fun enableSelected() {
        if (selectedPackages.isEmpty()) {
            Toast.makeText(this, R.string.add_apps_nothing_selected, Toast.LENGTH_SHORT).show()
            return
        }

        var successCount = 0
        val toEnable = selectedPackages.toList()

        for (pkg in toEnable) {
            try {
                dpm.enableSystemApp(adminComponent, pkg)
                successCount++
                Log.i(TAG, "Enabled system app: $pkg")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to enable $pkg: ${e.message}")
            }
        }

        Toast.makeText(
            this,
            getString(R.string.add_apps_enabled_count, successCount, toEnable.size),
            Toast.LENGTH_LONG
        ).show()

        // Снять галки
        checkBoxes.forEach { (cb, pkg) ->
            if (toEnable.contains(pkg)) cb.isChecked = false
        }
        selectedPackages.clear()
    }
}
