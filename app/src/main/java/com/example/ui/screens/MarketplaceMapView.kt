package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.local.JobRequestEntity
import com.example.data.local.ServiceProviderEntity
import com.example.location.LocationRepository
import com.example.location.UserLocation
import com.example.ui.theme.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState

sealed class MapTarget {
    data class Provider(val provider: ServiceProviderEntity) : MapTarget()
    data class Request(val request: JobRequestEntity) : MapTarget()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketplaceMapView(
    providers: List<ServiceProviderEntity>,
    requests: List<JobRequestEntity>,
    onBackClick: () -> Unit,
    onProviderClick: (String) -> Unit,
    onChatWithProvider: (ServiceProviderEntity) -> Unit,
    onChatForJobRequest: (JobRequestEntity) -> Unit
) {
    BackHandler { onBackClick() }

    val context = LocalContext.current
    var selectedFilter by remember { mutableStateOf("ALL") }
    var selectedTarget by remember { mutableStateOf<MapTarget?>(null) }
    var locationPermissionGranted by remember { mutableStateOf(hasLocationPermission(context)) }
    var permissionRequested by remember { mutableStateOf(false) }
    var userLocation by remember { mutableStateOf<UserLocation?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        locationPermissionGranted =
            result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    LaunchedEffect(Unit) {
        if (!locationPermissionGranted && !permissionRequested) {
            permissionRequested = true
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    LaunchedEffect(locationPermissionGranted) {
        userLocation = if (locationPermissionGranted) {
            runCatching { LocationRepository(context).getLastKnownLocation() }.getOrNull()
        } else {
            null
        }
    }

    val validProviders = remember(providers) {
        providers.filter { hasValidMapPoint(it.latitude, it.longitude) }
    }
    val validRequests = remember(requests) {
        requests.filter { hasValidMapPoint(it.latitude, it.longitude) }
    }

    val fallbackCenter = remember(validProviders, validRequests) {
        validProviders.firstOrNull()?.let { LatLng(it.latitude, it.longitude) }
            ?: validRequests.firstOrNull()?.let { LatLng(it.latitude, it.longitude) }
            ?: LatLng(39.0, 35.0)
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            fallbackCenter,
            if (validProviders.isEmpty() && validRequests.isEmpty()) 5.5f else 12f
        )
    }

    LaunchedEffect(userLocation) {
        userLocation?.let {
            cameraPositionState.move(
                CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 13f)
            )
        }
    }

    val visibleProviders = remember(validProviders, selectedFilter) {
        when (selectedFilter) {
            "REQUESTS" -> emptyList()
            "EMERGENCY" -> validProviders.filter { it.isEmergencyAvailable && it.isOpenForOffers }
            else -> validProviders
        }
    }
    val visibleRequests = remember(validRequests, selectedFilter) {
        when (selectedFilter) {
            "PROVIDERS", "EMERGENCY" -> emptyList()
            else -> validRequests
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Mahalle Haritası", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(
                            if (locationPermissionGranted)
                                "Yakındaki ilanlar • konumlar gizlilik için yaklaşık gösterilir"
                            else
                                "Konum izni kapalı • ilanların yaklaşık alanları gösteriliyor",
                            fontSize = 11.sp,
                            color = Slate500
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("btn_map_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Geri"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = locationPermissionGranted),
                uiSettings = MapUiSettings(
                    myLocationButtonEnabled = locationPermissionGranted,
                    zoomControlsEnabled = false,
                    compassEnabled = true
                ),
                onMapClick = { selectedTarget = null }
            ) {
                visibleProviders.forEach { provider ->
                    key("provider-" + provider.id) {
                        Marker(
                            state = rememberUpdatedMarkerState(
                                position = LatLng(provider.latitude, provider.longitude)
                            ),
                            title = provider.name,
                            snippet = provider.title + " • " + provider.district,
                            onClick = {
                                selectedTarget = MapTarget.Provider(provider)
                                true
                            }
                        )
                    }
                }

                visibleRequests.forEach { request ->
                    key("request-" + request.id) {
                        Marker(
                            state = rememberUpdatedMarkerState(
                                position = LatLng(request.latitude, request.longitude)
                            ),
                            title = request.title,
                            snippet = request.district,
                            onClick = {
                                selectedTarget = MapTarget.Request(request)
                                true
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
                    .align(Alignment.TopStart),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == "ALL",
                    onClick = { selectedFilter = "ALL" },
                    label = { Text("Tümü (" + (validProviders.size + validRequests.size) + ")", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = selectedFilter == "EMERGENCY",
                    onClick = { selectedFilter = "EMERGENCY" },
                    label = { Text("🚨 Acil Nöbetçi", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EmergencyRed,
                        selectedLabelColor = Color.White
                    )
                )
                FilterChip(
                    selected = selectedFilter == "PROVIDERS",
                    onClick = { selectedFilter = "PROVIDERS" },
                    label = { Text("🛠️ Hizmet Verenler", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = selectedFilter == "REQUESTS",
                    onClick = { selectedFilter = "REQUESTS" },
                    label = { Text("📢 Talepler", fontSize = 11.sp) }
                )
            }

            if (visibleProviders.isEmpty() && visibleRequests.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 4.dp
                ) {
                    Text(
                        "Bu filtre için haritada gösterilebilecek konumlu ilan bulunamadı.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            selectedTarget?.let { target ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .testTag("map_preview_sheet")
                ) {
                    when (target) {
                        is MapTarget.Provider -> {
                            val provider = target.provider
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(provider.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Text(provider.title, fontSize = 12.sp, color = Slate600)
                                        Text(
                                            "📍 " + provider.district + " • yaklaşık hizmet alanı",
                                            fontSize = 11.sp,
                                            color = TealPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    IconButton(onClick = { selectedTarget = null }) {
                                        Icon(Icons.Default.Close, contentDescription = "Kapat")
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { onChatWithProvider(provider) },
                                        modifier = Modifier.weight(1f).testTag("btn_map_chat")
                                    ) {
                                        Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Mesaj")
                                    }
                                    Button(
                                        onClick = { onProviderClick(provider.id) },
                                        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                                        modifier = Modifier.weight(1f).testTag("btn_map_view_profile")
                                    ) {
                                        Text("Profili Gör")
                                    }
                                }
                            }
                        }

                        is MapTarget.Request -> {
                            val request = target.request
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(request.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Text(
                                            "📍 " + request.district + " • yaklaşık talep alanı",
                                            fontSize = 12.sp,
                                            color = Slate600
                                        )
                                    }
                                    IconButton(onClick = { selectedTarget = null }) {
                                        Icon(Icons.Default.Close, contentDescription = "Kapat")
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = { onChatForJobRequest(request) },
                                    colors = ButtonDefaults.buttonColors(containerColor = FestiveCoral),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.LocalOffer, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Talep Hakkında Görüş")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun hasValidMapPoint(latitude: Double, longitude: Double): Boolean =
    latitude in -90.0..90.0 &&
        longitude in -180.0..180.0 &&
        !(latitude == 0.0 && longitude == 0.0)
