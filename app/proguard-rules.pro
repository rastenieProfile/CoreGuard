# CoreGuard

# ── Весь пакет приложения ────────────────────────────────────────────────────
# Entry-points (Activity, Service, Receiver) R8 сохраняет автоматически через
# манифест. Явно оставляем остальное.
-keep class com.coreguard.app.** { *; }

# ── ZXing (QR-генерация в DonateActivity) ────────────────────────────────────
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# ── AndroidX Preference ──────────────────────────────────────────────────────
-keep class androidx.preference.** { *; }
-keepclassmembers class * extends androidx.preference.Preference {
    public <init>(android.content.Context, android.util.AttributeSet);
}

# ── ViewBinding ──────────────────────────────────────────────────────────────
-keep class * implements androidx.viewbinding.ViewBinding {
    public static ** bind(android.view.View);
    public static ** inflate(...);
}

# ── Android DPC / DevicePolicyManager ────────────────────────────────────────
-keep class android.app.admin.** { *; }

# ── Kotlin ───────────────────────────────────────────────────────────────────
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings { <fields>; }

# ── Логи: убрать в release ───────────────────────────────────────────────────
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

