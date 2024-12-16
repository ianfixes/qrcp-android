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


class MainActivity : ComponentActivity() {
    private var httpServer: SimpleHttpServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QRCPTheme {
                MainScreen(
                    onPickFile = { openFilePicker() },
                    onStopServer = { stopHttpServer() }
                )
            }
        }
    }

    // File picker launcher
    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            startHttpServer(uri)
        }
    }

    private fun openFilePicker() {
        filePickerLauncher.launch(arrayOf("*/*")) // Allow all file types
    }

    private fun startHttpServer(fileUri: Uri) {
        stopHttpServer() // Stop any existing server


        httpServer = SimpleHttpServer(
            context = this,
            fileUri = fileUri,
            maxDownloads = 1, // Serve the file once for now
            onServerStopped = { httpServer = null }
        )
        httpServer?.start()
        val port = httpServer?.getServerPort() ?: -1
        Log.d("MainActivity", "Server started on port: $port")

    }

    private fun stopHttpServer() {
        httpServer?.stop()
        httpServer = null
    }
}

@Composable
fun MainScreen(
    onPickFile: () -> Unit,
    onStopServer: () -> Unit
) {
    var logMessages by remember { mutableStateOf(listOf<String>()) }
    var serverRunning by remember { mutableStateOf(false) }
    var serverPort by remember { mutableStateOf(-1) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Share/Stop Button
        Button(
            onClick = {
                if (serverRunning) {
                    logMessages = logMessages + "Stopping server..."
                    onStopServer()
                    serverRunning = false
                    serverPort = -1
                    logMessages = logMessages + "Server stopped."
                } else {
                    logMessages = logMessages + "Opening file picker..."
                    onPickFile()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Text(if (serverRunning) "Stop Sharing" else "Select File to Share")
        }

        // Show server status
        if (serverRunning) {
            Text(
                text = "Server is running on port: $serverPort",
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
