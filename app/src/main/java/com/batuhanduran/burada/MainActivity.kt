package com.batuhanduran.burada

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.batuhanduran.burada.data.local.DigitalReceiptEntity
import com.batuhanduran.burada.data.local.JobRequestEntity
import com.batuhanduran.burada.data.local.QuoteEntity
import com.batuhanduran.burada.data.model.FeedFlowType
import com.batuhanduran.burada.data.model.SectorType
import com.batuhanduran.burada.data.model.UrgencyMode
import com.batuhanduran.burada.ui.MarketplaceViewModel
import com.batuhanduran.burada.ui.BuradaApp
import com.batuhanduran.burada.ui.ScreenDestination
import com.batuhanduran.burada.ui.components.CostEstimatorSheet
import com.batuhanduran.burada.ui.components.DigitalReceiptDialog
import com.batuhanduran.burada.ui.components.EscrowPaymentDialog
import com.batuhanduran.burada.ui.components.ReportListingDialog
import com.batuhanduran.burada.ui.screens.*
import com.batuhanduran.burada.ui.theme.BuradaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BuradaTheme {
                BuradaApp()
            }
        }
    }
}

@Composable
fun MarketplaceApp(
    viewModel: MarketplaceViewModel = viewModel(),
    accountHeader: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val blockedUids by viewModel.blockedUids.collectAsStateWithLifecycle()
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
    val myRequests by viewModel.myRequests.collectAsStateWithLifecycle()
    val quotes by viewModel.allQuotes.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val activeChatMessages by viewModel.activeChatMessages.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    var reportingTarget by remember { mutableStateOf<Triple<String?, String?, String>?>(null) }
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
        containerColor = androidx.compose.ui.graphics.Color.White,
        modifier = Modifier.fillMaxSize(),
        topBar = accountHeader,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (isMainTabScreen) {
                com.batuhanduran.burada.ui.components.MarketplaceBottomNavBar(
                    currentScreen = currentScreen,
                    onTabSelected = { target ->
                        viewModel.navigateTo(target)
                    },
                    unreadMessagesCount = conversations.sumOf { it.unreadCount },
                    activeRequestsCount = myRequests.size
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.White).padding(innerPadding)) {
            when (val screen = currentScreen) {
                is ScreenDestination.Home -> {
                    HomeScreen(
                        providers = providers.filter { it.ownerUid !in blockedUids },
                        jobRequests = requests.filter { it.ownerUid !in blockedUids },
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
                        activeRequestsCount = myRequests.size,
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
                        onNeighborhoodSelected = viewModel::setNeighborhood,
                        onCoordinateSelected = viewModel::setCoordinate,
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
                        onNeighborhoodSelected = viewModel::setNeighborhood,
                        onCoordinateSelected = viewModel::setCoordinate,
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
                        onViewReceipt = { viewModel.showUnavailablePayment() },
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
                        blockedParticipantIds = blockedUids,
                        onUnblockParticipant = { viewModel.setUserBlocked(it, false) },
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
                        onSendMessage = { text, isOffer, price, onResult ->
                            viewModel.sendChatMessage(screen.conversationId, text, isOffer, price, onResult)
                        },
                        onSendVoiceNote = { duration ->
                            viewModel.sendVoiceNote(screen.conversationId, duration)
                        },
                        onSendPhoto = { uri ->
                            viewModel.sendPhotoMessage(context, screen.conversationId, uri)
                        },
                        onCallClick = {
                            viewModel.sendChatMessage(
                                screen.conversationId,
                                "📞 Sesli arama isteği gönderildi.",
                                false,
                                ""
                            )
                        },
                        onReportClick = {},
                        isBlocked = conv?.participantId in blockedUids,
                        onBlockChanged = { blocked -> conv?.let { viewModel.setUserBlocked(it.participantId, blocked) } },
                        onSubmitReport = { reason, details -> conv?.let { viewModel.reportConversation(it.id, it.participantId, reason, details) } }
                    )
                }

                is ScreenDestination.ProviderDashboard -> {
                    ProviderDashboardScreen(
                        providers = providers.filter { it.ownerUid == viewModel.currentUid },
                        requests = requests.filter { it.ownerUid != viewModel.currentUid && it.status == "PENDING" },
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
                    onConfirmReport = { reason, details ->
                        viewModel.reportListing(providerId, reqId, reason, details)
                        reportingTarget = null
                    }
                )
            }
        }
    }
}
