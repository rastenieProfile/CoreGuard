package com.coreguard.app.trigger

/**
 * Конфигурация триггеров.
 *
 * Логика: ВСЕГДА ИЛИ. Если хотя бы один включённый триггер активен → действие.
 * Действие: отмеченные приложения замораживаются, неотмеченные продолжают
 * работать напрямую (в обход VPN основного профиля).
 */
data class TriggerConfig(
    val triggerOnVpn: Boolean = false,
    val triggerOnWifi: Boolean = false,
    val wifiSsidList: Set<String> = emptySet(),
    val triggerOnMobile: Boolean = false,
    val triggerOnOperator: Boolean = false,
    val operatorList: Set<String> = emptySet(),
    val wifiForceKillSwitch: Boolean = false,
    val killSwitch: Boolean = false,
    /** Набор trigger-ключей, отмеченных как «корпоративная сеть» (VPN, mobile) */
    val corporateTriggers: Set<String> = emptySet(),
    /** Wi-Fi SSID, отмеченные как корпоративные */
    val corporateWifiSsids: Set<String> = emptySet(),
    /** Операторы, отмеченные как корпоративные */
    val corporateOperators: Set<String> = emptySet()
)
