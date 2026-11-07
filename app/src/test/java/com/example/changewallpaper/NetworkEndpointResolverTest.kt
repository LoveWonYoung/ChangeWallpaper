package com.example.changewallpaper

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkEndpointResolverTest {
    @Test
    fun `network type selects the configured API address`() {
        val settings = AppSettings(
            wifiApiBaseUrl = "http://192.168.1.10:8080",
            mobileApiBaseUrl = "https://wallpaper.example.com"
        )

        assertEquals("http://192.168.1.10:8080", NetworkEndpointResolver.baseUrl(settings, NetworkConnection.WIFI))
        assertEquals("https://wallpaper.example.com", NetworkEndpointResolver.baseUrl(settings, NetworkConnection.CELLULAR))
        assertEquals("https://wallpaper.example.com", NetworkEndpointResolver.baseUrl(settings, NetworkConnection.OTHER))
    }

    @Test
    fun `named wifi uses private address only for an exact SSID match`() {
        val settings = AppSettings(
            wifiApiBaseUrl = "http://192.168.1.10:8080",
            mobileApiBaseUrl = "https://wallpaper.example.com",
            wifiSsid = "Home WiFi"
        )

        assertEquals("http://192.168.1.10:8080", NetworkEndpointResolver.baseUrl(settings, NetworkConnection.WIFI, "Home WiFi"))
        assertEquals("https://wallpaper.example.com", NetworkEndpointResolver.baseUrl(settings, NetworkConnection.WIFI, "Cafe WiFi"))
        assertEquals("https://wallpaper.example.com", NetworkEndpointResolver.baseUrl(settings, NetworkConnection.WIFI, null))
    }

    @Test
    fun `system SSID quotes are removed before matching`() {
        assertEquals("Home WiFi", NetworkEndpointResolver.normalizeSsid("\"Home WiFi\""))
        assertEquals(null, NetworkEndpointResolver.normalizeSsid("<unknown ssid>"))
    }
}
