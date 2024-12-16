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

        // Check the initial Wi-Fi state
        isWifiConnected = checkWifiConnection()

        // Register a NetworkCallback to monitor network changes
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: android.net.Network, capabilities: NetworkCapabilities) {
                val wifiConnected = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                if (wifiConnected != isWifiConnected) {
                    isWifiConnected = wifiConnected
                    if (!wifiConnected && serverRunning) {
                        stopHttpServer()
                    }
                }
            }

            override fun onLost(network: android.net.Network) {
                if (isWifiConnected && serverRunning) {
                    isWifiConnected = false
                    stopHttpServer()
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
                    isWifiConnected = isWifiConnected,
                    sharedUrl = sharedUrl,
                    serverRunning = serverRunning
                )
            }
        }
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

    private fun checkWifiConnection(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }



    private fun openWifiSettings() {
        val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
        startActivity(intent)
    }

    private fun getWifiIpAddress(): String? {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return null
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return null

        // Check if the active network is Wi-Fi
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            // Get the Wi-Fi network's link properties
            val linkProperties = connectivityManager.getLinkProperties(network) ?: return null
            for (linkAddress in linkProperties.linkAddresses) {
                val address = linkAddress.address
                if (address is Inet4Address && !address.isLoopbackAddress) {
                    return address.hostAddress // Return IPv4 address
                }
            }
        }
        return null
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
            Text(
                text = "QRCP shares files locally (via WiFi) to phones that can scan a QR code. Tap 'Select File' to begin.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        } else {
            Text(
                text = "QRCP can only share over Wi-Fi",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // Share/Stop Button or Open Wi-Fi Settings
        Button(
            onClick = {
                if (isWifiConnected) {
                    if (serverRunning) onStopServer() else onPickFile()
                } else {
                    openWifiSettings()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (isWifiConnected) {
                    if (serverRunning) "Stop Sharing" else "Select File to Share"
                } else {
                    "Open Wi-Fi Settings"
                }
            )
        }

        // QR Code and URL Display
        if (isWifiConnected && serverRunning && sharedUrl != null) {
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
