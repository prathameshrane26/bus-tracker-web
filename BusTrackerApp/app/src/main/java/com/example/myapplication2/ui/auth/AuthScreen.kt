package com.example.myapplication2.ui.auth

import android.util.Log
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.myapplication2.data.ApiConfig
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
    var viewState by remember { mutableStateOf(AuthViewState.LOGIN) }
    var selectedRole by remember { mutableStateOf(UserRole.PASSENGER) }
    var loggedInUserEmail by remember { mutableStateOf("") }
    var busId by remember { mutableStateOf("") }
    var driverBusCategory by remember { mutableStateOf("KTCL") }
    var routeId by remember { mutableStateOf("route-42") }

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
                    onLoginSubmitted = { role, f1, f2, category ->
                        selectedRole = role
                        authErrorMessage = null
                        driverBusCategory = category

                        if (role == UserRole.DRIVER) {
                            // Backend verification call for Driver
                            isAuthLoading = true
                            coroutineScope.launch(Dispatchers.IO) {
                                runCatching {
                                    val jsonPayload = JSONObject().apply {
                                        put("busId", f1.trim())
                                        put("phoneNumber", f2.trim())
                                        put("role", "DRIVER")
                                    }.toString()

                                    val requestBody = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())
                                    val request = Request.Builder()
                                        .url(ApiConfig.LOGIN_URL)
                                        .post(requestBody)
                                        .build()

                                    val response = httpClient.newCall(request).execute()
                                    val responseStr = response.body?.string() ?: ""
                                    response.close()

                                    if (response.isSuccessful) {
                                        val jsonRes = JSONObject(responseStr)
                                        val returnedBusId = jsonRes.optString("busId", f1.trim())
                                        val driverName = jsonRes.optString("driverName", "Driver ($returnedBusId)")
                                        val returnedCategory = jsonRes.optString("busCategory", category)

                                        withContext(Dispatchers.Main) {
                                            busId = returnedBusId
                                            driverBusCategory = returnedCategory
                                            loggedInUserEmail = driverName
                                            isAuthLoading = false
                                            viewState = AuthViewState.LOGGED_IN
                                        }
                                    } else {
                                        var errMsg = "Invalid credentials ($responseStr)"
                                        if (responseStr.isNotBlank()) {
                                            runCatching {
                                                val jsonErr = JSONObject(responseStr)
                                                errMsg = jsonErr.optString("message", errMsg)
                                            }
                                        }
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
                        } else {
                            // Passenger Mode
                            loggedInUserEmail = if (f1.isNotBlank()) f1 else "passenger@goa.bus"
                            viewState = AuthViewState.LOGGED_IN
                        }
                    },
                    onNavigateToSignup = {
                        authErrorMessage = null
                        viewState = AuthViewState.SIGNUP
                    }
                )
            }
            AuthViewState.SIGNUP -> {
                SignupScreen(
                    selectedRole = selectedRole,
                    onRoleSelected = { selectedRole = it },
                    onSignupSubmitted = { role, name, email, bId, rId ->
                        selectedRole = role
                        loggedInUserEmail = if (name.isNotBlank()) name else email
                        if (bId.isNotBlank()) busId = bId
                        if (rId.isNotBlank()) routeId = rId
                        viewState = AuthViewState.LOGGED_IN
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
                    initialBusId = busId,
                    initialBusCategory = driverBusCategory,
                    initialRouteId = routeId,
                    onStartDriverBroadcasting = onStartDriverBroadcasting,
                    onStopDriverBroadcasting = onStopDriverBroadcasting,
                    onStartPassengerTracking = onStartPassengerTracking,
                    onStopPassengerTracking = onStopPassengerTracking,
                    onLogout = {
                        authErrorMessage = null
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
