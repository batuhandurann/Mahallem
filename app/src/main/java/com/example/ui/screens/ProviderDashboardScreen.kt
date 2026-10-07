package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.JobRequestEntity
import com.example.data.local.ServiceProviderEntity
import com.example.ui.components.AvailabilityCalendarView
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderDashboardScreen(
    providers: List<ServiceProviderEntity>,
    requests: List<JobRequestEntity>,
    onBackClick: () -> Unit,
    onToggleOffers: (providerId: String, currentStatus: Boolean) -> Unit,
    onToggleCalendarDate: (provider: ServiceProviderEntity, dateIso: String) -> Unit,
    onSubmitQuote: (requestId: String, provider: ServiceProviderEntity, price: String, arrival: String, notes: String) -> Unit
) {
    BackHandler { onBackClick() }

    var selectedProviderIndex by remember { mutableStateOf(0) }
    val currentProv = providers.getOrNull(selectedProviderIndex) ?: providers.firstOrNull()

    var showQuoteDialogForRequest by remember { mutableStateOf<JobRequestEntity?>(null) }
    var quotePriceInput by remember { mutableStateOf("") }
    var quoteArrivalInput by remember { mutableStateOf("") }
    var quoteNoteInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Esnaf & Sanatçı Paneli", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Müsaitlik ve Teklif Yönetimi", fontSize = 11.sp, color = Slate500)
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("btn_provider_dashboard_back")
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        if (currentProv == null) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile Persona Switcher
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Profil Yönetimi (Aktif Esnaf / Sanatçı)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Slate500
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            providers.take(3).forEachIndexed { index, prov ->
                                val isSelected = index == selectedProviderIndex
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) TealPrimary else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { selectedProviderIndex = index }
                                        .padding(vertical = 2.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = prov.name.split(" ").first(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = if (isSelected) Color.White else Slate700
                                        )
                                        Text(
                                            text = prov.title.split("&").first().trim(),
                                            fontSize = 10.sp,
                                            color = if (isSelected) TealContainer else Slate500,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Calendar & Availability Controls
            item {
                Column {
                    Text(
                        text = "📅 Takvim & Müsaitlik Yönetimi",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Hafta sonu veya belirli günleri 'Dolu' olarak işaretleyebilir veya teklif alımını kapatabilirsiniz.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate500
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    AvailabilityCalendarView(
                        bookedDatesJson = currentProv.bookedDatesJson,
                        isOpenForOffers = currentProv.isOpenForOffers,
                        isEditable = true,
                        onDateToggle = { dateIso ->
                            onToggleCalendarDate(currentProv, dateIso)
                        },
                        onToggleOpenForOffers = {
                            onToggleOffers(currentProv.id, currentProv.isOpenForOffers)
                        }
                    )
                }
            }

            // Incoming Neighborhood Requests
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📢 Mahalledeki Yeni Talepler",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${requests.size} Talep Açık",
                        style = MaterialTheme.typography.labelSmall,
                        color = TealPrimary
                    )
                }
            }

            items(requests, key = { it.id }) { req ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth().testTag("provider_req_item_${req.id}")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = req.title,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.weight(1f)
                            )
                            if (req.urgencyMode == "EMERGENCY") {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = EmergencyRedContainer
                                ) {
                                    Text(
                                        text = "🚨 ACİL",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EmergencyRed,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "📍 ${req.district} • Tarih: ${req.eventOrJobDate} ${req.eventTime}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate600
                        )

                        if (req.sector == "HOME_RENOVATION") {
                            Text(
                                text = "Alan: ${req.areaSquareMeters} m² • ${req.roomCount} • ${if (req.isFurnished) "Eşyalı" else "Boş"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Slate500
                            )
                        } else {
                            Text(
                                text = "Etkinlik: ${req.eventType} • ${req.durationHours} Saat • ${req.targetAgeGroup} • Kostüm: ${req.selectedCostumeOrCharacter}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Slate500
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                showQuoteDialogForRequest = req
                                quotePriceInput = if (req.sector == "HOME_RENOVATION") "9.500 ₺" else "2.800 ₺"
                                quoteArrivalInput = if (req.urgencyMode == "EMERGENCY") "45 dakikada kapınızdayım" else "Belirtilen gün ve saatte hazırım"
                                quoteNoteInput = "Merhabalar, işinizi özenle ve garantili yapabilirim."
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("btn_give_quote_${req.id}")
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Bu Talebe Fiyat Teklifi Gönder")
                        }
                    }
                }
            }
        }
    }

    // Modal Dialog to submit a custom quote
    showQuoteDialogForRequest?.let { req ->
        val prov = currentProv ?: return@let
        AlertDialog(
            onDismissRequest = { showQuoteDialogForRequest = null },
            title = { Text("Teklif Ver: ${prov.name}") },
            text = {
                Column {
                    Text("Müşteri: ${req.customerName}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = quotePriceInput,
                        onValueChange = { quotePriceInput = it },
                        label = { Text("Teklif Fiyatınız") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_quote_price")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = quoteArrivalInput,
                        onValueChange = { quoteArrivalInput = it },
                        label = { Text("Varış Süresi / Süre") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_quote_arrival")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = quoteNoteInput,
                        onValueChange = { quoteNoteInput = it },
                        label = { Text("Açıklama & Dahil Olanlar") },
                        modifier = Modifier.fillMaxWidth().testTag("input_quote_notes")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSubmitQuote(
                            req.id,
                            prov,
                            quotePriceInput,
                            quoteArrivalInput,
                            quoteNoteInput
                        )
                        showQuoteDialogForRequest = null
                    },
                    modifier = Modifier.testTag("btn_confirm_send_quote")
                ) {
                    Text("Teklifi Gönder")
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuoteDialogForRequest = null }) {
                    Text("Vazgeç")
                }
            }
        )
    }
}
