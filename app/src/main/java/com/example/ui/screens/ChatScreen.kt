package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ConversationEntity
import com.example.ui.theme.*

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
    onSendPhoto: (desc: String) -> Unit = {},
    onCallClick: () -> Unit,
    onReportClick: () -> Unit
) {
    BackHandler { onBackClick() }

    var messageInput by remember { mutableStateOf("") }
    var showOfferDialog by remember { mutableStateOf(false) }
    var offerPriceInput by remember { mutableStateOf("") }
    var isRecordingSimulated by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
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
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(SafeBadgeGreen)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Çevrim içi • ${conversation?.participantTitle ?: "Hizmet Sağlayıcı"}",
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
                    IconButton(
                        onClick = onCallClick,
                        modifier = Modifier.testTag("btn_chat_call")
                    ) {
                        Icon(imageVector = Icons.Default.Phone, contentDescription = "Ara", tint = TealPrimary)
                    }
                    IconButton(
                        onClick = onReportClick,
                        modifier = Modifier.testTag("btn_chat_report")
                    ) {
                        Icon(imageVector = Icons.Default.ReportProblem, contentDescription = "Şikayet Et", tint = Slate500)
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
                                onSendPhoto("Daire hasar ve keşif fotoğrafı eklendi 📸")
                            },
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

    if (showOfferDialog) {
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
                            Text(message.photoDescription, fontSize = 11.sp, color = if (isMe) TealLight else Slate600)
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
                        text = "14:32",
                        fontSize = 10.sp,
                        color = if (isMe) TealLight else Slate500
                    )
                    if (isMe) {
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            Icons.Default.DoneAll,
                            contentDescription = "Okundu",
                            tint = TealLight,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}
