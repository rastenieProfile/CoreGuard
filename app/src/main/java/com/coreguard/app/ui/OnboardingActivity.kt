package com.coreguard.app.ui

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.coreguard.app.R

class OnboardingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        setContentView(R.layout.activity_onboarding)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        }

        val title = findViewById<TextView>(R.id.onboardingTitle)
        val content = findViewById<TextView>(R.id.onboardingContent)
        val btnOk = findViewById<TextView>(R.id.btnOk)

        val isOwner = intent.getBooleanExtra("is_profile_owner", false)

        title.text = getString(R.string.onboarding_title)
        content.text = if (isOwner)
            getText(R.string.onboarding_message_wp)
        else
            getText(R.string.onboarding_message)
        btnOk.text = getString(R.string.onboarding_ok)

        // OEM-предупреждение
        val oemCard = findViewById<LinearLayout>(R.id.oemCard)
        val oemTitle = findViewById<TextView>(R.id.oemTitle)
        val oemContent = findViewById<TextView>(R.id.oemContent)
        val oemRes = getOemMessageRes()
        if (oemRes != 0) {
            oemCard.visibility = View.VISIBLE
            oemTitle.text = getString(R.string.oem_warning_title)
            oemContent.text = getString(oemRes)
        }

        btnOk.setOnClickListener { finishOnboarding() }
    }

    private fun getOemMessageRes(): Int {
        val mfr = android.os.Build.MANUFACTURER.lowercase()
        return when {
            mfr in listOf("xiaomi", "redmi", "poco") -> R.string.oem_warning_xiaomi
            mfr in listOf("huawei", "honor") -> R.string.oem_warning_huawei
            mfr == "samsung" -> R.string.oem_warning_samsung
            mfr in listOf("oppo", "realme", "oneplus", "vivo") -> R.string.oem_warning_oppo
            else -> 0  // generic — не показываем карточку
        }
    }

    private fun finishOnboarding() {
        getSharedPreferences("app_state", MODE_PRIVATE).edit()
            .putBoolean("first_launch_shown", true)
            .putBoolean("privacy_warning_shown", true)
            .putBoolean("wp_onboarding_shown", true)
            .putBoolean("oem_warning_shown", true)
            .commit()
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Блокируем — пользователь должен нажать "Понятно"
    }
}
