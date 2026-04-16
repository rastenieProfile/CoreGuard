package com.coreguard.app.trigger

import android.util.Log

/**
 * Чистая логика: должен ли хотя бы один триггер сработать?
 *
 * Правило: ЕСЛИ любой включённый триггер активен → true.
 * Без режимов AND. Без выбора действия. Простота = надёжность.
 */
object TriggerEvaluator {

    private const val TAG = "AM_Trigger"

    data class Result(
        val shouldActivate: Boolean,
        val forceKillSwitch: Boolean = false,
        /** true → заморозить ВСЕ приложения (корпоративный триггер) */
        val forceFreezeAll: Boolean = false
    )

    fun shouldActivate(state: NetworkState, config: TriggerConfig): Boolean {
        return evaluate(state, config).shouldActivate
    }

    fun evaluate(state: NetworkState, config: TriggerConfig): Result {
        val vpnTriggered = config.triggerOnVpn && state.isVpnActive
        val wifiTriggered = config.triggerOnWifi && isWifiMatched(state, config)
        val mobileTriggered = config.triggerOnMobile && state.isCellularConnected
        val operatorTriggered = config.triggerOnOperator && isOperatorMatched(state, config)

        val result = vpnTriggered || wifiTriggered || mobileTriggered || operatorTriggered
        val forceKill = wifiTriggered && config.wifiForceKillSwitch

        // Корпоративный режим: если хотя бы один «корпоративный» триггер сработал
        val corporateKeys = config.corporateTriggers
        val isCorporate = (vpnTriggered && "trigger_vpn" in corporateKeys)
                || (wifiTriggered && isWifiCorporate(state, config))
                || (mobileTriggered && "trigger_mobile" in corporateKeys)
                || (operatorTriggered && isOperatorCorporate(state, config))

        Log.d(TAG, "Evaluate: vpn=${state.isVpnActive}->$vpnTriggered, " +
                "wifi=${state.wifiSsid}->$wifiTriggered, " +
                "mobile=${state.isCellularConnected}->$mobileTriggered, " +
                "operator=${state.operatorName}->$operatorTriggered, " +
                "forceKill=$forceKill, corporate=$isCorporate => $result")

        return Result(result, forceKill || isCorporate, isCorporate)
    }

    private fun isWifiMatched(state: NetworkState, config: TriggerConfig): Boolean {
        if (!state.isWifiConnected) return false
        val ssid = state.wifiSsid ?: return false
        return config.wifiSsidList.any { it.equals(ssid, ignoreCase = true) }
    }

    /** Подключённая Wi-Fi сеть отмечена как корпоративная? */
    private fun isWifiCorporate(state: NetworkState, config: TriggerConfig): Boolean {
        val ssid = state.wifiSsid ?: return false
        return config.corporateWifiSsids.any { it.equals(ssid, ignoreCase = true) }
    }

    private fun isOperatorMatched(state: NetworkState, config: TriggerConfig): Boolean {
        val name = state.operatorName ?: return false
        return config.operatorList.any { it.equals(name, ignoreCase = true) }
    }

    /** Текущий оператор отмечен как корпоративный? */
    private fun isOperatorCorporate(state: NetworkState, config: TriggerConfig): Boolean {
        val name = state.operatorName ?: return false
        return config.corporateOperators.any { it.equals(name, ignoreCase = true) }
    }
}
