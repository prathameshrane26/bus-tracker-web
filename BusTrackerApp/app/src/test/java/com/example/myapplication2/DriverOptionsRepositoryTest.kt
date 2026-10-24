package com.example.myapplication2

import com.example.myapplication2.data.DriverOptionsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DriverOptionsRepositoryTest {

    private val sampleRoutes = listOf(
        "Panaji-Mapusa-Bicholim-Sanquelim",
        "Panaji-Mapusa-Bicholim-Sanquelim-Valpoi",
        "Panaji-Marcel-Sanquelim-Valpoi",
        "Bicholim-Sanquelim-Honda-Ponda",
        "Bicholim-Mapusa-Panaji",
        "Panaji-Marcel-Sanquelim-Advoi",
        "Panaji-Agassaim-Margao"
    )

    @Test
    fun filterRoutes_returnsAll_whenStartAndDestAreBlank() {
        val result = DriverOptionsRepository.filterRoutes(sampleRoutes, "", "")
        assertEquals(sampleRoutes, result)
    }

    @Test
    fun filterRoutes_matchesForward_fromPanajiToValpoi() {
        val result = DriverOptionsRepository.filterRoutes(sampleRoutes, "Panaji", "Valpoi")
        assertEquals(2, result.size)
        assertTrue(result.contains("Panaji-Mapusa-Bicholim-Sanquelim-Valpoi"))
        assertTrue(result.contains("Panaji-Marcel-Sanquelim-Valpoi"))
    }

    @Test
    fun filterRoutes_matchesReturnTrip_fromSanquelimToPanaji() {
        // Order does not matter: Sanquelim -> Panaji matches routes containing both stops regardless of stop order
        val result = DriverOptionsRepository.filterRoutes(sampleRoutes, "Sanquelim", "Panaji")
        assertEquals(4, result.size)
        assertTrue(result.contains("Panaji-Mapusa-Bicholim-Sanquelim"))
        assertTrue(result.contains("Panaji-Mapusa-Bicholim-Sanquelim-Valpoi"))
        assertTrue(result.contains("Panaji-Marcel-Sanquelim-Valpoi"))
        assertTrue(result.contains("Panaji-Marcel-Sanquelim-Advoi"))
    }

    @Test
    fun filterRoutes_matchesReturnTrip_fromMargaoToPanaji() {
        val result = DriverOptionsRepository.filterRoutes(sampleRoutes, "Margao", "Panaji")
        assertEquals(1, result.size)
        assertEquals("Panaji-Agassaim-Margao", result[0])
    }

    @Test
    fun filterRoutes_deduplicatesReverseRoutes() {
        val listWithDuplicates = listOf(
            "Panaji-Agassaim-Margao",
            "Margao-Agassaim-Panaji"
        )
        val result = DriverOptionsRepository.filterRoutes(listWithDuplicates, "Panaji", "Margao")
        assertEquals(1, result.size)
        assertEquals("Panaji-Agassaim-Margao", result[0])
    }

    @Test
    fun filterRoutes_returnsFallback_whenNoPredefinedRouteMatches() {
        val result = DriverOptionsRepository.filterRoutes(sampleRoutes, "UnknownStart", "UnknownDest")
        assertEquals(2, result.size)
        assertTrue(result.contains("UnknownStart - UnknownDest Direct"))
        assertTrue(result.contains("UnknownStart - UnknownDest via Highway"))
    }
}
