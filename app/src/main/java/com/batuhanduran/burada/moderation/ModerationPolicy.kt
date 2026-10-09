package com.batuhanduran.burada.moderation

/** Stable server values; translated labels are presentation only. */
enum class ReportReason(val code: String, val label: String) {
    FRAUD("fraud", "Dolandırıcılık veya sahte ilan"),
    MISLEADING("misleading", "Yanıltıcı bilgi veya fiyat"),
    HARASSMENT("harassment", "Taciz, hakaret veya tehdit"),
    SPAM("spam", "Spam veya istenmeyen reklam"),
    UNSAFE_SERVICE("unsafe_service", "Güvensiz hizmet"),
    OTHER("other", "Diğer")
}

enum class ReportTargetType(val code: String) {
    LISTING("listing"), CONVERSATION("conversation"), MESSAGE("message"), USER("user")
}

data class ReportDraft(
    val targetType: ReportTargetType,
    val targetId: String,
    val targetUid: String,
    val reason: ReportReason,
    val details: String = "",
    val conversationId: String = ""
) {
    fun validated(reporterUid: String): ReportDraft {
        require(reporterUid.isNotBlank()) { "Şikayet için giriş yapın." }
        require(targetId.isNotBlank() && targetId.length <= 256 && '/' !in targetId) { "Geçersiz içerik." }
        require(targetUid.isNotBlank() && targetUid.length <= 128 && '/' !in targetUid) { "Geçersiz kullanıcı." }
        require(targetUid != reporterUid) { "Kendi içeriğinizi şikayet edemezsiniz." }
        require(details.length <= MAX_REPORT_DETAILS) { "Açıklama en fazla $MAX_REPORT_DETAILS karakter olabilir." }
        require(conversationId.length <= 256 && '/' !in conversationId) { "Geçersiz sohbet." }
        when (targetType) {
            ReportTargetType.LISTING -> require(
                (targetId.startsWith("provider:") && targetId.removePrefix("provider:").isNotBlank()) ||
                    (targetId.startsWith("request:") && targetId.removePrefix("request:").isNotBlank())
            ) { "Geçersiz ilan." }
            ReportTargetType.MESSAGE -> require(conversationId.isNotBlank()) { "Mesajın sohbeti gerekli." }
            ReportTargetType.USER -> require(targetId == targetUid) { "Şikayet edilen kullanıcı eşleşmiyor." }
            ReportTargetType.CONVERSATION -> Unit
        }
        require(targetType == ReportTargetType.MESSAGE || conversationId.isEmpty()) { "Beklenmeyen sohbet alanı." }
        return copy(details = details.trim())
    }

    /** Call after validated(); colons in conversation/listing IDs remain literal IDs. */
    fun targetDocumentPath(): String = when (targetType) {
        ReportTargetType.LISTING -> if (targetId.startsWith("provider:"))
            "providers/${targetId.removePrefix("provider:")}" else "requests/${targetId.removePrefix("request:")}"
        ReportTargetType.CONVERSATION -> "conversations/$targetId"
        ReportTargetType.MESSAGE -> "conversations/$conversationId/messages/$targetId"
        ReportTargetType.USER -> "users/$targetUid"
    }
}

const val MAX_REPORT_DETAILS = 2000

/** History stays visible as evidence; either-direction blocking prevents new interaction. */
object ModerationPolicy {
    fun canInteract(viewerUid: String, targetUid: String, ownBlocks: Set<String>, otherBlockedViewer: Boolean): Boolean =
        viewerUid.isNotBlank() && targetUid.isNotBlank() && viewerUid != targetUid &&
            targetUid !in ownBlocks && !otherBlockedViewer

    fun validateBlock(ownerUid: String, blockedUid: String) {
        require(ownerUid.isNotBlank()) { "Engellemek için giriş yapın." }
        require(blockedUid.isNotBlank() && blockedUid.length <= 128 && '/' !in blockedUid) { "Geçersiz kullanıcı." }
        require(blockedUid != ownerUid) { "Kendinizi engelleyemezsiniz." }
    }
}
