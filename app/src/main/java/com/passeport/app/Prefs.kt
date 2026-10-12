package com.passeport.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Rate(val phpPerEur: Double, val day: String, val fetchedAt: Long, val source: String)

data class Kept(val time: Long, val eur: Double, val php: Double, val fromEur: Boolean)

/** Ce que l'appli garde en mémoire sur le téléphone. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("passeport", Context.MODE_PRIVATE)

    var deeplKey: String
        get() = sp.getString("deepl_key", "") ?: ""
        set(value) = sp.edit().putString("deepl_key", value.trim()).apply()

    var precise: Boolean
        get() = sp.getBoolean("precise", false)
        set(value) = sp.edit().putBoolean("precise", value).apply()

    var eurToPhp: Boolean
        get() = sp.getBoolean("eur_to_php", true)
        set(value) = sp.edit().putBoolean("eur_to_php", value).apply()

    var rate: Rate?
        get() {
            val value = sp.getString("rate", null)?.toDoubleOrNull() ?: return null
            return Rate(
                value,
                sp.getString("rate_day", "") ?: "",
                sp.getLong("rate_fetched_at", 0L),
                sp.getString("rate_source", "") ?: "",
            )
        }
        set(r) {
            if (r == null) return
            sp.edit()
                .putString("rate", r.phpPerEur.toString())
                .putString("rate_day", r.day)
                .putLong("rate_fetched_at", r.fetchedAt)
                .putString("rate_source", r.source)
                .apply()
        }

    fun history(): List<Kept> {
        val arr = try {
            JSONArray(sp.getString("history", "[]"))
        } catch (e: Exception) {
            JSONArray()
        }
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Kept(o.getLong("t"), o.getDouble("eur"), o.getDouble("php"), o.optBoolean("fromEur", true))
        }
    }

    fun addKept(k: Kept) {
        val list = listOf(k) + history().take(99)
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("t", it.time).put("eur", it.eur).put("php", it.php).put("fromEur", it.fromEur))
        }
        sp.edit().putString("history", arr.toString()).apply()
    }

    fun clearHistory() = sp.edit().remove("history").apply()
}
