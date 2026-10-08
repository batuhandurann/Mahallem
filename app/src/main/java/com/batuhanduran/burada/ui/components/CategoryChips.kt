package com.batuhanduran.burada.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.batuhanduran.burada.data.model.APP_CATEGORIES
import com.batuhanduran.burada.data.model.Category
import com.batuhanduran.burada.data.model.SectorType
import com.batuhanduran.burada.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryChips(
    selectedSector: SectorType,
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit
) {
    val visibleCategories = APP_CATEGORIES.filter {
        selectedSector == SectorType.ALL || it.sector == selectedSector
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // "Hepsi" chip
            FilterChip(
                selected = selectedCategory == null,
                onClick = { onCategorySelected(null) },
                label = {
                    Text(
                        text = "Tüm Kategoriler",
                        fontWeight = if (selectedCategory == null) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 12.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Apps,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                modifier = Modifier.testTag("chip_cat_all")
            )

            visibleCategories.forEach { category ->
                val isSelected = selectedCategory == category.id
                FilterChip(
                    selected = isSelected,
                    onClick = { onCategorySelected(category.id) },
                    label = {
                        Text(
                            text = category.name,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = getCategoryIcon(category.iconName),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isSelected) Color.White else getCategoryColor(category.sector)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = getCategoryColor(category.sector),
                        selectedLabelColor = Color.White
                    ),
                    modifier = Modifier.testTag("chip_cat_${category.id}")
                )
            }
        }
    }
}

fun getCategoryIcon(iconName: String): ImageVector {
    return when (iconName) {
        "brush" -> Icons.Default.Brush
        "plumbing" -> Icons.Default.Plumbing
        "local_shipping" -> Icons.Default.LocalShipping
        "bolt" -> Icons.Default.Bolt
        "sentiment_very_satisfied" -> Icons.Default.Celebration
        "theater_comedy" -> Icons.Default.TheaterComedy
        "auto_awesome" -> Icons.Default.AutoAwesome
        "palette" -> Icons.Default.ColorLens
        "music_note" -> Icons.Default.MusicNote
        else -> Icons.Default.Handyman
    }
}

fun getCategoryColor(sector: SectorType): Color {
    return when (sector) {
        SectorType.HOME_REPAIR -> TealPrimary
        SectorType.CLEANING -> Color(0xFF0284C7)
        SectorType.EVENT_ENTERTAINMENT -> FestiveCoral
        SectorType.MOVING_ASSEMBLY -> FestiveAmber
        SectorType.TUTORING_CONSULTING -> Color(0xFF6366F1)
        SectorType.PERSONAL_CARE -> Color(0xFFEC4899)
        SectorType.ALL -> Slate700
    }
}
