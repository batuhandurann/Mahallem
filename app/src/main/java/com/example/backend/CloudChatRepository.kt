package com.example.backend

import com.example.data.local.ChatMessageEntity
import com.example.data.local.ConversationEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class CloudChatRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private fun uid() = auth.currentUser?.uid ?: error("Giriş gerekli.")

    suspend fun startOrGetConversation(providerOrUserId: String, relatedItemTitle: String): String {
        return FunctionsRepository().startConversation(
            targetId = providerOrUserId,
            relatedItemId = providerOrUserId,
            relatedItemTitle = relatedItemTitle
        )
    }

    fun observeConversations(): Flow<List<ConversationEntity>> = callbackFlow {
        val me = uid()
        val listener = firestore.collection("conversations")
            .whereArrayContains("participantIds", me)
            .limit(50)
            .addSnapshotListener { snap, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val list = snap?.documents.orEmpty().map { d ->
                    val participants = d.get("participantIds") as? List<*> ?: emptyList<Any?>()
                    val other = participants.firstOrNull { it != me }?.toString().orEmpty()
                    ConversationEntity(
                        id = d.id, participantId = other, participantName = "Mahalle Hizmet Vereni",
                        participantTitle = "Hizmet", lastMessage = d.getString("lastMessagePreview") ?: "Sohbet başlatıldı",
                        lastTimestamp = (d.getTimestamp("updatedAt")?.toDate()?.time ?: System.currentTimeMillis()),
                        unreadCount = 0, relatedItemTitle = d.getString("relatedItemTitle") ?: ""
                    )
                }
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    fun observeMessages(conversationId: String): Flow<List<ChatMessageEntity>> = callbackFlow {
        uid()
        val listener = firestore.collection("messages")
            .whereEqualTo("conversationId", conversationId)
            .limit(200)
            .addSnapshotListener { snap, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val list = snap?.documents.orEmpty().map { d ->
                    val timestamp = d.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis()
                    val sender = d.getString("senderId").orEmpty()
                    ChatMessageEntity(
                        id = d.id.hashCode().toLong() and 0x7fffffffL, conversationId = conversationId,
                        senderId = sender, senderName = if (sender == auth.currentUser?.uid) "Ben" else "Hizmet Sağlayıcı",
                        text = d.getString("text") ?: "", timestamp = timestamp, isFromMe = sender == auth.currentUser?.uid
                    )
                }.sortedBy { it.timestamp }
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    suspend fun startOrGetConversationForRequest(requestId: Long, relatedItemTitle: String): String {
        return FunctionsRepository().startConversation(
            targetId = "request-owner",
            relatedItemId = requestId.toString(),
            relatedItemTitle = relatedItemTitle
        )
    }

    suspend fun sendMessage(
        conversationId: String, text: String, messageType: String = "TEXT", attachmentUrl: String? = null
    ) {
        FunctionsRepository().sendMessage(
            conversationId = conversationId,
            text = text,
            messageType = messageType,
            attachmentUrl = attachmentUrl
        )
    }
}