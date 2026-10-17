package com.example.myapplication2.data

import android.content.Context
import org.json.JSONObject

data class DriverDropdownOptions(
    val busIds: List<String>,
    val startPoints: List<String>,
    val destinations: List<String>,
    val routes: List<String>
)

object DriverOptionsRepository {

    private val defaultOptions = DriverDropdownOptions(
        busIds = listOf("GA-01-A-1001", "GA-02-B-2002", "GA-03-X-1001", "GA-04-C-3003", "GA-08-D-4004"),
        startPoints = listOf("Panaji", "Margao", "Vasco da Gama", "Mapusa", "Ponda", "Bicholim"),
        destinations = listOf("Margao", "Panaji", "Ponda", "Calangute", "Vasco da Gama", "Mapusa"),
        routes = listOf("Panaji - Margao", "Panaji - Calangute", "Vasco - Panaji", "Route 101 via NH66", "Route 201 via Highway")
    )

    fun loadOptions(context: Context? = null): DriverDropdownOptions {
        var jsonString: String? = null

        if (context != null) {
            runCatching {
                context.assets.open("bus_options.json").use { inputStream ->
                    jsonString = inputStream.bufferedReader().readText()
                }
            }
        }

        val rawJson = jsonString ?: return defaultOptions

        return runCatching {
            val jsonObject = JSONObject(rawJson)
            val busIds = jsonObject.optJSONArray("busIds")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: defaultOptions.busIds

            val startPoints = jsonObject.optJSONArray("startPoints")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: defaultOptions.startPoints

            val destinations = jsonObject.optJSONArray("destinations")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: defaultOptions.destinations

            val routes = jsonObject.optJSONArray("routes")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: defaultOptions.routes

            DriverDropdownOptions(
                busIds = busIds,
                startPoints = startPoints,
                destinations = destinations,
                routes = routes
            )
        }.getOrDefault(defaultOptions)
    }
}
