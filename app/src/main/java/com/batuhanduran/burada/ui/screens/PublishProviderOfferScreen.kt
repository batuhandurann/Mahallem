package com.batuhanduran.burada.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.batuhanduran.burada.data.model.APP_CATEGORIES
import com.batuhanduran.burada.data.model.SectorType
import com.batuhanduran.burada.data.model.NeighborhoodRef
import com.batuhanduran.burada.data.model.GeoCoordinate
import com.batuhanduran.burada.ui.components.CurrentLocationSelector
import com.batuhanduran.burada.ui.components.NeighborhoodSelector
import com.batuhanduran.burada.ui.components.getCategoryIcon
import com.batuhanduran.burada.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PublishProviderOfferScreen(
    onBackClick: () -> Unit,
    onNeighborhoodSelected: (NeighborhoodRef) -> Unit = {},
    onCoordinateSelected: (GeoCoordinate) -> Unit = {},
    onPublish: (
        name: String,
        title: String,
        sector: SectorType,
        categoryId: String,
        district: String,
        price: String,
        phone: String,
        bio: String,
        experienceYears: Int,
        isEmergency: Boolean,
        hasSafeBadge: Boolean,
        hasMykBadge: Boolean,
        hasChildSafeBadge: Boolean,
        brandsOrCharacters: String,
        equipments: String
    ) -> Unit
) {
    BackHandler { onBackClick() }

    var name by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var selectedSector by remember { mutableStateOf(SectorType.HOME_REPAIR) }
    var selectedCategoryId by remember { mutableStateOf(APP_CATEGORIES.first().id) }
    var selectedNeighborhood by remember { mutableStateOf<NeighborhoodRef?>(null) }
    val selectedDistrict = selectedNeighborhood?.districtLabel.orEmpty()
    var selectedCoordinate by remember { mutableStateOf<GeoCoordinate?>(null) }
    var price by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var experienceYears by remember { mutableStateOf("5") }

    var isEmergency by remember { mutableStateOf(false) }
    var hasSafeBadge by remember { mutableStateOf(true) }
    var hasMykBadge by remember { mutableStateOf(false) }
    var hasChildSafeBadge by remember { mutableStateOf(false) }

    var brandsOrCharacters by remember { mutableStateOf("") }
    var equipments by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Hizmet / Usta İlanı Ver", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Akış A • Sahibinden & Letgo Vitrini", fontSize = 11.sp, color = Slate500)
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("btn_publish_back")
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Button(
                    enabled = selectedNeighborhood != null,
                    onClick = {
                        val neighborhood = selectedNeighborhood ?: return@Button
                        onNeighborhoodSelected(neighborhood)
                        selectedCoordinate?.let(onCoordinateSelected)
                        onPublish(
                            name,
                            title,
                            selectedSector,
                            selectedCategoryId,
                            selectedDistrict,
                            price,
                            phone,
                            bio,
                            experienceYears.toIntOrNull() ?: 3,
                            isEmergency,
                            hasSafeBadge,
                            hasMykBadge,
                            hasChildSafeBadge,
                            brandsOrCharacters,
                            equipments
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(52.dp)
                        .testTag("btn_submit_provider_offer")
                ) {
                    Icon(Icons.Default.Storefront, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("İlanı Yayına Al (Mahalleye Duyur)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Sector Selector
            Text("1. Hizmet Sektörünüz", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SectorType.values().filter { it != SectorType.ALL }.forEach { sec ->
                    FilterChip(
                        selected = selectedSector == sec,
                        onClick = {
                            selectedSector = sec
                            val matching = APP_CATEGORIES.firstOrNull { it.sector == sec }
                            if (matching != null) selectedCategoryId = matching.id
                        },
                        label = { Text("${sec.icon} ${sec.titleTr}", fontSize = 12.sp) }
                    )
                }
            }

            // Category Selector
            Text("2. Meslek / Kategori", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            val catsInSector = APP_CATEGORIES.filter { it.sector == selectedSector }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                catsInSector.forEach { cat ->
                    FilterChip(
                        selected = selectedCategoryId == cat.id,
                        onClick = { selectedCategoryId = cat.id },
                        label = { Text(cat.name, fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(imageVector = getCategoryIcon(cat.iconName), contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                }
            }

            // Listing info
            Text("3. İlan Bilgileri", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Adınız & Soyadınız veya İşletme Adı") },
                placeholder = { Text("Örn: İbrahim Usta veya Masal Palyaço") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("input_provider_name")
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("İlan Başlığı") },
                placeholder = { Text("Örn: Garantili Daire Boyama & Alçı Tamiratı") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("input_provider_title")
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Taban Ücret / Fiyat") },
                    placeholder = { Text("Örn: 1.500 ₺") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("input_provider_price")
                )

                OutlinedTextField(
                    value = experienceYears,
                    onValueChange = { experienceYears = it },
                    label = { Text("Deneyim (Yıl)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("input_provider_exp")
                )
            }

            NeighborhoodSelector(
                selected = selectedNeighborhood,
                onSelected = { selectedNeighborhood = it },
                modifier = Modifier.fillMaxWidth()
            )

            CurrentLocationSelector(
                onSelected = { selectedCoordinate = it },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Telefon") },
                placeholder = { Text("05xx xxx xx xx") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("input_provider_phone")
            )

            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it },
                label = { Text("Hizmet Açıklaması & Güvenceniz") },
                placeholder = { Text("İşimizde temizlik ve müşteri memnuniyeti esastır...") },
                modifier = Modifier.fillMaxWidth().height(100.dp).testTag("input_provider_bio")
            )

            // Sektörel Ekstra Alanlar
            if (selectedSector == SectorType.HOME_REPAIR) {
                OutlinedTextField(
                    value = brandsOrCharacters,
                    onValueChange = { brandsOrCharacters = it },
                    label = { Text("Kullandığınız Markalar (Örn: Marshall, Jotun, Kale)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (selectedSector == SectorType.EVENT_ENTERTAINMENT) {
                OutlinedTextField(
                    value = brandsOrCharacters,
                    onValueChange = { brandsOrCharacters = it },
                    label = { Text("Canlandırdığınız Karakterler (Örn: Spiderman, Elsa)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            OutlinedTextField(
                value = equipments,
                onValueChange = { equipments = it },
                label = { Text("Yanınızda Getirdiğiniz Ekipmanlar / Malzemeler") },
                placeholder = { Text("Örn: Buharlı makine, merdiven, ses sistemi, organik boya") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Güvenlik & Rozet Doğrulamaları
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🛡️ Güvenlik ve Belge Rozetleri", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🚨 7/24 Acil Müdahale Desteği", fontSize = 12.sp)
                        Switch(checked = isEmergency, onCheckedChange = { isEmergency = it })
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("✅ Adli Sicil / Sabıka Kaydı Temiz Beyanı", fontSize = 12.sp)
                        Switch(checked = hasSafeBadge, onCheckedChange = { hasSafeBadge = it })
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📜 MYK / Mesleki Ustalık Belgesi", fontSize = 12.sp)
                        Switch(checked = hasMykBadge, onCheckedChange = { hasMykBadge = it })
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
