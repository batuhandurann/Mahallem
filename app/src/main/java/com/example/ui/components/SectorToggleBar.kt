package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FeedFlowType
import com.example.data.model.SectorType
import com.example.data.model.UrgencyMode
import com.example.ui.theme.*

@Composable
fun SectorToggleBar(
    feedFlowType: FeedFlowType,
    onFeedFlowTypeSelected: (FeedFlowType) -> Unit,
    selectedSector: SectorType,
    onSectorSelected: (SectorType) -> Unit,
    selectedUrgency: UrgencyMode,
    onUrgencySelected: (UrgencyMode) -> Unit,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    searchSuggestions: List<String> = emptyList(),
    onSuggestionSelected: (String) -> Unit = {},
    onOpenMapClick: () -> Unit = {},
    onOpenEstimatorClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // Search Input Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChanged,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_search"),
            placeholder = {
                Text(
                    text = "Boyacı, palyaço, koltuk yıkama, çilingir, özel ders...",
                    fontSize = 13.sp,
                    color = Slate500
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Ara",
                    tint = TealPrimary
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { onSearchQueryChanged("") },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Temizle",
                            tint = Slate500
                        )
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = TealPrimary,
                unfocusedBorderColor = SurfaceCardBorder
            )
        )

        // Instant Autocomplete Search Suggestions (Google / Letgo style)
        if (searchSuggestions.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                shadowElevation = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    searchSuggestions.forEach { suggestion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSuggestionSelected(suggestion) }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = Slate500,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = suggestion,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Dual-Flow Segmented Bar: [Tüm İlanlar] | [🛠️ Usta İlanları (Letgo)] | [📋 Müşteri Talepleri (Armut)]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(3.dp)
        ) {
            FeedFlowSegmentButton(
                title = "Tümü",
                isSelected = feedFlowType == FeedFlowType.ALL,
                onClick = { onFeedFlowTypeSelected(FeedFlowType.ALL) },
                modifier = Modifier.weight(1f)
            )
            FeedFlowSegmentButton(
                title = "Usta İlanları",
                isSelected = feedFlowType == FeedFlowType.PROVIDER_OFFERS,
                onClick = { onFeedFlowTypeSelected(FeedFlowType.PROVIDER_OFFERS) },
                modifier = Modifier.weight(1.3f)
            )
            FeedFlowSegmentButton(
                title = "Talep Panosu",
                isSelected = feedFlowType == FeedFlowType.SEEKER_REQUESTS,
                onClick = { onFeedFlowTypeSelected(FeedFlowType.SEEKER_REQUESTS) },
                modifier = Modifier.weight(1.3f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 6 Sector Horizontal Scrolling Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectorType.values().forEach { sec ->
                val isSelected = selectedSector == sec
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) TealPrimary else MaterialTheme.colorScheme.surface,
                    label = "sector_chip_bg"
                )
                val textColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = bgColor,
                    shadowElevation = if (isSelected) 2.dp else 0.dp,
                    border = if (!isSelected) ButtonDefaults.outlinedButtonBorder else null,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onSectorSelected(sec) }
                        .testTag("chip_sector_${sec.name.lowercase()}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = sec.icon, fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = sec.titleTr,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp
                            ),
                            color = textColor
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Urgency filter pills (Acil vs Planlı)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Mod:",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Slate500,
                modifier = Modifier.padding(end = 4.dp)
            )

            UrgencyMode.values().forEach { mode ->
                val isSelected = selectedUrgency == mode
                val bgColor by animateColorAsState(
                    targetValue = when {
                        isSelected && mode == UrgencyMode.EMERGENCY -> EmergencyRed
                        isSelected && mode == UrgencyMode.PLANNED -> TealPrimary
                        isSelected -> Slate800
                        else -> MaterialTheme.colorScheme.surface
                    }, label = "urgency_pill"
                )
                val textColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface

                Surface(
                    shape = CircleShape,
                    color = bgColor,
                    shadowElevation = if (isSelected) 2.dp else 0.dp,
                    border = if (!isSelected) ButtonDefaults.outlinedButtonBorder else null,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onUrgencySelected(mode) }
                        .testTag("chip_urgency_${mode.name.lowercase()}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = mode.labelTr,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp
                            ),
                            color = textColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Map & Radar button
            Surface(
                shape = CircleShape,
                color = TealContainer,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onOpenMapClick)
                    .testTag("chip_open_map")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🗺️ Harita & Radar", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = OnTealContainer)
                }
            }

            // Cost Estimator button
            Surface(
                shape = CircleShape,
                color = FestiveCoralLight,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onOpenEstimatorClick)
                    .testTag("chip_open_calculator")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📊 Maliyet Hesapla", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FestiveCoral)
                }
            }
        }
    }
}

@Composable
private fun FeedFlowSegmentButton(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
        label = "segment_bg"
    )

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(10.dp),
        shadowElevation = if (isSelected) 2.dp else 0.dp,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 12.sp
                ),
                color = if (isSelected) TealPrimary else Slate600,
                maxLines = 1
            )
        }
    }
}
