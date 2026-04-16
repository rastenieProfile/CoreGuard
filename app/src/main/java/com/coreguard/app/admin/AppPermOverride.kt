package com.coreguard.app.admin

import android.content.Context
import org.json.JSONObject

/**
 * Состояние разрешения для конкретного приложения.
 *
 * AUTO  — следовать глобальной настройке профиля
 * DENY  — запретить принудительно (независимо от глобальной)
 * ALLOW — разрешить принудительно (снять запрет)
 */
enum class PermState { AUTO, DENY, ALLOW }

/**
 * Переопределения разрешений для конкретного приложения.
 * AUTO — следовать глобальной настройке / системному дефолту.
 */
data class AppPermOverride(
    val queryAllPackages:   PermState = PermState.AUTO,
    val readPhoneState:     PermState = PermState.AUTO,
    val camera:             PermState = PermState.AUTO,
    val recordAudio:        PermState = PermState.AUTO,
    val fineLocation:       PermState = PermState.AUTO,
    val backgroundLocation: PermState = PermState.AUTO,
    val readContacts:       PermState = PermState.AUTO,
    val sms:                PermState = PermState.AUTO,
    val callLog:            PermState = PermState.AUTO,
    val accounts:           PermState = PermState.AUTO,
    val bluetoothScan:      PermState = PermState.AUTO
) {
    fun isDefault() = queryAllPackages   == PermState.AUTO
        && readPhoneState     == PermState.AUTO
        && camera             == PermState.AUTO
        && recordAudio        == PermState.AUTO
        && fineLocation       == PermState.AUTO
        && backgroundLocation == PermState.AUTO
        && readContacts       == PermState.AUTO
        && sms                == PermState.AUTO
        && callLog            == PermState.AUTO
        && accounts           == PermState.AUTO
        && bluetoothScan      == PermState.AUTO
}

/**
 * Хранилище переопределений разрешений.
 * Сохраняет данные как JSON в SharedPreferences.
 *
 * Формат: { "com.sberbank": { "qap": "ALLOW", "rps": "DENY" }, ... }
 */
object AppPermOverrides {

    private const val PREFS_NAME = "app_preferences"
    private const val KEY = "app_perm_overrides"

    fun load(context: Context): MutableMap<String, AppPermOverride> {
        val json = context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return mutableMapOf()
        return try {
            val obj = JSONObject(json)
            val map = mutableMapOf<String, AppPermOverride>()
            for (pkg in obj.keys()) {
                val e = obj.getJSONObject(pkg)
                map[pkg] = AppPermOverride(
                    queryAllPackages   = PermState.valueOf(e.optString("qap",  "AUTO")),
                    readPhoneState     = PermState.valueOf(e.optString("rps",  "AUTO")),
                    camera             = PermState.valueOf(e.optString("cam",  "AUTO")),
                    recordAudio        = PermState.valueOf(e.optString("mic",  "AUTO")),
                    fineLocation       = PermState.valueOf(e.optString("loc",  "AUTO")),
                    backgroundLocation = PermState.valueOf(e.optString("bloc", "AUTO")),
                    readContacts       = PermState.valueOf(e.optString("cnt",  "AUTO")),
                    sms                = PermState.valueOf(e.optString("sms",  "AUTO")),
                    callLog            = PermState.valueOf(e.optString("clg",  "AUTO")),
                    accounts           = PermState.valueOf(e.optString("acc",  "AUTO")),
                    bluetoothScan      = PermState.valueOf(e.optString("bts",  "AUTO"))
                )
            }
            map
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    fun save(context: Context, overrides: Map<String, AppPermOverride>) {
        val obj = JSONObject()
        for ((pkg, o) in overrides) {
            if (o.isDefault()) continue
            val e = JSONObject()
            if (o.queryAllPackages   != PermState.AUTO) e.put("qap",  o.queryAllPackages.name)
            if (o.readPhoneState     != PermState.AUTO) e.put("rps",  o.readPhoneState.name)
            if (o.camera             != PermState.AUTO) e.put("cam",  o.camera.name)
            if (o.recordAudio        != PermState.AUTO) e.put("mic",  o.recordAudio.name)
            if (o.fineLocation       != PermState.AUTO) e.put("loc",  o.fineLocation.name)
            if (o.backgroundLocation != PermState.AUTO) e.put("bloc", o.backgroundLocation.name)
            if (o.readContacts       != PermState.AUTO) e.put("cnt",  o.readContacts.name)
            if (o.sms                != PermState.AUTO) e.put("sms",  o.sms.name)
            if (o.callLog            != PermState.AUTO) e.put("clg",  o.callLog.name)
            if (o.accounts           != PermState.AUTO) e.put("acc",  o.accounts.name)
            if (o.bluetoothScan      != PermState.AUTO) e.put("bts",  o.bluetoothScan.name)
            obj.put(pkg, e)
        }
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, obj.toString())
            .apply()
    }

    fun setForPackage(context: Context, pkg: String, override: AppPermOverride) {
        val map = load(context).toMutableMap()
        if (override.isDefault()) map.remove(pkg) else map[pkg] = override
        save(context, map)
    }

    fun getForPackage(context: Context, pkg: String): AppPermOverride =
        load(context)[pkg] ?: AppPermOverride()
}
