package com.example.ui.screens

import com.example.data.model.SectorType
import java.util.Locale

/**
 * Use the persisted sector code rather than assuming every non-renovation
 * request is an entertainment event. Unknown/legacy codes are not mislabeled.
 */
internal fun requestSectorLabel(rawSector: String): String {
    val normalized = rawSector.trim().uppercase(Locale.ROOT)
    return SectorType.values()
        .firstOrNull { it != SectorType.ALL && it.name == normalized }
        ?.titleTr ?: "Diğer Hizmet"
}
