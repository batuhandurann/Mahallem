package com.example

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.AppEnvironment
import com.example.privacy.ConsentRepository
import com.example.notification.PushTokenRepository
import com.example.data.local.DigitalReceiptEntity
import com.example.data.local.JobRequestEntity
import com.example.data.local.QuoteEntity
import com.example.data.model.FeedFlowType
import com.example.data.model.SectorType
import com.example.data.model.UrgencyMode
import com.example.ui.MarketplaceViewModel
import com.example.ui.ScreenDestination
import com.example.ui.components.CostEstimatorSheet
import com.example.ui.components.DigitalReceiptDialog
import com.example.ui.components.EscrowPaymentDialog
import com.example.ui.components.ReportListingDialog
import com.example.ui.screens.*
import com.google.firebase.auth.FirebaseAuth
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MarketplaceApp()
            }
        }
    }
}

@Composable
fun MarketplaceApp() {
    if (AppEnvironment.mode == AppEnvironment.Mode.LOCAL) {
        MarketplaceContent()
        return
    }

    val auth = remember { runCatching { FirebaseAuth.getInstance() }.getOrNull() }
    if (auth == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            androidx.compose.material3.Text(
                "Firebase yapılandırması eksik. Staging/production için google-services.json gerekir.",
                modifier = Modifier.padding(24.dp)
            )
        }
        return
    }

    var currentUser by remember { mutableStateOf(auth.currentUser) }
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { currentUser = it.currentUser }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    if (currentUser == null) {
        PhoneAuthScreen(onAuthenticated = {})
    } else {
        LaunchedEffect(currentUser?.uid) {
            runCatching { PushTokenRepository().registerCurrentDevice() }
        }
        val context = LocalContext.current
        val consent = remember(context) { ConsentRepository(context) }
        if (!consent.privacyNoticeAcknowledged) {
            PrivacyConsentScreen(
                onCompleted = { currentUser = auth.currentUser }
            )
        } else {
            LaunchedEffect(currentUser?.uid) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
                    && ContextCompat.checkSelfPermission(
                        LocalContext.current,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            MarketplaceContent()
        }
    }
}

@Composable
private fun MarketplaceContent(
    viewModel: MarketplaceViewModel = viewModel()
) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val isProviderMode by viewModel.isProviderMode.collectAsStateWithLifecycle()
    val feedFlowType by viewModel.feedFlowType.collectAsStateWithLifecycle()
    val selectedSector by viewModel.selectedSector.collectAsStateWithLifecycle()
    val selectedUrgency by viewModel.selectedUrgency.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchSuggestions by viewModel.searchSuggestions.collectAsStateWithLifecycle()
    val selectedDistrict by viewModel.selectedDistrict.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val requests by viewModel.jobRequests.collectAsStateWithLifecycle()
    val quotes by viewModel.allQuotes.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val activeChatMessages by viewModel.activeChatMessages.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    var reportingTarget by remember { mutableStateOf<Triple<String?, Long?, String>?>(null) }
    var escrowTargetQuote by remember { mutableStateOf<Pair<QuoteEntity, JobRequestEntity>?>(null) }
    var activeReceipt by remember { mutableStateOf<DigitalReceiptEntity?>(null) }
    var showCostEstimator by remember { mutableStateOf(false) }

    LaunchedEffect(toastMessage) {
        toastMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearToast()
        }
    }

    val isMainTabScreen = currentScreen is ScreenDestination.Home ||
            currentScreen is ScreenDestination.MapView ||
            currentScreen is ScreenDestination.MyRequests ||
            currentScreen is ScreenDestination.ConversationsList

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (isMainTabScreen) {
                com.example.ui.components.MarketplaceBottomNavBar(
                    currentScreen = currentScreen,
                    onTabSelected = { target ->
                        viewModel.navigateTo(target)
                    },
                    unreadMessagesCount = conversations.sumOf { it.unreadCount },
                    activeRequestsCount = requests.size
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(innerPadding)) {
            when (val screen = currentScreen) {
                is ScreenDestination.Home -> {
                    HomeScreen(
                        providers = providers,
                        jobRequests = requests,
                        feedFlowType = feedFlowType,
                        onFeedFlowTypeSelected = { viewModel.setFeedFlowType(it) },
                        selectedSector = selectedSector,
                        onSectorSelected = { viewModel.setSector(it) },
                        selectedUrgency = selectedUrgency,
                        onUrgencySelected = { viewModel.setUrgency(it) },
                        selectedCategory = selectedCategory,
                        onCategorySelected = { viewModel.selectCategory(it) },
                        searchQuery = searchQuery,
                        onSearchQueryChanged = { viewModel.setSearchQuery(it) },
                        searchSuggestions = searchSuggestions,
                        onSuggestionSelected = { suggestion ->
                            viewModel.setSearchQuery(suggestion)
                        },
                        selectedDistrict = selectedDistrict,
                        onDistrictSelected = { viewModel.setDistrict(it) },
                        onProviderClick = { id ->
                            viewModel.navigateTo(ScreenDestination.ProviderDetail(id))
                        },
                        onFavoriteToggle = { viewModel.toggleFavorite(it) },
                        onRequestQuoteForProvider = { id ->
                            val prov = providers.find { it.id == id }
                            viewModel.navigateTo(
                                ScreenDestination.CreateRequest(
                                    preselectedCategory = prov?.categoryId,
                                    isEmergency = prov?.isEmergencyAvailable == true
                                )
                            )
                        },
                        onChatWithProvider = { prov ->
                            viewModel.openChatWithProvider(prov)
                        },
                        onChatForJobRequest = { req ->
                            viewModel.openChatForJobRequest(req)
                        },
                        onCreateRequestClick = {
                            viewModel.navigateTo(ScreenDestination.CreateRequest(preselectedCategory = selectedCategory))
                        },
                        onPublishOfferClick = {
                            viewModel.navigateTo(ScreenDestination.PublishProviderOffer)
                        },
                        onEmergencyTriggerClick = {
                            viewModel.setUrgency(UrgencyMode.EMERGENCY)
                            viewModel.navigateTo(
                                ScreenDestination.CreateRequest(
                                    preselectedCategory = "tesisatci",
                                    isEmergency = true
                                )
                            )
                        },
                        onMyRequestsClick = {
                            viewModel.navigateTo(ScreenDestination.MyRequests)
                        },
                        onMessagesClick = {
                            viewModel.navigateTo(ScreenDestination.ConversationsList)
                        },
                        activeRequestsCount = requests.size,
                        unreadMessagesCount = conversations.sumOf { it.unreadCount },
                        isProviderMode = isProviderMode,
                        onToggleProviderMode = { viewModel.toggleProviderMode() },
                        onReportListing = { providerId, reqId, title ->
                            reportingTarget = Triple(providerId, reqId, title)
                        },
                        onOpenMapClick = {
                            viewModel.navigateTo(ScreenDestination.MapView)
                        },
                        onOpenEstimatorClick = {
                            showCostEstimator = true
                        }
                    )
                }

                is ScreenDestination.MapView -> {
                    MarketplaceMapView(
                        providers = providers,
                        requests = requests,
                        onBackClick = { viewModel.navigateBack() },
                        onProviderClick = { id ->
                            viewModel.navigateTo(ScreenDestination.ProviderDetail(id))
                        },
                        onChatWithProvider = { prov ->
                            viewModel.openChatWithProvider(prov)
                        },
                        onChatForJobRequest = { req ->
                            viewModel.openChatForJobRequest(req)
                        }
                    )
                }

                is ScreenDestination.ProviderDetail -> {
                    val provider = providers.find { it.id == screen.providerId }
                    ProviderDetailScreen(
                        provider = provider,
                        onBackClick = { viewModel.navigateBack() },
                        onFavoriteToggle = { provider?.let { viewModel.toggleFavorite(it) } },
                        onRequestQuoteClick = {
                            viewModel.navigateTo(
                                ScreenDestination.CreateRequest(
                                    preselectedCategory = provider?.categoryId,
                                    isEmergency = provider?.isEmergencyAvailable == true
                                )
                            )
                        },
                        onChatClick = {
                            provider?.let { viewModel.openChatWithProvider(it) }
                        },
                        onReportClick = {
                            provider?.let { reportingTarget = Triple(it.id, null, it.name) }
                        }
                    )
                }

                is ScreenDestination.CreateRequest -> {
                    CreateJobRequestScreen(
                        preselectedCategoryId = screen.preselectedCategory,
                        isEmergencyPreselected = screen.isEmergency,
                        onBackClick = { viewModel.navigateBack() },
                        onSubmitRequest = { title, sector, category, district, urgency, date, time, address,
                                            customerName, customerPhone, areaSquareMeters, roomCount,
                                            isFurnished, materialsIncluded, renovationNotes, eventType,
                                            durationHours, targetAgeGroup, costumeOrCharacter, extraServices, budget ->
                            viewModel.submitJobRequest(
                                title = title,
                                sector = sector,
                                category = category,
                                district = district,
                                urgency = urgency,
                                date = date,
                                time = time,
                                address = address,
                                customerName = customerName,
                                customerPhone = customerPhone,
                                areaSquareMeters = areaSquareMeters,
                                roomCount = roomCount,
                                isFurnished = isFurnished,
                                materialsIncluded = materialsIncluded,
                                renovationNotes = renovationNotes,
                                eventType = eventType,
                                durationHours = durationHours,
                                targetAgeGroup = targetAgeGroup,
                                costumeOrCharacter = costumeOrCharacter,
                                extraServices = extraServices,
                                budget = budget
                            )
                        }
                    )
                }

                is ScreenDestination.PublishProviderOffer -> {
                    PublishProviderOfferScreen(
                        onBackClick = { viewModel.navigateBack() },
                        onPublish = { name, title, sector, categoryId, district, price, phone, bio,
                                      experienceYears, isEmergency, hasSafeBadge, hasMykBadge,
                                      hasChildSafeBadge, brandsOrCharacters, equipments ->
                            viewModel.publishProviderListing(
                                name = name,
                                title = title,
                                sector = sector,
                                categoryId = categoryId,
                                district = district,
                                price = price,
                                phone = phone,
                                bio = bio,
                                experienceYears = experienceYears,
                                isEmergency = isEmergency,
                                hasSafeBadge = hasSafeBadge,
                                hasMykBadge = hasMykBadge,
                                hasChildSafeBadge = hasChildSafeBadge,
                                brandsOrCharacters = brandsOrCharacters,
                                equipments = equipments
                            )
                        }
                    )
                }

                is ScreenDestination.MyRequests -> {
                    MyRequestsScreen(
                        requests = requests,
                        quotes = quotes,
                        onBackClick = { viewModel.navigateBack() },
                        onAcceptQuote = { reqId, quoteId, pName ->
                            viewModel.acceptQuote(reqId, quoteId, pName)
                        },
                        onAcceptWithEscrow = { quote, request ->
                            escrowTargetQuote = Pair(quote, request)
                        },
                        onViewReceipt = { quote ->
                            val dummyReceipt = DigitalReceiptEntity(
                                receiptCode = if (quote.receiptCode.isNotBlank()) quote.receiptCode else "MHL-2026-8812",
                                requestId = quote.requestId,
                                quoteId = quote.id,
                                jobTitle = "3+1 Daire Boya ve Badana Hizmeti",
                                customerName = "Cemil Kaya",
                                providerName = quote.providerName,
                                providerTitle = quote.providerTitle,
                                totalAmount = quote.price,
                                escrowStatus = "LOCKED",
                                warrantyInfo = "2 Yıl İşçilik & Malzeme Mahallemde Güvencesi",
                                createdAtDate = "05.10.2026",
                                district = "Kadıköy / Moda"
                            )
                            activeReceipt = dummyReceipt
                        },
                        onRejectQuote = { quoteId ->
                            viewModel.rejectQuote(quoteId)
                        },
                        onNewRequestClick = {
                            viewModel.navigateTo(ScreenDestination.CreateRequest())
                        }
                    )
                }

                is ScreenDestination.ConversationsList -> {
                    ConversationsListScreen(
                        conversations = conversations,
                        onBackClick = { viewModel.navigateBack() },
                        onConversationClick = { convId ->
                            viewModel.navigateTo(ScreenDestination.Chat(convId))
                        }
                    )
                }

                is ScreenDestination.Chat -> {
                    val conv = conversations.find { it.id == screen.conversationId }
                    ChatScreen(
                        conversation = conv,
                        messages = activeChatMessages,
                        onBackClick = { viewModel.navigateBack() },
                        onSendMessage = { text, isOffer, price ->
                            viewModel.sendChatMessage(screen.conversationId, text, isOffer, price)
                        },
                        onSendVoiceNote = { duration ->
                            viewModel.sendVoiceNote(screen.conversationId, duration)
                        },
                        onSendPhoto = { desc ->
                            viewModel.sendPhotoMessage(screen.conversationId, desc)
                        },
                        onCallClick = {
                            viewModel.sendChatMessage(
                                screen.conversationId,
                                "📞 Sesli arama isteği gönderildi.",
                                false,
                                ""
                            )
                        },
                        onReportClick = {
                            conv?.let {
                                reportingTarget = Triple(it.participantId, null, it.participantName)
                            }
                        }
                    )
                }

                is ScreenDestination.ProviderDashboard -> {
                    ProviderDashboardScreen(
                        providers = providers,
                        requests = requests,
                        onBackClick = { viewModel.navigateBack() },
                        onToggleOffers = { pId, status ->
                            viewModel.toggleProviderOpenForOffers(pId, status)
                        },
                        onToggleCalendarDate = { prov, dateIso ->
                            viewModel.toggleBookedDate(prov, dateIso)
                        },
                        onSubmitQuote = { reqId, prov, price, arrival, notes ->
                            viewModel.submitProviderQuote(reqId, prov, price, arrival, notes)
                        }
                    )
                }

                else -> {}
            }

            // Escrow Payment Modal Dialog
            escrowTargetQuote?.let { (quote, req) ->
                EscrowPaymentDialog(
                    quote = quote,
                    jobTitle = req.title,
                    onDismiss = { escrowTargetQuote = null },
                    onConfirmPayment = { q ->
                        viewModel.fundEscrowPayment(
                            quote = q,
                            jobTitle = req.title,
                            customerName = req.customerName,
                            district = req.district,
                            onReceiptGenerated = { receipt ->
                                activeReceipt = receipt
                            },
                            onCheckoutUrl = { url ->
                                runCatching {
                                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }.onFailure {
                                    snackbarHostState.showSnackbar("Ödeme sayfası açılamadı.")
                                }
                            }
                        )
                        escrowTargetQuote = null
                    }
                )
            }

            // Digital Receipt & Warranty Dialog
            activeReceipt?.let { receipt ->
                DigitalReceiptDialog(
                    receipt = receipt,
                    onDismiss = { activeReceipt = null },
                    onReleaseFunds = { code ->
                        viewModel.releaseEscrowPayment(receipt.requestId, receipt.quoteId, code)
                        activeReceipt = null
                    },
                    onDisputeClick = {
                        reportingTarget = Triple(null, receipt.requestId, "İtiraz: ${receipt.jobTitle}")
                        activeReceipt = null
                    }
                )
            }

            // Smart Cost Estimator Bottom Sheet
            if (showCostEstimator) {
                CostEstimatorSheet(
                    onDismiss = { showCostEstimator = false },
                    onCreateRequestWithBudget = { cat, budget, note ->
                        viewModel.navigateTo(
                            ScreenDestination.CreateRequest(preselectedCategory = cat.id)
                        )
                    }
                )
            }

            // Report Listing Modal Dialog
            reportingTarget?.let { (providerId, reqId, title) ->
                ReportListingDialog(
                    itemTitle = title,
                    onDismiss = { reportingTarget = null },
                    onConfirmReport = { reason ->
                        viewModel.reportListing(providerId, reqId)
                        reportingTarget = null
                    }
                )
            }
        }
    }
}
