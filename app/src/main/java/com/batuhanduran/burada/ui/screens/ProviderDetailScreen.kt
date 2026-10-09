package com.batuhanduran.burada.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.batuhanduran.burada.data.local.ServiceProviderEntity
import com.batuhanduran.burada.ui.components.*
import com.batuhanduran.burada.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProviderDetailScreen(
    provider: ServiceProviderEntity?,
    onBackClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onRequestQuoteClick: (String) -> Unit,
    onChatClick: () -> Unit = {},
    onReportClick: () -> Unit = {}
) {
    BackHandler { onBackClick() }

    if (provider == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val isRenovation = provider.sector == "HOME_REPAIR"
    val brandList = parseJsonList(provider.paintBrandsJson)
    val characterList = parseJsonList(provider.charactersOfferedJson)
    val equipmentList = parseJsonList(provider.includedEquipmentsJson)

    // Parse Before-After items
    val beforeAfterItems = if (isRenovation) {
        listOf(
            BeforeAfterItem("3+1 Daire Salon Badana", "Sararmış duvarlar ve tavan çatlakları", "2 kat Jotun Safir Beyazı pürüzsüz boyama"),
            BeforeAfterItem("Antre ve Koridor Yenileme", "Eski kabarık duvar kağıdı", "Alçı saten tamiratı + Marshall Kumsal Beji")
        )
    } else emptyList()

    // Parse Videos
    val videoItems = if (!isRenovation) {
        listOf(
            VideoShowcaseItem("Doğum Günü Mini Disco Dansı", "1:45 dk", "3.8k izlenme"),
            VideoShowcaseItem("Sosis Balon Kılıç ve Kuğu Yapımı", "0:55 dk", "2.1k izlenme"),
            VideoShowcaseItem("İnteraktif Çocuk Oyunları", "2:10 dk", "4.5k izlenme")
        )
    } else emptyList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(provider.name, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("btn_detail_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Geri"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onFavoriteToggle,
                        modifier = Modifier.testTag("btn_detail_fav")
                    ) {
                        Icon(
                            imageVector = if (provider.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Favori",
                            tint = if (provider.isFavorite) FestiveCoral else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = onReportClick,
                        modifier = Modifier.testTag("btn_detail_report")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ReportProblem,
                            contentDescription = "Şikayet Et",
                            tint = Slate500
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Fiyat / Ücret",
                            style = MaterialTheme.typography.labelSmall,
                            color = Slate500
                        )
                        Text(
                            text = provider.hourlyOrBasePrice,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isRenovation) TealPrimary else FestiveCoral
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onChatClick,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("btn_detail_chat")
                        ) {
                            Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Mesaj", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { onRequestQuoteClick(provider.id) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRenovation) TealPrimary else FestiveCoral
                            ),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("btn_detail_request_quote")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Teklif İste",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("provider_detail_list"),
            contentPadding = PaddingValues(16.dp)
        ) {
            // Profile Card Header
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
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
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column {
                                Text(
                                    text = provider.name,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp
                                    )
                                )
                                Text(
                                    text = provider.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Slate600
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = StarGold,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = " ${provider.rating}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = " (${provider.reviewCount} Değerlendirme)",
                                        fontSize = 12.sp,
                                        color = Slate500
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Badges Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (provider.verifiedSafeBadge) {
                                BadgeChip(
                                    icon = Icons.Default.Security,
                                    text = "Adli Sicil Onaylı",
                                    bgColor = SafeBadgeGreenContainer,
                                    textColor = SafeBadgeText
                                )
                            }
                            if (provider.mykCertified) {
                                BadgeChip(
                                    icon = Icons.Default.WorkspacePremium,
                                    text = "MYK Usta Belgesi",
                                    bgColor = TealContainer,
                                    textColor = OnTealContainer
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
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Bio
                        Text(
                            text = provider.bio,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Slate700,
                            lineHeight = 22.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Sektörel Detaylar: Boya Markaları veya Karakterler
            if (brandList.isNotEmpty()) {
                item {
                    DetailSectionCard(title = "🎨 Tercih Edilen Boya Markaları") {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            brandList.forEach { brand ->
                                AssistChip(
                                    onClick = {},
                                    label = { Text(brand, fontWeight = FontWeight.SemiBold) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = TealPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            if (characterList.isNotEmpty()) {
                item {
                    DetailSectionCard(title = "🎭 Canlandırılan Karakter & Kostümler") {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            characterList.forEach { char ->
                                AssistChip(
                                    onClick = {},
                                    label = { Text(char, fontWeight = FontWeight.SemiBold) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.TheaterComedy,
                                            contentDescription = null,
                                            tint = FestiveCoral,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Included Equipment / Supplies
            if (equipmentList.isNotEmpty()) {
                item {
                    DetailSectionCard(title = "🧰 Yanında Getirdiği Ekipman ve Malzemeler") {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            equipmentList.forEach { eq ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = if (isRenovation) TealPrimary else FestiveCoral,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = eq,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Slate700
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Calendar & Müsaitlik
            item {
                AvailabilityCalendarView(
                    bookedDatesJson = provider.bookedDatesJson,
                    isOpenForOffers = provider.isOpenForOffers,
                    isEditable = false
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Showcase Section: Videos or Before/After
            if (beforeAfterItems.isNotEmpty()) {
                item {
                    BeforeAfterSection(beforeAfterItems = beforeAfterItems)
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            if (videoItems.isNotEmpty()) {
                item {
                    VideoShowcaseSection(videos = videoItems)
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun DetailSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}
