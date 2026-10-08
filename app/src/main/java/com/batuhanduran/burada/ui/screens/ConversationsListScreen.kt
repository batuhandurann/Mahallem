package com.batuhanduran.burada.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.batuhanduran.burada.data.local.ConversationEntity
import com.batuhanduran.burada.ui.theme.*
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsListScreen(
    conversations: List<ConversationEntity>,
    onBackClick: () -> Unit,
    onConversationClick: (String) -> Unit,
    blockedParticipantIds: Set<String> = emptySet(),
    onUnblockParticipant: ((String) -> Unit)? = null
) {
    BackHandler { onBackClick() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mesajlarım", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("btn_conv_back")
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        if (conversations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.ChatBubbleOutline,
                        contentDescription = null,
                        tint = Slate500,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Henüz Bir Mesajınız Yok",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Ustalara, palyaçolara veya ilan sahiplerine profil üzerinden doğrudan mesaj atabilirsiniz.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate500
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(conversations, key = { it.id }) { conv ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(1.5.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onConversationClick(conv.id) }
                            .testTag("conversation_item_${conv.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(TealPrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = conv.participantName.take(1),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = conv.participantName,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (conv.lastTimestamp > 0) DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(conv.lastTimestamp)) else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Slate500
                                    )
                                }

                                Text(
                                    text = conv.participantTitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TealPrimary,
                                    maxLines = 1
                                )

                                if (conv.participantId in blockedParticipantIds) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Engellendi • mesaj geçmişi", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                        if (onUnblockParticipant != null) {
                                            TextButton(onClick = { onUnblockParticipant(conv.participantId) }) { Text("Engeli kaldır") }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = conv.lastMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Slate600,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            if (conv.unreadCount > 0) {
                                Badge(
                                    containerColor = FestiveCoral,
                                    contentColor = Color.White
                                ) {
                                    Text("${conv.unreadCount}")
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = Slate500,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
