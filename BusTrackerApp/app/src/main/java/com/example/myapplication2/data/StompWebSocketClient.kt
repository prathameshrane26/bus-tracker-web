package com.example.myapplication2.data

import android.util.Log
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class StompWebSocketClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(10, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()
) : WebSocketListener() {

    private var webSocket: WebSocket? = null

    @Volatile
    var isConnected: Boolean = false
        private set

    @Volatile
    private var isConnecting: Boolean = false

    private var pendingLocationPayload: String? = null
    private var currentWsUrl: String = ApiConfig.BASE_WS_URL

    @Synchronized
    fun connect(wsUrl: String = ApiConfig.BASE_WS_URL) {
        currentWsUrl = wsUrl
        if (webSocket != null && (isConnected || isConnecting)) {
            Log.d("StompClient", "WebSocket already connected or connecting.")
            return
        }

        isConnecting = true
        isConnected = false
        Log.d("StompClient", "Initiating WebSocket connection to $wsUrl...")
        runCatching {
            val request = Request.Builder()
                .url(wsUrl)
                .build()
            webSocket = httpClient.newWebSocket(request, this)
        }.onFailure { e ->
            Log.e("StompClient", "Failed to initiate WebSocket connection: ${e.message}")
            isConnecting = false
            webSocket = null
        }
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        Log.d("StompClient", "WebSocket transport opened. Sending STOMP CONNECT frame...")
        val connectFrame = "CONNECT\naccept-version:1.1,1.0\nheart-beat:10000,10000\n\n\u0000"
        val sent = webSocket.send(connectFrame)
        if (!sent) {
            Log.e("StompClient", "Failed to send STOMP CONNECT frame on open.")
            isConnecting = false
            this.webSocket = null
        }
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        Log.d("StompClient", "Received STOMP frame: $text")
        if (text.startsWith("CONNECTED")) {
            isConnected = true
            isConnecting = false
            Log.d("StompClient", "STOMP Connection established successfully!")

            // Flush any pending location payload stored while reconnecting
            pendingLocationPayload?.let { payload ->
                pendingLocationPayload = null
                sendFrameInternal(ApiConfig.STOMP_LOCATION_DESTINATION, payload)
            }
        }
    }

    fun sendLocationUpdate(
        busId: String,
        latitude: Double,
        longitude: Double,
        startPoint: String = "",
        destination: String = "",
        routeTaken: String = "",
        busCategory: String = "KTCL",
        busNumber: String? = null
    ) {
        val derivedBusNumber = busNumber ?: busId.filter { it.isDigit() }.ifBlank { busId }

        val jsonPayload = JSONObject().apply {
            put("busId", busId)
            put("busNumber", derivedBusNumber)
            put("latitude", latitude)
            put("longitude", longitude)
            if (startPoint.isNotBlank()) {
                put("startPoint", startPoint)
            }
            if (destination.isNotBlank()) {
                put("destination", destination)
            }
            if (routeTaken.isNotBlank()) {
                put("routeTaken", routeTaken)
            }
            if (busCategory.isNotBlank()) {
                put("busCategory", busCategory)
            }
        }.toString()

        if (!isConnected || webSocket == null) {
            Log.w("StompClient", "STOMP not connected (isConnected=$isConnected, ws=$webSocket). Storing pending location and reconnecting...")
            pendingLocationPayload = jsonPayload
            connect(currentWsUrl)
            return
        }

        val success = sendFrameInternal(ApiConfig.STOMP_LOCATION_DESTINATION, jsonPayload)
        if (!success) {
            Log.w("StompClient", "Failed to send location update frame. Queueing for reconnect...")
            pendingLocationPayload = jsonPayload
            reconnect()
        }
    }

    fun sendStopLocation(
        busId: String,
        busNumber: String? = null
    ) {
        val derivedBusNumber = busNumber ?: busId.filter { it.isDigit() }.ifBlank { busId }

        val jsonPayload = JSONObject().apply {
            put("busId", busId)
            put("busNumber", derivedBusNumber)
            put("status", "STOPPED")
            put("stopped", true)
            put("isActivelySharing", false)
        }.toString()

        if (isConnected && webSocket != null) {
            sendFrameInternal(ApiConfig.STOMP_STOP_LOCATION_DESTINATION, jsonPayload)
        } else {
            Log.w("StompClient", "Cannot send stop location, STOMP client is not connected.")
        }
    }

    private fun sendFrameInternal(destination: String, jsonPayload: String): Boolean {
        val ws = webSocket ?: return false
        val sendFrame = "SEND\ndestination:$destination\ncontent-type:application/json\n\n$jsonPayload\u0000"
        val sent = ws.send(sendFrame)
        if (sent) {
            Log.d("StompClient", "Sent STOMP frame to $destination: $jsonPayload")
        } else {
            Log.e("StompClient", "ws.send returned false for destination $destination")
        }
        return sent
    }

    private fun reconnect() {
        isConnected = false
        isConnecting = false
        webSocket?.cancel()
        webSocket = null
        connect(currentWsUrl)
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Log.e("StompClient", "WebSocket failure: ${t.message}", t)
        isConnected = false
        isConnecting = false
        this.webSocket = null
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        Log.d("StompClient", "WebSocket closed ($code): $reason")
        isConnected = false
        isConnecting = false
        this.webSocket = null
    }

    fun disconnect() {
        pendingLocationPayload = null
        if (webSocket != null && isConnected) {
            val disconnectFrame = "DISCONNECT\n\n\u0000"
            webSocket?.send(disconnectFrame)
            webSocket?.close(1000, "Service stopped")
        } else {
            webSocket?.cancel()
        }
        webSocket = null
        isConnected = false
        isConnecting = false
    }
}
