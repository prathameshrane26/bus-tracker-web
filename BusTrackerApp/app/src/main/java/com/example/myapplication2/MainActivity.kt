package com.example.myapplication2

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.myapplication2.data.ApiConfig
import com.example.myapplication2.ui.auth.AuthScreen
import com.example.myapplication2.ui.theme.AppTheme
import okhttp3.*
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONObject

class MainActivity : ComponentActivity() {

    private val sseHttpClient = ApiConfig.sseHttpClient
    private var eventSource: EventSource? = null
    private var isSseTrackingActive = false
    private val reconnectHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var liveBusDataState by mutableStateOf<String?>(null)
    private var activeDriverBusId: String = ""
    private var activeDriverBusCategory: String = "KTCL"
    private var activeDriverStartPoint: String = ""
    private var activeDriverDestination: String = ""
    private var activeDriverRouteTaken: String = ""

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineGranted || coarseGranted) {
            startLocationService()
        } else {
            Toast.makeText(this, "Location permission is required for live tracking", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AuthScreen(
                        onStartDriverBroadcasting = { busId, startPoint, destination, routeTaken, busCategory ->
                            if (busId.isNotBlank()) activeDriverBusId = busId
                            if (startPoint.isNotBlank()) activeDriverStartPoint = startPoint
                            if (destination.isNotBlank()) activeDriverDestination = destination
                            if (routeTaken.isNotBlank()) activeDriverRouteTaken = routeTaken
                            if (busCategory.isNotBlank()) activeDriverBusCategory = busCategory
                            checkAndRequestPermissions()
                        },
                        onStopDriverBroadcasting = { reachedDestination ->
                            stopLocationService(reachedDestination)
                        },
                        onStartPassengerTracking = { _ ->
                            startSseStream()
                        },
                        onStopPassengerTracking = {
                            stopSseStream()
                        },
                        liveBusData = liveBusDataState
                    )
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            startLocationService()
        }
    }

    private fun startLocationService() {
        val intent = Intent(this, LocationService::class.java).apply {
            action = LocationService.ACTION_START_LOCATION_SERVICE
            putExtra("BUS_ID", activeDriverBusId)
            putExtra("BUS_CATEGORY", activeDriverBusCategory)
            putExtra("START_POINT", activeDriverStartPoint)
            putExtra("DESTINATION", activeDriverDestination)
            putExtra("ROUTE_TAKEN", activeDriverRouteTaken)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopLocationService(reachedDestination: Boolean = false) {
        val action = if (reachedDestination) {
            LocationService.ACTION_REACHED_DESTINATION
        } else {
            LocationService.ACTION_STOP_LOCATION_SERVICE
        }
        val intent = Intent(this, LocationService::class.java).apply {
            this.action = action
        }
        startService(intent)
    }

    private fun startSseStream() {
        if (isSseTrackingActive && eventSource != null) {
            android.util.Log.d("MainActivity_SSE", "SSE Stream connection is already active.")
            return
        }

        isSseTrackingActive = true
        reconnectHandler.removeCallbacksAndMessages(null)
        eventSource?.cancel()

        val request = Request.Builder()
            .url(ApiConfig.SSE_LOCATIONS_STREAM_URL)
            .header("Accept", "text/event-stream")
            .build()

        val listener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                android.util.Log.d("MainActivity_SSE", "SSE Stream connected successfully to ${ApiConfig.SSE_LOCATIONS_STREAM_URL}")
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                // Ignore keep-alive heartbeats
                if (type == "heartbeat" || data.contains("keep-alive")) {
                    return
                }

                android.util.Log.d("MainActivity_SSE", "Received SSE Event ($type): $data")

                val eventType = type ?: "location-update"
                val wrapped = JSONObject().apply {
                    put("eventType", eventType)
                    put("payload", data)
                    put("timestamp", System.currentTimeMillis())
                }.toString()

                runOnUiThread {
                    liveBusDataState = wrapped
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                android.util.Log.e("MainActivity_SSE", "SSE Stream Failure: ${t?.message}. Reconnecting in 3 seconds...", t)
                eventSource.cancel()
                this@MainActivity.eventSource = null
                if (isSseTrackingActive) {
                    reconnectHandler.postDelayed({
                        if (isSseTrackingActive) {
                            startSseStream()
                        }
                    }, 3000L)
                }
            }

            override fun onClosed(eventSource: EventSource) {
                android.util.Log.d("MainActivity_SSE", "SSE Stream closed.")
                this@MainActivity.eventSource = null
                if (isSseTrackingActive) {
                    reconnectHandler.postDelayed({
                        if (isSseTrackingActive) {
                            startSseStream()
                        }
                    }, 3000L)
                }
            }
        }

        val factory = EventSources.createFactory(sseHttpClient)
        eventSource = factory.newEventSource(request, listener)
    }

    private fun stopSseStream() {
        isSseTrackingActive = false
        reconnectHandler.removeCallbacksAndMessages(null)
        eventSource?.cancel()
        eventSource = null
    }

    override fun onDestroy() {
        super.onDestroy()
        eventSource?.cancel()
    }
}
