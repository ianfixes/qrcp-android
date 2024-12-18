package com.ianfixes.qrcp

import android.content.Context
import android.net.Uri
import fi.iki.elonen.NanoHTTPD
import java.io.InputStream

class SimpleHttpServer(
    private val context: Context,
    private val fileUri: Uri,
    private var maxDownloads: Int = 1, // Default to 1 download
    private val onServerStopped: () -> Unit // Callback for when the server stops
) : NanoHTTPD(0) { // Use 0 to let the system assign a random port

    private var downloadCount = 0

    override fun serve(session: IHTTPSession): Response {
        synchronized(this) {
            if (downloadCount >= maxDownloads) {
                stopServer()
                return newFixedLengthResponse(Response.Status.GONE, "text/plain", "File no longer available.")
            }

            val fileStream: InputStream? = context.contentResolver.openInputStream(fileUri)
            val fileSize = fileStream?.available()?.toLong() ?: 0

            downloadCount++
            return newFixedLengthResponse(Response.Status.OK, getMimeType(fileUri), fileStream, fileSize)
        }
    }

    fun getServerPort(): Int = listeningPort

    private fun stopServer() {
        stop()
        onServerStopped()
    }

    private fun getMimeType(uri: Uri): String {
        return context.contentResolver.getType(uri) ?: "application/octet-stream"
    }
}
