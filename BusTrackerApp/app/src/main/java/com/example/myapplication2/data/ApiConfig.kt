package com.example.myapplication2.data

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object ApiConfig {
    // Configurable server base host.
    // Default host IP or 10.0.2.2 for Android Emulator.
    var SERVER_HOST: String = "10.97.44.61:8080"

    // Re-usable shared OkHttpClient with optimized timeouts
    val sharedHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    val BASE_HTTP_URL: String
        get() = "http://$SERVER_HOST"

    val BASE_WS_URL: String
        get() = "ws://$SERVER_HOST/ws"

    // REST Endpoints
    val LOGIN_URL: String
        get() = "$BASE_HTTP_URL/api/auth/login"

    val ACTIVE_BUSES_URL: String
        get() = "$BASE_HTTP_URL/api/buses/active"

    val BUS_LOCATIONS_URL: String
        get() = "$BASE_HTTP_URL/api/buses/locations"

    val SSE_LOCATIONS_STREAM_URL: String
        get() = "$BASE_HTTP_URL/api/buses/locations/stream"

    val ALL_SCHEDULED_LOCATIONS_URL: String
        get() = "$BASE_HTTP_URL/api/buses/all-scheduled-locations"

    val STOP_SHARING_URL: String
        get() = "$BASE_HTTP_URL/api/buses/stop-sharing"

    // STOMP Destination for location updates & stop location
    const val STOMP_LOCATION_DESTINATION = "/app/update-location"
    const val STOMP_STOP_LOCATION_DESTINATION = "/app/stop-location"
}

data class DriverAuthRequest(
    val busId: String,
    val phoneNumber: String,
    val role: String = "DRIVER"
)

data class AuthResponse(
    val status: String,
    val busId: String? = null,
    val driverName: String? = null,
    val message: String? = null
)

data class BusLocationDto(
    val busId: String,
    val latitude: Double,
    val longitude: Double,
    val destination: String? = null
)
