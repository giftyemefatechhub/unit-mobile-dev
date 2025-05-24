package com.example.software_eng

import org.junit.Test
import org.junit.Assert.*


class SearchLogicTest {

    // Sample devices exactly matching your Composable’s allDevices list
    private val testDevices = listOf(
        Device(1, "White_LED",   "desc", false, "LED",    0.0),
        Device(2, "Yellow_LED",  "desc", false, "LED",    0.0),
        Device(3, "Buzzer",      "desc", true,  "BUZZER", 0.0),
        Device(4, "Gas_Sensor",  "desc", false, "SENSOR", 0.0)
    )

    // Exactly the same filter logic you have in your LaunchedEffect
    private fun filterDevices(query: String) =
        if (query.isBlank()) emptyList<Device>()
        else testDevices.filter { it.name.contains(query, ignoreCase = true) }

    @Test
    fun blankQueryReturnsEmptyList() {
        assertTrue(
            "Empty searchQuery should yield no results",
            filterDevices("").isEmpty()
        )
    }

    @Test
    fun exactNameFindsBuzzer() {
        val results = filterDevices("Buzzer")
        assertEquals("Should find exactly one", 1, results.size)
        assertEquals("Buzzer", results[0].name)
    }

    @Test
    fun caseInsensitiveLedSearch() {
        val names = filterDevices("led").map { it.name }
        // Should pick up both White_LED and Yellow_LED
        assertTrue("Missing White_LED",   names.contains("White_LED"))
        assertTrue("Missing Yellow_LED",  names.contains("Yellow_LED"))
        assertEquals("Only two matches expected", 2, names.size)
    }

    @Test
    fun unknownDeviceReturnsEmpty() {
        assertTrue(
            "Searching for fridge should yield no results",
            filterDevices("fridge").isEmpty()
        )
    }
}
