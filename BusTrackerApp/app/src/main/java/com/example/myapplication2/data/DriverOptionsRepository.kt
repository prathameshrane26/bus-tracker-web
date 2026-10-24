package com.example.myapplication2.data

import android.content.Context
import org.json.JSONObject

data class DriverDropdownOptions(
    val startPoints: List<String>,
    val destinations: List<String>,
    val routes: List<String>
)

object DriverOptionsRepository {

    private val defaultOptions = DriverDropdownOptions(
        startPoints = listOf("Panaji", "Margao", "Vasco da Gama", "Mapusa", "Ponda", "Bicholim", "Valpoi", "Sanquelim", "Advoi", "Calangute"),
        destinations = listOf("Margao", "Panaji", "Ponda", "Calangute", "Vasco da Gama", "Mapusa", "Bicholim", "Valpoi", "Sanquelim", "Advoi"),
        routes = listOf(
            "Panaji-Mapusa-Bicholim-Sanquelim",
            "Panaji-Mapusa-Bicholim-Sanquelim-Valpoi",
            "Panaji-Marcel-Sanquelim-Valpoi",
            "Bicholim-Sanquelim-Honda-Ponda",
            "Bicholim-Mapusa-Panaji",
            "Panaji-Marcel-Sanquelim-Advoi",
            "Panaji-Agassaim-Margao",
            "Panaji-Bambolim-Vasco da Gama",
            "Panaji-Porvorim-Calangute",
            "Panaji-Old Goa-Ponda",
            "Margao-Ponda"
        )
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
                startPoints = startPoints,
                destinations = destinations,
                routes = routes
            )
        }.getOrDefault(defaultOptions)
    }

    /**
     * Filters available routes based on the driver's selected start point and destination point.
     * Only shows one unique option per physical route (does not duplicate reverse representations).
     */
    fun filterRoutes(
        allRoutes: List<String>,
        startPoint: String,
        destination: String
    ): List<String> {
        val start = startPoint.trim()
        val dest = destination.trim()

        val rawMatches = if (start.isBlank() && dest.isBlank()) {
            allRoutes
        } else {
            val matched = allRoutes.filter { route ->
                isRouteMatching(route, start, dest)
            }
            if (matched.isNotEmpty()) {
                matched
            } else if (start.isNotBlank() && dest.isNotBlank()) {
                val partialMatches = allRoutes.filter { route ->
                    val lowerRoute = route.lowercase()
                    lowerRoute.contains(start.lowercase()) || lowerRoute.contains(dest.lowercase())
                }
                if (partialMatches.isNotEmpty()) {
                    partialMatches
                } else {
                    listOf("$start - $dest Direct", "$start - $dest via Highway")
                }
            } else if (start.isNotBlank()) {
                allRoutes.filter { it.lowercase().contains(start.lowercase()) }.ifEmpty { allRoutes }
            } else {
                allRoutes.filter { it.lowercase().contains(dest.lowercase()) }.ifEmpty { allRoutes }
            }
        }

        return deduplicateReverseRoutes(rawMatches)
    }

    private fun deduplicateReverseRoutes(routes: List<String>): List<String> {
        val result = mutableListOf<String>()
        val seenSignatures = mutableSetOf<Set<String>>()

        for (route in routes) {
            val stops = route.split(Regex("[-➔]|->|/|\\svia\\s"))
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }

            if (stops.size >= 2) {
                val ends = setOf(stops.first(), stops.last())
                val intermediates = stops.subList(1, stops.size - 1).toSet()
                val signature = ends + intermediates
                if (seenSignatures.contains(signature)) {
                    continue
                }
                seenSignatures.add(signature)
            }
            result.add(route)
        }

        return result
    }

    private fun isRouteMatching(
        route: String,
        startPoint: String,
        destination: String
    ): Boolean {
        val start = startPoint.trim()
        val dest = destination.trim()

        if (start.isBlank() && dest.isBlank()) return true

        // Delimiters for stops in route string (e.g., "Panaji-Mapusa-Bicholim-Sanquelim", "Panaji - Margao", "Panaji -> Valpoi")
        val stops = route.split(Regex("[-➔]|->|/|\\svia\\s")).map { it.trim() }.filter { it.isNotEmpty() }

        if (start.isNotBlank() && dest.isNotBlank()) {
            val startIndex = stops.indexOfFirst { it.equals(start, ignoreCase = true) || it.contains(start, ignoreCase = true) }
            val destIndex = stops.indexOfFirst { it.equals(dest, ignoreCase = true) || it.contains(dest, ignoreCase = true) }

            if (startIndex != -1 && destIndex != -1) {
                // Order does not matter; source & destination can be at either end or anywhere along the route
                return startIndex != destIndex || stops.size == 1
            }

            // Fallback string matching if not split properly
            val lowerRoute = route.lowercase()
            return lowerRoute.contains(start.lowercase()) && lowerRoute.contains(dest.lowercase())
        }

        if (start.isNotBlank()) {
            return stops.any { it.equals(start, ignoreCase = true) || it.contains(start, ignoreCase = true) }
        }

        if (dest.isNotBlank()) {
            return stops.any { it.equals(dest, ignoreCase = true) || it.contains(dest, ignoreCase = true) }
        }

        return true
    }
}
