package com.example.myapplication2.ui.auth

import android.util.Log
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.myapplication2.data.ApiConfig
import com.example.myapplication2.data.UserSession
import com.example.myapplication2.data.UserSessionRepository
import com.example.myapplication2.ui.home.HomeScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

enum class AuthViewState {
    LOGIN,
    SIGNUP,
    LOGGED_IN
}

@Composable
fun AuthScreen(
    onStartDriverBroadcasting: (busId: String, startPoint: String, destination: String, routeTaken: String, busCategory: String) -> Unit,
    onStopDriverBroadcasting: (reachedDestination: Boolean) -> Unit = {},
    onStartPassengerTracking: (routeId: String) -> Unit,
    onStopPassengerTracking: () -> Unit = {},
    liveBusData: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val initialSession = remember {
        ApiConfig.init(context)
        UserSessionRepository.getUserSession(context)
    }

    var viewState by remember {
        mutableStateOf(if (initialSession.isLoggedIn) AuthViewState.LOGGED_IN else AuthViewState.LOGIN)
    }
    var selectedRole by remember { mutableStateOf(initialSession.role) }
    var loggedInUserEmail by remember { mutableStateOf(initialSession.userEmail) }
    var busId by remember { mutableStateOf(initialSession.busId) }
    var driverBusCategory by remember { mutableStateOf(initialSession.driverBusCategory) }
    var routeId by remember { mutableStateOf(initialSession.routeId) }

    var isAuthLoading by remember { mutableStateOf(false) }
    var authErrorMessage by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val httpClient = ApiConfig.sharedHttpClient

    Crossfade(
        targetState = viewState,
        modifier = modifier.fillMaxSize(),
        label = "AuthNavigation"
    ) { state ->
        when (state) {
            AuthViewState.LOGIN -> {
                LoginScreen(
                    selectedRole = selectedRole,
                    onRoleSelected = {
                        selectedRole = it
                        authErrorMessage = null
                    },
                    isLoading = isAuthLoading,
                    errorMessage = authErrorMessage,
                    onRequestOtp = { phoneNumber, callback ->
                        coroutineScope.launch(Dispatchers.IO) {
                            runCatching {
                                val jsonPayload = JSONObject().apply {
                                    put("phoneNumber", phoneNumber.trim())
                                }.toString()

                                val requestBody = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())
                                val request = Request.Builder()
                                    .url(ApiConfig.REQUEST_OTP_URL)
                                    .post(requestBody)
                                    .build()

                                val response = httpClient.newCall(request).execute()
                                val responseStr = response.body?.string() ?: ""
                                val isSuccess = response.isSuccessful
                                response.close()

                                val jsonRes = runCatching { JSONObject(responseStr) }.getOrNull()
                                val msg = jsonRes?.optString("message")

                                withContext(Dispatchers.Main) {
                                    if (isSuccess) {
                                        callback(true, msg ?: "OTP sent successfully")
                                    } else {
                                        callback(false, msg ?: "Failed to request OTP ($responseStr)")
                                    }
                                }
                            }.onFailure { e ->
                                withContext(Dispatchers.Main) {
                                    callback(false, "Connection error: ${e.localizedMessage ?: "Unable to reach server"}")
                                }
                            }
                        }
                    },
                    onLoginSubmitted = { role, f1, f2, category ->
                        selectedRole = role
                        authErrorMessage = null
                        driverBusCategory = category
                        isAuthLoading = true

                        coroutineScope.launch(Dispatchers.IO) {
                            runCatching {
                                val jsonPayload = if (role == UserRole.DRIVER) {
                                    JSONObject().apply {
                                        put("phoneNumber", f1.trim())
                                        put("busCategory", category)
                                        put("otp", f2.trim())
                                    }.toString()
                                } else {
                                    JSONObject().apply {
                                        put("username", f1.trim())
                                        put("password", f2.trim())
                                        put("role", "PASSENGER")
                                    }.toString()
                                }

                                val requestBody = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())
                                val request = Request.Builder()
                                    .url(ApiConfig.LOGIN_URL)
                                    .post(requestBody)
                                    .build()

                                val response = httpClient.newCall(request).execute()
                                val responseStr = response.body?.string() ?: ""
                                val isSuccess = response.isSuccessful
                                response.close()

                                val jsonRes = runCatching { JSONObject(responseStr) }.getOrNull()

                                if (isSuccess) {
                                    withContext(Dispatchers.Main) {
                                        val session: UserSession
                                        if (role == UserRole.DRIVER) {
                                            val returnedBusId = jsonRes?.optString("busId")?.ifBlank { null }
                                                ?: jsonRes?.optString("username")?.ifBlank { null }
                                                ?: f1.trim()
                                            val driverName = jsonRes?.optString("driverName")?.ifBlank { null }
                                                ?: jsonRes?.optString("displayName")?.ifBlank { null }
                                                ?: "Driver"
                                            val returnedCategory = jsonRes?.optString("busCategory")?.ifBlank { null }
                                                ?: category

                                            busId = returnedBusId
                                            driverBusCategory = returnedCategory
                                            loggedInUserEmail = driverName

                                            session = UserSession(
                                                isLoggedIn = true,
                                                role = UserRole.DRIVER,
                                                userEmail = driverName,
                                                busId = returnedBusId,
                                                driverBusCategory = returnedCategory,
                                                routeId = routeId
                                            )
                                        } else {
                                            val passengerName = jsonRes?.optString("displayName")?.ifBlank { null }
                                                ?: jsonRes?.optString("username")?.ifBlank { null }
                                                ?: f1.trim()
                                            loggedInUserEmail = passengerName

                                            session = UserSession(
                                                isLoggedIn = true,
                                                role = UserRole.PASSENGER,
                                                userEmail = passengerName,
                                                busId = "",
                                                driverBusCategory = "KTCL",
                                                routeId = routeId
                                            )
                                        }
                                        UserSessionRepository.saveUserSession(context, session)
                                        isAuthLoading = false
                                        viewState = AuthViewState.LOGGED_IN
                                    }
                                } else {
                                    val errMsg = jsonRes?.optString("message")?.ifBlank { null }
                                        ?: "Invalid credentials"
                                    withContext(Dispatchers.Main) {
                                        isAuthLoading = false
                                        authErrorMessage = errMsg
                                    }
                                }
                            }.onFailure { e ->
                                withContext(Dispatchers.Main) {
                                    isAuthLoading = false
                                    authErrorMessage = "Connection error: ${e.localizedMessage ?: "Unable to reach server"}"
                                }
                            }
                        }
                    },
                    onNavigateToSignup = {
                        authErrorMessage = null
                        selectedRole = UserRole.PASSENGER
                        viewState = AuthViewState.SIGNUP
                    }
                )
            }
            AuthViewState.SIGNUP -> {
                SignupScreen(
                    isLoading = isAuthLoading,
                    errorMessage = authErrorMessage,
                    onSignupSubmitted = { fullName, email, password ->
                        authErrorMessage = null
                        isAuthLoading = true

                        coroutineScope.launch(Dispatchers.IO) {
                            runCatching {
                                val jsonPayload = JSONObject().apply {
                                    put("username", email.trim())
                                    put("password", password.trim())
                                    put("role", "PASSENGER")
                                    put("displayName", fullName.trim())
                                }.toString()

                                val requestBody = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())
                                val request = Request.Builder()
                                    .url(ApiConfig.SIGNUP_URL)
                                    .post(requestBody)
                                    .build()

                                val response = httpClient.newCall(request).execute()
                                val responseStr = response.body?.string() ?: ""
                                val isSuccess = response.isSuccessful
                                response.close()

                                val jsonRes = runCatching { JSONObject(responseStr) }.getOrNull()

                                if (isSuccess) {
                                    val returnedName = jsonRes?.optString("displayName")?.ifBlank { null }
                                        ?: jsonRes?.optString("username")?.ifBlank { null }
                                        ?: fullName.trim()

                                    withContext(Dispatchers.Main) {
                                        selectedRole = UserRole.PASSENGER
                                        loggedInUserEmail = returnedName

                                        val session = UserSession(
                                            isLoggedIn = true,
                                            role = UserRole.PASSENGER,
                                            userEmail = returnedName,
                                            busId = "",
                                            driverBusCategory = "KTCL",
                                            routeId = routeId
                                        )
                                        UserSessionRepository.saveUserSession(context, session)

                                        isAuthLoading = false
                                        viewState = AuthViewState.LOGGED_IN
                                    }
                                } else {
                                    val errMsg = jsonRes?.optString("message")?.ifBlank { null }
                                        ?: "Signup failed ($responseStr)"
                                    withContext(Dispatchers.Main) {
                                        isAuthLoading = false
                                        authErrorMessage = errMsg
                                    }
                                }
                            }.onFailure { e ->
                                withContext(Dispatchers.Main) {
                                    isAuthLoading = false
                                    authErrorMessage = "Connection error: ${e.localizedMessage ?: "Unable to reach server"}"
                                }
                            }
                        }
                    },
                    onNavigateToLogin = {
                        authErrorMessage = null
                        viewState = AuthViewState.LOGIN
                    }
                )
            }
            AuthViewState.LOGGED_IN -> {
                HomeScreen(
                    userRole = selectedRole,
                    userEmail = loggedInUserEmail,
                    initialBusCategory = driverBusCategory,
                    onStartDriverBroadcasting = onStartDriverBroadcasting,
                    onStopDriverBroadcasting = onStopDriverBroadcasting,
                    onStartPassengerTracking = onStartPassengerTracking,
                    onLogout = {
                        authErrorMessage = null
                        UserSessionRepository.clearUserSession(context)
                        ApiConfig.clearCookies()

                        coroutineScope.launch(Dispatchers.IO) {
                            // Call backend logout endpoint
                            runCatching {
                                val request = Request.Builder()
                                    .url(ApiConfig.LOGOUT_URL)
                                    .post("{}".toRequestBody("application/json".toMediaType()))
                                    .build()
                                val response = httpClient.newCall(request).execute()
                                response.close()
                            }
                        }

                        if (selectedRole == UserRole.DRIVER) {
                            // 1. Stop location service / driver broadcasting
                            onStopDriverBroadcasting(false)

                            // 2. Send request to backend to stop tracking
                            val currentBusId = busId
                            if (currentBusId.isNotBlank()) {
                                coroutineScope.launch(Dispatchers.IO) {
                                    runCatching {
                                        val jsonPayload = JSONObject().apply {
                                            put("busId", currentBusId)
                                        }.toString()
                                        val requestBody = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())
                                        val url = "${ApiConfig.STOP_SHARING_URL}?busId=${currentBusId}"
                                        val request = Request.Builder()
                                            .url(url)
                                            .post(requestBody)
                                            .build()

                                        val response = httpClient.newCall(request).execute()
                                        Log.d("AuthScreen", "Driver logout stop-sharing response code: ${response.code}")
                                        response.close()
                                    }.onFailure { e ->
                                        Log.e("AuthScreen", "Failed to send stop-sharing request on driver logout: ${e.message}")
                                    }
                                }
                            }

                            // Reset driver session fields
                            busId = ""
                            loggedInUserEmail = ""
                        } else {
                            onStopPassengerTracking()
                        }
                        viewState = AuthViewState.LOGIN
                    },
                    liveBusData = liveBusData
                )
            }
        }
    }
}
