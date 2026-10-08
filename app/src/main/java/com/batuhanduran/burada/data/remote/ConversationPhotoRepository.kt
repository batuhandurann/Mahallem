package com.batuhanduran.burada.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.batuhanduran.burada.BuildConfig
import com.google.android.gms.tasks.Task
import com.google.firebase.functions.FirebaseFunctions
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ConversationPhoto(val mediaId: String, val storagePath: String)

/** Private media never produces bearer download URLs or accepts arbitrary HTTP URLs. */
class ConversationPhotoRepository {
    private val uid = requireNotNull(FirebaseServices.auth.currentUser).uid
    private val functions = FirebaseFunctions.getInstance(FirebaseServices.app, "europe-west3").apply {
        if (BuildConfig.USE_FIREBASE_EMULATORS) useEmulator(BuildConfig.EMULATOR_HOST, 5001)
    }
    private fun requireAccount() = check(FirebaseServices.auth.currentUser?.uid == uid) { "Oturum değişti." }

    suspend fun upload(context: Context, conversationId: String, uri: Uri): ConversationPhoto = withContext(Dispatchers.IO) {
        requireAccount()
        require(uri.scheme == "content") { "Fotoğraf seçiciden bir fotoğraf seçin." }
        val mime = context.contentResolver.getType(uri)
        require(mime in listOf("image/jpeg", "image/png", "image/webp")) { "JPEG, PNG veya WebP fotoğraf seçin." }
        val input = context.contentResolver.openInputStream(uri)?.use { it.readNBytesCompat(MAX_BYTES + 1) }
            ?: error("Fotoğraf okunamadı.")
        require(input.size <= MAX_BYTES) { "Fotoğraf en fazla 5 MB olabilir." }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(input, 0, input.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 16_000_000) {
            "Fotoğrafın boyutları desteklenmiyor (en fazla 16 megapiksel)."
        }
        val bitmap = BitmapFactory.decodeByteArray(input, 0, input.size) ?: error("Geçersiz fotoğraf.")
        // Decode/re-encode removes EXIF including GPS. Server independently validates the bytes.
        val bytes = ByteArrayOutputStream().use { output ->
            try { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)); output.toByteArray() }
            finally { bitmap.recycle() }
        }
        require(bytes.size <= MAX_BYTES)
        requireAccount()
        val response = functions.getHttpsCallable("uploadConversationPhoto")
            .call(mapOf("conversationId" to conversationId, "base64" to Base64.encodeToString(bytes, Base64.NO_WRAP)))
            .awaitPhoto().data as? Map<*, *> ?: error("Fotoğraf yüklenemedi.")
        requireAccount()
        ConversationPhoto(response["mediaId"] as String, response["storagePath"] as String)
    }

    suspend fun download(conversationId: String, mediaId: String): ByteArray = withContext(Dispatchers.IO) {
        requireAccount()
        val response = functions.getHttpsCallable("readConversationPhoto")
            .call(mapOf("conversationId" to conversationId, "mediaId" to mediaId)).awaitPhoto().data as Map<*, *>
        requireAccount()
        val encoded = response["base64"] as? String ?: error("Fotoğraf okunamadı.")
        require(encoded.length <= ((MAX_BYTES + 2) / 3) * 4)
        Base64.decode(encoded, Base64.NO_WRAP).also { require(it.size <= MAX_BYTES) }
    }

    companion object { const val MAX_BYTES = 5 * 1024 * 1024 }
}

private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (output.size() < limit) {
        val count = read(buffer, 0, minOf(buffer.size, limit - output.size()))
        if (count < 0) break
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

private suspend fun <T> Task<T>.awaitPhoto(): T = withTimeout(60_000) {
    suspendCancellableCoroutine { c -> addOnCompleteListener { task ->
        if (c.isActive) {
            if (task.isSuccessful) c.resume(task.result)
            else c.resumeWithException(task.exception ?: IllegalStateException("Fotoğraf işlemi başarısız."))
        }
    } }
}
