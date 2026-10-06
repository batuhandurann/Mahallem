package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ConversationEntity
import com.example.data.local.InitialData
import com.example.data.local.JobRequestEntity
import com.example.data.local.QuoteEntity
import com.example.data.local.ServiceProviderEntity
import com.example.data.model.APP_CATEGORIES
import com.example.data.model.Category
import com.example.data.model.FeedFlowType
import com.example.data.model.SectorType
import com.example.data.model.UrgencyMode
import com.example.data.repository.MarketplaceRepository
import com.example.core.AppEnvironment
import com.example.payment.parseTryAmountMinor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class ScreenDestination {
    object Home : ScreenDestination()
    data class ProviderDetail(val providerId: String) : ScreenDestination()
    data class CreateRequest(val preselectedCategory: String? = null, val isEmergency: Boolean = false) : ScreenDestination()
    object PublishProviderOffer : ScreenDestination()
    object MyRequests : ScreenDestination()
    object ProviderDashboard : ScreenDestination()
    data class Chat(val conversationId: String) : ScreenDestination()
    object ConversationsList : ScreenDestination()
    object MapView : ScreenDestination()
}

class MarketplaceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MarketplaceRepository
    private val cloudRepository = com.example.backend.CloudMarketplaceRepository()
    private val cloudChatRepository = com.example.backend.CloudChatRepository()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = MarketplaceRepository(database.appDao())
        viewModelScope.launch {
            repository.checkAndSeedInitialData()
        }
    }

    // --- Navigation & Role ---
    private val _screenStack = MutableStateFlow<List<ScreenDestination>>(listOf(ScreenDestination.Home))
    val screenStack: StateFlow<List<ScreenDestination>> = _screenStack.asStateFlow()

    val currentScreen: StateFlow<ScreenDestination> = _screenStack.combine(_screenStack) { stack, _ ->
        stack.lastOrNull() ?: ScreenDestination.Home
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScreenDestination.Home)

    private val _isProviderMode = MutableStateFlow(false)
    val isProviderMode: StateFlow<Boolean> = _isProviderMode.asStateFlow()

    // --- Dual Flow: Flow A (Usta İlanları / Letgo) vs Flow B (Müşteri Talepleri / Armut) ---
    private val _feedFlowType = MutableStateFlow(FeedFlowType.ALL)
    val feedFlowType: StateFlow<FeedFlowType> = _feedFlowType.asStateFlow()

    // --- Filters ---
    private val _selectedSector = MutableStateFlow(SectorType.ALL)
    val selectedSector: StateFlow<SectorType> = _selectedSector.asStateFlow()

    private val _selectedUrgency = MutableStateFlow(UrgencyMode.ALL)
    val selectedUrgency: StateFlow<UrgencyMode> = _selectedUrgency.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(FlowPreview::class)
    private val debouncedSearchQuery = _searchQuery
        .debounce(SEARCH_DEBOUNCE_MS)
        .distinctUntilChanged()

    private val _selectedDistrict = MutableStateFlow("Tüm İlçeler")
    val selectedDistrict: StateFlow<String> = _selectedDistrict.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    // --- Autocomplete search suggestions (Like Letgo / Google / Sahibinden) ---
    val searchSuggestions: StateFlow<List<String>> = _searchQuery.combine(_selectedSector) { query, sector ->
        if (query.length < 2) emptyList()
        else {
            val suggestions = mutableListOf<String>()
            val matchingCats = APP_CATEGORIES.filter {
                it.name.contains(query, ignoreCase = true) ||
                        it.popularTags.any { tag -> tag.contains(query, ignoreCase = true) }
            }
            matchingCats.forEach { cat ->
                suggestions.add(cat.name)
                cat.popularTags.filter { it.contains(query, ignoreCase = true) }.forEach { tag ->
                    if (!suggestions.contains(tag)) suggestions.add(tag)
                }
            }
            suggestions.take(5)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Providers Flow (Flow A: Esnaf/Hizmet İlanları) ---
    @OptIn(ExperimentalCoroutinesApi::class)
    val providers: StateFlow<List<ServiceProviderEntity>> = combine(
        _selectedSector,
        _selectedUrgency,
        _selectedCategory,
        debouncedSearchQuery,
        _selectedDistrict
    ) { sector, urgency, category, query, district ->
        FilterState(sector, urgency, category, query, district)
    }.flatMapLatest { f ->
        if (AppEnvironment.mode == AppEnvironment.Mode.LOCAL) {
            repository.getFilteredProviders(f.sector, f.urgency, f.category, f.query, f.district)
        } else {
            cloudRepository.observeProviders().map { list ->
                list.filter { p ->
                    val sectorMatch = f.sector == SectorType.ALL || p.sector == f.sector.name
                    val urgencyMatch = when (f.urgency) {
                        UrgencyMode.ALL -> true
                        UrgencyMode.EMERGENCY -> p.isEmergencyAvailable
                        UrgencyMode.PLANNED -> !p.isEmergencyAvailable || p.isOpenForOffers
                    }
                    val categoryMatch = f.category.isNullOrBlank() || p.categoryId == f.category
                    val districtMatch = f.district == "Tüm İlçeler" || p.district.contains(f.district.split("/").first().trim(), true)
                    val queryMatch = f.query.isBlank() || p.name.contains(f.query, true) || p.title.contains(f.query, true) || p.bio.contains(f.query, true)
                    sectorMatch && urgencyMatch && categoryMatch && districtMatch && queryMatch
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val jobRequests: StateFlow<List<JobRequestEntity>> = combine(
        _selectedSector,
        _selectedUrgency,
        _selectedCategory,
        debouncedSearchQuery,
        _selectedDistrict
    ) { sector, urgency, category, query, district ->
        FilterState(sector, urgency, category, query, district)
    }.flatMapLatest { f ->
        if (AppEnvironment.mode == AppEnvironment.Mode.LOCAL) {
            repository.getFilteredRequests(f.sector, f.urgency, f.category, f.query, f.district)
        } else {
            cloudRepository.observeRequests().map { list ->
                list.filter { req ->
                    val sectorMatch = f.sector == SectorType.ALL || req.sector == f.sector.name
                    val urgencyMatch = f.urgency == UrgencyMode.ALL || req.urgencyMode == f.urgency.name
                    val categoryMatch = f.category.isNullOrBlank() || req.categoryId == f.category
                    val districtMatch = f.district == "Tüm İlçeler" || req.district.contains(f.district.split("/").first().trim(), true)
                    val queryMatch = f.query.isBlank() || req.title.contains(f.query, true) || req.budgetEstimate.contains(f.query, true)
                    sectorMatch && urgencyMatch && categoryMatch && districtMatch && queryMatch
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val myJobRequests: StateFlow<List<JobRequestEntity>> = if (AppEnvironment.mode == AppEnvironment.Mode.LOCAL) {
        repository.getAllRequests()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    } else {
        cloudRepository.observeMyRequests()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    // --- Quotes Flow ---
    val allQuotes: StateFlow<List<QuoteEntity>> = if (AppEnvironment.mode == AppEnvironment.Mode.LOCAL) {
        repository.getAllQuotes()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    } else {
        cloudRepository.observeQuotes()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    // --- Conversations List Flow (In-App Messaging) ---
    val conversations: StateFlow<List<ConversationEntity>> = if (AppEnvironment.mode == AppEnvironment.Mode.LOCAL) {
        repository.getAllConversations().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    } else {
        cloudChatRepository.observeConversations().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    // --- Active Chat Messages Flow ---
    private val _activeConversationId = MutableStateFlow<String?>(null)
    @OptIn(ExperimentalCoroutinesApi::class)
    val activeChatMessages: StateFlow<List<ChatMessageEntity>> = _activeConversationId.flatMapLatest { convId ->
        if (convId == null) kotlinx.coroutines.flow.flowOf(emptyList())
        else if (AppEnvironment.mode == AppEnvironment.Mode.LOCAL) repository.getMessagesForConversation(convId)
        else cloudChatRepository.observeMessages(convId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Navigation Actions ---
    fun navigateTo(destination: ScreenDestination) {
        if (destination is ScreenDestination.Chat) {
            _activeConversationId.value = destination.conversationId
            if (AppEnvironment.mode != AppEnvironment.Mode.LOCAL) {
                viewModelScope.launch {
                    runCatching {
                        cloudChatRepository.markConversationRead(destination.conversationId)
                    }.onFailure {
                        _toastMessage.value = "Sohbet okundu durumu güncellenemedi."
                    }
                }
            }
        }
        val current = _screenStack.value
        _screenStack.value = current + destination
    }

    fun navigateBack(): Boolean {
        val current = _screenStack.value
        return if (current.size > 1) {
            _screenStack.value = current.dropLast(1)
            val top = _screenStack.value.lastOrNull()
            if (top is ScreenDestination.Chat) {
                _activeConversationId.value = top.conversationId
            } else {
                _activeConversationId.value = null
            }
            true
        } else {
            false
        }
    }

    fun popToHome() {
        _activeConversationId.value = null
        _screenStack.value = listOf(ScreenDestination.Home)
    }

    fun toggleProviderMode() {
        val next = !_isProviderMode.value
        _isProviderMode.value = next
        if (next) {
            navigateTo(ScreenDestination.ProviderDashboard)
        } else {
            popToHome()
        }
    }

    // --- Filter Setters ---
    fun setFeedFlowType(flowType: FeedFlowType) {
        _feedFlowType.value = flowType
    }

    fun setSector(sector: SectorType) {
        _selectedSector.value = sector
        _selectedCategory.value = null
    }

    fun setUrgency(urgency: UrgencyMode) {
        _selectedUrgency.value = urgency
    }

    fun selectCategory(categoryId: String?) {
        _selectedCategory.value = if (_selectedCategory.value == categoryId) null else categoryId
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = normalizeSearchQuery(query)
    }

    fun setDistrict(district: String) {
        _selectedDistrict.value = district
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun toggleFavorite(provider: ServiceProviderEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(provider.id, provider.isFavorite)
        }
    }

    // --- Report / Spam Prevention ---
    fun reportListing(providerId: String?, requestId: Long?, reason: String = "") {
        viewModelScope.launch {
            if (AppEnvironment.mode != AppEnvironment.Mode.LOCAL) {
                val targetType = if (providerId != null) "PROVIDER" else "JOB_REQUEST"
                val targetId = providerId ?: requestId?.toString().orEmpty()
                if (targetId.isBlank()) {
                    _toastMessage.value = "Şikayet hedefi bulunamadı."
                    return@launch
                }
                runCatching {
                    com.example.backend.FunctionsRepository().reportContent(
                        targetType = targetType,
                        targetId = targetId,
                        reason = reason
                    )
                }.onSuccess {
                    _toastMessage.value = "Şikayetiniz güvenli şekilde alındı. Güvenlik ekibimiz inceleyecek. 🛡️"
                }.onFailure {
                    _toastMessage.value = it.message ?: "Şikayet gönderilemedi."
                }
                return@launch
            }

            if (providerId != null) {
                repository.reportProvider(providerId)
            }
            if (requestId != null) {
                repository.reportJobRequest(requestId)
            }
            _toastMessage.value = "Demo şikayetiniz alındı. 🛡️"
        }
    }

    // --- Chat & Messaging Actions ---
    fun openChatWithProvider(provider: ServiceProviderEntity) {
        viewModelScope.launch {
            val convId = if (AppEnvironment.mode == AppEnvironment.Mode.LOCAL) {
                repository.startOrGetConversation(
                    participantId = provider.id,
                    participantName = provider.name,
                    participantTitle = provider.title,
                    relatedItemTitle = provider.title
                )
            } else {
                cloudChatRepository.startOrGetConversation(provider.id, provider.title)
            }
            navigateTo(ScreenDestination.Chat(convId))
        }
    }

    fun openChatForJobRequest(request: JobRequestEntity) {
        viewModelScope.launch {
            val convId = if (AppEnvironment.mode == AppEnvironment.Mode.LOCAL) {
                repository.startOrGetConversation(
                    participantId = "req-user-" + request.id,
                    participantName = request.customerName,
                    participantTitle = "İlan Sahibi",
                    relatedItemTitle = request.title
                )
            } else {
                cloudChatRepository.startOrGetConversationForRequest(request.id, request.title)
            }
            navigateTo(ScreenDestination.Chat(convId))
        }
    }

    fun sendChatMessage(conversationId: String, text: String, isOffer: Boolean = false, offerPrice: String = "") {
        if (text.isBlank() && offerPrice.isBlank()) return
        viewModelScope.launch {
            if (AppEnvironment.mode == AppEnvironment.Mode.LOCAL) {
                repository.sendChatMessage(
                    conversationId = conversationId, senderName = "Ben", text = text, isFromMe = true,
                    isOffer = isOffer, offerPrice = offerPrice
                )
            } else {
                cloudChatRepository.sendMessage(
                    conversationId = conversationId,
                    text = if (isOffer) "Fiyat Teklifi: " + offerPrice + if (text.isBlank()) "" else " — " + text else text,
                    messageType = if (isOffer) "OFFER" else "TEXT"
                )
            }

            if (AppEnvironment.mode == AppEnvironment.Mode.LOCAL) {
                delay(1500)
                val autoReply = when {
                    isOffer -> "Teklifiniz için teşekkürler! $offerPrice makul görünüyor, detayları konuşalım."
                    text.contains("müsait", ignoreCase = true) -> "Evet, belirtilen gün ve saatte müsaitim. Konumu netleştirebilir miyiz?"
                    text.contains("indirim", ignoreCase = true) || text.contains("fiyat", ignoreCase = true) -> "İşin büyüklüğüne göre ufak bir ikram yapabilirim."
                    else -> "Mesajınızı aldım! Size en kısa sürede dönüş sağlayacağım."
                }
                repository.sendChatMessage(
                    conversationId = conversationId,
                    senderName = "Hizmet Sağlayıcı",
                    text = autoReply,
                    isFromMe = false
                )
            }
        }
    }

    fun sendVoiceNote(conversationId: String, durationSeconds: Int) {
        viewModelScope.launch {
            if (AppEnvironment.mode == AppEnvironment.Mode.LOCAL) {
                repository.sendChatMessage(
                    conversationId = conversationId, senderName = "Ben", text = "🎙️ Sesli Not",
                    isFromMe = true, isVoiceNote = true, voiceDurationSeconds = durationSeconds
                )
            } else {
                cloudChatRepository.sendMessage(conversationId, "🎙️ Sesli Not ($durationSeconds sn)", "VOICE")
            }
            if (AppEnvironment.mode == AppEnvironment.Mode.LOCAL) {
                delay(1500)
                repository.sendChatMessage(
                    conversationId = conversationId,
                    senderName = "Hizmet Sağlayıcı",
                    text = "Sesli mesajınızı dinledim, gayet net anlaşıldı 👍",
                    isFromMe = false
                )
            }
        }
    }

    fun sendPhotoMessage(conversationId: String, uri: android.net.Uri) {
        viewModelScope.launch {
            if (AppEnvironment.mode == AppEnvironment.Mode.LOCAL) {
                repository.sendChatMessage(
                    conversationId = conversationId,
                    senderName = "Ben",
                    text = "📷 İş / keşif fotoğrafı",
                    isFromMe = true,
                    hasPhotoAttachment = true,
                    photoDescription = "Seçilen fotoğraf"
                )
                delay(1500)
                repository.sendChatMessage(
                    conversationId = conversationId,
                    senderName = "Hizmet Sağlayıcı",
                    text = "Fotoğrafı inceledim. Gerekli alet ve malzemeleri hazırlıyorum.",
                    isFromMe = false
                )
                return@launch
            }

            val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            if (currentUser == null) {
                _toastMessage.value = "Fotoğraf göndermek için giriş yapmanız gerekiyor."
                return@launch
            }

            runCatching {
                val path = com.example.media.StorageRepository().uploadChatImage(
                    senderUid = currentUser.uid,
                    conversationId = conversationId,
                    uri = uri,
                    contentResolver = getApplication<Application>().contentResolver
                )
                cloudChatRepository.sendMessage(
                    conversationId = conversationId,
                    text = "📷 İş / keşif fotoğrafı",
                    messageType = "IMAGE",
                    attachmentUrl = path
                )
            }.onSuccess {
                _toastMessage.value = "Fotoğraf gönderildi."
            }.onFailure {
                _toastMessage.value = it.message ?: "Fotoğraf gönderilemedi."
            }
        }
    }

    // --- Escrow Havuz Ödeme ve Dijital İş Fişi ---
    fun fundEscrowPayment(
        quote: QuoteEntity,
        jobTitle: String,
        customerName: String,
        district: String,
        onReceiptGenerated: (com.example.data.local.DigitalReceiptEntity) -> Unit,
        onCheckoutUrl: (String) -> Unit = { }
    ) {
        viewModelScope.launch {
            if (AppEnvironment.mode != AppEnvironment.Mode.LOCAL) {
                if (quote.amountMinor <= 0L) {
                    _toastMessage.value = "Bu teklif gerçek ödeme için güvenilir tutar içermiyor."
                    return@launch
                }
                runCatching {
                    com.example.integration.ProductionPaymentGateway().createPayment(
                        requestId = quote.requestId,
                        quoteId = quote.id,
                        amountMinor = quote.amountMinor,
                        currency = "TRY"
                    )
                }.onSuccess { intent ->
                    _toastMessage.value = "Güvenli ödeme ekranı açılıyor. Ödeme durumu webhook ile doğrulanacak."
                    onCheckoutUrl(intent.checkoutUrl)
                }.onFailure {
                    _toastMessage.value = it.message ?: "Ödeme başlatılamadı."
                }
                return@launch
            }

            val code = repository.fundEscrowPayment(
                requestId = quote.requestId,
                quoteId = quote.id,
                amount = quote.price,
                jobTitle = jobTitle,
                customerName = customerName,
                providerName = quote.providerName,
                providerTitle = quote.providerTitle,
                district = district
            )
            _toastMessage.value = "Demo ödemesi yerel test havuzuna işlendi. Gerçek para hareketi yok."
            val receipt = com.example.data.local.DigitalReceiptEntity(
                receiptCode = code, requestId = quote.requestId, quoteId = quote.id,
                jobTitle = jobTitle, customerName = customerName, providerName = quote.providerName,
                providerTitle = quote.providerTitle, totalAmount = quote.price, escrowStatus = "LOCKED",
                warrantyInfo = "2 Yıl İşçilik & Malzeme Mahallemde Güvencesi",
                createdAtDate = "05.10.2026", district = district
            )
            onReceiptGenerated(receipt)
        }
    }

    fun releaseEscrowPayment(requestId: Long, quoteId: Long, receiptCode: String) {
        viewModelScope.launch {
            if (AppEnvironment.mode == AppEnvironment.Mode.LOCAL) {
                repository.releaseEscrowPayment(requestId, quoteId, receiptCode)
                _toastMessage.value = "Demo ödeme yerelde serbest bırakıldı."
            } else {
                _toastMessage.value = "Gerçek ödeme serbest bırakma, doğrulanmış ödeme kimliği üzerinden sunucudan yapılacak."
            }
        }
    }

    fun loadReceiptForRequest(
        requestId: Long,
        onLoaded: (com.example.data.local.DigitalReceiptEntity?) -> Unit
    ) {
        viewModelScope.launch {
            if (AppEnvironment.mode != AppEnvironment.Mode.LOCAL) {
                _toastMessage.value = "Dijital iş fişi gerçek ödeme tamamlandıktan sonra sunucudan gösterilecek."
                onLoaded(null)
                return@launch
            }
            onLoaded(repository.getReceiptForRequest(requestId).first())
        }
    }

    // --- Flow A: Esnaf / Hizmet İlanı Yayınlama ---
    fun publishProviderListing(
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
    ) {
        viewModelScope.launch {
            val id = "p-custom-${System.currentTimeMillis()}"
            val entity = ServiceProviderEntity(
                id = id,
                name = name.ifBlank { "Usta $name" },
                title = title.ifBlank { "Hizmet Uzmanı" },
                sector = sector.name,
                categoryId = categoryId,
                rating = 5.0,
                reviewCount = 1,
                experienceYears = experienceYears,
                district = district,
                city = district.split(",").lastOrNull()?.trim() ?: "İstanbul",
                hourlyOrBasePrice = price.ifBlank { "Anlaşmaya Bağlı" },
                isEmergencyAvailable = isEmergency,
                verifiedSafeBadge = hasSafeBadge,
                mykCertified = hasMykBadge,
                childSafeCertified = hasChildSafeBadge,
                phoneVerified = true,
                daysRemaining = 30,
                isReported = false,
                paintBrandsJson = if (sector == SectorType.HOME_REPAIR) "[\"$brandsOrCharacters\"]" else "[]",
                charactersOfferedJson = if (sector == SectorType.EVENT_ENTERTAINMENT) "[\"$brandsOrCharacters\"]" else "[]",
                includedEquipmentsJson = "[\"$equipments\"]",
                bookedDatesJson = "[]",
                isOpenForOffers = true,
                phone = phone.ifBlank { "05xx xxx xx xx" },
                bio = bio.ifBlank { "Garantili ve güvenilir hizmet sunuyorum." }
            )
            if (AppEnvironment.mode != AppEnvironment.Mode.LOCAL) {
                val result = runCatching { cloudRepository.saveProvider(entity) }
                result.exceptionOrNull()?.let {
                    _toastMessage.value = "Hizmet ilanı yayınlanamadı: ${it.message ?: "Bilinmeyen hata"}"
                    return@launch
                }
            }

            if (AppEnvironment.mode == AppEnvironment.Mode.LOCAL) {
                repository.publishProviderListing(entity)
            }
            _toastMessage.value = if (AppEnvironment.mode == AppEnvironment.Mode.LOCAL) {
                "Demo hizmet ilanı hazır."
            } else {
                "Hizmet ilanınız güvenli şekilde yayına alındı."
            }
            popToHome()
        }
    }

    // --- Flow B: Hizmet Arayan İhtiyaç İlanı (Armut) ---
    fun submitJobRequest(
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
        areaSquareMeters: Int,
        roomCount: String,
        isFurnished: Boolean,
        materialsIncluded: Boolean,
        renovationNotes: String,
        eventType: String,
        durationHours: Int,
        targetAgeGroup: String,
        costumeOrCharacter: String,
        extraServices: String,
        budget: String
    ) {
        viewModelScope.launch {
            val entity = JobRequestEntity(
                title = title,
                sector = sector.name,
                categoryId = category.id,
                district = district,
                urgencyMode = urgency.name,
                eventOrJobDate = date,
                eventTime = time,
                address = address,
                status = "PENDING",
                customerName = customerName.ifBlank { "Mahalle Sakini" },
                customerPhone = customerPhone.ifBlank { "05xx xxx xx xx" },
                phoneVerified = true,
                daysRemaining = 7,
                isReported = false,
                areaSquareMeters = areaSquareMeters,
                roomCount = roomCount,
                isFurnished = isFurnished,
                materialsIncluded = materialsIncluded,
                renovationNotes = renovationNotes,
                eventType = eventType,
                durationHours = durationHours,
                targetAgeGroup = targetAgeGroup,
                selectedCostumeOrCharacter = costumeOrCharacter,
                extraServicesRequested = extraServices,
                budgetEstimate = budget
            )
            val storedEntity = if (AppEnvironment.mode == AppEnvironment.Mode.LOCAL) {
                entity.copy(id = repository.createJobRequest(entity))
            } else {
                entity.copy(id = System.currentTimeMillis().coerceAtLeast(1L))
            }

            if (AppEnvironment.mode != AppEnvironment.Mode.LOCAL) {
                val result = runCatching { cloudRepository.saveJobRequest(storedEntity) }
                result.exceptionOrNull()?.let {
                    _toastMessage.value = "Talep yayınlanamadı: ${it.message ?: "Bilinmeyen hata"}"
                    return@launch
                }
            } else {
                simulateProviderResponse(storedEntity.id, category, urgency)
            }

            _toastMessage.value = if (AppEnvironment.mode == AppEnvironment.Mode.LOCAL) {
                "Demo talebi hazır."
            } else {
                "Talebiniz güvenli şekilde yayınlandı ve uygun hizmet verenlere iletilmek üzere sisteme alındı."
            }
            popToHome()
            navigateTo(ScreenDestination.MyRequests)
        }
    }

    private suspend fun simulateProviderResponse(requestId: Long, category: Category, urgency: UrgencyMode) {
        val all = InitialData.getSeedProviders().filter { it.categoryId == category.id }
        val responder = all.firstOrNull() ?: InitialData.getSeedProviders().first()

        val priceStr = when (category.id) {
            "boyaci" -> "11.500 ₺ (Malzeme hariç, astar dahil)"
            "palyaco" -> "2.400 ₺ (Yüz boyama + sosis balon dahil)"
            "maskot" -> "3.200 ₺ (Spiderman & Mini Disco)"
            "tesisatci" -> "1.100 ₺ (Noktasal tespit + parça garantisi)"
            "cilingir" -> "650 ₺ (Hasarsız kapı açma)"
            "ev_temizligi" -> "1.600 ₺ (Tüm gün detaylı temizlik)"
            "matematik_ders" -> "900 ₺ / Saat (Birebir soru çözümü)"
            else -> "1.750 ₺"
        }

        val arrivalStr = if (urgency == UrgencyMode.EMERGENCY) "30 dakika içinde kapınızda" else "Belirttiğiniz tarihte müsaitiz"

        val quote = QuoteEntity(
            requestId = requestId,
            providerId = responder.id,
            providerName = responder.name,
            providerTitle = responder.title,
            providerRating = responder.rating,
            price = priceStr,
            durationOrArrival = arrivalStr,
            notes = "Talebinizi inceledim. Detaylar ve istekleriniz doğrultusunda en kaliteli hizmeti garantili olarak vermeye hazırım. İletişime geçebilirsiniz.",
            status = "PENDING"
        )
        repository.sendQuote(quote)
    }

    fun acceptQuote(requestId: Long, quoteId: Long, providerName: String) {
        viewModelScope.launch {
            if (AppEnvironment.mode == AppEnvironment.Mode.LOCAL) {
                repository.acceptQuote(requestId, quoteId)
                _toastMessage.value = "$providerName teklifini onayladınız! İletişim bilgileri paylaşıldı 🤝"
            } else {
                runCatching { com.example.backend.FunctionsRepository().acceptQuote(quoteId) }
                    .onSuccess { _toastMessage.value = "$providerName teklifi güvenli sunucu üzerinden onaylandı. 🤝" }
                    .onFailure { _toastMessage.value = it.message ?: "Teklif onaylanamadı." }
            }
        }
    }

    fun rejectQuote(quoteId: Long) {
        viewModelScope.launch {
            if (AppEnvironment.mode == AppEnvironment.Mode.LOCAL) {
                repository.rejectQuote(quoteId)
                _toastMessage.value = "Teklif reddedildi."
            } else {
                runCatching { com.example.backend.FunctionsRepository().rejectQuote(quoteId) }
                    .onSuccess { _toastMessage.value = "Teklif güvenli sunucu üzerinden reddedildi." }
                    .onFailure { _toastMessage.value = it.message ?: "Teklif reddedilemedi." }
            }
        }
    }

    fun submitProviderQuote(
        requestId: Long,
        provider: ServiceProviderEntity,
        price: String,
        arrival: String,
        notes: String
    ) {
        viewModelScope.launch {
            val amountMinor = parseTryAmountMinor(price)
            if (amountMinor == null) {
                _toastMessage.value = "Teklif tutarı tek bir kesin TL tutarı olmalı. Örn: 3.500 ₺"
                return@launch
            }
            val quote = QuoteEntity(
                requestId = requestId,
                providerId = provider.id,
                providerName = provider.name,
                providerTitle = provider.title,
                providerRating = provider.rating,
                price = price,
                amountMinor = amountMinor,
                durationOrArrival = arrival,
                notes = notes,
                status = "PENDING"
            )
            if (AppEnvironment.mode != AppEnvironment.Mode.LOCAL) {
                val cloudQuote = quote.copy(id = System.currentTimeMillis().coerceAtLeast(1L))
                val result = runCatching { cloudRepository.saveQuote(cloudQuote) }
                result.exceptionOrNull()?.let {
                    _toastMessage.value = "Teklif gönderilemedi: ${it.message ?: "Bilinmeyen hata"}"
                    return@launch
                }
            } else {
                repository.sendQuote(quote)
            }
            _toastMessage.value = if (AppEnvironment.mode == AppEnvironment.Mode.LOCAL) {
                "Demo teklifi hazır."
            } else {
                "Teklifiniz güvenli şekilde müşteriye iletildi."
            }
        }
    }

    // --- Provider Calendar & Availability Management ---
    fun toggleProviderOpenForOffers(providerId: String, currentStatus: Boolean) {
        viewModelScope.launch {
            if (AppEnvironment.mode != AppEnvironment.Mode.LOCAL) {
                runCatching {
                    com.example.backend.FunctionsRepository().updateProviderAvailability(
                        providerId = providerId,
                        isOpenForOffers = !currentStatus
                    )
                }.onSuccess {
                    _toastMessage.value = if (!currentStatus)
                        "Teklif alımı ve takviminiz açıldı ✅"
                    else
                        "Teklif alımı kapatıldı (Müsait değil) ⏸️"
                }.onFailure {
                    _toastMessage.value = it.message ?: "Müsaitlik güncellenemedi."
                }
                return@launch
            }
            repository.toggleOpenForOffers(providerId, !currentStatus)
            _toastMessage.value = if (!currentStatus)
                "Teklif alımı ve takvim rezervasyonlara açıldı ✅"
            else
                "Teklif alımı kapatıldı (Müsait değil) ⏸️"
        }
    }

    fun toggleBookedDate(provider: ServiceProviderEntity, dateIso: String) {
        viewModelScope.launch {
            val currentlyBooked = provider.bookedDatesJson
                .removePrefix("[")
                .removeSuffix("]")
                .split(",")
                .map { it.trim().trim('\"') }
                .filter { it.isNotBlank() }
                .contains(dateIso)
            val nextBooked = !currentlyBooked

            if (AppEnvironment.mode != AppEnvironment.Mode.LOCAL) {
                runCatching {
                    com.example.backend.FunctionsRepository().updateProviderAvailability(
                        providerId = provider.id,
                        dateIso = dateIso,
                        dateBooked = nextBooked
                    )
                }.onSuccess {
                    _toastMessage.value = if (nextBooked)
                        "$dateIso tarihi 'Dolu / Rezerve' olarak işaretlendi 🔴"
                    else
                        "$dateIso tarihi müsait olarak açıldı 🟢"
                }.onFailure {
                    _toastMessage.value = it.message ?: "Takvim güncellenemedi."
                }
                return@launch
            }

            val list = provider.bookedDatesJson
                .removePrefix("[")
                .removeSuffix("]")
                .split(",")
                .map { it.trim().trim('\"') }
                .filter { it.isNotBlank() }
                .toMutableList()
            if (nextBooked) list.add(dateIso) else list.remove(dateIso)
            val newJson = "[" + list.distinct().sorted().joinToString(",") { "\"$it\"" } + "]"
            repository.updateBookedDates(provider.id, newJson)
            _toastMessage.value = if (nextBooked)
                "$dateIso tarihi 'Dolu / Rezerve' olarak işaretlendi 🔴"
            else
                "$dateIso tarihi müsait olarak açıldı 🟢"
        }
    }
}

internal const val MAX_SEARCH_QUERY_LENGTH = 80
internal const val SEARCH_DEBOUNCE_MS = 250L

internal fun normalizeSearchQuery(query: String): String =
    query.trim().take(MAX_SEARCH_QUERY_LENGTH)

private data class FilterState(
    val sector: SectorType,
    val urgency: UrgencyMode,
    val category: String?,
    val query: String,
    val district: String
)
