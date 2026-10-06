package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import com.example.data.model.APP_CATEGORIES
import com.example.data.model.Category
import com.example.data.model.SectorType
import com.example.data.model.UrgencyMode
import com.example.ui.components.DISTRICT_OPTIONS
import com.example.ui.components.getCategoryIcon
import com.example.ui.theme.*
import com.example.validation.RequestFormInput
import com.example.validation.RequestFormValidator

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateJobRequestScreen(
    preselectedCategoryId: String?,
    isEmergencyPreselected: Boolean,
    onBackClick: () -> Unit,
    onSubmitRequest: (
        title: String,
        sector: SectorType,
        category: Category,
        district: String,
        urgency: UrgencyMode,
        date: String,
        time: String,
        address: String,
        customerName: String,
        customerPhone: String,
        // Renovation
        areaSquareMeters: Int,
        roomCount: String,
        isFurnished: Boolean,
        materialsIncluded: Boolean,
        renovationNotes: String,
        // Event
        eventType: String,
        durationHours: Int,
        targetAgeGroup: String,
        costumeOrCharacter: String,
        extraServices: String,
        budget: String
    ) -> Unit
) {
    BackHandler { onBackClick() }

    val initialCat = APP_CATEGORIES.find { it.id == preselectedCategoryId } ?: APP_CATEGORIES.first()
    var selectedCategory by remember { mutableStateOf(initialCat) }
    var selectedSector by remember { mutableStateOf(selectedCategory.sector) }
    var selectedUrgency by remember {
        mutableStateOf(if (isEmergencyPreselected) UrgencyMode.EMERGENCY else UrgencyMode.PLANNED)
    }

    var selectedDistrict by remember { mutableStateOf(DISTRICT_OPTIONS.getOrElse(1) { "Kadıköy, İstanbul" }) }
    var districtMenuExpanded by remember { mutableStateOf(false) }

    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(if (isEmergencyPreselected) "Hemen / Bugün" else "2026-10-18") }
    var time by remember { mutableStateOf(if (isEmergencyPreselected) "En geç 1 saat içinde" else "14:00") }
    var address by remember { mutableStateOf("") }
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var budget by remember { mutableStateOf("") }

    // --- Dynamic Tadilat / Hizmet Grubu State ---
    var areaSquareMeters by remember { mutableStateOf("95") }
    var selectedRoomCount by remember { mutableStateOf("3+1") }
    var isFurnished by remember { mutableStateOf(true) }
    var materialsIncluded by remember { mutableStateOf(false) }
    var renovationNotes by remember { mutableStateOf("") }

    // --- Dynamic Organizasyon / Etkinlik Grubu State ---
    var selectedEventType by remember { mutableStateOf("Doğum Günü Partisi") }
    var durationHours by remember { mutableStateOf(2) }
    var selectedAgeGroup by remember { mutableStateOf("4-7 Yaş") }
    var selectedCostume by remember { mutableStateOf("Palyaço & Yüz Boyama") }
    var extraServices by remember { mutableStateOf("Yüz Boyama + Sosis Balon") }
    var formError by remember { mutableStateOf<String?>(null) }

    val isPhysicalService = selectedSector == SectorType.HOME_REPAIR ||
            selectedSector == SectorType.CLEANING ||
            selectedSector == SectorType.MOVING_ASSEMBLY

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isPhysicalService) "Hizmet & Usta Talep Formu" else "Etkinlik & Organizasyon Formu",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = "Dinamik İlan Mimarisi",
                            fontSize = 11.sp,
                            color = Slate500
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("btn_create_back")
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
                    onClick = {
                        val computedTitle = if (title.isNotBlank()) title else {
                            if (isPhysicalService) {
                                "${selectedDistrict}'da ${selectedRoomCount} ${selectedCategory.name} Talebi"
                            } else {
                                "${selectedEventType} İçin ${selectedCategory.name} (${selectedCostume})"
                            }
                        }
                        val validation = RequestFormValidator.validate(
                            RequestFormInput(
                                title = computedTitle,
                                district = selectedDistrict,
                                date = date,
                                time = time,
                                address = address,
                                customerName = customerName,
                                customerPhone = customerPhone,
                                areaSquareMeters = areaSquareMeters.toIntOrNull() ?: 0,
                                isPhysicalService = isPhysicalService
                            )
                        )
                        if (!validation.isValid) {
                            formError = validation.errors.joinToString(" ")
                            return@Button
                        }
                        formError = null
                        onSubmitRequest(
                            computedTitle,
                            selectedSector,
                            selectedCategory,
                            selectedDistrict,
                            selectedUrgency,
                            date,
                            time,
                            address.trim(),
                            customerName.trim(),
                            customerPhone.trim(),
                            areaSquareMeters.toIntOrNull() ?: 0,
                            selectedRoomCount,
                            isFurnished,
                            materialsIncluded,
                            renovationNotes,
                            selectedEventType,
                            durationHours,
                            selectedAgeGroup,
                            selectedCostume,
                            extraServices,
                            budget.trim()
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPhysicalService) TealPrimary else FestiveCoral
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(52.dp)
                        .testTag("btn_submit_job_request")
                ) {
                    Icon(imageVector = Icons.Default.Send, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Talebi Mahalledeki Usta ve Sanatçılara İlet",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Sector Picker
            Text(
                text = "1. Hizmet Grubu Seçin",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

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
                            if (matching != null) selectedCategory = matching
                        },
                        label = { Text("${sec.icon} ${sec.titleTr}", fontSize = 12.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Category Dropdown / Selection
            Text(
                text = "2. Kategori",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(8.dp))

            val filteredCategories = APP_CATEGORIES.filter { it.sector == selectedSector }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filteredCategories.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory.id == cat.id,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat.name, fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = getCategoryIcon(cat.iconName),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (isPhysicalService) TealPrimary else FestiveCoral,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        ),
                        modifier = Modifier.testTag("chip_select_cat_${cat.id}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Urgency & District
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Urgency Picker
                Column(modifier = Modifier.weight(1f)) {
                    Text("Aciliyet Durumu", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.height(4.dp))
                    Row {
                        FilterChip(
                            selected = selectedUrgency == UrgencyMode.EMERGENCY,
                            onClick = { selectedUrgency = UrgencyMode.EMERGENCY },
                            label = { Text("🚨 Acil", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmergencyRed,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.testTag("btn_urgency_emergency")
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        FilterChip(
                            selected = selectedUrgency == UrgencyMode.PLANNED,
                            onClick = { selectedUrgency = UrgencyMode.PLANNED },
                            label = { Text("📅 Planlı", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TealPrimary,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.testTag("btn_urgency_planned")
                        )
                    }
                }

                // District Dropdown
                Column(modifier = Modifier.weight(1f)) {
                    Text("İlçe", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.height(4.dp))
                    Box {
                        OutlinedButton(
                            onClick = { districtMenuExpanded = true },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("btn_select_district")
                        ) {
                            Text(selectedDistrict.split(",").first(), fontSize = 12.sp, maxLines = 1)
                        }
                        DropdownMenu(
                            expanded = districtMenuExpanded,
                            onDismissRequest = { districtMenuExpanded = false }
                        ) {
                            DISTRICT_OPTIONS.filter { it != "Tüm İlçeler" }.forEach { dist ->
                                DropdownMenuItem(
                                    text = { Text(dist) },
                                    onClick = {
                                        selectedDistrict = dist
                                        districtMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ---------------------------------------------------------
            // 3. DİNAMİK SEKTÖR FORMU (Tadilat vs Organizasyon)
            // ---------------------------------------------------------
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (isPhysicalService) {
                        // --- TADİLAT / FİZİKSEL HİZMET FORMU ---
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Roofing, contentDescription = null, tint = TealPrimary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Tadilat Detayları (m², Oda, Malzeme)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))

                        // Metrekare & Oda Sayısı
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = areaSquareMeters,
                                onValueChange = { areaSquareMeters = it },
                                label = { Text("Alan (m²)") },
                                placeholder = { Text("Örn: 90") },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("input_area_sqm")
                            )

                            // Oda Sayısı Dropdown
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Oda Sayısı", style = MaterialTheme.typography.labelSmall)
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf("1+1", "2+1", "3+1", "4+1").forEach { room ->
                                        FilterChip(
                                            selected = selectedRoomCount == room,
                                            onClick = { selectedRoomCount = room },
                                            label = { Text(room, fontSize = 11.sp) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Eşyalı mı?
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Eşyalı Daire mi?", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("Mobilyalar özel koruma naylonuyla sarılacak", fontSize = 11.sp, color = Slate500)
                            }
                            Switch(
                                checked = isFurnished,
                                onCheckedChange = { isFurnished = it },
                                modifier = Modifier.testTag("switch_furnished")
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Malzeme dahil mi?
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Malzeme Dahil mi? (Boya, boru vs.)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("Usta sadece işçilik mi versin, malzeme dahil mi?", fontSize = 11.sp, color = Slate500)
                            }
                            Switch(
                                checked = materialsIncluded,
                                onCheckedChange = { materialsIncluded = it },
                                modifier = Modifier.testTag("switch_materials")
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = renovationNotes,
                            onValueChange = { renovationNotes = it },
                            label = { Text("Öncesi Durumu & Hasar Notları") },
                            placeholder = { Text("Tavanlarda rutubet var, Marshall açık gri boya istiyoruz...") },
                            modifier = Modifier.fillMaxWidth().height(90.dp).testTag("input_renovation_notes")
                        )

                    } else {
                        // --- ORGANİZASYON GRUBU FORMU ---
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Celebration, contentDescription = null, tint = FestiveCoral)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Etkinlik & Çocuk Eğlencesi Detayları",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))

                        // Etkinlik Türü Seçimi
                        Text("Etkinlik Türü", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        Spacer(modifier = Modifier.height(4.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("Doğum Günü", "Okul / Kreş Şenliği", "Sünnet Düğünü", "Mağaza Açılışı").forEach { evType ->
                                FilterChip(
                                    selected = selectedEventType == evType,
                                    onClick = { selectedEventType = evType },
                                    label = { Text(evType, fontSize = 11.5.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = FestiveCoral,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Süre ve Yaş Grubu
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Süre (Saat)", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                Row(
                                    modifier = Modifier.padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf(1, 2, 3).forEach { h ->
                                        FilterChip(
                                            selected = durationHours == h,
                                            onClick = { durationHours = h },
                                            label = { Text("$h Saat", fontSize = 11.sp) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text("Çocuk Yaş Grubu", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                Row(
                                    modifier = Modifier.padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf("3-6 Yaş", "7-11 Yaş").forEach { age ->
                                        FilterChip(
                                            selected = selectedAgeGroup == age,
                                            onClick = { selectedAgeGroup = age },
                                            label = { Text(age, fontSize = 11.sp) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Kostüm / Karakter Tercihi
                        Text("İstenen Karakter & Kostüm", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        Spacer(modifier = Modifier.height(4.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("Palyaço", "Spiderman", "Karlar Kraliçesi Elsa", "Dev Sevimli Ayıcık", "Pamuk Prenses").forEach { costume ->
                                FilterChip(
                                    selected = selectedCostume == costume,
                                    onClick = { selectedCostume = costume },
                                    label = { Text(costume, fontSize = 11.sp) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = extraServices,
                            onValueChange = { extraServices = it },
                            label = { Text("Ekstra Talepler (Yüz Boyama, Sosis Balon...)") },
                            placeholder = { Text("Antialerjik organik yüz boyası, müzik sistemi...") },
                            modifier = Modifier.fillMaxWidth().testTag("input_event_extras")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tarih & Saat & Adres
            Text(
                text = "4. Tarih, Saat ve İletişim",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Tarih") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("input_date")
                )
                OutlinedTextField(
                    value = time,
                    onValueChange = { time = it },
                    label = { Text("Saat") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("input_time")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("Açık Adres veya Mahalle") },
                placeholder = { Text("Örn: Caferağa Mah. Moda Cad. No:12") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("input_address")
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = customerName,
                    onValueChange = { customerName = it },
                    label = { Text("Adınız Soyadınız") },
                    placeholder = { Text("Ahmet Yılmaz") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("input_name")
                )
                OutlinedTextField(
                    value = customerPhone,
                    onValueChange = { customerPhone = it },
                    label = { Text("Telefon Numaranız") },
                    placeholder = { Text("05xx xxx xx xx") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("input_phone")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = budget,
                onValueChange = { budget = it },
                label = { Text("Tahmini Bütçeniz (Opsiyonel)") },
                placeholder = { Text("Örn: 3.500 ₺") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("input_budget")
            )

            formError?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("form_error")
                )
            }
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}
