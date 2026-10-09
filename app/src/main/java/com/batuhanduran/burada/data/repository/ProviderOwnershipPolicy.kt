package com.batuhanduran.burada.data.repository

import com.batuhanduran.burada.data.local.ServiceProviderEntity

/**
 * Only render/edit the active account's provider profiles in the dashboard.
 * The public search filters must not determine the owner's management list.
 */
internal fun ownedProviderProfiles(
    profiles: List<ServiceProviderEntity>,
    activeUid: String
): List<ServiceProviderEntity> =
    if (activeUid.isBlank()) emptyList()
    else profiles.filter { it.ownerUid == activeUid }
