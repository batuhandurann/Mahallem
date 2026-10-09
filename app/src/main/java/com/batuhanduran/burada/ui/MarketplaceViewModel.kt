package com.batuhanduran.burada.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.batuhanduran.burada.data.local.ChatMessageEntity
import com.batuhanduran.burada.data.local.ConversationEntity
import com.batuhanduran.burada.data.local.JobRequestEntity
import com.batuhanduran.burada.data.local.QuoteEntity
import com.batuhanduran.burada.data.local.ServiceProviderEntity
import com.batuhanduran.burada.data.model.APP_CATEGORIES
import com.batuhanduran.burada.data.model.Category
import com.batuhanduran.burada.data.model.FeedFlowType
import com.batuhanduran.burada.data.model.SectorType
import com.batuhanduran.burada.data.model.UrgencyMode
import com.batuhanduran.burada.data.repository.MarketplaceRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import com.batuhanduran.burada.moderation.*
import com.batuhanduran.burada.data.model.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

sealed class ScreenDestination {
    object Home : ScreenDestination()
    data class ProviderDetail(val providerId: String) : ScreenDestination()
    data class CreateRequest(val preselectedCategory: String? = null, val isEmergency: Boolean = false) : ScreenDestination()
    object PublishProviderOffer : ScreenDestination()
    object MyRequests : ScreenDestination()
    object ProviderDashboard : ScreenDestination()
    object MyJobs : ScreenDestination()
    data class JobDetail(val requestId: String) : ScreenDestination()
    data class Chat(val conversationId: String) : ScreenDestination()
    object ConversationsList : ScreenDestination()
    object MapView : ScreenDestination()
}

class MarketplaceViewModel @JvmOverloads constructor(
    application: Application,
    sessionUid: String = requireNotNull(com.batuhanduran.burada.data.remote.FirebaseServices.auth.currentUser).uid
) : AndroidViewModel(application) {

    private val repository = MarketplaceRepository(uid = sessionUid)
    val currentUid: String get() = repository.uid
    private val moderation = ModerationRepository(uid = sessionUid)
    val blockedUids = moderation.observeBlockedUids().catch {
        _toastMessage.value = "Engelleme tercihleri yüklenemedi. Yeniden giriş yapın."
        emit(emptySet())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    private var selectedNeighborhood: NeighborhoodRef? = null
    private var selectedCoordinate: GeoCoordinate? = null
    fun setNeighborhood(neighborhood: NeighborhoodRef) { selectedNeighborhood = neighborhood }
    fun setCoordinate(coordinate: GeoCoordinate) { selectedCoordinate = coordinate }
    private fun requiredNeighborhood() = requireNotNull(selectedNeighborhood) { "Bir mahalle seçin." }
    private fun publicGeoHash() = selectedCoordinate?.let { Geohash.encode(it, 5) } ?: ""
    fun setUserBlocked(uid: String, blocked: Boolean) { action { moderation.setBlocked(uid, blocked) } }
    fun reportConversation(id: String, targetUid: String, reason: ReportReason, details: String) {
        action { moderation.submitReport(ReportDraft(ReportTargetType.CONVERSATION, id, targetUid, reason, details))
            _toastMessage.value = "Şikayetiniz inceleme için kaydedildi." }
    }


    private fun action(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() }
        catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            _toastMessage.value = "İşlem süresi doldu. Verileri yeniden açarak sonucu kontrol edin."
        }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            _toastMessage.value = e.message ?: "İşlem tamamlanamadı. Bağlantınızı kontrol edin."
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

    private val _selectedDistrict = MutableStateFlow("Tüm İlçeler")
    val selectedDistrict: StateFlow<String> = _selectedDistrict.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    init { viewModelScope.launch { repository.errors.collect { _toastMessage.value = it } } }

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
        _searchQuery,
        _selectedDistrict
    ) { sector, urgency, category, query, district ->
        FilterState(sector, urgency, category, query, district)
    }.flatMapLatest { f ->
        repository.getFilteredProviders(f.sector, f.urgency, f.category, f.query, f.district)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Owner dashboard must not inherit marketplace search, category, district or urgency filters.
    val ownedProviders: StateFlow<List<ServiceProviderEntity>> = repository.getOwnedProviders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Job Requests Flow (Flow B: Hizmet Arayan Talepleri - Armut) ---
    @OptIn(ExperimentalCoroutinesApi::class)
    val jobRequests: StateFlow<List<JobRequestEntity>> = combine(
        _selectedSector,
        _selectedUrgency,
        _selectedCategory,
        _searchQuery,
        _selectedDistrict
    ) { sector, urgency, category, query, district ->
        FilterState(sector, urgency, category, query, district)
    }.flatMapLatest { f ->
        repository.getFilteredRequests(f.sector, f.urgency, f.category, f.query, f.district)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val myRequests = repository.getMyRequests().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val assignedRequests = repository.getAssignedRequests().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val jobLifecycles = repository.getJobLifecycles().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    @OptIn(ExperimentalCoroutinesApi::class)
    val activeJobEvents = combine(currentScreen, jobLifecycles) { screen, jobs ->
        (screen as? ScreenDestination.JobDetail)?.requestId?.takeIf { id -> jobs.any { it.requestId == id } }
    }.flatMapLatest { id -> if (id == null) kotlinx.coroutines.flow.flowOf(emptyList()) else repository.getJobEvents(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _busyJobIds = MutableStateFlow<Set<String>>(emptySet())
    val busyJobIds = _busyJobIds.asStateFlow()
    private val _jobErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val jobErrors = _jobErrors.asStateFlow()
    private data class JobAttempt(val requestId: String, val action: JobAction, val version: Int,
        val note: String, val reason: String, val id: String = java.util.UUID.randomUUID().toString())
    private val jobAttempts = mutableMapOf<String, JobAttempt>()

    fun submitJobAction(requestId: String, action: JobAction, version: Int, note: String, reason: String) {
        if (requestId in _busyJobIds.value) return
        val previous = jobAttempts[requestId]
        val candidate = JobAttempt(requestId, action, version, note.trim(), reason)
        val attempt = if (previous != null && candidate.copy(id = previous.id) == previous) previous else candidate
        jobAttempts[requestId] = attempt
        performJobAction(attempt)
    }
    fun retryJobAction(requestId: String) { jobAttempts[requestId]?.let(::performJobAction) }
    private fun performJobAction(attempt: JobAttempt) {
        if (attempt.requestId in _busyJobIds.value) return
        _busyJobIds.value += attempt.requestId
        _jobErrors.value -= attempt.requestId
        viewModelScope.launch {
            try {
                repository.manageJob(attempt.requestId, attempt.action, attempt.version, attempt.note, attempt.reason, attempt.id)
                jobAttempts.remove(attempt.requestId)
                _toastMessage.value = "İşlem kaydedildi. Güncel durum iş ekranında gösterilecek."
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                _jobErrors.value += attempt.requestId to "Yanıt alınamadı. Durumu kontrol edin; tekrar denemek aynı işlemi çoğaltmaz."
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                _jobErrors.value += attempt.requestId to (e.message ?: "İşlem kaydedilemedi. Bağlantınızı kontrol edin.")
            } finally { _busyJobIds.value -= attempt.requestId }
        }
    }

    val reviewedRequestIds = repository.getReviewedRequestIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _reviewBusy = MutableStateFlow<Set<String>>(emptySet())
    val reviewBusy = _reviewBusy.asStateFlow()
    private val _reviewProvider = MutableStateFlow<String?>(null)
    private val _reviewLimit = MutableStateFlow(20L)
    @OptIn(ExperimentalCoroutinesApi::class)
    val providerReviews = combine(_reviewProvider, _reviewLimit) { id, limit -> id to limit }
        .flatMapLatest { (id, limit) -> if (id == null) flowOf(emptyList()) else repository.getProviderReviews(id, limit) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun selectReviewProvider(id: String?) { _reviewProvider.value = id; _reviewLimit.value = 20 }
    fun loadMoreReviews() { _reviewLimit.value += 20 }
    private fun reviewAction(id: String, block: suspend () -> Unit) {
        if (id in _reviewBusy.value) return
        _reviewBusy.value = _reviewBusy.value + id
        action { try { block() } finally { _reviewBusy.value = _reviewBusy.value - id } }
    }
    fun submitJobReview(id: String, rating: Int, comment: String) = reviewAction(id) {
        repository.submitJobReview(id, rating, comment)
        _toastMessage.value = "Değerlendirmeniz kaydedildi. Teşekkürler."
    }
    fun reportJobReview(providerId: String, reviewId: String, reason: String) = action {
        repository.reportJobReview(providerId, reviewId, reason)
        _toastMessage.value = "Değerlendirme şikayetiniz inceleme için kaydedildi."
    }

    // --- Quotes Flow ---
    val allQuotes: StateFlow<List<QuoteEntity>> = repository.getAllQuotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Conversations List Flow (In-App Messaging) ---
    val conversations: StateFlow<List<ConversationEntity>> = repository.getAllConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Active Chat Messages Flow ---
    private val _activeConversationId = MutableStateFlow<String?>(null)
    @OptIn(ExperimentalCoroutinesApi::class)
    val activeChatMessages: StateFlow<List<ChatMessageEntity>> = _activeConversationId.flatMapLatest { convId ->
        if (convId == null) kotlinx.coroutines.flow.flowOf(emptyList())
        else repository.getMessagesForConversation(convId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Navigation Actions ---
    fun navigateTo(destination: ScreenDestination) {
        if (destination is ScreenDestination.CreateRequest || destination is ScreenDestination.PublishProviderOffer) {
            selectedNeighborhood = null
            selectedCoordinate = null
        }
        if (destination is ScreenDestination.Chat) {
            _activeConversationId.value = destination.conversationId
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
        _searchQuery.value = query
    }

    fun setDistrict(district: String) {
        _selectedDistrict.value = district
    }

    fun showUnavailablePayment() {
        _toastMessage.value = "Ödeme ve makbuz altyapısı henüz kullanılamıyor."
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun toggleFavorite(provider: ServiceProviderEntity) {
        action {
            repository.toggleFavorite(provider.id, provider.isFavorite)
        }
    }

    // --- Report / Spam Prevention ---
    fun reportListing(providerId: String?, requestId: String?, reason: ReportReason, details: String) {
        action {
            val provider = providers.value.find { it.id == providerId }
            val request = jobRequests.value.find { it.id == requestId }
            val targetUid = provider?.ownerUid ?: request?.ownerUid ?: error("İlan bulunamadı.")
            val targetId = if (provider != null) "provider:${provider.id}" else "request:${requireNotNull(request).id}"
            moderation.submitReport(ReportDraft(ReportTargetType.LISTING, targetId, targetUid, reason, details))
            _toastMessage.value = "Şikayetiniz inceleme için kaydedildi."
        }
    }

    // --- Chat & Messaging Actions ---
    fun openChatWithProvider(provider: ServiceProviderEntity) {
        action {
            val convId = repository.startOrGetConversation(
                participantId = provider.ownerUid,
                participantName = provider.name,
                participantTitle = provider.title,
                relatedItemTitle = provider.title
            )
            navigateTo(ScreenDestination.Chat(convId))
        }
    }

    fun openChatForJobRequest(request: JobRequestEntity) {
        action {
            val convId = repository.startOrGetConversation(
                participantId = request.ownerUid,
                participantName = request.customerName,
                participantTitle = "İlan Sahibi",
                relatedItemTitle = request.title
            )
            navigateTo(ScreenDestination.Chat(convId))
        }
    }

    fun sendChatMessage(conversationId: String, text: String, isOffer: Boolean = false, offerPrice: String = "") {
        if (text.isBlank() && offerPrice.isBlank()) return
        action {
            repository.sendChatMessage(
                conversationId = conversationId,
                senderName = "Ben",
                text = text,
                isFromMe = true,
                isOffer = isOffer,
                offerPrice = offerPrice
            )

        }
    }

    fun sendVoiceNote(conversationId: String, durationSeconds: Int) {
        _toastMessage.value = "Sesli mesaj yükleme henüz kullanılamıyor. Metin mesajı gönderebilirsiniz."
    }

    fun sendPhotoMessage(context: android.content.Context, conversationId: String, uri: android.net.Uri) {
        action {
            val photo = com.batuhanduran.burada.data.remote.ConversationPhotoRepository().upload(context.applicationContext, conversationId, uri)
            repository.sendChatMessage(conversationId, "", "Fotoğraf", true, hasPhotoAttachment = true,
                photoDescription = "Fotoğraf", photoMediaId = photo.mediaId)
        }
    }

    // --- Escrow Havuz Ödeme ve Dijital İş Fişi ---
    fun fundEscrowPayment(
        quote: QuoteEntity,
        jobTitle: String,
        customerName: String,
        district: String,
        onReceiptGenerated: (com.batuhanduran.burada.data.local.DigitalReceiptEntity) -> Unit
    ) {
        action {
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
            _toastMessage.value = "Ödeme Burada Güvenli Havuzu'nda bloke edildi! 🔒"
            val receipt = com.batuhanduran.burada.data.local.DigitalReceiptEntity(
                receiptCode = code,
                requestId = quote.requestId,
                quoteId = quote.id,
                jobTitle = jobTitle,
                customerName = customerName,
                providerName = quote.providerName,
                providerTitle = quote.providerTitle,
                totalAmount = quote.price,
                escrowStatus = "LOCKED",
                warrantyInfo = "2 Yıl İşçilik & Malzeme Burada Güvencesi",
                createdAtDate = "05.10.2026",
                district = district
            )
            onReceiptGenerated(receipt)
        }
    }

    fun releaseEscrowPayment(requestId: String, quoteId: String, receiptCode: String) {
        action {
            repository.releaseEscrowPayment(requestId, quoteId, receiptCode)
            _toastMessage.value = "İş başarıyla tamamlandı ve ödeme ustaya aktarıldı! Teşekkür ederiz 🤝"
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
        action {
            val area = requiredNeighborhood()
            val id = ""
            val entity = ServiceProviderEntity(
                id = id,
                provinceId = area.provinceId, districtId = area.districtId,
                neighborhoodId = area.neighborhoodId, neighborhoodName = area.neighborhoodName, publicGeoHash = publicGeoHash(),
                name = name.ifBlank { "Usta $name" },
                title = title.ifBlank { "Hizmet Uzmanı" },
                sector = sector.name,
                categoryId = categoryId,
                rating = 0.0,
                reviewCount = 0,
                experienceYears = experienceYears,
                district = district,
                city = area.provinceName,
                hourlyOrBasePrice = price.ifBlank { "Anlaşmaya Bağlı" },
                isEmergencyAvailable = isEmergency,
                verifiedSafeBadge = false,
                mykCertified = false,
                childSafeCertified = false,
                phoneVerified = false,
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
            repository.publishProviderListing(entity)
            _toastMessage.value = "Hizmet ilanınız başarıyla yayına alındı! Mahalle sakinleri artık profilinize ulaşabilir 🎉"
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
        action {
            com.batuhanduran.burada.validation.RequestSchedules.requireValid(title, date, time)
            val area = requiredNeighborhood()
            val entity = JobRequestEntity(
                provinceId = area.provinceId, districtId = area.districtId,
                neighborhoodId = area.neighborhoodId, neighborhoodName = area.neighborhoodName, publicGeoHash = publicGeoHash(),
                title = title.trim(),
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
                phoneVerified = false,
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
            repository.createJobRequest(entity)
            _toastMessage.value = "Talebiniz yayınlandı! Bölgedeki uygun esnaf ve sanatçılara iletildi 🎉"
            popToHome()
            navigateTo(ScreenDestination.MyRequests)
        }
    }

    fun acceptQuote(requestId: String, quoteId: String, providerName: String) {
        action {
            repository.acceptQuote(requestId, quoteId)
            _toastMessage.value = "$providerName teklifini onayladınız. Detayları mesajlaşarak konuşabilirsiniz."
        }
    }

    fun rejectQuote(quoteId: String) {
        action {
            repository.rejectQuote(quoteId)
            _toastMessage.value = "Teklif reddedildi."
        }
    }

    fun submitProviderQuote(
        requestId: String,
        provider: ServiceProviderEntity,
        price: String,
        arrival: String,
        notes: String
    ) {
        action {
            val quote = QuoteEntity(
                requestId = requestId,
                providerId = provider.id,
                providerName = provider.name,
                providerTitle = provider.title,
                providerRating = provider.rating,
                price = price,
                durationOrArrival = arrival,
                notes = notes,
                status = "PENDING"
            )
            repository.sendQuote(quote)
            _toastMessage.value = "Teklifiniz müşteriye başarıyla iletildi 🚀"
        }
    }

    // --- Provider Calendar & Availability Management ---
    fun toggleProviderOpenForOffers(providerId: String, currentStatus: Boolean) {
        action {
            repository.toggleOpenForOffers(providerId, !currentStatus)
            _toastMessage.value = if (!currentStatus)
                "Teklif alımı ve takvim rezervasyonlara açıldı ✅"
            else
                "Teklif alımı kapatıldı (Müsait değil) ⏸️"
        }
    }

    fun toggleBookedDate(provider: ServiceProviderEntity, dateIso: String) {
        action {
            val list = try {
                val cleaned = provider.bookedDatesJson.replace("[", "").replace("]", "").replace("\"", "")
                if (cleaned.isBlank()) mutableListOf() else cleaned.split(",").map { it.trim() }.toMutableList()
            } catch (e: Exception) {
                mutableListOf()
            }

            if (list.contains(dateIso)) {
                list.remove(dateIso)
                _toastMessage.value = "$dateIso tarihi müsait olarak açıldı 🟢"
            } else {
                list.add(dateIso)
                _toastMessage.value = "$dateIso tarihi 'Dolu / Rezerve' olarak işaretlendi 🔴"
            }
            val newJson = "[" + list.joinToString(",") { "\"$it\"" } + "]"
            repository.updateBookedDates(provider.id, newJson)
        }
    }
}

private data class FilterState(
    val sector: SectorType,
    val urgency: UrgencyMode,
    val category: String?,
    val query: String,
    val district: String
)
