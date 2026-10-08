package com.example

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.AppEnvironment
import com.example.privacy.ConsentRepository
import com.example.auth.UserProfileRepository
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
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_CONVERSATION_ID = "mahallem.conversationId"
        const val EXTRA_OPEN_MY_REQUESTS = "mahallem.openMyRequests"
    }

    private val pendingConversationId = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    private val pendingOpenMyRequests = kotlinx.coroutines.flow.MutableStateFlow(false)

    private fun captureNavigationIntent(intent: Intent?) {
        val conversationId = intent?.getStringExtra(EXTRA_CONVERSATION_ID)
            ?: intent?.getStringExtra("conversationId")
        pendingConversationId.value = conversationId?.takeIf { it.matches(Regex("^[a-f0-9]{64}$")) }
        pendingOpenMyRequests.value =
            intent?.getBooleanExtra(EXTRA_OPEN_MY_REQUESTS, false) == true ||
                intent?.getStringExtra("destination") == "MY_REQUESTS"
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureNavigationIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        captureNavigationIntent(intent)
        setContent {
            val requestedConversationId by pendingConversationId.collectAsStateWithLifecycle()
            val requestedMyRequests by pendingOpenMyRequests.collectAsStateWithLifecycle()
            MyApplicationTheme {
                MarketplaceApp(
                    notificationConversationId = requestedConversationId,
                    notificationOpenMyRequests = requestedMyRequests,
                    onNotificationNavigationConsumed = {
                        pendingConversationId.value = null
                        pendingOpenMyRequests.value = false
                        intent?.removeExtra(EXTRA_CONVERSATION_ID)
                        intent?.removeExtra(EXTRA_OPEN_MY_REQUESTS)
                    }
                )
            }
        }
    }
}

@Composable
fun MarketplaceApp(
    notificationConversationId: String? = null,
    notificationOpenMyRequests: Boolean = false,
    onNotificationNavigationConsumed: () -> Unit = {}
) {
    if (AppEnvironment.mode == AppEnvironment.Mode.LOCAL) {
        MarketplaceContent(sessionKey = "local")
        return
    }

    val context = LocalContext.current
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

    val sessionStore: com.example.auth.AccountViewModelStore = viewModel()
    var currentUser by remember { mutableStateOf(auth.currentUser) }
    var profileReady by remember(currentUser?.uid) { mutableStateOf(false) }
    var profileError by remember(currentUser?.uid) { mutableStateOf<String?>(null) }
    var profileRetryToken by remember(currentUser?.uid) { mutableIntStateOf(0) }
    var deletionStatus by remember(currentUser?.uid) { mutableStateOf<String?>(null) }
    var consentRevision by remember(currentUser?.uid) { mutableIntStateOf(0) }
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener {
            sessionStore.selectAccount(it.currentUser?.uid)
            currentUser = it.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(currentUser?.uid) {
        if (currentUser == null) {
            runCatching {
                com.google.firebase.analytics.FirebaseAnalytics.getInstance(context).apply {
                    setAnalyticsCollectionEnabled(false)
                    resetAnalyticsData()
                }
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    if (currentUser == null) {
        PhoneAuthScreen(onAuthenticated = {})
    } else {
        LaunchedEffect(currentUser?.uid, profileRetryToken) {
            profileReady = false
            profileError = null
            deletionStatus = null
            currentUser?.let { user ->
                runCatching {
                    val profiles = UserProfileRepository()
                    val status = profiles.getDeletionStatus(user)
                    if (status == "REQUESTED" || status == "PURGING") {
                        status
                    } else {
                        profiles.ensureUserProfile(user)
                        "ACTIVE"
                    }
                }.onSuccess { status ->
                    deletionStatus = status
                    if (status == "ACTIVE") {
                        profileReady = true
                        runCatching {
                            PushTokenRepository().registerCurrentDevice()
                        }.onFailure {
                            android.util.Log.w("MahallemAuth", "Cihaz bildirimi kaydı başarısız; uygulama erişimi engellenmeyecek.", it)
                        }
                    }
                }.onFailure {
                    profileError = "Hesabınız hazırlanamadı. İnternet bağlantınızı kontrol edip tekrar deneyin."
                    android.util.Log.w("MahallemAuth", "Kullanıcı profili hazırlanamadı", it)
                }
            }
        }

        val consent = remember(context, currentUser?.uid, consentRevision) {
            ConsentRepository(context, currentUser!!.uid)
        }

        if (deletionStatus == "REQUESTED" || deletionStatus == "PURGING") {
            AccountDeletionPendingScreen(
                status = deletionStatus!!,
                onCanceled = {
                    deletionStatus = null
                    profileRetryToken++
                }
            )
        } else if (profileError != null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                androidx.compose.foundation.layout.Column(
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                ) {
                    androidx.compose.material3.Text(
                        profileError!!,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    androidx.compose.material3.Button(
                        onClick = { profileRetryToken++ }
                    ) {
                        androidx.compose.material3.Text("Tekrar Dene")
                    }
                }
            }
        } else if (!profileReady) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                androidx.compose.material3.Text(
                    "Hesabınız hazırlanıyor...",
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else if (!consent.privacyNoticeAcknowledged) {
            PrivacyConsentScreen(
                userId = currentUser!!.uid,
                onCompleted = { consentRevision++ }
            )
        } else {
            LaunchedEffect(currentUser?.uid, consent.analyticsConsent) {
                runCatching {
                    com.google.firebase.analytics.FirebaseAnalytics.getInstance(context)
                        .setAnalyticsCollectionEnabled(consent.analyticsConsent)
                }
            }
            LaunchedEffect(currentUser?.uid) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
                    && ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            MarketplaceContent(
                sessionKey = currentUser!!.uid,
                viewModel = viewModel(
                    viewModelStoreOwner = sessionStore,
                    factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(
                        context.applicationContext as android.app.Application
                    ),
                    key = marketplaceViewModelKey(currentUser!!.uid)
                ),
                notificationConversationId = notificationConversationId,
                notificationOpenMyRequests = notificationOpenMyRequests,
                onNotificationNavigationConsumed = onNotificationNavigationConsumed
            )
        }
    }
}

internal fun marketplaceViewModelKey(sessionKey: String): String =
    "marketplace-" + sessionKey.ifBlank { "local" }

@Composable
private fun MarketplaceContent(
    sessionKey: String,
    viewModel: MarketplaceViewModel = viewModel(key = marketplaceViewModelKey(sessionKey)),
    notificationConversationId: String? = null,
    notificationOpenMyRequests: Boolean = false,
    onNotificationNavigationConsumed: () -> Unit = {}
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
    val myRequests by viewModel.myJobRequests.collectAsStateWithLifecycle()
    val quotes by viewModel.allQuotes.collectAsStateWithLifecycle()
    val providerReviews by viewModel.providerReviews.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val activeChatMessages by viewModel.activeChatMessages.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()
    val paymentStatus by viewModel.paymentStatus.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(notificationConversationId, notificationOpenMyRequests, conversations) {
        when {
            notificationConversationId != null &&
                conversations.any { it.id == notificationConversationId } -> {
                viewModel.navigateTo(ScreenDestination.Chat(notificationConversationId))
                onNotificationNavigationConsumed()
            }
            notificationOpenMyRequests -> {
                viewModel.navigateTo(ScreenDestination.MyRequests)
                onNotificationNavigationConsumed()
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshLastPaymentStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(paymentStatus) {
        when (paymentStatus) {
            "PAID" -> snackbarHostState.showSnackbar("Ödeme PayTR tarafından başarılı olarak bildirildi.")
            "FAILED" -> snackbarHostState.showSnackbar("Ödeme başarısız oldu veya onaylanmadı.")
            "PENDING" -> Unit
            "CREATED" -> Unit
            "RELEASE_REQUESTED" -> snackbarHostState.showSnackbar("Ödeme serbest bırakma talebi alındı.")
            "REFUND_REQUESTED" -> snackbarHostState.showSnackbar("İade talebi alındı.")
            "REFUNDED" -> snackbarHostState.showSnackbar("Ödeme iade edildi.")
            "RELEASED" -> snackbarHostState.showSnackbar("Ödeme satıcı aktarımı tamamlandı.")
        }
    }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

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
            currentScreen is ScreenDestination.ConversationsList ||
            currentScreen is ScreenDestination.AccountSettings

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
                        reviews = providerReviews,
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
                        requests = myRequests,
                        quotes = quotes,
                        onBackClick = { viewModel.navigateBack() },
                        onAcceptQuote = { reqId, quoteId, pName ->
                            viewModel.acceptQuote(reqId, quoteId, pName)
                        },
                        onAcceptWithEscrow = { quote, request ->
                            escrowTargetQuote = Pair(quote, request)
                        },
                        onViewReceipt = { quote ->
                            viewModel.loadReceiptForRequest(quote.requestId) { receipt ->
                                if (receipt != null) {
                                    activeReceipt = receipt
                                } else if (AppEnvironment.mode == AppEnvironment.Mode.LOCAL) {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Bu talep için henüz dijital iş fişi oluşturulmadı.")
                                    }
                                }
                            }
                        },
                        onRejectQuote = { quoteId ->
                            viewModel.rejectQuote(quoteId)
                        },
                        onCancelRequest = { requestId ->
                            viewModel.cancelJobRequest(requestId)
                        },
                        onConfirmCompletion = { requestId ->
                            viewModel.confirmJobCompletion(requestId)
                        },
                        onOpenDispute = { requestId, reason ->
                            viewModel.openDispute(requestId, reason)
                        },
                        onCreateReview = { requestId, rating, comment ->
                            viewModel.createVerifiedReview(requestId, rating, comment)
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

                is ScreenDestination.AccountSettings -> {
                    AccountSettingsScreen(isLocalMode = AppEnvironment.mode == AppEnvironment.Mode.LOCAL)
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
                        onSendPhoto = { uri ->
                            viewModel.sendPhotoMessage(screen.conversationId, uri)
                        },
                        onMarkRead = {
                            viewModel.markConversationRead(screen.conversationId)
                        },
                        onBlockClick = {
                            conv?.let { viewModel.setUserBlocked(it.participantId, true) }
                        },
                        onReportClick = {
                            conv?.let {
                                reportingTarget = Triple("user:" + it.participantId, null, it.participantName)
                            }
                        }
                    )
                }

                is ScreenDestination.ProviderDashboard -> {
                    ProviderDashboardScreen(
                        providers = providers,
                        requests = requests,
                        quotes = quotes,
                        onBackClick = { viewModel.navigateBack() },
                        onToggleOffers = { pId, status ->
                            viewModel.toggleProviderOpenForOffers(pId, status)
                        },
                        onToggleCalendarDate = { prov, dateIso ->
                            viewModel.toggleBookedDate(prov, dateIso)
                        },
                        onSubmitQuote = { reqId, prov, price, arrival, notes ->
                            viewModel.submitProviderQuote(reqId, prov, price, arrival, notes)
                        },
                        onConfirmCompletion = { requestId ->
                            viewModel.confirmJobCompletion(requestId)
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
                    onConfirmPayment = { q, customerEmail ->
                        viewModel.fundEscrowPayment(
                            quote = q,
                            jobTitle = req.title,
                            customerName = req.customerName,
                            district = req.district,
                            customerEmail = FirebaseAuth.getInstance().currentUser?.email.orEmpty(),
                            onReceiptGenerated = { receipt ->
                                activeReceipt = receipt
                            },
                            onCheckoutUrl = { url ->
                                runCatching {
                                    require(com.example.integration.isAllowedPaytrCheckoutUrl(url)) {
                                        "Ödeme adresi güvenlik nedeniyle reddedildi."
                                    }
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }.onFailure {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Ödeme sayfası açılamadı.")
                                    }
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
                        viewModel.openDispute(
                            receipt.requestId,
                            "Dijital iş fişi üzerinden kullanıcı itirazı."
                        )
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
                        viewModel.reportListing(providerId, reqId, reason)
                        reportingTarget = null
                    }
                )
            }
        }
    }
}
