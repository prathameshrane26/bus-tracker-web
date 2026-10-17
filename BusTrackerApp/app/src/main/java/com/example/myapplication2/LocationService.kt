package com.example.myapplication2

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.myapplication2.data.ApiConfig
import com.example.myapplication2.data.StompWebSocketClient
import com.google.android.gms.location.*

class LocationService : Service() {

    companion object {
        const val ACTION_START_LOCATION_SERVICE = "com.example.myapplication2.action.START_LOCATION_SERVICE"
        const val ACTION_STOP_LOCATION_SERVICE = "com.example.myapplication2.action.STOP_LOCATION_SERVICE"
        const val ACTION_REACHED_DESTINATION = "com.example.myapplication2.action.REACHED_DESTINATION"
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val stompClient = StompWebSocketClient()

    private var busId = ""
    private var busCategory = "KTCL"
    private var startPoint = ""
    private var destination = ""
    private var routeTaken = ""

    private var lastLatitude: Double? = null
    private var lastLongitude: Double? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val action = it.action
            if (action == ACTION_STOP_LOCATION_SERVICE) {
                runCatching { stompClient.sendStopLocation(busId) }
                stopSelf()
                return START_NOT_STICKY
            } else if (action == ACTION_REACHED_DESTINATION) {
                val lastLat = lastLatitude
                val lastLng = lastLongitude
                if (lastLat != null && lastLng != null) {
                    stompClient.sendLocationUpdate(
                        busId = busId,
                        latitude = lastLat,
                        longitude = lastLng,
                        startPoint = startPoint,
                        destination = destination,
                        routeTaken = routeTaken,
                        busCategory = busCategory
                    )
                }
                runCatching { stompClient.sendStopLocation(busId) }
                stopSelf()
                return START_NOT_STICKY
            }

            val bId = it.getStringExtra("BUS_ID")
            val bCat = it.getStringExtra("BUS_CATEGORY")
            val sp = it.getStringExtra("START_POINT")
            val dest = it.getStringExtra("DESTINATION") ?: it.getStringExtra("ROUTE_ID")
            val rt = it.getStringExtra("ROUTE_TAKEN")
            if (!bId.isNullOrBlank()) busId = bId
            if (!bCat.isNullOrBlank()) busCategory = bCat
            if (!sp.isNullOrBlank()) startPoint = sp
            if (!dest.isNullOrBlank()) destination = dest
            if (!rt.isNullOrBlank()) routeTaken = rt

            // Refresh notification so it displays the updated bus number & details entered by the driver
            val manager = getSystemService(NotificationManager::class.java)
            manager?.notify(1, createNotification())
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupNotificationChannel()
        startForeground(1, createNotification())

        // Connect STOMP over WebSocket
        runCatching {
            stompClient.connect(ApiConfig.BASE_WS_URL)
        }.onFailure { e ->
            Log.e("LocationService", "Error connecting STOMP WebSocket: ${e.message}")
        }

        startLocationUpdates()
    }

    private fun startLocationUpdates() {
        // Broadcast location every 15 seconds as per API specification
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            15000L
        ).apply {
            setMinUpdateIntervalMillis(15000L)
        }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    lastLatitude = location.latitude
                    lastLongitude = location.longitude
                    stompClient.sendLocationUpdate(
                        busId = busId,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        startPoint = startPoint,
                        destination = destination,
                        routeTaken = routeTaken,
                        busCategory = busCategory
                    )
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e("LocationService", "SecurityException requesting location updates: ${e.message}")
        }
    }

    private fun setupNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "location_channel",
                "Bus Location Tracker Active",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val title = if (busId.isNotBlank()) "Bus Tracker Live ($busId)" else "Bus Tracker Live"
        val contentText = if (startPoint.isNotBlank() && destination.isNotBlank()) {
            "Broadcasting location: $startPoint ➔ $destination ($busCategory)"
        } else {
            "Broadcasting location over STOMP WebSocket..."
        }

        return NotificationCompat.Builder(this, "location_channel")
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching {
            stompClient.sendStopLocation(busId)
            stompClient.disconnect()
        }
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
