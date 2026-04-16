package com.coreguard.app.ui

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.coreguard.app.R
import com.coreguard.app.admin.AppPermOverride
import com.coreguard.app.admin.AppPermOverrides
import com.coreguard.app.admin.PermState
import com.coreguard.app.admin.ProfileManager

/**
 * Экран настройки индивидуальных разрешений для каждого приложения.
 *
 * По умолчанию каждое приложение следует глобальным настройкам профиля.
 * Здесь можно задать исключения — разрешить или запретить конкретное
 * разрешение для отдельного приложения вне зависимости от глобального toggle.
 */
class AppPermissionsActivity : AppCompatActivity() {

    private lateinit var profileManager: ProfileManager
    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        profileManager = ProfileManager(this)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(getColor(R.color.bg_light))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 64)
            container = this
        }
        scroll.addView(root)
        setContentView(scroll)

        supportActionBar?.title = getString(R.string.app_perms_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        buildList()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun buildList() {
        container.removeAllViews()

        // ─── Описание ─────────────────────────────────────────────────────
        val descCard = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 28 }
            cardElevation = 0f
            setCardBackgroundColor(getColor(R.color.primary_container))
            radius = 16f
        }
        descCard.addView(TextView(this).apply {
            text = getString(R.string.app_perms_description)
            setPadding(40, 32, 40, 32)
            textSize = 13f
            setLineSpacing(4f, 1f)
            setTextColor(getColor(R.color.on_surface))
        })
        container.addView(descCard)

        // ─── Список приложений ────────────────────────────────────────────
        val pm = packageManager
        @Suppress("DEPRECATION")
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.packageName != packageName }
            .sortedWith(compareBy(
                { (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0 }, // user apps first
                { it.loadLabel(pm).toString().lowercase() }
            ))

        if (apps.isEmpty()) {
            container.addView(TextView(this).apply {
                text = getString(R.string.app_perms_empty)
                setPadding(0, 32, 0, 0)
                textSize = 14f
                setTextColor(getColor(R.color.text_secondary))
            })
            return
        }

        val overrides = AppPermOverrides.load(this)
        for (app in apps) {
            container.addView(buildAppRow(app, overrides[app.packageName] ?: AppPermOverride()))
        }
    }

    private fun buildAppRow(app: ApplicationInfo, override: AppPermOverride): MaterialCardView {
        val pm = packageManager
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 10 }
            radius = 16f
            cardElevation = if (override.isDefault()) 1f else 3f
            isClickable = true
            isFocusable = true
            setOnClickListener { showEditDialog(app.packageName) }
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(28, 22, 28, 22)
        }

        // Иконка приложения
        val dpScale = resources.displayMetrics.density
        val iconSize = (44 * dpScale).toInt()
        val iconMargin = (16 * dpScale).toInt()
        row.addView(ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                rightMargin = iconMargin
                topMargin = (2 * dpScale).toInt()
            }
            try { setImageDrawable(app.loadIcon(pm)) } catch (_: Exception) {}
            scaleType = ImageView.ScaleType.FIT_CENTER
        })

        // Текстовый блок
        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        textCol.addView(TextView(this).apply {
            text = app.loadLabel(pm).toString()
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(getColor(R.color.on_surface))
        })
        textCol.addView(TextView(this).apply {
            text = buildStatusText(override)
            textSize = 12f
            setTextColor(
                if (override.isDefault()) getColor(R.color.text_secondary)
                else getColor(R.color.primary)
            )
            setPadding(0, 4, 0, 0)
        })
        row.addView(textCol)

        // Стрелка
        row.addView(TextView(this).apply {
            text = "›"
            textSize = 24f
            setTextColor(getColor(R.color.text_secondary))
        })

        card.addView(row)
        return card
    }

    private fun buildStatusText(override: AppPermOverride): String {
        if (override.isDefault()) return getString(R.string.perm_all_default)
        val parts = mutableListOf<String>()
        fun badge(state: PermState, icon: String) {
            when (state) {
                PermState.DENY  -> parts += "$icon ${getString(R.string.perm_badge_deny)}"
                PermState.ALLOW -> parts += "$icon ${getString(R.string.perm_badge_allow)}"
                PermState.AUTO  -> Unit
            }
        }
        badge(override.queryAllPackages,   "🔍")
        badge(override.readPhoneState,     "📱")
        badge(override.camera,             "📷")
        badge(override.recordAudio,        "🎤")
        badge(override.fineLocation,       "📍")
        badge(override.backgroundLocation, "🗺")
        badge(override.readContacts,       "👥")
        badge(override.sms,                "💬")
        badge(override.callLog,            "📞")
        badge(override.accounts,           "👤")
        badge(override.bluetoothScan,      "🔠")
        return parts.joinToString("  ")
    }

    private fun showEditDialog(pkg: String) {
        val pm = packageManager
        val appInfo = try { pm.getApplicationInfo(pkg, 0) } catch (_: Exception) { return }
        val appName = appInfo.loadLabel(pm).toString()
        val override = AppPermOverrides.getForPackage(this, pkg)

        val globalPhoneRestrict = getSharedPreferences("trigger_config", MODE_PRIVATE)
            .getBoolean("pref_restrict_phone_state", false)
        val globalPhoneLabel = if (globalPhoneRestrict)
            getString(R.string.perm_state_deny) else getString(R.string.perm_state_allow_default)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 16, 64, 16)
        }

        fun divider() {
            content.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                ).apply { topMargin = 20; bottomMargin = 12 }
                setBackgroundColor(0xFFDDDDDD.toInt())
            })
        }

        fun sectionTitle(text: String) {
            content.addView(TextView(this).apply {
                this.text = text
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(getColor(R.color.on_surface))
                setPadding(0, 8, 0, 6)
            })
        }

        fun makeGroup(autoText: String, denyText: String, allowText: String,
                      current: PermState): RadioGroup {
            val autoId  = View.generateViewId()
            val denyId  = View.generateViewId()
            val allowId = View.generateViewId()
            return RadioGroup(this).also { g ->
                g.addView(RadioButton(this).apply { id = autoId;  text = autoText })
                g.addView(RadioButton(this).apply { id = denyId;  text = denyText })
                g.addView(RadioButton(this).apply { id = allowId; text = allowText })
                when (current) {
                    PermState.AUTO  -> g.check(autoId)
                    PermState.DENY  -> g.check(denyId)
                    PermState.ALLOW -> g.check(allowId)
                }
            }
        }

        // index 0 = auto, index 1 = deny, index 2 = allow
        fun stateOf(group: RadioGroup): PermState = when (group.checkedRadioButtonId) {
            group.getChildAt(0).id -> PermState.AUTO
            group.getChildAt(1).id -> PermState.DENY
            else                   -> PermState.ALLOW
        }

        // ─── QUERY_ALL_PACKAGES ────────────────────────────────────────────
        sectionTitle(getString(R.string.app_perms_qap_label))
        val qapGroup = makeGroup(
            getString(R.string.perm_auto_deny),
            getString(R.string.perm_state_deny),
            getString(R.string.perm_state_allow),
            override.queryAllPackages)
        content.addView(qapGroup)

        // ─── READ_PHONE_STATE ──────────────────────────────────────────────
        divider()
        sectionTitle(getString(R.string.app_perms_rps_label))
        val rpsGroup = makeGroup(
            getString(R.string.perm_auto_format, globalPhoneLabel),
            getString(R.string.perm_state_deny),
            getString(R.string.perm_state_allow),
            override.readPhoneState)
        content.addView(rpsGroup)

        // ─── CAMERA ───────────────────────────────────────────────────────
        divider()
        sectionTitle(getString(R.string.app_perms_camera_label))
        val camGroup = makeGroup(
            getString(R.string.perm_auto_default),
            getString(R.string.perm_state_deny),
            getString(R.string.perm_allow_auto_grant),
            override.camera)
        content.addView(camGroup)

        // ─── RECORD_AUDIO ─────────────────────────────────────────────────
        divider()
        sectionTitle(getString(R.string.app_perms_mic_label))
        val micGroup = makeGroup(
            getString(R.string.perm_auto_default),
            getString(R.string.perm_state_deny),
            getString(R.string.perm_allow_auto_grant),
            override.recordAudio)
        content.addView(micGroup)

        // ─── ACCESS_FINE_LOCATION ─────────────────────────────────────────
        divider()
        sectionTitle(getString(R.string.app_perms_location_label))
        val locGroup = makeGroup(
            getString(R.string.perm_auto_default),
            getString(R.string.perm_state_deny),
            getString(R.string.perm_allow_auto_grant),
            override.fineLocation)
        content.addView(locGroup)

        // ─── ACCESS_BACKGROUND_LOCATION ───────────────────────────────────
        divider()
        sectionTitle(getString(R.string.app_perms_bg_location_label))
        val blocGroup = makeGroup(
            getString(R.string.perm_auto_default),
            getString(R.string.perm_state_deny),
            getString(R.string.perm_allow_auto_grant),
            override.backgroundLocation)
        content.addView(blocGroup)

        // ─── READ_CONTACTS ────────────────────────────────────────────────
        divider()
        sectionTitle(getString(R.string.app_perms_contacts_label))
        val cntGroup = makeGroup(
            getString(R.string.perm_auto_default),
            getString(R.string.perm_state_deny),
            getString(R.string.perm_allow_auto_grant),
            override.readContacts)
        content.addView(cntGroup)

        // ─── SMS ──────────────────────────────────────────────────────────
        divider()
        sectionTitle(getString(R.string.app_perms_sms_label))
        val smsGroup = makeGroup(
            getString(R.string.perm_auto_default),
            getString(R.string.perm_state_deny),
            getString(R.string.perm_allow_auto_grant),
            override.sms)
        content.addView(smsGroup)

        // ─── CALL_LOG ─────────────────────────────────────────────────────
        divider()
        sectionTitle(getString(R.string.app_perms_call_log_label))
        val clgGroup = makeGroup(
            getString(R.string.perm_auto_default),
            getString(R.string.perm_state_deny),
            getString(R.string.perm_allow_auto_grant),
            override.callLog)
        content.addView(clgGroup)

        // ─── ACCOUNTS ─────────────────────────────────────────────────────
        divider()
        sectionTitle(getString(R.string.app_perms_accounts_label))
        val accGroup = makeGroup(
            getString(R.string.perm_auto_default),
            getString(R.string.perm_state_deny),
            getString(R.string.perm_allow_auto_grant),
            override.accounts)
        content.addView(accGroup)

        // ─── BLUETOOTH_SCAN ───────────────────────────────────────────────
        divider()
        sectionTitle(getString(R.string.app_perms_bt_scan_label))
        val btsGroup = makeGroup(
            getString(R.string.perm_auto_default),
            getString(R.string.perm_state_deny),
            getString(R.string.perm_allow_auto_grant),
            override.bluetoothScan)
        content.addView(btsGroup)

        val scrollView = ScrollView(this)
        scrollView.addView(content)

        AlertDialog.Builder(this)
            .setTitle(appName)
            .setView(scrollView)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                AppPermOverrides.setForPackage(this, pkg, AppPermOverride(
                    queryAllPackages   = stateOf(qapGroup),
                    readPhoneState     = stateOf(rpsGroup),
                    camera             = stateOf(camGroup),
                    recordAudio        = stateOf(micGroup),
                    fineLocation       = stateOf(locGroup),
                    backgroundLocation = stateOf(blocGroup),
                    readContacts       = stateOf(cntGroup),
                    sms                = stateOf(smsGroup),
                    callLog            = stateOf(clgGroup),
                    accounts           = stateOf(accGroup),
                    bluetoothScan      = stateOf(btsGroup)
                ))
                profileManager.applyPermissionsForNewApp(pkg)
                buildList()
            }
            .setNeutralButton(R.string.btn_reset) { _, _ ->
                AppPermOverrides.setForPackage(this, pkg, AppPermOverride())
                profileManager.applyPermissionsForNewApp(pkg)
                buildList()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
