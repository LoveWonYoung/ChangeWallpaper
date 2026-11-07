package com.example.changewallpaper

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat

enum class NetworkConnection { WIFI, CELLULAR, OTHER }

object NetworkEndpointResolver {
    fun currentConnection(context: Context): NetworkConnection {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return NetworkConnection.OTHER
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkConnection.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkConnection.CELLULAR
            else -> NetworkConnection.OTHER
        }
    }

    @Suppress("DEPRECATION")
    fun currentSsid(context: Context): String? {
        if (currentConnection(context) != NetworkConnection.WIFI) return null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED) return null
        val manager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val value = runCatching { manager.connectionInfo?.ssid }.getOrNull()
        return normalizeSsid(value)
    }

    internal fun normalizeSsid(value: String?): String? = value
        ?.removeSurrounding("\"")
        ?.takeUnless { it == WifiManager.UNKNOWN_SSID || it.isBlank() }

    fun baseUrl(settings: AppSettings, connection: NetworkConnection, ssid: String? = null): String = when (connection) {
        NetworkConnection.WIFI -> if (settings.wifiSsid == null || settings.wifiSsid == ssid) {
            settings.wifiApiBaseUrl
        } else settings.mobileApiBaseUrl
        NetworkConnection.CELLULAR, NetworkConnection.OTHER -> settings.mobileApiBaseUrl
    }

    fun currentBaseUrl(context: Context, settings: AppSettings): String {
        val connection = currentConnection(context)
        return baseUrl(settings, connection, if (settings.wifiSsid == null) null else currentSsid(context))
    }
}
