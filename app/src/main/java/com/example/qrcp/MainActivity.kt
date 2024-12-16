package com.example.qrcp

// Jetpack Compose
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

import android.net.Uri
import android.os.Bundle
// import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
// import androidx.compose.foundation.lazy.LazyColumn
// import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.qrcp.ui.theme.QRCPTheme
import java.net.Inet4Address
import java.net.NetworkInterface


class MainActivity : ComponentActivity() {
    private var httpServer: SimpleHttpServer? = null
    private var sharedUrl by mutableStateOf<String?>(null) // Track the shared URL
    private var serverRunning by mutableStateOf(false) // Track server state
    private val logMessages = mutableStateListOf<String>() // Debug log messages

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QRCPTheme {
                MainScreen(
                    onPickFile = { openFilePicker() },
                    onStopServer = { stopHttpServer() },
                    sharedUrl = sharedUrl,
                    serverRunning = serverRunning
                )
            }
        }
    }

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            startHttpServer(uri)
        }
    }

    private fun openFilePicker() {
        filePickerLauncher.launch(arrayOf("*/*"))
    }

    private fun startHttpServer(fileUri: Uri) {
        try {
            stopHttpServer() // Stop any existing server

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

            // Construct the shared URL
            val ipAddress = getLocalIpAddress() ?: "127.0.0.1"
            val port = httpServer?.getServerPort() ?: -1
            sharedUrl = "http://$ipAddress:$port"
            serverRunning = true
        } catch (e: Exception) {
            logMessages.add("Failed to start server: ${e.message}")
        }
    }

    private fun stopHttpServer() {
        httpServer?.stop()
        httpServer = null
        serverRunning = false
        sharedUrl = null
    }

    private fun getLocalIpAddress(): String? {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        for (intf in interfaces) {
            val addresses = intf.inetAddresses
            for (addr in addresses) {
                if (!addr.isLoopbackAddress && addr is Inet4Address) {
                    return addr.hostAddress
                }
            }
        }
        return null
    }
}


@Composable
fun MainScreen(
    onPickFile: () -> Unit,
    onStopServer: () -> Unit,
    sharedUrl: String?,
    serverRunning: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Share/Stop Button
        Button(
            onClick = {
                if (serverRunning) {
                    onStopServer()
                } else {
                    onPickFile()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (serverRunning) "Stop Sharing" else "Select File to Share")
        }

        // QR Code and URL Display
        if (serverRunning && sharedUrl != null) {
            QRCodeDisplay(url = sharedUrl)
        }
    }
}

@Composable
fun QRCodeDisplay(url: String) {
    val qrCodeBitmap = remember(url) {
        generateQRCode(url)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        qrCodeBitmap?.let {
            Image(
                bitmap = it,
                contentDescription = "QR Code",
                modifier = Modifier.size(200.dp)
            )
        }
        Text(
            text = url,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

fun generateQRCode(url: String): ImageBitmap? {
    return try {
        val size = 256 // Pixels
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
