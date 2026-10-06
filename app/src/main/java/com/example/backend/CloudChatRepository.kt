package com.example.backend

import com.example.data.local.ChatMessageEntity
import com.example.data.local.ConversationEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
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
        val me = uid()
        val existing = firestore.collection("conversations")
            .whereArrayContains("participantIds", me)
            .get().await().documents.firstOrNull { doc ->
                providerOrUserId in (doc.get("participantIds") as? List<*> ?: emptyList<Any?>())
            }
        if (existing != null) return existing.id
        val ownerDoc = firestore.collection("users").document(providerOrUserId).get().await()
        val participant = if (ownerDoc.exists()) providerOrUserId else
            firestore.collection("providers").document(providerOrUserId).get().await().getString("ownerId")
        val participantUid = participant ?: error("Sohbet katılımcısı bulunamadı.")
        val ref = firestore.collection("conversations").document()
        ref.set(
            mapOf(
                "participantIds" to listOf(me, participantUid),
                "relatedItemId" to providerOrUserId,
                "relatedItemTitle" to relatedItemTitle,
                "createdAt" to com.google.firebase.Timestamp.now(),
                "updatedAt" to com.google.firebase.Timestamp.now()
            )
        ).await()
        return ref.id
    }

    fun observeConversations(): Flow<List<ConversationEntity>> = callbackFlow {
        val me = uid()
        val listener = firestore.collection("conversations")
            .whereArrayContains("participantIds", me)
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

    suspend fun sendMessage(
        conversationId: String, text: String, messageType: String = "TEXT", attachmentUrl: String? = null
    ) {
        val me = uid()
        val ref = firestore.collection("messages").document()
        ref.set(
            mapOf(
                "conversationId" to conversationId, "senderId" to me, "text" to text,
                "attachmentUrl" to attachmentUrl, "messageType" to messageType,
                "createdAt" to com.google.firebase.Timestamp.now()
            )
        ).await()
        firestore.collection("conversations").document(conversationId).set(
            mapOf(
                "lastMessageAt" to com.google.firebase.Timestamp.now(),
                "lastMessagePreview" to if (messageType == "IMAGE") "📷 Fotoğraf" else text,
                "updatedAt" to com.google.firebase.Timestamp.now()
            ), SetOptions.merge()
        ).await()
    }
}