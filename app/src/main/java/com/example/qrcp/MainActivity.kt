package com.example.qrcp

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
                    serverRunning = serverRunning,
                    logMessages = logMessages
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
                    logMessages.add("Server stopped.")
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

            logMessages.add("Server started: $sharedUrl")
        } catch (e: Exception) {
            logMessages.add("Failed to start server: ${e.message}")
        }
    }

    private fun stopHttpServer() {
        httpServer?.stop()
        httpServer = null
        serverRunning = false
        sharedUrl = null
        logMessages.add("Server stopped.")
    }

    private fun getLocalIpAddress(): String? {
        // Example implementation to get local IP address
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
    serverRunning: Boolean,
    logMessages: List<String>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Text(if (serverRunning) "Stop Sharing" else "Select File to Share")
        }

        // Show the shared URL if the server is running
        if (serverRunning && sharedUrl != null) {
            Text(
                text = "Shared URL: $sharedUrl",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // Debug Log List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(logMessages) { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
