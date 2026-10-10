package com.batuhanduran.burada.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.batuhanduran.burada.data.local.JobRequestEntity
import com.batuhanduran.burada.data.local.ServiceProviderEntity
import com.batuhanduran.burada.ui.components.AvailabilityCalendarView
import com.batuhanduran.burada.ui.theme.*
import com.batuhanduran.burada.validation.quoteDraftError

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderDashboardScreen(
    providers: List<ServiceProviderEntity>,
    requests: List<JobRequestEntity>,
    onBackClick: () -> Unit,
    onToggleOffers: (providerId: String, currentStatus: Boolean) -> Unit,
    onToggleCalendarDate: (provider: ServiceProviderEntity, dateIso: String) -> Unit,
    onSubmitQuote: (requestId: String, provider: ServiceProviderEntity, price: String, arrival: String, notes: String, onResult: (Boolean) -> Unit) -> Unit,
    onOpenMyJobs: () -> Unit = {},
    quoteComposer: ProviderQuoteComposerState? = null
) {
    BackHandler { onBackClick() }

    var selectedProviderIndex by remember { mutableStateOf(0) }
    val composer = quoteComposer ?: remember { ProviderQuoteComposerState() }
    val currentProv = if (composer.showDialog) providers.find { it.id == composer.providerId }
        else providers.getOrNull(selectedProviderIndex) ?: providers.firstOrNull()
    val showQuoteDialogForRequest = requests.find { it.id == composer.requestId }.takeIf { composer.showDialog }
    var quotePriceInput by composer::price
    var quoteArrivalInput by composer::arrival
    var quoteNoteInput by composer::notes
    val quoteError = quoteDraftError(quotePriceInput, quoteArrivalInput, quoteNoteInput)

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
                actions = { TextButton(onClick = onOpenMyJobs, modifier = Modifier.testTag("btn_my_jobs")) { Text("İşlerim") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        if (currentProv == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp)
                    .testTag("provider_dashboard_empty"),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Henüz hizmet veren ilanınız yok",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Hizmet ilanı yayınladıktan sonra teklif ve müsaitlik yönetimini buradan yapabilirsiniz.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate600
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("btn_provider_dashboard_return_home")
                    ) { Text("Ana sayfaya dön") }
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("provider_dashboard_list"),
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

                        if (req.sector == "HOME_REPAIR") {
                            Text(
                                text = "Alan: ${req.areaSquareMeters} m² • ${req.roomCount} • ${if (req.isFurnished) "Eşyalı" else "Boş"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Slate500
                            )
                        } else if (req.sector == "EVENT_ENTERTAINMENT") {
                            Text(
                                text = "Etkinlik: ${req.eventType} • ${req.durationHours} Saat • ${req.targetAgeGroup} • Kostüm: ${req.selectedCostumeOrCharacter}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Slate500
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                composer.open(req.id, currentProv.id)
                            },
                            enabled = !composer.isSending,
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
            onDismissRequest = { if (!composer.isSending) composer.showDialog = false },
            title = { Text("Teklif Ver: ${prov.name}") },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    if (req.customerName.isNotBlank()) {
                        Text("Müşteri: ${req.customerName}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Text(
                        "Fiyatı ve sağlayabileceğiniz süreyi kendiniz belirleyin; varsayılan bir taahhüt oluşturulmaz.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate600
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = quotePriceInput,
                        onValueChange = { quotePriceInput = it },
                        label = { Text("Teklif Fiyatınız") },
                        placeholder = { Text("Örn. 1.250,50 TL") },
                        singleLine = true,
                        enabled = !composer.isSending,
                        modifier = Modifier.fillMaxWidth().testTag("input_quote_price")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = quoteArrivalInput,
                        onValueChange = { quoteArrivalInput = it },
                        label = { Text("Varış Süresi / Süre") },
                        placeholder = { Text("Örn. Yarın 14.00") },
                        singleLine = true,
                        enabled = !composer.isSending,
                        modifier = Modifier.fillMaxWidth().testTag("input_quote_arrival")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = quoteNoteInput,
                        onValueChange = { quoteNoteInput = it },
                        label = { Text("Açıklama & Dahil Olanlar") },
                        enabled = !composer.isSending,
                        modifier = Modifier.fillMaxWidth().testTag("input_quote_notes")
                    )
                    if (quoteError != null) {
                        Text(
                            text = quoteError,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("quote_validation_error")
                        )
                    }
                    composer.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("quote_send_error"))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (quoteError == null && !composer.isSending) {
                            composer.isSending = true
                            composer.error = null
                            onSubmitQuote(
                                req.id,
                                prov,
                                quotePriceInput.trim(),
                                quoteArrivalInput.trim(),
                                quoteNoteInput.trim(),
                                composer::complete
                            )
                        }
                    },
                    enabled = quoteError == null && !composer.isSending,
                    modifier = Modifier.testTag("btn_confirm_send_quote")
                ) {
                    Text(if (composer.isSending) "Gönderiliyor…" else "Teklifi Gönder")
                }
            },
            dismissButton = {
                TextButton(onClick = { composer.showDialog = false }, enabled = !composer.isSending) {
                    Text("Vazgeç")
                }
            }
        )
    }
}
