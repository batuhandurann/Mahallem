package com.batuhanduran.burada.data.repository

import com.batuhanduran.burada.data.local.ServiceProviderEntity
import com.batuhanduran.burada.data.local.JobRequestEntity
import com.squareup.moshi.Moshi
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

    @Test fun listingVisibilityIsEnvelopeMetadataAndCannotEnterWireData() {
        val moshi = Moshi.Builder().build()
        val providerAdapter = moshi.adapter(ServiceProviderEntity::class.java)
        val provider = provider("archived-provider", "uid-a").copy(visibility = "archived")
        val providerData = providerAdapter.toJsonValue(provider) as Map<*, *>
        assertTrue("Provider wire data must preserve the Rules field allowlist", !providerData.containsKey("visibility"))
        assertEquals("published", providerAdapter.fromJsonValue(providerData + ("visibility" to "archived"))!!.visibility)

        val requestAdapter = moshi.adapter(JobRequestEntity::class.java)
        val request = JobRequestEntity(title = "Test", sector = "HOME_REPAIR", categoryId = "repair",
            district = "Buca", urgencyMode = "PLANNED", eventOrJobDate = "2026-10-11",
            eventTime = "12:00", address = "", status = "PENDING", customerName = "Test",
            customerPhone = "", visibility = "archived")
        val requestData = requestAdapter.toJsonValue(request) as Map<*, *>
        assertTrue("Request wire data must preserve the Rules field allowlist", !requestData.containsKey("visibility"))
        assertEquals("published", requestAdapter.fromJsonValue(requestData + ("visibility" to "archived"))!!.visibility)
    }
}
