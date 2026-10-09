package com.batuhanduran.burada.data.model

data class JobLifecycle(
    val requestId: String,
    val status: String,
    val version: Int,
    val cancellationByUid: String = "",
    val previousStatus: String = "",
    val note: String = "",
    val reasonCode: String = ""
)

data class JobEvent(
    val id: String,
    val requestId: String,
    val action: String,
    val actorRole: String,
    val toStatus: String,
    val note: String,
    val reasonCode: String,
    val version: Int,
    val createdAt: Long
)

enum class JobAction(val label: String, val needsNote: Boolean = false, val needsReason: Boolean = false) {
    START("İşe başladım"),
    SUBMIT_COMPLETION("İşi tamamladım", true),
    CONFIRM_COMPLETION("Tamamlandığını onayla"),
    REQUEST_REVISION("Düzeltme iste", true),
    CANCEL_OPEN("Talebi iptal et", true, true),
    REQUEST_CANCEL("İptal talebi gönder", true, true),
    ACCEPT_CANCEL("İptali kabul et"),
    DECLINE_CANCEL("İptali reddet", true),
    WITHDRAW_CANCEL("İptal talebimi geri çek")
}

val jobCancellationReasons = linkedMapOf(
    "CHANGE_OF_PLANS" to "İhtiyacım / planım değişti",
    "SCHEDULE" to "Tarih veya saat uyuşmuyor",
    "SCOPE" to "İşin kapsamı değişti",
    "NO_SHOW" to "Randevuya gelinmedi",
    "QUALITY" to "Hizmetle ilgili sorun var",
    "OTHER" to "Diğer"
)

fun jobStatusLabel(status: String): String = when (status) {
    "PENDING" -> "Teklif bekleniyor"
    "ACCEPTED" -> "Hizmet veren seçildi"
    "IN_PROGRESS" -> "İş devam ediyor"
    "AWAITING_CONFIRMATION" -> "Tamamlama onayı bekleniyor"
    "CANCELLATION_REQUESTED" -> "İptal yanıtı bekleniyor"
    "COMPLETED" -> "İş tamamlandı"
    "CANCELLED" -> "İptal edildi"
    else -> "Durum yükleniyor"
}

fun availableJobActions(status: String, isCustomer: Boolean, isCancellationRequester: Boolean): List<JobAction> = when (status) {
    "PENDING" -> if (isCustomer) listOf(JobAction.CANCEL_OPEN) else emptyList()
    "ACCEPTED" -> if (isCustomer) listOf(JobAction.REQUEST_CANCEL)
        else listOf(JobAction.START, JobAction.SUBMIT_COMPLETION, JobAction.REQUEST_CANCEL)
    "IN_PROGRESS" -> if (isCustomer) listOf(JobAction.REQUEST_CANCEL)
        else listOf(JobAction.SUBMIT_COMPLETION, JobAction.REQUEST_CANCEL)
    "AWAITING_CONFIRMATION" -> if (isCustomer)
        listOf(JobAction.CONFIRM_COMPLETION, JobAction.REQUEST_REVISION, JobAction.REQUEST_CANCEL)
        else listOf(JobAction.REQUEST_CANCEL)
    "CANCELLATION_REQUESTED" -> if (isCancellationRequester) listOf(JobAction.WITHDRAW_CANCEL)
        else listOf(JobAction.ACCEPT_CANCEL, JobAction.DECLINE_CANCEL)
    else -> emptyList()
}
