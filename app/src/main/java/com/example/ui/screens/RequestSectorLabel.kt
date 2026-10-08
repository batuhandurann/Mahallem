package com.example.ui.screens

import com.example.data.model.SectorType
import java.util.Locale

/**
 * Never label an unknown or legacy sector as entertainment by default.
 * Persisted codes are normalized with a locale-independent mapping.
 */
internal fun requestSectorLabel(rawSector: String): String {
    val normalized = rawSector.trim().uppercase(Locale.ROOT)
    return SectorType.values()
        .firstOrNull { it != SectorType.ALL && it.name == normalized }
        ?.titleTr ?: "Diğer Hizmet"
}

internal enum class RequestDetailsKind {
    RENOVATION,
    EVENT,
    GENERAL
}

internal fun requestDetailsKind(rawSector: String): RequestDetailsKind =
    when (rawSector.trim().uppercase(Locale.ROOT)) {
        SectorType.HOME_REPAIR.name -> RequestDetailsKind.RENOVATION
        SectorType.EVENT_ENTERTAINMENT.name -> RequestDetailsKind.EVENT
        else -> RequestDetailsKind.GENERAL
    }
