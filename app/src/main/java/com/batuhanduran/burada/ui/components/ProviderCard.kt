package com.batuhanduran.burada.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.batuhanduran.burada.data.local.ServiceProviderEntity
import com.batuhanduran.burada.ui.theme.*

@Composable
fun ProviderCard(
    provider: ServiceProviderEntity,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onRequestQuoteClick: () -> Unit,
    onChatClick: () -> Unit,
    onReportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRenovation = provider.sector == "HOME_REPAIR"
    val brandList = parseJsonList(provider.paintBrandsJson)
    val characterList = parseJsonList(provider.charactersOfferedJson)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("provider_card_${provider.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, SurfaceCardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Avatar, Name, Badges, Favorite & Report
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar Icon
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(
                                if (isRenovation)
                                    Brush.linearGradient(listOf(TealPrimary, TealDark))
                                else
                                    Brush.linearGradient(listOf(FestiveCoral, FestiveAmber))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isRenovation) Icons.Default.Handyman else Icons.Default.Celebration,
                            contentDescription = provider.name,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = provider.name,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (provider.verifiedSafeBadge) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = "Onaylı Hizmet Veren",
                                    tint = if (isRenovation) TealPrimary else SafeBadgeGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Text(
                            text = provider.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate600,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Rating & Location
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Yıldız",
                                tint = StarGold,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = " ${provider.rating}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = " (${provider.reviewCount}) • ${provider.experienceYears} Yıl",
                                style = MaterialTheme.typography.labelSmall,
                                color = Slate500
                            )
                        }
                    }
                }

                // Action icons: Favorite & Report
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onFavoriteToggle,
                        modifier = Modifier.size(36.dp).testTag("btn_fav_${provider.id}")
                    ) {
                        Icon(
                            imageVector = if (provider.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Favoriye Ekle",
                            tint = if (provider.isFavorite) FestiveCoral else Slate500,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onReportClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Şikayet Et",
                            tint = Slate500,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Safety & Verification Badges (Sabıka Kaydı Temiz, MYK Belgesi, Telefon Onayı)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (provider.phoneVerified) {
                    BadgeChip(
                        icon = Icons.Default.PhoneAndroid,
                        text = "Tel Doğrulandı",
                        bgColor = SafeBadgeGreenContainer,
                        textColor = SafeBadgeText
                    )
                }

                if (provider.childSafeCertified) {
                    BadgeChip(
                        icon = Icons.Default.ChildCare,
                        text = "Çocuk Dostu",
                        bgColor = FestiveAmberLight,
                        textColor = Color(0xFF92400E)
                    )
                }

                if (provider.mykCertified) {
                    BadgeChip(
                        icon = Icons.Default.WorkspacePremium,
                        text = "MYK Usta",
                        bgColor = TealContainer,
                        textColor = OnTealContainer
                    )
                }

                if (provider.isEmergencyAvailable) {
                    BadgeChip(
                        icon = Icons.Default.Bolt,
                        text = "🚨 Acil",
                        bgColor = EmergencyRedContainer,
                        textColor = EmergencyRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bio / Description
            Text(
                text = provider.bio,
                style = MaterialTheme.typography.bodySmall,
                color = Slate700,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )

            // Dynamic tags depending on category:
            if (brandList.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Markalar:",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Slate600
                    )
                    brandList.take(3).forEach { brand ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Slate100,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        ) {
                            Text(
                                text = brand,
                                style = MaterialTheme.typography.labelSmall,
                                color = Slate700,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            } else if (characterList.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Karakterler:",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = FestiveCoral
                    )
                    characterList.take(2).forEach { char ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = FestiveCoralLight,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        ) {
                            Text(
                                text = char,
                                style = MaterialTheme.typography.labelSmall,
                                color = FestiveCoral,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = SurfaceCardBorder, thickness = 0.8.dp)
            Spacer(modifier = Modifier.height(10.dp))

            // Footer: Location & Price & Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            tint = Slate500,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = provider.district,
                            style = MaterialTheme.typography.labelSmall,
                            color = Slate600
                        )
                    }

                    Text(
                        text = provider.hourlyOrBasePrice,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        ),
                        color = if (isRenovation) TealPrimary else FestiveCoral
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // In-App Chat button (Letgo / Sahibinden style)
                    OutlinedButton(
                        onClick = onChatClick,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.heightIn(min = 40.dp).testTag("btn_chat_${provider.id}")
                    ) {
                        Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Mesaj", fontSize = 11.5.sp)
                    }

                    // Request Quote button (Armut style)
                    Button(
                        onClick = onRequestQuoteClick,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRenovation) TealPrimary else FestiveCoral
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.heightIn(min = 40.dp).testTag("btn_request_quote_${provider.id}")
                    ) {
                        Icon(Icons.Default.LocalOffer, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Teklif Al", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun BadgeChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    bgColor: Color,
    textColor: Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = textColor
            )
        }
    }
}

fun parseJsonList(json: String): List<String> {
    return try {
        val cleaned = json.replace("[", "").replace("]", "").replace("\"", "")
        if (cleaned.isBlank()) emptyList() else cleaned.split(",").map { it.trim() }
    } catch (e: Exception) {
        emptyList()
    }
}
