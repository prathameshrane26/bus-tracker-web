package com.example.myapplication2.ui.map

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

data class BusLocation(
    val id: String,
    val routeName: String,
    val destination: String = "Margao",
    val startPoint: String = "Panaji",
    val latitude: Double,
    val longitude: Double,
    val busCategory: String = "KTCL",
    val status: String = "In Transit",
    val isActivelySharing: Boolean = true,
    val driverName: String = "Driver Active",
    val updatedAt: Double = System.currentTimeMillis() / 1000.0
)

// Default Goa Center Coordinates
private const val GOA_LAT = 15.4989
private const val GOA_LNG = 73.8278

@Composable
fun PassengerMapView(
    buses: List<BusLocation>,
    selectedBusId: String?,
    trackedBusIds: Set<String> = emptySet(),
    onSelectBus: (BusLocation) -> Unit,
    onToggleTrackBus: (String) -> Unit = {},
    isLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }

    val selectedBus = buses.find { it.id == selectedBusId }

    // Initialize osmdroid user agent configuration
    DisposableEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
        onDispose {
            mapViewInstance?.onDetach()
        }
    }

    // Auto-center map on selected bus when selectedBusId or selectedBus position changes
    LaunchedEffect(selectedBusId, selectedBus?.latitude, selectedBus?.longitude) {
        val busToCenter = buses.find { it.id == selectedBusId }
        if (busToCenter != null && mapViewInstance != null) {
            mapViewInstance?.controller?.animateTo(
                GeoPoint(busToCenter.latitude, busToCenter.longitude)
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // OpenStreetMap Rendered via AndroidView
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(12.5)
                    controller.setCenter(GeoPoint(GOA_LAT, GOA_LNG))
                    mapViewInstance = this
                }
            },
            update = { mapView ->
                // Intelligently update locator pin markers without destroying existing overlays
                val currentMarkers = mapView.overlays.filterIsInstance<Marker>()
                val activeBusIds = buses.map { it.id }.toSet()

                // Remove stale markers no longer in buses list
                val staleMarkers = currentMarkers.filter { (it.id ?: it.title) !in activeBusIds }
                if (staleMarkers.isNotEmpty()) {
                    mapView.overlays.removeAll(staleMarkers)
                }

                buses.forEach { bus ->
                    val isTracked = trackedBusIds.contains(bus.id)
                    val isSelected = (bus.id == selectedBusId)
                    val newGeoPoint = GeoPoint(bus.latitude, bus.longitude)
                    val snippetText = "Route: ${bus.routeName}\nDestination: ${bus.destination}${if (isTracked) "\n[LIVE TRACKING ACTIVE]" else ""}"

                    val existingMarker = currentMarkers.find { (it.id ?: it.title) == bus.id }

                    if (existingMarker != null) {
                        // Dynamically update locator pin position when HTTP SSE message is received
                        existingMarker.position = newGeoPoint
                        existingMarker.title = bus.id
                        existingMarker.snippet = snippetText

                        if (isSelected && !existingMarker.isInfoWindowShown) {
                            existingMarker.showInfoWindow()
                        }
                    } else {
                        val marker = Marker(mapView).apply {
                            id = bus.id
                            title = bus.id
                            snippet = snippetText
                            position = newGeoPoint
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

                            setOnMarkerClickListener { _, _ ->
                                onSelectBus(bus)
                                showInfoWindow()
                                true
                            }
                        }

                        if (isSelected) {
                            marker.showInfoWindow()
                        }

                        mapView.overlays.add(marker)
                    }
                }

                mapView.invalidate()
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top Header Badge
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Map,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isLoading && buses.isEmpty()) {
                        "OpenStreetMap • Goa, India (Loading...)"
                    } else {
                        "OpenStreetMap • Goa, India (${buses.size} Buses)"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Floating Zoom / Recenter Controls
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FloatingActionButton(
                onClick = { mapViewInstance?.controller?.zoomIn() },
                modifier = Modifier.size(42.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom In")
            }

            FloatingActionButton(
                onClick = { mapViewInstance?.controller?.zoomOut() },
                modifier = Modifier.size(42.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
            }

            FloatingActionButton(
                onClick = {
                    mapViewInstance?.controller?.apply {
                        setZoom(12.5)
                        animateTo(GeoPoint(GOA_LAT, GOA_LNG))
                    }
                },
                modifier = Modifier.size(42.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Recenter Goa")
            }
        }

        // Bottom Selected Bus Telemetry Card
        AnimatedVisibility(
            visible = selectedBus != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            selectedBus?.let { bus ->
                val isTracked = trackedBusIds.contains(bus.id)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isTracked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.DirectionsBus,
                                            contentDescription = null,
                                            tint = if (isTracked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = bus.id,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${if (bus.busCategory.equals("KTCL", ignoreCase = true)) "KTCL Bus" else "Private Bus"} • ${bus.driverName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isTracked) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (isTracked) Color(0xFF2E7D32) else Color.Gray)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isTracked) "TRACKING" else "STATIC",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isTracked) Color(0xFF1B5E20) else Color.DarkGray
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Bus Route & Destination Details
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.TripOrigin,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Start: ${bus.startPoint}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Directions,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Route: ${bus.routeName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Destination: ${bus.destination}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.PinDrop,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Location: %.4f, %.4f".format(bus.latitude, bus.longitude),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Toggle Track Button
                        Button(
                            onClick = { onToggleTrackBus(bus.id) },
                            colors = if (isTracked) {
                                ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2E7D32),
                                    contentColor = Color.White
                                )
                            } else {
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = if (isTracked) Icons.Default.Check else Icons.Default.Radio,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isTracked) "Tracking Live (Tap to Stop)" else "Start Live Tracking",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
