package com.batuhanduran.burada.ui.screens

import com.batuhanduran.burada.data.model.SectorType
import java.util.Locale

/** Applies saved sector codes to the UI without mislabeling unknown services as entertainment. */
internal fun requestSectorLabel(rawSector: String): String {
    val normalized = rawSector.trim().uppercase(Locale.ROOT)
    return SectorType.values()
        .firstOrNull { it != SectorType.ALL && it.name == normalized }
        ?.titleTr ?: "Diğer Hizmet"
}

internal enum class RequestDetailsKind { RENOVATION, EVENT, GENERAL }

/** Never render an event costume or renovation-area summary for unrelated services. */
internal fun requestDetailsKind(rawSector: String): RequestDetailsKind =
    when (rawSector.trim().uppercase(Locale.ROOT)) {
        SectorType.HOME_REPAIR.name -> RequestDetailsKind.RENOVATION
        SectorType.EVENT_ENTERTAINMENT.name -> RequestDetailsKind.EVENT
        else -> RequestDetailsKind.GENERAL
    }
