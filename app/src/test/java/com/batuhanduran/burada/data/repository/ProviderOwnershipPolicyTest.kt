package com.batuhanduran.burada.data.repository

import com.batuhanduran.burada.data.local.ServiceProviderEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderOwnershipPolicyTest {
    private fun provider(id: String, owner: String) = ServiceProviderEntity(
        id = id,
        name = "Örnek Usta",
        title = "Ev Tamiratı",
        sector = "HOME_REPAIR",
        categoryId = "repair",
        rating = 0.0,
        reviewCount = 0,
        experienceYears = 0,
        district = "Buca",
        city = "İzmir",
        hourlyOrBasePrice = "100 TL",
        isEmergencyAvailable = false,
        verifiedSafeBadge = false,
        mykCertified = false,
        childSafeCertified = false,
        phone = "",
        bio = "Test hizmeti",
        ownerUid = owner
    )

    private val publicListings = listOf(
        provider("my-repair", "uid-a"),
        provider("another-account", "uid-b"),
        provider("my-cleaning", "uid-a").copy(sector = "CLEANING", district = "Bornova")
    )

    @Test fun returnsOnlyCurrentAccountListingsRegardlessOfSectorOrDistrict() {
        assertEquals(
            listOf("my-repair", "my-cleaning"),
            ownedProviderProfiles(publicListings, "uid-a").map { it.id }
        )
    }

    @Test fun neverDisplaysOtherAccountsProviderProfiles() {
        assertEquals(
            listOf("another-account"),
            ownedProviderProfiles(publicListings, "uid-b").map { it.id }
        )
    }

    @Test fun missingSessionDoesNotFallbackToPublicListings() {
        assertTrue(ownedProviderProfiles(publicListings, "").isEmpty())
        assertTrue(ownedProviderProfiles(publicListings, " ").isEmpty())
    }

    @Test fun ownershipComparisonIsExactNotFuzzy() {
        assertTrue(ownedProviderProfiles(publicListings, "UID-A").isEmpty())
        assertTrue(ownedProviderProfiles(publicListings, "uid").isEmpty())
    }
}
