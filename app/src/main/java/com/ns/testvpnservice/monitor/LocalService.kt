package com.ns.testvpnservice.monitor

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket

class LocalService : Service() {
    companion object {
        private const val TAG = "LocalService"
    }
    private val binder: IBinder = LocalBinder()
    private lateinit var serverSocket: ServerSocket

    inner class LocalBinder : Binder() {
        fun getService(): LocalService = this@LocalService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "LocalService starting...")
        try {
            // Start listening on a TCP port (e.g., 12345)
            serverSocket = ServerSocket(3939)
            Log.i(TAG, "LocalService started...")

            // Start a background thread to handle incoming connections
            Thread {
                while (true) {
                    try {
                        // Accept incoming connections
                        val clientSocket: Socket = serverSocket.accept()

                        // Handle the connection (you can do something with the socket)
                        handleClient(clientSocket)
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }.start()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun handleClient(clientSocket: Socket) {
        // Implement your logic for handling the incoming connection
        // This could involve reading/writing data to/from the socket
        Log.d(TAG, "Handling client connection from ${clientSocket.inetAddress}")
    }
}