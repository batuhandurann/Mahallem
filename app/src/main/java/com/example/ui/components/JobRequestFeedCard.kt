package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.AppEnvironment
import com.example.data.local.JobRequestEntity
import com.example.ui.theme.*

@Composable
fun JobRequestFeedCard(
    request: JobRequestEntity,
    onSendOfferClick: () -> Unit,
    onChatClick: () -> Unit,
    onReportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isEmergency = request.urgencyMode == "EMERGENCY"

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        border = BorderStroke(1.dp, SurfaceCardBorder),
        modifier = modifier
            .fillMaxWidth()
            .testTag("feed_request_card_${request.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Seeker badge, Verification, Expiration countdown, Report
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = FestiveCoralLight
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📢 HİZMET ARANIYOR",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = FestiveCoral
                            )
                        }
                    }

                    if (isEmergency) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = EmergencyRedContainer
                        ) {
                            Text(
                                text = "🚨 ACİL",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmergencyRed,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Days remaining & report
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Slate100
                    ) {
                        Text(
                            text = "⏱️ ${request.daysRemaining} gün kaldı",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Slate600,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }

                    IconButton(
                        onClick = onReportClick,
                        modifier = Modifier.size(28.dp).padding(start = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Seçenekler",
                            tint = Slate500,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Title
            Text(
                text = request.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Customer verification info
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = request.customerName,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Slate700
                )
                if (request.phoneVerified) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = SafeBadgeGreenContainer
                    ) {
                        Text(
                            text = if (AppEnvironment.isLocal) "✓ Tel Doğrulandı • Demo" else "✓ Tel Doğrulandı",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = SafeBadgeText,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Details depending on request
            if (request.sector == "HOME_REPAIR" && request.areaSquareMeters > 0) {
                Text(
                    text = "📐 ${request.areaSquareMeters} m² • ${request.roomCount} • ${if (request.isFurnished) "Eşyalı" else "Boş Daire"} • ${if (request.materialsIncluded) "Malzeme Dahil" else "Sadece İşçilik"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate600
                )
            } else if (request.eventType.isNotBlank()) {
                Text(
                    text = "🎪 ${request.eventType} • ${request.durationHours} Saat • ${request.targetAgeGroup} • ${request.selectedCostumeOrCharacter}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate600
                )
            }

            if (request.renovationNotes.isNotBlank()) {
                Text(
                    text = "\"${request.renovationNotes}\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate500,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 2
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = SurfaceCardBorder, thickness = 0.8.dp)
            Spacer(modifier = Modifier.height(10.dp))

            // Footer: Date & Location & CTA
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Place, contentDescription = null, tint = Slate500, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(text = request.district, fontSize = 11.sp, color = Slate600)
                    }
                    Text(
                        text = "Tarih: ${request.eventOrJobDate} ${request.eventTime}",
                        fontSize = 11.sp,
                        color = TealPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Chat button (Letgo style)
                    OutlinedButton(
                        onClick = onChatClick,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.heightIn(min = 40.dp)
                    ) {
                        Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Mesaj At", fontSize = 11.5.sp)
                    }

                    // Offer button (Armut style)
                    Button(
                        onClick = onSendOfferClick,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = FestiveCoral),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.heightIn(min = 40.dp)
                    ) {
                        Icon(Icons.Default.LocalOffer, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Teklif Ver", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
