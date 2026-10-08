package com.batuhanduran.burada.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import com.batuhanduran.burada.data.model.APP_CATEGORIES
import com.batuhanduran.burada.data.model.Category
import com.batuhanduran.burada.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CostEstimatorSheet(
    onDismiss: () -> Unit,
    onCreateRequestWithBudget: (category: Category, budget: String, note: String) -> Unit
) {
    var selectedCategory by remember { mutableStateOf(APP_CATEGORIES.first()) }
    var areaSquareMeters by remember { mutableStateOf(95f) }
    var durationHours by remember { mutableStateOf(2f) }
    var isMaterialIncluded by remember { mutableStateOf(true) }
    var isFurnishedOrSpecial by remember { mutableStateOf(true) }

    // Dynamic cost calculation logic
    val (lowPrice, avgPrice, highPrice) = remember(
        selectedCategory.id,
        areaSquareMeters,
        durationHours,
        isMaterialIncluded,
        isFurnishedOrSpecial
    ) {
        when (selectedCategory.id) {
            "boyaci" -> {
                val base = areaSquareMeters * 90
                val mat = if (isMaterialIncluded) areaSquareMeters * 45 else 0f
                val furn = if (isFurnishedOrSpecial) 1500f else 0f
                val total = base + mat + furn
                Triple((total * 0.85f).toInt(), total.toInt(), (total * 1.25f).toInt())
            }
            "palyaco", "maskot" -> {
                val base = durationHours * 1100f
                val extra = if (isMaterialIncluded) 400f else 0f
                val total = base + extra
                Triple((total * 0.9f).toInt(), total.toInt(), (total * 1.25f).toInt())
            }
            "ev_temizligi" -> {
                val base = 1200f + (areaSquareMeters * 5.5f)
                val total = if (isFurnishedOrSpecial) base * 1.2f else base
                Triple((total * 0.85f).toInt(), total.toInt(), (total * 1.2f).toInt())
            }
            "tesisatci" -> {
                val base = if (isMaterialIncluded) 1500f else 950f
                Triple((base * 0.85f).toInt(), base.toInt(), (base * 1.3f).toInt())
            }
            "nakliyeci" -> {
                val base = 6500f + (areaSquareMeters * 35f)
                Triple((base * 0.9f).toInt(), base.toInt(), (base * 1.25f).toInt())
            }
            "matematik_ders" -> {
                val base = durationHours * 850f
                Triple((base * 0.9f).toInt(), base.toInt(), (base * 1.2f).toInt())
            }
            else -> Triple(1200, 1800, 2500)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Akıllı Piyasa Fiyat Hesaplayıcı", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Armut & Mahalle Fiyat İndeksi (2026)", fontSize = 11.5.sp, color = Slate500)
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = TealContainer
                ) {
                    Text(
                        text = "📊 Canlı Piyasa",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnTealContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Category Horizontal Chips
            Text("Hizmet Türü", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                APP_CATEGORIES.take(7).forEach { cat ->
                    FilterChip(
                        selected = selectedCategory.id == cat.id,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat.name, fontSize = 12.sp) }
                    )
                }
            }

            // Dynamic Sliders
            if (selectedCategory.id in listOf("boyaci", "ev_temizligi", "nakliyeci")) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Alan Büyüklüğü:", fontSize = 12.5.sp, color = Slate700)
                        Text("${areaSquareMeters.toInt()} m²", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TealPrimary)
                    }
                    Slider(
                        value = areaSquareMeters,
                        onValueChange = { areaSquareMeters = it },
                        valueRange = 40f..250f,
                        steps = 20,
                        modifier = Modifier.testTag("slider_area")
                    )
                }
            } else if (selectedCategory.id in listOf("palyaco", "maskot", "matematik_ders")) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Etkinlik / Ders Süresi:", fontSize = 12.5.sp, color = Slate700)
                        Text("${durationHours.toInt()} Saat", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = FestiveCoral)
                    }
                    Slider(
                        value = durationHours,
                        onValueChange = { durationHours = it },
                        valueRange = 1f..6f,
                        steps = 4,
                        modifier = Modifier.testTag("slider_duration")
                    )
                }
            }

            // Toggle Switches
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectedCategory.id == "boyaci") "Boya Malzemeleri Dahil Olsun" else "Ekstra Paket & Malzemeler Dahil",
                    fontSize = 12.sp
                )
                Switch(checked = isMaterialIncluded, onCheckedChange = { isMaterialIncluded = it })
            }

            // Price Estimation Cards (3 Tiers)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PriceTierCard(
                    title = "Ekonomik",
                    price = "$lowPrice ₺",
                    subtitle = "İşçilik Odaklı",
                    color = Slate600,
                    modifier = Modifier.weight(1f)
                )

                PriceTierCard(
                    title = "Piyasa Ortalaması",
                    price = "$avgPrice ₺",
                    subtitle = "En Çok Tercih",
                    color = TealPrimary,
                    isPopular = true,
                    modifier = Modifier.weight(1.2f)
                )

                PriceTierCard(
                    title = "Premium / Garantili",
                    price = "$highPrice ₺",
                    subtitle = "Malzeme Dahil",
                    color = FestiveCoral,
                    modifier = Modifier.weight(1f)
                )
            }

            // Distribution Breakdown Bar
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Slate100,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("Maliyet Dağılımı Tahmini", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate700)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    ) {
                        Box(modifier = Modifier.weight(0.65f).fillMaxHeight().background(TealPrimary))
                        Box(modifier = Modifier.weight(0.35f).fillMaxHeight().background(FestiveCoral))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("• %65 Usta İşçilik & Zaman", fontSize = 9.5.sp, color = TealDark)
                        Text("• %35 Malzeme & Yol", fontSize = 9.5.sp, color = FestiveCoral)
                    }
                }
            }

            // CTA Button: "Bu Fiyat Aralığından Talep Aç"
            Button(
                onClick = {
                    val budgetStr = "$lowPrice - $avgPrice ₺"
                    val noteStr = "${areaSquareMeters.toInt()} m² alan için tahmini piyasa bütçesiyle hesaplandı."
                    onCreateRequestWithBudget(selectedCategory, budgetStr, noteStr)
                    onDismiss()
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("btn_create_job_from_calculator")
            ) {
                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Bu Fiyattan Talep Yayınla ($avgPrice ₺)", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun PriceTierCard(
    title: String,
    price: String,
    subtitle: String,
    color: Color,
    isPopular: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isPopular) TealContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = if (isPopular) ButtonDefaults.outlinedButtonBorder else null,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
            Spacer(modifier = Modifier.height(3.dp))
            Text(price, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, fontSize = 9.sp, color = Slate500)
        }
    }
}
