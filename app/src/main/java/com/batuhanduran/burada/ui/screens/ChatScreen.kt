package com.batuhanduran.burada.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.batuhanduran.burada.data.local.ChatMessageEntity
import com.batuhanduran.burada.data.local.ConversationEntity
import com.batuhanduran.burada.data.remote.ConversationPhotoRepository
import com.batuhanduran.burada.data.remote.FirebaseServices
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.batuhanduran.burada.ui.theme.*
import com.batuhanduran.burada.ui.components.ReportContentDialog
import com.batuhanduran.burada.moderation.ReportReason
import java.text.DateFormat
import java.util.Date

val QUICK_REPLY_QUESTIONS = listOf(
    "Hâlâ müsait misiniz?",
    "Fiyatta pazarlık payı var mı?",
    "Konum gönderebilir misiniz?",
    "Malzemeler dahil mi?",
    "Hafta sonu gelebilir misiniz?"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversation: ConversationEntity?,
    messages: List<ChatMessageEntity>,
    onBackClick: () -> Unit,
    onSendMessage: (text: String, isOffer: Boolean, offerPrice: String) -> Unit,
    onSendVoiceNote: (duration: Int) -> Unit = {},
    onSendPhoto: (Uri) -> Unit = {},
    onCallClick: () -> Unit,
    onReportClick: () -> Unit,
    isBlocked: Boolean = false,
    onBlockChanged: ((Boolean) -> Unit)? = null,
    onSubmitReport: ((ReportReason, String) -> Unit)? = null
) {
    BackHandler { onBackClick() }

    var messageInput by remember(conversation?.id) { mutableStateOf("") }
    var showBlockConfirmation by remember(conversation?.id) { mutableStateOf(false) }
    var showReportDialog by remember(conversation?.id) { mutableStateOf(false) }
    var showOfferDialog by remember(conversation?.id) { mutableStateOf(false) }
    var offerPriceInput by remember(conversation?.id) { mutableStateOf("") }
    var photoSelectionConversationId by remember { mutableStateOf<String?>(null) }
    var photoSelectionUid by remember { mutableStateOf<String?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && !isBlocked && conversation != null &&
            conversation.id == photoSelectionConversationId &&
            FirebaseServices.auth.currentUser?.uid == photoSelectionUid) {
            onSendPhoto(uri)
        }
        photoSelectionConversationId = null
        photoSelectionUid = null
    }

    val listState = rememberLazyListState()

    LaunchedEffect(conversation?.id, messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(TealPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = conversation?.participantName?.take(1) ?: "U",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = conversation?.participantName ?: "Sohbet",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isBlocked) "Engellendi" else conversation?.participantTitle ?: "Hizmet Sağlayıcı",
                                    fontSize = 11.sp,
                                    color = Slate500
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("btn_chat_back")
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    if (onBlockChanged != null && conversation != null) {
                        IconButton(
                            onClick = { showBlockConfirmation = true },
                            modifier = Modifier.testTag("btn_chat_block")
                        ) {
                            Icon(Icons.Default.Block, contentDescription = if (isBlocked) "Engeli kaldır" else "Kullanıcıyı engelle", tint = Slate500)
                        }
                    }
                    IconButton(
                        onClick = onCallClick,
                        enabled = !isBlocked,
                        modifier = Modifier.testTag("btn_chat_call")
                    ) {
                        Icon(imageVector = Icons.Default.Phone, contentDescription = "Ara", tint = TealPrimary)
                    }
                    IconButton(
                        onClick = {
                            if (onSubmitReport != null && conversation != null) showReportDialog = true
                            else onReportClick()
                        },
                        modifier = Modifier.testTag("btn_chat_report")
                    ) {
                        Icon(imageVector = Icons.Default.ReportProblem, contentDescription = "Şikayet Et", tint = Slate500)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            if (isBlocked) {
                Surface(Modifier.fillMaxWidth().navigationBarsPadding(), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text("Bu kullanıcıyı engellediniz. Geçmiş mesajlar korunur; yeni mesaj ve teklif gönderemezsiniz.", Modifier.padding(16.dp))
                }
            } else Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    // Quick reply question pills (Letgo / Sahibinden style)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Quick Offer Button
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = FestiveCoralLight,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { showOfferDialog = true }
                                .testTag("btn_quick_offer")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.LocalOffer, contentDescription = null, tint = FestiveCoral, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Fiyat Teklif Et", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FestiveCoral)
                            }
                        }

                        QUICK_REPLY_QUESTIONS.forEach { q ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { onSendMessage(q, false, "") }
                            ) {
                                Text(
                                    text = q,
                                    fontSize = 11.sp,
                                    color = Slate700,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    // Input Row with Photo & Voice Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Photo Attachment Button
                        IconButton(
                            onClick = {
                                photoSelectionConversationId = conversation?.id
                                photoSelectionUid = FirebaseServices.auth.currentUser?.uid
                                photoPicker.launch("image/*")
                            },
                            enabled = conversation != null,
                            modifier = Modifier.size(40.dp).testTag("btn_attach_photo")
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = "Fotoğraf Çek/Yükle", tint = TealPrimary)
                        }

                        // Voice Note Button
                        IconButton(
                            onClick = {
                                onSendVoiceNote(6)
                            },
                            modifier = Modifier.size(40.dp).testTag("btn_send_voice_note")
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = "Sesli Not Gönder", tint = FestiveCoral)
                        }

                        OutlinedTextField(
                            value = messageInput,
                            onValueChange = { messageInput = it },
                            placeholder = { Text("Mesaj veya sesli not...", fontSize = 13.sp) },
                            shape = RoundedCornerShape(24.dp),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_chat_message")
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        IconButton(
                            onClick = {
                                if (messageInput.isNotBlank()) {
                                    onSendMessage(messageInput, false, "")
                                    messageInput = ""
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(TealPrimary)
                                .testTag("btn_send_chat_message")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Gönder",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Slate50)
        ) {
            // Header item info banner (e.g. Which job/request they are talking about)
            conversation?.let { conv ->
                if (conv.relatedItemTitle.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "İlgili İlan: ${conv.relatedItemTitle}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatBubble(message = msg)
                }
            }
        }
    }

    if (showReportDialog && onSubmitReport != null) {
        ReportContentDialog(
            title = conversation?.participantName ?: "Sohbet",
            onDismiss = { showReportDialog = false },
            onSubmit = { reason, details ->
                onSubmitReport(reason, details)
                showReportDialog = false
            }
        )
    }

    if (showBlockConfirmation && onBlockChanged != null) {
        AlertDialog(
            onDismissRequest = { showBlockConfirmation = false },
            title = { Text(if (isBlocked) "Engeli kaldır" else "Kullanıcıyı engelle") },
            text = { Text(if (isBlocked) "Bu kullanıcıyla yeniden iletişim kurabileceksiniz." else "Yeni mesaj ve teklifler durdurulur. Geçmiş mesajlar inceleme için korunur.") },
            confirmButton = {
                TextButton(
                    onClick = { onBlockChanged(!isBlocked); showBlockConfirmation = false },
                    modifier = Modifier.testTag("btn_confirm_chat_block")
                ) { Text(if (isBlocked) "Engeli kaldır" else "Engelle") }
            },
            dismissButton = { TextButton(onClick = { showBlockConfirmation = false }) { Text("Vazgeç") } }
        )
    }

    if (showOfferDialog && !isBlocked) {
        AlertDialog(
            onDismissRequest = { showOfferDialog = false },
            title = { Text("Fiyat Teklifi Gönder") },
            text = {
                Column {
                    Text("Bu iş veya hizmet için karşı tarafa doğrudan fiyat teklifinizi iletin:", fontSize = 12.sp, color = Slate600)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = offerPriceInput,
                        onValueChange = { offerPriceInput = it },
                        label = { Text("Teklif Edilen Tutar (₺)") },
                        placeholder = { Text("Örn: 3.500 ₺") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_offer_price_dialog")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (offerPriceInput.isNotBlank()) {
                            onSendMessage("Size $offerPriceInput tutarında fiyat teklifi gönderdim.", true, offerPriceInput)
                            showOfferDialog = false
                            offerPriceInput = ""
                        }
                    },
                    modifier = Modifier.testTag("btn_confirm_send_offer")
                ) {
                    Text("Teklifi İlet")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOfferDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }
}

@Composable
fun ChatBubble(message: ChatMessageEntity) {
    val isMe = message.isFromMe

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isMe) 16.dp else 4.dp,
                bottomEnd = if (isMe) 4.dp else 16.dp
            ),
            color = if (isMe) TealPrimary else Color.White,
            shadowElevation = 1.dp,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // If it's an offer card
                if (message.isOfferMessage) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isMe) TealDark else FestiveCoralLight,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.LocalOffer,
                                contentDescription = null,
                                tint = if (isMe) Color.White else FestiveCoral,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "TEKLİF: ${message.offerPrice}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (isMe) Color.White else FestiveCoral
                            )
                        }
                    }
                }

                // If it's a simulated voice note
                if (message.isVoiceNote) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isMe) TealDark else Slate100,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Oynat",
                                tint = if (isMe) Color.White else TealPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            // Waveform bars simulation
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val heights = listOf(8, 14, 20, 10, 16, 22, 12, 18, 14, 8)
                                heights.forEach { h ->
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .height(h.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(if (isMe) Color.White.copy(alpha = 0.8f) else TealPrimary)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "0:0${message.voiceDurationSeconds}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isMe) Color.White else Slate700
                            )
                        }
                    }
                }

                // If it's a photo attachment
                if (message.hasPhotoAttachment) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isMe) TealDark else Slate100,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = if (isMe) Color.White else TealPrimary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("İş / Keşif Fotoğrafı", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (isMe) Color.White else Slate800)
                            }
                            if (message.photoMediaId.isNotBlank()) {
                                ConversationPhotoThumbnail(message.conversationId, message.photoMediaId)
                            } else {
                                Text("Fotoğraf dosyasına erişilemiyor.", style = MaterialTheme.typography.bodySmall)
                            }
                            if (message.photoDescription.isNotBlank()) {
                                Text(message.photoDescription, fontSize = 11.sp, color = if (isMe) TealLight else Slate600)
                            }
                        }
                    }
                }

                Text(
                    text = message.text,
                    fontSize = 14.sp,
                    color = if (isMe) Color.White else Slate900,
                    lineHeight = 19.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (message.timestamp > 0) DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.timestamp)) else "Gönderiliyor",
                        fontSize = 10.sp,
                        color = if (isMe) TealLight else Slate500
                    )
                    if (isMe) {
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Gönderildi",
                            tint = TealLight,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}


/** Authenticated callable reads only; never caches a bearer URL or another account's bitmap. */
@Composable
private fun ConversationPhotoThumbnail(conversationId: String, mediaId: String) {
    val accountUid = FirebaseServices.auth.currentUser?.uid
    var bitmap by remember(accountUid, conversationId, mediaId) { mutableStateOf<ImageBitmap?>(null) }
    var loading by remember(accountUid, conversationId, mediaId) { mutableStateOf(true) }
    var failed by remember(accountUid, conversationId, mediaId) { mutableStateOf(false) }
    var retry by remember(accountUid, conversationId, mediaId) { mutableIntStateOf(0) }
    LaunchedEffect(accountUid, conversationId, mediaId, retry) {
        bitmap = null
        loading = true
        failed = false
        try {
            check(!accountUid.isNullOrBlank())
            val bytes = ConversationPhotoRepository().download(conversationId, mediaId)
            val decoded = withContext(Dispatchers.IO) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0 &&
                    bounds.outWidth.toLong() * bounds.outHeight <= 16_000_000)
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 768) sample *= 2
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)).asImageBitmap()
            }
            if (FirebaseServices.auth.currentUser?.uid == accountUid) bitmap = decoded
            else failed = true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        } finally {
            loading = false
        }
    }
    when {
        loading -> CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp))
        failed -> Column {
            Text("Fotoğraf yüklenemedi.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { retry++ }, modifier = Modifier.testTag("btn_retry_chat_photo")) { Text("Tekrar dene") }
        }
        bitmap != null -> Image(
            bitmap = requireNotNull(bitmap),
            contentDescription = "Sohbette paylaşılan fotoğraf",
            modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Fit
        )
    }
}
