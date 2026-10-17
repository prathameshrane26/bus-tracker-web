package com.example.myapplication2.data

import android.content.Context
import com.example.myapplication2.ui.map.BusLocation
import org.json.JSONArray
import org.json.JSONObject

object BusCacheRepository {

    private const val PREF_NAME = "bus_cache_prefs"
    private const val KEY_BUSES = "cached_buses_json"

    fun getCachedBuses(context: Context): List<BusLocation> {
        return runCatching {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val jsonString = prefs.getString(KEY_BUSES, null) ?: return emptyList()
            val array = JSONArray(jsonString)
            val list = mutableListOf<BusLocation>()

            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val bId = item.optString("id", "")
                if (bId.isBlank()) continue

                list.add(
                    BusLocation(
                        id = bId,
                        routeName = item.optString("routeName", "Panaji - Margao"),
                        destination = item.optString("destination", "Margao"),
                        startPoint = item.optString("startPoint", "Panaji"),
                        latitude = item.optDouble("latitude", 15.4989),
                        longitude = item.optDouble("longitude", 73.8278),
                        busCategory = item.optString("busCategory", "KTCL"),
                        status = item.optString("status", "In Transit"),
                        isActivelySharing = item.optBoolean("isActivelySharing", true),
                        driverName = item.optString("driverName", "Driver Active"),
                        updatedAt = item.optDouble("updatedAt", System.currentTimeMillis() / 1000.0)
                    )
                )
            }
            list
        }.getOrDefault(emptyList())
    }

    fun saveCachedBuses(context: Context, buses: List<BusLocation>) {
        if (buses.isEmpty()) return
        runCatching {
            val array = JSONArray()
            buses.forEach { bus ->
                val item = JSONObject().apply {
                    put("id", bus.id)
                    put("routeName", bus.routeName)
                    put("destination", bus.destination)
                    put("startPoint", bus.startPoint)
                    put("latitude", bus.latitude)
                    put("longitude", bus.longitude)
                    put("busCategory", bus.busCategory)
                    put("status", bus.status)
                    put("isActivelySharing", bus.isActivelySharing)
                    put("driverName", bus.driverName)
                    put("updatedAt", bus.updatedAt)
                }
                array.put(item)
            }

            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_BUSES, array.toString()).apply()
        }
    }
}
