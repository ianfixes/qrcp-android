package com.ianfixes.qrcp

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class SimpleHttpServer(
    private val context: Context,
    private val fileUri: Uri,
    private var maxDownloads: Int = 1, // Default to 1 download
    private val onServerStopped: () -> Unit // Callback for when the server stops
) : NanoHTTPD(0) { // Use 0 to let the system assign a random port


    private class CustomResponse(
        status: Response.Status,
        mimeType: String,
        data: InputStream?,
        contentLength: Long
    ) : Response(status, mimeType, data, contentLength) {
        private var onCompleteCallback: (() -> Unit)? = null

        fun setOnCompleteCallback(callback: () -> Unit) {
            this.onCompleteCallback = callback
        }

        override fun send(outputStream: OutputStream) {
            super.send(outputStream)
            onCompleteCallback?.invoke()
        }
    }

    private var downloadCount = 0

    override fun serve(session: IHTTPSession): Response {
        synchronized(this) {
            if (downloadCount >= maxDownloads) {
                stopServer()
                return newFixedLengthResponse(Response.Status.GONE, "text/plain", "File no longer available.")
            }

            val fileStream: InputStream? = context.contentResolver.openInputStream(fileUri)
            val fileSize = fileStream?.available()?.toLong() ?: 0
            val filename = getFileNameFromUri(context, fileUri)

            downloadCount++
            // we have already checked that the URI can be opened
            val response = CustomResponse(Response.Status.OK, getMimeType(fileUri), fileStream!!, fileSize)

            // Add headers to set the filename
            response.addHeader("Content-Disposition", "filename=\"${filename}\"")

            // Schedule server to stop after sending the response
            response.setOnCompleteCallback {
                stopServer()
            }
            return response
        }
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        return try {
            if (uri.scheme == "content") {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        return it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                    }
                }
            } else if (uri.scheme == "file") {
                return File(uri.path!!).name
            }
            null
        } catch (e: Exception) {
            Log.e("MainActivity", "Error retrieving file name for URI: $uri", e)
            null
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
