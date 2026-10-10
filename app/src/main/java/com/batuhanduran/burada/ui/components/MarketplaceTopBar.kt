package com.batuhanduran.burada.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.batuhanduran.burada.ui.theme.*

val DISTRICT_OPTIONS = listOf(
    "Tüm İlçeler",
    "Kadıköy, İstanbul",
    "Beşiktaş, İstanbul",
    "Üsküdar, İstanbul",
    "Ataşehir, İstanbul",
    "Maltepe, İstanbul",
    "Çankaya, Ankara",
    "Bornova, İzmir",
    "Muratpaşa, Antalya"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketplaceTopBar(
    title: String,
    selectedDistrict: String,
    onDistrictSelected: (String) -> Unit,
    showBackButton: Boolean = false,
    onBackClick: () -> Unit = {},
    isProviderMode: Boolean = false,
    onToggleProviderMode: () -> Unit = {},
    onMyRequestsClick: () -> Unit = {},
    activeRequestsCount: Int = 0,
    onMessagesClick: () -> Unit = {},
    unreadMessagesCount: Int = 0
) {
    var districtMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Back button or App Logo
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showBackButton) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier
                                .testTag("btn_back")
                                .size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Geri Dön",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else {
                        // App Brand Icon - House Logo (Ev Logosu)
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(PrimaryBlue, FestiveCoral)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Yakıno Logosu",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        // District selector chip
                        Box {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { districtMenuExpanded = true }
                                    .padding(vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = "Konum",
                                    tint = TealPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = selectedDistrict,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.sp
                                    ),
                                    color = TealPrimary
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "İlçe Değiştir",
                                    tint = TealPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = districtMenuExpanded,
                                onDismissRequest = { districtMenuExpanded = false }
                            ) {
                                DISTRICT_OPTIONS.forEach { district ->
                                    DropdownMenuItem(
                                        text = { Text(district) },
                                        onClick = {
                                            onDistrictSelected(district)
                                            districtMenuExpanded = false
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Place,
                                                contentDescription = null,
                                                tint = if (district == selectedDistrict) TealPrimary else Slate500
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Right action buttons: Messages, My Requests & Provider Mode Toggle
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Chat Messages Icon with badge (Letgo / Sahibinden style)
                    IconButton(
                        onClick = onMessagesClick,
                        modifier = Modifier
                            .testTag("btn_top_messages")
                            .size(48.dp)
                    ) {
                        BadgedBox(
                            badge = {
                                if (unreadMessagesCount > 0) {
                                    Badge(containerColor = FestiveCoral, contentColor = Color.White) {
                                        Text("$unreadMessagesCount")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Chat,
                                contentDescription = "Mesajlarım",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Requests Icon with badge
                    IconButton(
                        onClick = onMyRequestsClick,
                        modifier = Modifier
                            .testTag("btn_my_requests")
                            .size(48.dp)
                    ) {
                        BadgedBox(
                            badge = {
                                if (activeRequestsCount > 0) {
                                    Badge(
                                        containerColor = FestiveCoral,
                                        contentColor = Color.White
                                    ) {
                                        Text("$activeRequestsCount")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Assignment,
                                contentDescription = "Taleplerim",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Switch role (Esnaf/Usta Paneli vs Müşteri)
                    FilterChip(
                        selected = isProviderMode,
                        onClick = onToggleProviderMode,
                        label = {
                            Text(
                                text = if (isProviderMode) "Esnaf Modu" else "Usta/Sanatçı Ol",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (isProviderMode) Icons.Default.Storefront else Icons.Default.Engineering,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = FestiveCoral,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        ),
                        modifier = Modifier.testTag("btn_toggle_role")
                    )
                }
            }
        }
    }
}
