package com.coreguard.app.trigger

/**
 * Описывает текущее состояние сети устройства.
 */
data class NetworkState(
    /** Активен ли VPN-транспорт на устройстве */
    val isVpnActive: Boolean = false,

    /** Подключён ли Wi-Fi */
    val isWifiConnected: Boolean = false,

    /** SSID текущей Wi-Fi сети (null если не подключено или нет разрешения) */
    val wifiSsid: String? = null,

    /** Подключена ли мобильная сеть (мобильные данные) */
    val isCellularConnected: Boolean = false,

    /** Имя текущего мобильного оператора (null если нет SIM или нет сети) */
    val operatorName: String? = null
)
