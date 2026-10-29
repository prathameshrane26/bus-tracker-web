package com.example.myapplication2.data

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

class PersistentCookieJar(context: Context) : CookieJar {
    private val appContext = context.applicationContext
    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

    companion object {
        private const val PREFS_NAME = "cookie_prefs"
    }

    init {
        loadCookies()
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val existing = cookieStore[url.host] ?: mutableListOf()
        cookies.forEach { newCookie ->
            existing.removeAll { it.name == newCookie.name }
            existing.add(newCookie)
        }
        cookieStore[url.host] = existing
        saveCookies()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val cookies = cookieStore[url.host] ?: return emptyList()
        val now = System.currentTimeMillis()
        val valid = cookies.filter { it.expiresAt > now }
        if (valid.size != cookies.size) {
            cookieStore[url.host] = valid.toMutableList()
            saveCookies()
        }
        return valid
    }

    fun clear() {
        cookieStore.clear()
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun saveCookies() {
        runCatching {
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = JSONObject()
            cookieStore.forEach { (host, cookies) ->
                val arr = JSONArray()
                cookies.forEach { cookie ->
                    val obj = JSONObject().apply {
                        put("name", cookie.name)
                        put("value", cookie.value)
                        put("expiresAt", cookie.expiresAt)
                        put("domain", cookie.domain)
                        put("path", cookie.path)
                        put("secure", cookie.secure)
                        put("httpOnly", cookie.httpOnly)
                        put("hostOnly", cookie.hostOnly)
                    }
                    arr.put(obj)
                }
                json.put(host, arr)
            }
            prefs.edit().putString("cookies_data", json.toString()).apply()
        }
    }

    private fun loadCookies() {
        runCatching {
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString("cookies_data", null) ?: return
            val json = JSONObject(jsonStr)
            val keys = json.keys()
            while (keys.hasNext()) {
                val host = keys.next()
                val arr = json.getJSONArray(host)
                val list = mutableListOf<Cookie>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val builder = Cookie.Builder()
                        .name(obj.getString("name"))
                        .value(obj.getString("value"))
                        .expiresAt(obj.getLong("expiresAt"))
                        .path(obj.getString("path"))
                    val domain = obj.getString("domain")
                    if (obj.getBoolean("hostOnly")) {
                        builder.hostOnlyDomain(domain)
                    } else {
                        builder.domain(domain)
                    }
                    if (obj.getBoolean("secure")) builder.secure()
                    if (obj.getBoolean("httpOnly")) builder.httpOnly()
                    list.add(builder.build())
                }
                cookieStore[host] = list
            }
        }
    }
}

object ApiConfig {
    private var persistentCookieJar: PersistentCookieJar? = null

    fun init(context: Context) {
        if (persistentCookieJar == null) {
            persistentCookieJar = PersistentCookieJar(context.applicationContext)
        }
    }

    fun clearCookies() {
        persistentCookieJar?.clear()
    }

    private val dynamicCookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            persistentCookieJar?.saveFromResponse(url, cookies)
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return persistentCookieJar?.loadForRequest(url) ?: emptyList()
        }
    }

//        var SERVER_HOST: String = "10.20.64.15:8080" // Use this for emulator
    var SERVER_HOST: String = "10.0.2.2:8080"

    // Unique client identifier required by backend BusController endpoints
    val CLIENT_ID: String by lazy {
        UUID.randomUUID().toString()
    }

    // Re-usable shared OkHttpClient with cookie jar and optimized timeouts
    val sharedHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(dynamicCookieJar)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    // Re-usable OkHttpClient for long-lived SSE streaming (readTimeout set to 0/unlimited)
    val sseHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
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

    val REQUEST_OTP_URL: String
        get() = "$BASE_HTTP_URL/api/auth/request-otp"

    val SEND_OTP_URL: String
        get() = REQUEST_OTP_URL

    val VERIFY_OTP_URL: String
        get() = LOGIN_URL

    val SIGNUP_URL: String
        get() = "$BASE_HTTP_URL/api/auth/signup"

    val LOGOUT_URL: String
        get() = "$BASE_HTTP_URL/api/auth/logout"

    val ACTIVE_BUSES_URL: String
        get() = "$BASE_HTTP_URL/api/buses/active"

    val BUS_LOCATIONS_URL: String
        get() = "$BASE_HTTP_URL/api/buses/locations"

    val SSE_LOCATIONS_STREAM_URL: String
        get() = "$BASE_HTTP_URL/api/buses/locations/stream?clientId=$CLIENT_ID"

    val ALL_SCHEDULED_LOCATIONS_URL: String
        get() = "$BASE_HTTP_URL/api/buses/all-scheduled-locations"

    val STOP_SHARING_URL: String
        get() = "$BASE_HTTP_URL/api/buses/stop-sharing"

    val TRACK_BUS_URL: String
        get() = "$BASE_HTTP_URL/api/buses/track"

    val UNTRACK_BUS_URL: String
        get() = "$BASE_HTTP_URL/api/buses/untrack"

    fun getTrackBusUrl(busId: String, clientId: String = CLIENT_ID): String {
        return "$TRACK_BUS_URL?clientId=$clientId&busId=$busId"
    }

    fun getUntrackBusUrl(busId: String, clientId: String = CLIENT_ID): String {
        return "$UNTRACK_BUS_URL?clientId=$clientId&busId=$busId"
    }

    // Centralized location update interval (5 seconds) for sending and receiving location updates
    const val LOCATION_UPDATE_INTERVAL_MS = 5000L
    const val LOCATION_FASTEST_UPDATE_INTERVAL_MS = 5000L

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
