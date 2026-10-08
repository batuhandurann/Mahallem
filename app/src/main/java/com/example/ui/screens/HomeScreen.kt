package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.example.data.local.JobRequestEntity
import com.example.data.local.ServiceProviderEntity
import com.example.data.model.FeedFlowType
import com.example.data.model.SectorType
import com.example.data.model.UrgencyMode
import com.example.ui.components.*
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    providers: List<ServiceProviderEntity>,
    jobRequests: List<JobRequestEntity>,
    feedFlowType: FeedFlowType,
    onFeedFlowTypeSelected: (FeedFlowType) -> Unit,
    selectedSector: SectorType,
    onSectorSelected: (SectorType) -> Unit,
    selectedUrgency: UrgencyMode,
    onUrgencySelected: (UrgencyMode) -> Unit,
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    searchSuggestions: List<String>,
    onSuggestionSelected: (String) -> Unit,
    selectedDistrict: String,
    onDistrictSelected: (String) -> Unit,
    onProviderClick: (String) -> Unit,
    onFavoriteToggle: (ServiceProviderEntity) -> Unit,
    onRequestQuoteForProvider: (String) -> Unit,
    onChatWithProvider: (ServiceProviderEntity) -> Unit,
    onChatForJobRequest: (JobRequestEntity) -> Unit,
    onCreateRequestClick: () -> Unit,
    onPublishOfferClick: () -> Unit,
    onEmergencyTriggerClick: () -> Unit,
    onMyRequestsClick: () -> Unit,
    onMessagesClick: () -> Unit,
    activeRequestsCount: Int,
    unreadMessagesCount: Int,
    isProviderMode: Boolean,
    onToggleProviderMode: () -> Unit,
    onReportListing: (providerId: String?, requestId: Long?, title: String) -> Unit,
    onOpenMapClick: () -> Unit = {},
    onOpenEstimatorClick: () -> Unit = {}
) {
    var showPostOptionsModal by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.White,
        topBar = {
            MarketplaceTopBar(
                title = "Mahallemde",
                selectedDistrict = selectedDistrict,
                onDistrictSelected = onDistrictSelected,
                isProviderMode = isProviderMode,
                onToggleProviderMode = onToggleProviderMode,
                onMyRequestsClick = onMyRequestsClick,
                activeRequestsCount = activeRequestsCount,
                onMessagesClick = onMessagesClick,
                unreadMessagesCount = unreadMessagesCount
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showPostOptionsModal = true },
                containerColor = TealPrimary,
                contentColor = Color.White,
                icon = { Icon(imageVector = Icons.Default.AddCircle, contentDescription = null) },
                text = {
                    Text(
                        text = "+ İlan Yayınla",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                },
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("fab_post_listing")
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("home_list"),
            contentPadding = PaddingValues(bottom = 72.dp)
        ) {
            // 1. Search & Segmented Dual-Flow Bar
            item {
                SectorToggleBar(
                    feedFlowType = feedFlowType,
                    onFeedFlowTypeSelected = onFeedFlowTypeSelected,
                    selectedSector = selectedSector,
                    onSectorSelected = onSectorSelected,
                    selectedUrgency = selectedUrgency,
                    onUrgencySelected = onUrgencySelected,
                    searchQuery = searchQuery,
                    onSearchQueryChanged = onSearchQueryChanged,
                    searchSuggestions = searchSuggestions,
                    onSuggestionSelected = onSuggestionSelected,
                    onOpenMapClick = onOpenMapClick,
                    onOpenEstimatorClick = onOpenEstimatorClick
                )
            }

            // 2. Dynamic Hero / Announcement Banner
            item {
                HeroMarketplaceBanner(
                    selectedSector = selectedSector,
                    onEmergencyClick = onEmergencyTriggerClick,
                    onCreateRequestClick = onCreateRequestClick,
                    onPublishOfferClick = onPublishOfferClick
                )
            }

            // 3. Category Filter Chips
            item {
                CategoryChips(
                    selectedSector = selectedSector,
                    selectedCategory = selectedCategory,
                    onCategorySelected = onCategorySelected
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // 4. Section Title & Results Count
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (feedFlowType) {
                            FeedFlowType.ALL -> "Yerel İlan Panosu (Usta & İhtiyaç)"
                            FeedFlowType.PROVIDER_OFFERS -> "🛠️ Esnaf & Hizmet Vitrini (Sahibinden/Letgo)"
                            FeedFlowType.SEEKER_REQUESTS -> "📋 Mahalledeki İhtiyaç Talepleri (Armut)"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val count = when (feedFlowType) {
                        FeedFlowType.ALL -> providers.size + jobRequests.size
                        FeedFlowType.PROVIDER_OFFERS -> providers.size
                        FeedFlowType.SEEKER_REQUESTS -> jobRequests.size
                    }
                    Text(
                        text = "$count İlan",
                        style = MaterialTheme.typography.labelSmall,
                        color = Slate500
                    )
                }
            }

            // 5. Listings Feed based on feedFlowType
            if (feedFlowType == FeedFlowType.ALL || feedFlowType == FeedFlowType.SEEKER_REQUESTS) {
                if (jobRequests.isNotEmpty()) {
                    items(jobRequests, key = { "req-${it.id}" }) { req ->
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            JobRequestFeedCard(
                                request = req,
                                onSendOfferClick = { onChatForJobRequest(req) },
                                onChatClick = { onChatForJobRequest(req) },
                                onReportClick = { onReportListing(null, req.id, req.title) }
                            )
                        }
                    }
                }
            }

            if (feedFlowType == FeedFlowType.ALL || feedFlowType == FeedFlowType.PROVIDER_OFFERS) {
                if (providers.isNotEmpty()) {
                    items(providers, key = { "prov-${it.id}" }) { provider ->
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            ProviderCard(
                                provider = provider,
                                onClick = { onProviderClick(provider.id) },
                                onFavoriteToggle = { onFavoriteToggle(provider) },
                                onRequestQuoteClick = { onRequestQuoteForProvider(provider.id) },
                                onChatClick = { onChatWithProvider(provider) },
                                onReportClick = { onReportListing(provider.id, null, provider.name) }
                            )
                        }
                    }
                }
            }

            if (when (feedFlowType) {
                FeedFlowType.ALL -> providers.isEmpty() && jobRequests.isEmpty()
                FeedFlowType.PROVIDER_OFFERS -> providers.isEmpty()
                FeedFlowType.SEEKER_REQUESTS -> jobRequests.isEmpty()
            }) {
                item {
                    EmptyProvidersState(
                        onResetFilters = {
                            onFeedFlowTypeSelected(FeedFlowType.ALL)
                            onSectorSelected(SectorType.ALL)
                            onUrgencySelected(UrgencyMode.ALL)
                            onCategorySelected(null)
                            onSearchQueryChanged("")
                        }
                    )
                }
            }
        }
    }

    // Modal Sheet: İki Yönlü İlan Seçimi (Armut vs Sahibinden/Letgo)
    if (showPostOptionsModal) {
        ModalBottomSheet(
            onDismissRequest = { showPostOptionsModal = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Ne Tür Bir İlan Yayınlamak İstiyorsunuz?",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Her meslek grubuna ve her ihtiyaca açık yerel ilan panosu.",
                    fontSize = 12.sp,
                    color = Slate500
                )

                // Option B: İhtiyacım Var (Armut usulü)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = FestiveCoralLight),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showPostOptionsModal = false
                            onCreateRequestClick()
                        }
                        .testTag("btn_modal_create_request")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(FestiveCoral),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.HelpOutline, contentDescription = null, tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "1. Hizmet Arıyorum (Talep Aç - Armut Tarzı)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Örn: 'Acil su tesisatçısı lazım' veya 'Doğum günü palyaçosu aranıyor' deyin, esnaflar size fiyat teklifi versin.",
                                fontSize = 11.5.sp,
                                color = Slate700
                            )
                        }
                    }
                }

                // Option A: Hizmet Veriyorum (Sahibinden/Letgo usulü)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = TealContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showPostOptionsModal = false
                            onPublishOfferClick()
                        }
                        .testTag("btn_modal_publish_offer")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(TealPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Storefront, contentDescription = null, tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "2. Hizmet Veriyorum (Usta İlanı Aç - Letgo Tarzı)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Örn: 'Boyacıyım', 'Palyaçoyum', 'Kuaförüm' diye ilan açın, mahalle sakinleri profilinizi görüp doğrudan mesaj atsın.",
                                fontSize = 11.5.sp,
                                color = Slate700
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun HeroMarketplaceBanner(
    selectedSector: SectorType,
    onEmergencyClick: () -> Unit,
    onCreateRequestClick: () -> Unit,
    onPublishOfferClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF1E40AF),
                            Color(0xFF2563EB),
                            Color(0xFFEA580C)
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White.copy(alpha = 0.25f)
                            ) {
                                Text(
                                    text = "YEREL HİZMET & İHTİYAÇ PAZARI",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = StarGold.copy(alpha = 0.9f)
                            ) {
                                Text(
                                    text = "🔒 GÜVENLİ İLETİŞİM",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate900,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "İster Palyaço Çağır, İster Usta Bul!",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            ),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Yakındaki hizmet verenleri keşfet, ihtiyacını yayınla, teklifleri karşılaştır ve uygulama içinden güvenle iletişim kur.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.92f),
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onEmergencyClick,
                        colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("btn_quick_emergency")
                    ) {
                        Text(
                            text = "🚨 Acil Usta Çağır",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Button(
                        onClick = onCreateRequestClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("btn_quick_create_job")
                    ) {
                        Text("Talep Aç (Armut)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TealDark)
                    }

                    OutlinedButton(
                        onClick = onPublishOfferClick,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.linearGradient(listOf(Color.White, Color.White))),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("btn_quick_publish_service")
                    ) {
                        Text("İlan Aç (Letgo)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyProvidersState(onResetFilters: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.SearchOff,
                contentDescription = null,
                tint = Slate500,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Kriterlere Uygun İlan Bulunamadı",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Farklı bir kategori veya ilçe seçebilir, ya da filtreleri temizleyebilirsiniz.",
                style = MaterialTheme.typography.bodySmall,
                color = Slate500,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onResetFilters,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Tüm Filtreleri Temizle")
            }
        }
    }
}
