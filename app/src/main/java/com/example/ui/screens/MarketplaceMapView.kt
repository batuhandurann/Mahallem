package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.JobRequestEntity
import com.example.data.local.ServiceProviderEntity
import com.example.ui.theme.*

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

    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, EMERGENCY, PROVIDERS, REQUESTS
    var selectedTarget by remember { mutableStateOf<MapTarget?>(null) }

    // Pulsing radar animation
    val infiniteTransition = rememberInfiniteTransition(label = "radar_anim")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 20f,
        targetValue = 220f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_radius"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Canlı Mahalle Haritası & Radarı", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("Kadıköy / Moda Çevresinde 3 km", fontSize = 11.sp, color = Slate500)
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("btn_map_back")
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFE2E8F0)) // Clean map background
        ) {
            // Interactive Canvas Map Grid & Radar simulation
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            // Deselect target if clicking empty map
                            selectedTarget = null
                        }
                    }
            ) {
                val cx = size.width / 2f
                val cy = size.height / 2.3f

                // Draw map grid lines & neighborhood roads
                val roadColor = Color(0xFFCBD5E1)
                for (i in -4..4) {
                    val y = cy + (i * 110f)
                    drawLine(color = roadColor, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 5f)
                    val x = cx + (i * 110f)
                    drawLine(color = roadColor, start = Offset(x, 0f), end = Offset(x, size.height), strokeWidth = 5f)
                }

                // Radar rings
                drawCircle(color = TealPrimary.copy(alpha = pulseAlpha), radius = pulseRadius, center = Offset(cx, cy), style = Stroke(width = 3f))
                drawCircle(color = TealPrimary.copy(alpha = 0.15f), radius = 120f, center = Offset(cx, cy), style = Stroke(width = 1.5f))
                drawCircle(color = TealPrimary.copy(alpha = 0.1f), radius = 240f, center = Offset(cx, cy), style = Stroke(width = 1f))

                // User Location Dot
                drawCircle(color = Color.White, radius = 10f, center = Offset(cx, cy))
                drawCircle(color = TealPrimary, radius = 7f, center = Offset(cx, cy))
            }

            // Top Filter Chips on the Map
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == "ALL",
                    onClick = { selectedFilter = "ALL" },
                    label = { Text("Tümü (${providers.size + requests.size})", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = selectedFilter == "EMERGENCY",
                    onClick = { selectedFilter = "EMERGENCY" },
                    label = { Text("🚨 Acil Nöbetçi", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = EmergencyRed, selectedLabelColor = Color.White)
                )
                FilterChip(
                    selected = selectedFilter == "PROVIDERS",
                    onClick = { selectedFilter = "PROVIDERS" },
                    label = { Text("🛠️ Ustalar", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = selectedFilter == "REQUESTS",
                    onClick = { selectedFilter = "REQUESTS" },
                    label = { Text("📢 Talepler", fontSize = 11.sp) }
                )
            }

            // Floating Custom Map Pins
            // Provider 1 (İbrahim Usta - Boyacı)
            if (selectedFilter in listOf("ALL", "PROVIDERS")) {
                MapPinOverlay(
                    xOffset = -90,
                    yOffset = -110,
                    icon = Icons.Default.Brush,
                    color = TealPrimary,
                    label = "İbrahim Usta (Boyacı)",
                    onClick = {
                        val prov = providers.find { it.id == "p-boyaci-ibrahim" } ?: providers.first()
                        selectedTarget = MapTarget.Provider(prov)
                    }
                )
            }

            // Provider 2 (Palyaço Pıtırcık)
            if (selectedFilter in listOf("ALL", "PROVIDERS")) {
                MapPinOverlay(
                    xOffset = 110,
                    yOffset = -80,
                    icon = Icons.Default.SentimentVerySatisfied,
                    color = FestiveCoral,
                    label = "Palyaço Pıtırcık",
                    onClick = {
                        val prov = providers.find { it.id == "p-palyaco-pitircik" } ?: providers.first()
                        selectedTarget = MapTarget.Provider(prov)
                    }
                )
            }

            // Provider 3 (Acil Tesisatçı Selim Usta)
            if (selectedFilter in listOf("ALL", "EMERGENCY", "PROVIDERS")) {
                MapPinOverlay(
                    xOffset = 60,
                    yOffset = 80,
                    icon = Icons.Default.Plumbing,
                    color = EmergencyRed,
                    label = "🚨 Selim Usta (Acil)",
                    onClick = {
                        val prov = providers.find { it.id == "p-tesisatci-selim" } ?: providers.first()
                        selectedTarget = MapTarget.Provider(prov)
                    }
                )
            }

            // Request Pin 1 (Moda 3+1 Boya Talebi)
            if (selectedFilter in listOf("ALL", "REQUESTS")) {
                MapPinOverlay(
                    xOffset = -120,
                    yOffset = 60,
                    icon = Icons.Default.Campaign,
                    color = FestiveAmber,
                    label = "Boya Talebi (Moda)",
                    onClick = {
                        val req = requests.firstOrNull()
                        if (req != null) selectedTarget = MapTarget.Request(req)
                    }
                )
            }

            // Selected Pin Bottom Sheet Preview Card
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
                            val prov = target.provider
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(prov.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Text(prov.title, fontSize = 12.sp, color = Slate600)
                                        Text("📍 ${prov.district} • 0.8 km mesafede", fontSize = 11.sp, color = TealPrimary, fontWeight = FontWeight.SemiBold)
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
                                        onClick = { onChatWithProvider(prov) },
                                        modifier = Modifier.weight(1f).testTag("btn_map_chat")
                                    ) {
                                        Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Mesaj At")
                                    }

                                    Button(
                                        onClick = { onProviderClick(prov.id) },
                                        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                                        modifier = Modifier.weight(1f).testTag("btn_map_view_profile")
                                    ) {
                                        Text("Profili Gör")
                                    }
                                }
                            }
                        }
                        is MapTarget.Request -> {
                            val req = target.request
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(req.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Text("Müşteri: ${req.customerName} • 📍 ${req.district}", fontSize = 12.sp, color = Slate600)
                                    }
                                    IconButton(onClick = { selectedTarget = null }) {
                                        Icon(Icons.Default.Close, contentDescription = "Kapat")
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Button(
                                    onClick = { onChatForJobRequest(req) },
                                    colors = ButtonDefaults.buttonColors(containerColor = FestiveCoral),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.LocalOffer, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Bu Talebe Fiyat Teklifi Ver")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.MapPinOverlay(
    xOffset: Int,
    yOffset: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 4.dp,
        modifier = Modifier
            .align(Alignment.Center)
            .offset(x = xOffset.dp, y = yOffset.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("map_pin_$label")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate800)
        }
    }
}
