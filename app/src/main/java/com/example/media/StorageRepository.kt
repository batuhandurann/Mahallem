package com.example.media

import android.content.ContentResolver
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

class StorageRepository(private val storage: FirebaseStorage = FirebaseStorage.getInstance()) {
    suspend fun uploadUserImage(uid: String, uri: Uri, contentResolver: ContentResolver): String {
        validateImage(contentResolver, uri)
        val ref = storage.reference.child("users/" + uid + "/images/" + System.currentTimeMillis() + ".jpg")
        ref.putFile(uri).await()
        return ref.downloadUrl.await().toString()
    }

    private fun validateImage(contentResolver: ContentResolver, uri: Uri) {
        val type = contentResolver.getType(uri).orEmpty()
        require(type.startsWith("image/")) { "Yalnızca görsel dosyaları desteklenir." }
    }
}