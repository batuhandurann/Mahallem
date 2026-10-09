package com.batuhanduran.burada.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.batuhanduran.burada.data.local.JobRequestEntity
import com.batuhanduran.burada.data.local.QuoteEntity
import com.batuhanduran.burada.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyRequestsScreen(
    requests: List<JobRequestEntity>,
    quotes: List<QuoteEntity>,
    onBackClick: () -> Unit,
    onAcceptQuote: (requestId: String, quoteId: String, providerName: String) -> Unit,
    onAcceptWithEscrow: (quote: QuoteEntity, request: JobRequestEntity) -> Unit = { _, _ -> },
    onViewReceipt: (QuoteEntity) -> Unit = {},
    onRejectQuote: (quoteId: String) -> Unit,
    onNewRequestClick: () -> Unit
) {
    BackHandler { onBackClick() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Taleplerim ve Teklifler", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("btn_requests_back")
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        if (requests.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Inbox,
                        contentDescription = null,
                        tint = Slate500,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Henüz Bir Hizmet Talebiniz Yok",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Ev tadilatı veya doğum günü organizasyonu için hemen ücretsiz teklif isteyebilirsiniz.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate500
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onNewRequestClick) {
                        Text("Yeni Talep Oluştur")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(requests, key = { it.id }) { req ->
                    val requestQuotes = quotes.filter { it.requestId == req.id }
                    RequestItemCard(
                        request = req,
                        quotes = requestQuotes,
                        onAcceptQuote = { qId, pName -> onAcceptQuote(req.id, qId, pName) },
                        onAcceptWithEscrow = { q -> onAcceptWithEscrow(q, req) },
                        onViewReceipt = onViewReceipt,
                        onRejectQuote = onRejectQuote
                    )
                }
            }
        }
    }
}

@Composable
private fun RequestItemCard(
    request: JobRequestEntity,
    quotes: List<QuoteEntity>,
    onAcceptQuote: (String, String) -> Unit,
    onAcceptWithEscrow: (QuoteEntity) -> Unit,
    onViewReceipt: (QuoteEntity) -> Unit,
    onRejectQuote: (String) -> Unit
) {
    val isRenovation = request.sector == "HOME_REPAIR"
    val isEmergency = request.urgencyMode == "EMERGENCY"

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        border = BorderStroke(1.dp, SurfaceCardBorder),
        modifier = Modifier.fillMaxWidth().testTag("request_item_${request.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Category, Status, Urgency
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isRenovation) Icons.Default.Handyman else Icons.Default.Celebration,
                        contentDescription = null,
                        tint = if (isRenovation) TealPrimary else FestiveCoral,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isRenovation) "Ev & Tadilat" else "Eğlence & Organizasyon",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isRenovation) TealPrimary else FestiveCoral
                    )

                    if (isEmergency) {
                        Spacer(modifier = Modifier.width(6.dp))
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

                // Status chip
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (request.status) {
                        "ACCEPTED" -> SafeBadgeGreenContainer
                        "QUOTED" -> FestiveAmberLight
                        else -> Slate100
                    }
                ) {
                    Text(
                        text = when {
                            request.visibility == "archived" -> "Kaldırıldı"
                            request.visibility == "closed" -> "Kapalı"
                            request.visibility != "published" -> "Gizli"
                            request.status == "COMPLETED" -> "Tamamlandı"
                            request.status == "CANCELLED" -> "İptal edildi"
                            request.status == "ACCEPTED" -> "Usta Onaylandı"
                            request.status == "QUOTED" -> "${quotes.size} Teklif Geldi"
                            else -> "Teklif Bekleniyor"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (request.status) {
                            "ACCEPTED" -> SafeBadgeText
                            "QUOTED" -> Color(0xFF92400E)
                            else -> Slate700
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Title
            Text(
                text = request.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Specific Details
            if (isRenovation) {
                Text(
                    text = "📐 ${request.areaSquareMeters} m² • ${request.roomCount} • ${if (request.isFurnished) "Eşyalı Daire" else "Boş Daire"} • ${if (request.materialsIncluded) "Malzeme Dahil" else "Sadece İşçilik"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate600
                )
            } else {
                Text(
                    text = "🎪 ${request.eventType} • ${request.durationHours} Saat • ${request.targetAgeGroup} • Kostüm: ${request.selectedCostumeOrCharacter}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate600
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = Slate500, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${request.eventOrJobDate} Saat: ${request.eventTime} • ${request.district}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Slate500
                )
            }

            // Quotes Section
            if (request.visibility != "published") {
                Spacer(modifier = Modifier.height(10.dp))
                Text("Bu ilan yeni teklif ve kabul işlemlerine kapalı. Geçmiş teklifler aşağıda korunur.",
                    style = MaterialTheme.typography.bodySmall, color = Slate600)
            }
            if (quotes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = SurfaceCardBorder)
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Gelen Teklifler (${quotes.size})",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )

                Spacer(modifier = Modifier.height(8.dp))

                quotes.forEach { quote ->
                    QuoteCardView(
                        quote = quote,
                        isRequestAccepted = request.visibility != "published" || request.status !in listOf("PENDING", "QUOTED"),
                        onAccept = { onAcceptQuote(quote.id, quote.providerName) },
                        onAcceptWithEscrow = { onAcceptWithEscrow(quote) },
                        onViewReceipt = { onViewReceipt(quote) },
                        onReject = { onRejectQuote(quote.id) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

/** A rejected, withdrawn or accepted offer must never expose payment/acceptance controls. */
internal fun mayActOnQuote(status: String, requestAlreadyAccepted: Boolean): Boolean =
    status == "PENDING" && !requestAlreadyAccepted

@Composable
private fun QuoteCardView(
    quote: QuoteEntity,
    isRequestAccepted: Boolean,
    onAccept: () -> Unit,
    onAcceptWithEscrow: () -> Unit = {},
    onViewReceipt: () -> Unit = {},
    onReject: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (quote.status == "ACCEPTED") SafeBadgeGreenContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth().testTag("quote_card_${quote.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(TealPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = quote.providerName.take(1),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = quote.providerName,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = StarGold, modifier = Modifier.size(13.dp))
                            Text(" ${quote.providerRating}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Text(
                    text = quote.price,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TealPrimary
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "⏱️ ${quote.durationOrArrival}",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = Slate700
            )

            Text(
                text = quote.notes,
                style = MaterialTheme.typography.bodySmall,
                color = Slate600,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (quote.status == "ACCEPTED") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SafeBadgeGreen,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Teklif Onaylandı • Burada Güvencesi Aktif",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onViewReceipt,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("btn_view_receipt_${quote.id}")
                    ) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("📄 Dijital İş Fişi & Garantiyi İncele", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else if (quote.status == "REJECTED" || quote.status == "WITHDRAWN") {
                Text(
                    text = if (quote.status == "WITHDRAWN") "Hizmet veren teklifini geri çekti" else "Teklif reddedildi",
                    style = MaterialTheme.typography.labelMedium,
                    color = Slate600,
                    modifier = Modifier.testTag("quote_terminal_status_${quote.id}")
                )
            } else if (mayActOnQuote(quote.status, isRequestAccepted)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = onAccept,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_accept_quote_${quote.id}")
                    ) {
                        Text("Teklifi Kabul Et", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Teklifi kabul etmek ödeme yapmaz ve para bloke etmez.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Slate600
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onReject) {
                            Text("Reddet", color = Slate500, fontSize = 12.sp)
                        }
                        TextButton(
                            onClick = onAcceptWithEscrow,
                            modifier = Modifier.testTag("btn_escrow_info_${quote.id}")
                        ) {
                            Text("Ödeme bilgisi", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
