package com.example.myapplication2.ui.home

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TripOrigin
import androidx.compose.material.icons.filled.WifiOff
import android.util.Log
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication2.data.ApiConfig
import com.example.myapplication2.data.BusCacheRepository
import com.example.myapplication2.data.DriverOptionsRepository
import com.example.myapplication2.ui.auth.UserRole
import com.example.myapplication2.ui.map.BusLocation
import com.example.myapplication2.ui.map.PassengerMapView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

private fun parseBusLocationsFromJson(jsonString: String): List<BusLocation> {
    val list = mutableListOf<BusLocation>()
    if (jsonString.isBlank()) return list

    val raw = jsonString.trim()
    runCatching {
        fun parseObject(item: JSONObject): BusLocation? {
            val bId = item.optString("busId", "")
                .ifBlank { item.optString("id", "") }
                .ifBlank { item.optString("busNumber", "") }
                .ifBlank { item.optString("vehicleId", "") }
            if (bId.isBlank()) return null

            val locObj = item.optJSONObject("location") ?: item.optJSONObject("coords") ?: item
            val lat = when {
                locObj.has("latitude") -> locObj.optDouble("latitude", 15.4989)
                locObj.has("lat") -> locObj.optDouble("lat", 15.4989)
                else -> 15.4989
            }
            val lng = when {
                locObj.has("longitude") -> locObj.optDouble("longitude", 73.8278)
                locObj.has("lng") -> locObj.optDouble("lng", 73.8278)
                else -> 73.8278
            }
            val dest = item.optString("destination", "Margao").ifBlank { "Margao" }
            val start = item.optString("startPoint", "Panaji").ifBlank { "Panaji" }
            val route = item.optString("routeTaken", item.optString("route", "Panaji-Mapusa-Bicholim-Sanquelim")).ifBlank { "Panaji-Mapusa-Bicholim-Sanquelim" }
            val category = item.optString("busCategory", "KTCL").ifBlank { "KTCL" }
            val isStopped = item.optBoolean("stopped", false) ||
                    item.optString("status", "").equals("STOPPED", ignoreCase = true) ||
                    (item.has("isActivelySharing") && !item.optBoolean("isActivelySharing"))

            return BusLocation(
                id = bId,
                routeName = route,
                destination = dest,
                startPoint = start,
                latitude = lat,
                longitude = lng,
                busCategory = category,
                isActivelySharing = !isStopped,
                status = if (isStopped) "Stopped" else "In Transit"
            )
        }

        if (raw.startsWith("[")) {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                parseObject(item)?.let { list.add(it) }
            }
        } else if (raw.startsWith("{")) {
            val item = JSONObject(raw)
            parseObject(item)?.let { list.add(it) }
        }
    }
    return list
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    userRole: UserRole,
    userEmail: String,
    initialBusId: String = "",
    initialBusCategory: String = "KTCL",
    initialRouteId: String = "route-42",
    onStartDriverBroadcasting: (busId: String, startPoint: String, destination: String, routeTaken: String, busCategory: String) -> Unit,
    onStopDriverBroadcasting: (reachedDestination: Boolean) -> Unit = {},
    onStartPassengerTracking: (routeId: String) -> Unit,
    onStopPassengerTracking: () -> Unit = {},
    onLogout: () -> Unit,
    liveBusData: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val driverOptions = remember(context) { DriverOptionsRepository.loadOptions(context) }

    var selectedBusId by remember { mutableStateOf("") }
    var selectedBusCategory by remember { mutableStateOf(if (initialBusCategory.contains("Private", ignoreCase = true)) "Private" else "KTCL") }
    var selectedStartPoint by remember { mutableStateOf("") }
    var selectedDestination by remember { mutableStateOf("") }
    var selectedRoute by remember { mutableStateOf("") }
    var isBroadcasting by remember { mutableStateOf(false) }

    var categoryExpanded by remember { mutableStateOf(false) }
    var startPointExpanded by remember { mutableStateOf(false) }
    var destinationExpanded by remember { mutableStateOf(false) }
    var routeExpanded by remember { mutableStateOf(false) }

    val isFormValid = selectedBusId.isNotBlank() &&
            selectedStartPoint.isNotBlank() &&
            selectedDestination.isNotBlank() &&
            selectedRoute.isNotBlank()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    // Dynamic bus list populated initially from local cache (~1ms) then updated via backend
    val cachedBuses = remember { BusCacheRepository.getCachedBuses(context) }
    val defaultBuses = remember { mutableStateListOf<BusLocation>().apply { addAll(cachedBuses) } }
    val trackedBusIds = remember { mutableStateSetOf<String>() }

    var activeSelectedBusId by remember { mutableStateOf<String?>(null) }
    var isPassengerLoading by remember { mutableStateOf(false) }
    var passengerFetchError by remember { mutableStateOf<String?>(null) }
    var fetchTrigger by remember { mutableIntStateOf(0) }

    fun toggleTrackBus(busId: String) {
        if (trackedBusIds.contains(busId)) {
            trackedBusIds.remove(busId)

            // Send HTTP untrack request to backend
            coroutineScope.launch(Dispatchers.IO) {
                runCatching {
                    val url = ApiConfig.getUntrackBusUrl(busId)
                    val jsonPayload = JSONObject().apply {
                        put("busId", busId)
                        put("clientId", ApiConfig.CLIENT_ID)
                    }.toString()
                    val requestBody = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())
                    val request = Request.Builder()
                        .url(url)
                        .post(requestBody)
                        .build()

                    val response = ApiConfig.sharedHttpClient.newCall(request).execute()
                    Log.d("HomeScreen", "Untrack request for bus $busId response code: ${response.code}")
                    response.close()
                }.onFailure { e ->
                    Log.e("HomeScreen", "Error sending untrack request for bus $busId: ${e.message}")
                }
            }

            Toast.makeText(context, "Stopped live tracking for $busId", Toast.LENGTH_SHORT).show()
        } else {
            trackedBusIds.add(busId)
            activeSelectedBusId = busId

            // 1. Send HTTP track request to backend (includes mandatory clientId & busId)
            coroutineScope.launch(Dispatchers.IO) {
                runCatching {
                    val url = ApiConfig.getTrackBusUrl(busId)
                    val jsonPayload = JSONObject().apply {
                        put("busId", busId)
                        put("clientId", ApiConfig.CLIENT_ID)
                    }.toString()
                    val requestBody = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())
                    val request = Request.Builder()
                        .url(url)
                        .post(requestBody)
                        .build()

                    val response = ApiConfig.sharedHttpClient.newCall(request).execute()
                    Log.d("HomeScreen", "Track request for bus $busId response code: ${response.code}")
                    response.close()
                }.onFailure { e ->
                    Log.e("HomeScreen", "Error sending track request for bus $busId: ${e.message}")
                }
            }

            // 2. Start/Ensure HTTP SSE connection is active
            onStartPassengerTracking(busId)

            Toast.makeText(context, "Started live tracking for $busId", Toast.LENGTH_SHORT).show()
        }
    }

    // Connect SSE stream and fetch both active live locations and scheduled locations
    LaunchedEffect(userRole, fetchTrigger) {
        if (userRole == UserRole.PASSENGER) {
            // Auto-start SSE stream for immediate live updates on launch
            onStartPassengerTracking("live")

            isPassengerLoading = true
            passengerFetchError = null

            val client = ApiConfig.sharedHttpClient

            withContext(Dispatchers.IO) {
                runCatching {
                    val mergedFetchedBuses = mutableMapOf<String, BusLocation>()

                    // 1. Fetch active bus summaries (/api/buses/active)
                    val activeBusesReq = Request.Builder().url(ApiConfig.ACTIVE_BUSES_URL).build()
                    runCatching {
                        val resp = client.newCall(activeBusesReq).execute()
                        val body = resp.body?.string() ?: ""
                        resp.close()
                        if (resp.isSuccessful) {
                            parseBusLocationsFromJson(body).forEach { bus ->
                                mergedFetchedBuses[bus.id] = bus
                            }
                        }
                    }

                    // 2. Fetch active live in-memory bus locations (/api/buses/locations)
                    val activeLocationsReq = Request.Builder().url(ApiConfig.BUS_LOCATIONS_URL).build()
                    runCatching {
                        val resp = client.newCall(activeLocationsReq).execute()
                        val body = resp.body?.string() ?: ""
                        resp.close()
                        if (resp.isSuccessful) {
                            parseBusLocationsFromJson(body).forEach { bus ->
                                val existing = mergedFetchedBuses[bus.id]
                                if (existing != null) {
                                    mergedFetchedBuses[bus.id] = existing.copy(
                                        latitude = bus.latitude,
                                        longitude = bus.longitude,
                                        isActivelySharing = bus.isActivelySharing,
                                        status = bus.status
                                    )
                                } else {
                                    mergedFetchedBuses[bus.id] = bus
                                }
                            }
                        }
                    }

                    // 3. Fetch scheduled locations (/api/buses/all-scheduled-locations)
                    val scheduledReq = Request.Builder().url(ApiConfig.ALL_SCHEDULED_LOCATIONS_URL).build()
                    runCatching {
                        val resp = client.newCall(scheduledReq).execute()
                        val body = resp.body?.string() ?: ""
                        resp.close()
                        if (resp.isSuccessful) {
                            parseBusLocationsFromJson(body).forEach { bus ->
                                if (!mergedFetchedBuses.containsKey(bus.id)) {
                                    mergedFetchedBuses[bus.id] = bus
                                }
                            }
                        }
                    }

                    val resultList = mergedFetchedBuses.values.toList()

                    withContext(Dispatchers.Main) {
                        defaultBuses.clear()
                        defaultBuses.addAll(resultList)
                        if (resultList.isNotEmpty()) {
                            BusCacheRepository.saveCachedBuses(context, resultList)
                            if (activeSelectedBusId == null || defaultBuses.none { it.id == activeSelectedBusId }) {
                                activeSelectedBusId = resultList.first().id
                            }
                        }
                        isPassengerLoading = false
                    }
                }.onFailure { e ->
                    withContext(Dispatchers.Main) {
                        passengerFetchError = "Could not reach backend server (${e.localizedMessage ?: "Connection failed"})"
                        isPassengerLoading = false
                    }
                }
            }
        }
    }

    // Process incoming SSE events (location-snapshot, location-update, bus-stopped)
    LaunchedEffect(liveBusData) {
        liveBusData?.let { rawData ->
            val raw = rawData.trim()
            runCatching {
                var eventType = "location-update"
                var payload = raw

                if (raw.startsWith("{") && raw.contains("\"eventType\"") && raw.contains("\"payload\"")) {
                    val wrapper = JSONObject(raw)
                    eventType = wrapper.optString("eventType", "location-update")
                    payload = wrapper.optString("payload", raw).trim()
                }

                if (eventType.equals("bus-stopped", ignoreCase = true) || eventType.equals("location-stopped", ignoreCase = true)) {
                    val stoppedBusId = if (payload.startsWith("{")) {
                        JSONObject(payload).optString("busId", "")
                    } else if (payload.startsWith("[")) {
                        val arr = JSONArray(payload)
                        if (arr.length() > 0) arr.getJSONObject(0).optString("busId", "") else ""
                    } else {
                        payload.replace("\"", "").trim()
                    }

                    if (stoppedBusId.isNotBlank()) {
                        val index = defaultBuses.indexOfFirst { it.id.equals(stoppedBusId, ignoreCase = true) }
                        if (index != -1) {
                            defaultBuses[index] = defaultBuses[index].copy(
                                isActivelySharing = false,
                                status = "Stopped"
                            )
                        }
                    }
                } else {
                    val parsedBuses = parseBusLocationsFromJson(payload)
                    parsedBuses.forEach { bus ->
                        val index = defaultBuses.indexOfFirst { it.id.equals(bus.id, ignoreCase = true) }
                        if (index != -1) {
                            val existing = defaultBuses[index]
                            defaultBuses[index] = existing.copy(
                                latitude = bus.latitude,
                                longitude = bus.longitude,
                                updatedAt = System.currentTimeMillis() / 1000.0,
                                isActivelySharing = bus.isActivelySharing,
                                status = bus.status,
                                routeName = if (bus.routeName != "Panaji-Mapusa-Bicholim-Sanquelim" || existing.routeName.isBlank()) bus.routeName else existing.routeName,
                                destination = if (bus.destination != "Margao" || existing.destination.isBlank()) bus.destination else existing.destination,
                                startPoint = if (bus.startPoint != "Panaji" || existing.startPoint.isBlank()) bus.startPoint else existing.startPoint,
                                busCategory = if (bus.busCategory != "KTCL" || existing.busCategory.isBlank()) bus.busCategory else existing.busCategory
                            )
                        } else {
                            defaultBuses.add(bus)
                        }
                    }
                    if (parsedBuses.isNotEmpty()) {
                        BusCacheRepository.saveCachedBuses(context, defaultBuses.toList())
                    }
                }
            }
        }
    }

    if (userRole == UserRole.PASSENGER) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = false,
            drawerContent = {
                ActiveBusesSidePanel(
                    buses = defaultBuses,
                    selectedBusId = activeSelectedBusId,
                    trackedBusIds = trackedBusIds,
                    onSelectBus = { bus ->
                        activeSelectedBusId = bus.id
                    },
                    onToggleTrackBus = ::toggleTrackBus,
                    isLoading = isPassengerLoading,
                    onRefresh = {
                        fetchTrigger++
                        Toast.makeText(context, "Refreshing live bus data...", Toast.LENGTH_SHORT).show()
                    },
                    onCloseDrawer = {
                        coroutineScope.launch { drawerState.close() }
                    }
                )
            }
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = "BusTracker",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                val subtitleText = when {
                                    isPassengerLoading && defaultBuses.isEmpty() -> "Loading live buses..."
                                    isPassengerLoading && defaultBuses.isNotEmpty() -> "Updating live buses..."
                                    trackedBusIds.isNotEmpty() -> "Tracking ${trackedBusIds.size} bus(es) live"
                                    else -> "${defaultBuses.count { it.isActivelySharing }} active live buses"
                                }
                                Text(
                                    text = subtitleText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Open Active Buses Menu"
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = {
                                    fetchTrigger++
                                    Toast.makeText(context, "Refreshing live bus data...", Toast.LENGTH_SHORT).show()
                                },
                                enabled = !isPassengerLoading
                            ) {
                                if (isPassengerLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Refresh live bus data"
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = {
                                    coroutineScope.launch { drawerState.open() }
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DirectionsBus,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                val buttonText = if (isPassengerLoading && defaultBuses.isEmpty()) {
                                    "Buses (Loading...)"
                                } else {
                                    "Buses (${defaultBuses.size})"
                                }
                                Text(
                                    text = buttonText,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }

                            IconButton(onClick = onLogout) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                    contentDescription = "Logout"
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    )
                }
            ) { innerPadding ->
                Box(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    PassengerMapView(
                        buses = defaultBuses,
                        selectedBusId = activeSelectedBusId,
                        trackedBusIds = trackedBusIds,
                        onSelectBus = { bus ->
                            activeSelectedBusId = bus.id
                        },
                        onToggleTrackBus = ::toggleTrackBus,
                        isLoading = isPassengerLoading
                    )

                    if (isPassengerLoading && defaultBuses.isEmpty()) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                            shadowElevation = 6.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "Fetching live buses from server...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    } else if (passengerFetchError != null && defaultBuses.isEmpty()) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(16.dp)
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            shadowElevation = 6.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudOff,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Column {
                                        Text(
                                            text = "Server Unreachable",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Text(
                                            text = passengerFetchError ?: "Unable to fetch bus data",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                                Button(
                                    onClick = { fetchTrigger++ },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Retry",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Retry")
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Driver Screen View
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Driver Console",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = userEmail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onLogout) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "Logout"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                )
            }
        ) { innerPadding ->
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Role Badge Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.DirectionsBus,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column {
                            Text(
                                text = "Logged in as driver",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Share your live location to passengers",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                // Driver Controls
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Location Service Controls",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Select all details before starting the location server:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // 1. Bus ID Text Input
                        OutlinedTextField(
                            value = selectedBusId,
                            onValueChange = { selectedBusId = it },
                            label = { Text("Vehicle / Bus ID") },
                            placeholder = { Text("Enter Bus ID (e.g. GA-03-X-1001)") },
                            leadingIcon = { Icon(Icons.Default.DirectionsBus, contentDescription = null) },
                            singleLine = true,
                            enabled = !isBroadcasting,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // 2. Bus Category Dropdown
                        ExposedDropdownMenuBox(
                            expanded = categoryExpanded && !isBroadcasting,
                            onExpandedChange = { if (!isBroadcasting) categoryExpanded = !categoryExpanded }
                        ) {
                            OutlinedTextField(
                                value = if (selectedBusCategory.equals("Private", ignoreCase = true)) "Private" else "KTCL",
                                onValueChange = {},
                                readOnly = true,
                                enabled = !isBroadcasting,
                                label = { Text("Bus Category") },
                                leadingIcon = { Icon(Icons.Default.DirectionsBus, contentDescription = null) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                shape = RoundedCornerShape(12.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = categoryExpanded && !isBroadcasting,
                                onDismissRequest = { categoryExpanded = false }
                            ) {
                                listOf("KTCL", "Private").forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat) },
                                        onClick = {
                                            selectedBusCategory = if (cat.contains("Private", ignoreCase = true)) "Private" else "KTCL"
                                            categoryExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // 3. Bus Start Point Dropdown
                        ExposedDropdownMenuBox(
                            expanded = startPointExpanded && !isBroadcasting,
                            onExpandedChange = { if (!isBroadcasting) startPointExpanded = !startPointExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedStartPoint,
                                onValueChange = {},
                                readOnly = true,
                                enabled = !isBroadcasting,
                                label = { Text("Start Point") },
                                placeholder = { Text("Select Start Point") },
                                leadingIcon = { Icon(Icons.Default.TripOrigin, contentDescription = null) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = startPointExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                shape = RoundedCornerShape(12.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = startPointExpanded && !isBroadcasting,
                                onDismissRequest = { startPointExpanded = false }
                            ) {
                                driverOptions.startPoints.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            selectedStartPoint = option
                                            startPointExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // 4. Destination Dropdown
                        ExposedDropdownMenuBox(
                            expanded = destinationExpanded && !isBroadcasting,
                            onExpandedChange = { if (!isBroadcasting) destinationExpanded = !destinationExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedDestination,
                                onValueChange = {},
                                readOnly = true,
                                enabled = !isBroadcasting,
                                label = { Text("Destination") },
                                placeholder = { Text("Select Destination") },
                                leadingIcon = { Icon(Icons.Default.Place, contentDescription = null) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = destinationExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                shape = RoundedCornerShape(12.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = destinationExpanded && !isBroadcasting,
                                onDismissRequest = { destinationExpanded = false }
                            ) {
                                driverOptions.destinations.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            selectedDestination = option
                                            destinationExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // 5. Route Taken Dropdown
                        ExposedDropdownMenuBox(
                            expanded = routeExpanded && !isBroadcasting,
                            onExpandedChange = { if (!isBroadcasting) routeExpanded = !routeExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedRoute,
                                onValueChange = {},
                                readOnly = true,
                                enabled = !isBroadcasting,
                                label = { Text("Via") },
                                placeholder = { Text("Select Route") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.AltRoute, contentDescription = null) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = routeExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                shape = RoundedCornerShape(12.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = routeExpanded && !isBroadcasting,
                                onDismissRequest = { routeExpanded = false }
                            ) {
                                driverOptions.routes.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            selectedRoute = option
                                            routeExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        if (!isFormValid && !isBroadcasting) {
                            Text(
                                text = "⚠️ Please fill in the details to start sharing location.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        if (isBroadcasting) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Radio,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Broadcasting Active",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    Text(
                                        text = "Sharing live location for $selectedBusId [$selectedBusCategory] ($selectedStartPoint ➔ $selectedDestination)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }

                            // Button 1: Reached Destination
                            Button(
                                onClick = {
                                    isBroadcasting = false
                                    onStopDriverBroadcasting(true)
                                    Toast.makeText(context, "Location sharing stopped. Trip marked as completed", Toast.LENGTH_LONG).show()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2E7D32),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Reached Destination",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Button 2: Stop Location Sharing / Close Connection
                            OutlinedButton(
                                onClick = {
                                    isBroadcasting = false
                                    onStopDriverBroadcasting(false)
                                    Toast.makeText(context, "Location sharing stopped", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.WifiOff, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Stop Location Sharing",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Button(
                                onClick = {
                                    isBroadcasting = true
                                    onStartDriverBroadcasting(selectedBusId, selectedStartPoint, selectedDestination, selectedRoute, selectedBusCategory)
                                },
                                enabled = isFormValid,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Radio, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Start Driver Location Service",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
