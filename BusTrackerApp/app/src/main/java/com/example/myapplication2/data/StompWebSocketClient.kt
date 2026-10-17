package com.example.myapplication2.data

import android.util.Log
import okhttp3.*
import org.json.JSONObject

class StompWebSocketClient(
    private val httpClient: OkHttpClient = OkHttpClient()
) : WebSocketListener() {

    private var webSocket: WebSocket? = null
    var isConnected: Boolean = false
        private set

    fun connect(wsUrl: String = ApiConfig.BASE_WS_URL) {
        val request = Request.Builder()
            .url(wsUrl)
            .build()
        webSocket = httpClient.newWebSocket(request, this)
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        Log.d("StompClient", "WebSocket connection opened. Sending STOMP CONNECT...")
        val connectFrame = "CONNECT\naccept-version:1.1,1.0\nheart-beat:10000,10000\n\n\u0000"
        webSocket.send(connectFrame)
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        Log.d("StompClient", "Received STOMP frame: $text")
        if (text.startsWith("CONNECTED")) {
            isConnected = true
            Log.d("StompClient", "STOMP Connection established successfully!")
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
        val ws = webSocket
        if (ws == null) {
            Log.w("StompClient", "Cannot send location, WebSocket is null. Reconnecting...")
            connect()
            return
        }

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

        val sendFrame = "SEND\ndestination:${ApiConfig.STOMP_LOCATION_DESTINATION}\ncontent-type:application/json\n\n$jsonPayload\u0000"
        ws.send(sendFrame)
        Log.d("StompClient", "Sent STOMP location frame: $jsonPayload")
    }

    fun sendStopLocation(
        busId: String,
        busNumber: String? = null
    ) {
        val ws = webSocket
        if (ws == null) {
            Log.w("StompClient", "Cannot send stop location, WebSocket is null.")
            return
        }

        val derivedBusNumber = busNumber ?: busId.filter { it.isDigit() }.ifBlank { busId }

        val jsonPayload = JSONObject().apply {
            put("busId", busId)
            put("busNumber", derivedBusNumber)
            put("status", "STOPPED")
            put("stopped", true)
            put("isActivelySharing", false)
        }.toString()

        val sendFrame = "SEND\ndestination:${ApiConfig.STOMP_STOP_LOCATION_DESTINATION}\ncontent-type:application/json\n\n$jsonPayload\u0000"
        ws.send(sendFrame)
        Log.d("StompClient", "Sent STOMP stop location frame: $jsonPayload")
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Log.e("StompClient", "WebSocket failure: ${t.message}", t)
        isConnected = false
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        Log.d("StompClient", "WebSocket closed: $reason")
        isConnected = false
    }

    fun disconnect() {
        val disconnectFrame = "DISCONNECT\n\n\u0000"
        webSocket?.send(disconnectFrame)
        webSocket?.close(1000, "Service stopped")
        webSocket = null
        isConnected = false
    }
}
