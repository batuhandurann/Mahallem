package com.example.media

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class StorageRepository(
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) {
    companion object {
        private const val MAX_INPUT_BYTES = 15L * 1024L * 1024L
        private const val MAX_OUTPUT_BYTES = 5L * 1024L * 1024L
        private const val MAX_DIMENSION = 2048
    }

    suspend fun uploadUserImage(
        uid: String,
        uri: Uri,
        contentResolver: ContentResolver
    ): String {
        require(uid.isNotBlank()) { "Kullanıcı kimliği gerekli." }
        return uploadNormalizedImage(
            uri = uri,
            contentResolver = contentResolver,
            ref = storage.reference.child("users/\${uid}/images/\${UUID.randomUUID()}.jpg")
        )
    }

    suspend fun uploadChatImage(
        senderUid: String,
        conversationId: String,
        uri: Uri,
        contentResolver: ContentResolver
    ): String {
        require(senderUid.isNotBlank()) { "Kullanıcı kimliği gerekli." }
        require(conversationId.matches(Regex("^[A-Fa-f0-9]{64}$"))) {
            "Geçersiz sohbet kimliği."
        }
        return uploadNormalizedImage(
            uri = uri,
            contentResolver = contentResolver,
            ref = storage.reference.child(
                "chatAttachments/$conversationId/$senderUid/${UUID.randomUUID()}.jpg"
            )
        )
    }
    suspend fun uploadJobRequestImage(
        ownerUid: String,
        requestId: String,
        uri: Uri,
        contentResolver: ContentResolver
    ): String {
        require(ownerUid.isNotBlank()) { "Kullanıcı kimliği gerekli." }
        require(requestId.matches(Regex("^[A-Za-z0-9_-]{1,80}$"))) {
            "Geçersiz talep kimliği."
        }
        return uploadNormalizedImage(
            uri = uri,
            contentResolver = contentResolver,
            ref = storage.reference.child(
                "jobRequests/\${ownerUid}/\${requestId}/images/\${UUID.randomUUID()}.jpg"
            )
        )
    }

    private suspend fun uploadNormalizedImage(
        uri: Uri,
        contentResolver: ContentResolver,
        ref: StorageReference
    ): String {
        val mime = contentResolver.getType(uri).orEmpty().lowercase()
        require(mime in setOf("image/jpeg", "image/png", "image/webp")) {
            "Yalnızca JPEG, PNG veya WebP görseller desteklenir."
        }

        val inputLength = contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
            afd.length.takeIf { it >= 0L }
        }
        require(inputLength == null || inputLength <= MAX_INPUT_BYTES) {
            "Görsel çok büyük."
        }

        val source = File.createTempFile("mahallem-source-", ".img")
        val normalized = File.createTempFile("mahallem-normalized-", ".jpg")

        try {
            contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Görsel okunamadı." }
                FileOutputStream(source).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= MAX_INPUT_BYTES) { "Görsel çok büyük." }
                        output.write(buffer, 0, read)
                    }
                }
            }

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.absolutePath, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) {
                "Geçerli bir görsel değil."
            }

            var sample = 1
            while (
                bounds.outWidth / sample > MAX_DIMENSION
                || bounds.outHeight / sample > MAX_DIMENSION
            ) {
                sample *= 2
            }

            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeFile(source.absolutePath, options)
                ?: error("Görsel çözülemedi.")

            bitmap.use {
                FileOutputStream(normalized).use { output ->
                    require(it.compress(Bitmap.CompressFormat.JPEG, 85, output)) {
                        "Görsel dönüştürülemedi."
                    }
                }
            }

            require(normalized.length() <= MAX_OUTPUT_BYTES) {
                "Normalize edilmiş görsel çok büyük."
            }

            ref.putFile(
                Uri.fromFile(normalized),
                StorageMetadata.Builder()
                    .setContentType("image/jpeg")
                    .setCacheControl("private, max-age=3600")
                    .build()
            ).await()

            return ref.path
        } finally {
            source.delete()
            normalized.delete()
        }
    }
}
