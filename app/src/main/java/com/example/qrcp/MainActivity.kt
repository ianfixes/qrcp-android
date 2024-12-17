package com.example.qrcp

// Jetpack Compose
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.*

// ZXing QR Code
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

// Android Graphics
import android.graphics.Bitmap
import android.graphics.Color

// Android Network
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
// import androidx.compose.foundation.lazy.LazyColumn
// import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*

import android.provider.Settings


import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.qrcp.ui.theme.QRCPTheme
import java.net.Inet4Address
import java.net.NetworkInterface


class MainActivity : ComponentActivity() {
    private var httpServer: SimpleHttpServer? = null
    private var sharedUrl by mutableStateOf<String?>(null) // Track the shared URL
    private var serverRunning by mutableStateOf(false) // Track server state
    private var isWifiConnected by mutableStateOf(false) // Track Wi-Fi connection state

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            startHttpServer(uri)
        }
    }

    private fun openFilePicker() {
        filePickerLauncher.launch(arrayOf("*/*"))
    }

    private fun stopHttpServer() {
        httpServer?.stop()
        httpServer = null
        serverRunning = false
        sharedUrl = null
    }

    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize ConnectivityManager
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Initial check for Wi-Fi and hotspot
        isWifiConnected = checkWifiConnection()

        // Register NetworkCallback to monitor network changes
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: android.net.Network, capabilities: NetworkCapabilities) {
                val wifiConnected = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                if (wifiConnected != isWifiConnected) {
                    isWifiConnected = wifiConnected || isWifiApEnabled()
                    if (!isWifiConnected && serverRunning) {
                        stopHttpServer()
                        Log.d("MainActivity", "Wi-Fi disabled and no hotspot. Server stopped.")
                    }
                }
            }

            override fun onLost(network: android.net.Network) {
                // Recheck connection status when network is lost
                isWifiConnected = checkWifiConnection()
                if (!isWifiConnected && serverRunning) {
                    stopHttpServer()
                    Log.d("MainActivity", "Network lost. Server stopped.")
                }
            }
        }

        connectivityManager.registerDefaultNetworkCallback(networkCallback)

        val intent = intent
        if (intent?.action == Intent.ACTION_SEND) {
            handleSharedContent(intent)
        }

        setContent {
            QRCPTheme {
                MainScreen(
                    onPickFile = { openFilePicker() },
                    onStopServer = { stopHttpServer() },
                    openWifiSettings = { openWifiSettings() },
                    openHotspotSettings = { openHotspotSettings() },
                    isWifiConnected = isWifiConnected,
                    sharedUrl = sharedUrl,
                    serverRunning = serverRunning
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Check Wi-Fi and hotspot state when returning to the app
        isWifiConnected = checkWifiConnection()
        Log.d("MainActivity", "onResume: isWifiConnected = $isWifiConnected")
    }

    override fun onDestroy() {
        super.onDestroy()
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

    private fun handleSharedContent(intent: Intent) {
        val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        if (uri != null) {
            Log.d("MainActivity", "Received shared file: $uri")
            startHttpServer(uri)
        } else {
            Log.e("MainActivity", "No file found in shared intent.")
        }
    }

    private fun isWifiApEnabled(): Boolean {
        return try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val method = wifiManager.javaClass.getDeclaredMethod("isWifiApEnabled")
            method.isAccessible = true
            val hotspotEnabled = method.invoke(wifiManager) as Boolean
            Log.d("MainActivity", "Hotspot enabled: $hotspotEnabled")
            hotspotEnabled
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to check hotspot state", e)
            false
        }
    }

    private fun checkWifiConnection(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)

        // Return true if connected to Wi-Fi or acting as a hotspot
        return capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true || isWifiApEnabled()
    }

    private fun openWifiSettings() {
        val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
        startActivity(intent)
    }

    private fun getWifiIpAddress(): String? {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)

        // Standard Wi-Fi Client Mode
        if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            val linkProperties = connectivityManager.getLinkProperties(network) ?: return null
            for (linkAddress in linkProperties.linkAddresses) {
                val address = linkAddress.address
                if (address is Inet4Address && !address.isLoopbackAddress) {
                    return address.hostAddress
                }
            }
        }

        if (isWifiApEnabled()) {
            try {
                // Wi-Fi Hotspot Mode: Look for interfaces matching 'ap<number>'
                val interfaces = NetworkInterface.getNetworkInterfaces()
                val apInterfacePattern = Regex("^ap\\d+$") // Matches 'ap' followed by digits

                for (networkInterface in interfaces) {
                    if (networkInterface.name.matches(apInterfacePattern)) {
                        val addresses = networkInterface.inetAddresses
                        for (address in addresses) {
                            if (address is Inet4Address && !address.isLoopbackAddress) {
                                Log.d("MainActivity", "Hotspot Interface: ${networkInterface.name}, IP: ${address.hostAddress}")
                                return address.hostAddress
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error retrieving IP address", e)
            }
        }

        return null
    }

    private fun openHotspotSettings() {
        val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS)
        startActivity(intent)
    }



    private fun startHttpServer(fileUri: Uri) {
        try {
            stopHttpServer() // Stop any existing server

            val ipAddress = getWifiIpAddress() ?: throw Exception("Wi-Fi IP address not found")
            httpServer = SimpleHttpServer(
                context = this,
                fileUri = fileUri,
                maxDownloads = 1,
                onServerStopped = {
                    serverRunning = false
                    sharedUrl = null
                }
            )
            httpServer?.start()

            val port = httpServer?.getServerPort() ?: -1
            sharedUrl = "http://$ipAddress:$port"
            serverRunning = true
        } catch (e: Exception) {
            Log.e("MainActivity", "Error starting HTTP server", e)
            sharedUrl = null
            serverRunning = false
        }
    }
}



@Composable
fun MainScreen(
    onPickFile: () -> Unit,
    onStopServer: () -> Unit,
    openWifiSettings: () -> Unit,
    openHotspotSettings: () -> Unit,
    isWifiConnected: Boolean,
    sharedUrl: String?,
    serverRunning: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // Placeholder for instructional text
        if (isWifiConnected) {
            val intro = "QRCP shares files locally (via WiFi) to phones that can scan a QR code. Tap 'Select File' to begin."
            Text(
                text = if (serverRunning) "Sharing will stop automatically after one download of this URL" else intro,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        } else {
            Text(
                text = "QRCP can only share over Wi-Fi or Hotspot",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        if (isWifiConnected) {
            // Share/Stop Button
            Button(
                onClick = {
                    if (serverRunning) onStopServer() else onPickFile()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (serverRunning) "Stop Sharing" else "Select File to Share")
            }
        } else {
            // Wi-Fi Settings Button
            Button(
                onClick = { openWifiSettings() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open Wi-Fi Settings")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Hotspot Settings Button
            Button(
                onClick = { openHotspotSettings() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open Hotspot Settings")
            }
        }

        // QR Code and URL Display
        if (serverRunning && sharedUrl != null) {
            Spacer(modifier = Modifier.height(16.dp))
            QRCodeDisplay(url = sharedUrl)
        }
    }
}

@Composable
fun QRCodeDisplay(url: String) {
    // Get screen dimensions
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp

    // Determine the smaller dimension (width or height)
    val qrCodeSize = if (screenWidth < screenHeight) screenWidth else screenHeight

    // Generate the QR code
    val qrCodeBitmap: ImageBitmap? = remember(url) {
        generateQRCode(url, qrCodeSize.value.toInt())
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        qrCodeBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = "QR Code",
                modifier = Modifier.size(qrCodeSize)
            )
        }
        Text(
            text = url,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

fun generateQRCode(url: String, size: Int): ImageBitmap? {
    return try {
        val bits = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bits[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        bitmap.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}
