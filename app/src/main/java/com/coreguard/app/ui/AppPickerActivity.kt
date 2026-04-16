package com.coreguard.app.ui

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.coreguard.app.R

/**
 * Экран выбора приложений, которые будут замораживаться при триггерах.
 * Показывает список пользовательских приложений из текущего профиля.
 */
class AppPickerActivity : AppCompatActivity() {

    private val selectedPackages = mutableSetOf<String>()
    private val checkBoxes = mutableListOf<CheckBox>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("trigger_config", MODE_PRIVATE)
        selectedPackages.addAll(
            prefs.getStringSet("managed_packages", emptySet()) ?: emptySet()
        )

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        // Заголовок
        rootLayout.addView(TextView(this).apply {
            text = getString(R.string.app_picker_title)
            textSize = 20f
            setPadding(0, 0, 0, 24)
        })

        // Кнопки "Выбрать все" / "Снять все"
        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 16)
        }

        buttonRow.addView(MaterialButton(this).apply {
            text = getString(R.string.btn_select_all)
            setOnClickListener {
                checkBoxes.forEach { cb -> cb.isChecked = true }
            }
        })

        buttonRow.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.btn_deselect_all)
            setOnClickListener {
                checkBoxes.forEach { cb -> cb.isChecked = false }
            }
        })

        rootLayout.addView(buttonRow)

        // Список приложений
        val apps = getInstalledUserApps()

        for (app in apps) {
            val cb = CheckBox(this).apply {
                text = "${app.loadLabel(packageManager)} (${app.packageName})"
                isChecked = selectedPackages.contains(app.packageName)
                setPadding(0, 8, 0, 8)
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selectedPackages.add(app.packageName)
                    else selectedPackages.remove(app.packageName)
                }
            }
            checkBoxes.add(cb)
            rootLayout.addView(cb)
        }

        // Кнопка сохранения
        rootLayout.addView(MaterialButton(this).apply {
            text = getString(R.string.btn_save)
            setPadding(0, 32, 0, 0)
            setOnClickListener { saveAndFinish() }
        })

        val scrollView = ScrollView(this).apply { addView(rootLayout) }
        setContentView(scrollView)

        supportActionBar?.apply {
            title = getString(R.string.app_picker_header)
            setDisplayHomeAsUpEnabled(true)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun getInstalledUserApps(): List<ApplicationInfo> {
        return packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .filter { it.packageName != packageName } // исключаем себя
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
    }

    private fun saveAndFinish() {
        getSharedPreferences("trigger_config", MODE_PRIVATE)
            .edit()
            .putStringSet("managed_packages", selectedPackages.toSet())
            .apply()
        finish()
    }
}
