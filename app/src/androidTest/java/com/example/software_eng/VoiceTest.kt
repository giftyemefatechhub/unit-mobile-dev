package com.example.software_eng


import org.junit.Assert.*
import org.junit.Test
import java.util.*

class VoiceTest {

    private val testDevices = listOf(
        Device(1, "White_LED", "desc", false, "LED", 0.0),
        Device(2, "Yellow_LED", "desc", false, "LED", 0.0),
        Device(3, "Buzzer", "desc", true, "BUZZER", 0.0),
        Device(4, "Gas_Sensor", "desc", false, "SENSOR", 0.0)
    )

    private fun normalizeName(name: String): String {
        return name.replace("_", "").replace("\\s+".toRegex(), "").lowercase(Locale.getDefault())
    }

    private fun findDeviceFromVoice(text: String, devices: List<Device>): Pair<Device?, Boolean?> {
        val parts = text.lowercase(Locale.getDefault()).split("\\s+".toRegex()).filter { it.isNotEmpty() }

        if (parts.size < 2) return Pair(null, null)

        val desiredStatus = when (parts.last()) {
            "on" -> true
            "off" -> false
            else -> return Pair(null, null)
        }

        val spokenName = if (parts.size >= 3) parts.subList(0, parts.lastIndex).joinToString(" ")
        else parts[0]

        val cleanedSpoken = normalizeName(spokenName)

        val matched = devices.firstOrNull {
            normalizeName(it.name) == cleanedSpoken
        }

        return Pair(matched, desiredStatus)
    }

    @Test
    fun testVoiceCommandMatchesCorrectDevice() {
        val (device, status) = findDeviceFromVoice("yellow led on", testDevices)

        assertNotNull("Device should be found", device)
        assertEquals("Should match Yellow_LED", "Yellow_LED", device?.name)
        assertEquals("Desired status should be true (on)", true, status)
    }

    @Test
    fun testVoiceCommandMatchesOneWordDevice() {
        val (device, status) = findDeviceFromVoice("buzzer off", testDevices)

        assertNotNull("Device should be found", device)
        assertEquals("Should match Buzzer", "Buzzer", device?.name)
        assertEquals("Desired status should be false (off)", false, status)
    }

    @Test
    fun testVoiceCommandHandlesUnderscore() {
        val (device, status) = findDeviceFromVoice("white led off", testDevices)

        assertNotNull("Device should be found", device)
        assertEquals("Should match White_LED", "White_LED", device?.name)
    }

    @Test
    fun testVoiceCommandWithUnknownDevice() {
        val (device, _) = findDeviceFromVoice("fridge on", testDevices)
        assertNull("Unknown device should return null", device)
    }

    @Test
    fun testInvalidStatus() {
        val (_, status) = findDeviceFromVoice("white led maybe", testDevices)
        assertNull("Invalid status should return null", status)
    }
}
