package com.batuhanduran.burada.location

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.batuhanduran.burada.data.local.ServiceProviderEntity
import com.batuhanduran.burada.data.model.PilotNeighborhoodCatalog
import com.batuhanduran.burada.ui.components.CurrentLocationSelector
import com.batuhanduran.burada.ui.components.NeighborhoodSelector
import com.batuhanduran.burada.ui.screens.MarketplaceMapView
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NeighborhoodDiscoveryInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun emptyBackendDisplaysEmptyStateWithoutCrashing() {
        compose.setContent {
            MaterialTheme { MarketplaceMapView(emptyList(), emptyList(), {}, {}, {}, {}) }
        }
        compose.onNodeWithTag("neighborhood_discovery_empty").assertExists()
        compose.onNodeWithTag("discovery_filter_EMERGENCY").performClick()
        compose.onNodeWithTag("neighborhood_discovery_empty").assertExists()
    }

    @Test fun discoveryFiltersByStableNeighborhoodIdentity() {
        val firstId = "test_neighborhood_001"
        val secondId = "test_neighborhood_002"
        val providers = listOf(provider("first", firstId), provider("second", secondId))
        compose.setContent {
            MaterialTheme { MarketplaceMapView(providers, emptyList(), {}, {}, {}, {}) }
        }
        compose.onNodeWithTag("discovery_neighborhood_$firstId").performClick()
        compose.onNodeWithTag("discovery_provider_first").assertExists()
        compose.onNodeWithTag("discovery_provider_second").assertDoesNotExist()
    }

    @Test fun neighborhoodSelectorReportsStableIdAndLocationDoesNotRunAutomatically() {
        var selectedId: String? = null
        var locationCallbacks = 0
        val neighborhood = PilotNeighborhoodCatalog.neighborhoods[1]
        compose.setContent {
            MaterialTheme {
                androidx.compose.foundation.layout.Column {
                    NeighborhoodSelector(null, { selectedId = it.neighborhoodId })
                    CurrentLocationSelector({ locationCallbacks++ })
                }
            }
        }
        compose.onNodeWithTag("btn_select_neighborhood").performClick()
        compose.onNodeWithTag("neighborhood_${neighborhood.neighborhoodId}").performClick()
        compose.runOnIdle {
            assertEquals(neighborhood.neighborhoodId, selectedId)
            assertEquals(0, locationCallbacks)
        }
    }

    private fun provider(id: String, neighborhoodId: String) = ServiceProviderEntity(
        id = id, name = id, title = "Boya hizmeti", sector = "HOME_REPAIR", categoryId = "boyaci",
        rating = 0.0, reviewCount = 0, experienceYears = 2, district = "Buca", city = "İzmir",
        hourlyOrBasePrice = "1000 ₺", isEmergencyAvailable = false, verifiedSafeBadge = false,
        mykCertified = false, childSafeCertified = false, phone = "", bio = "",
        provinceId = "tr_35", districtId = "tr_35_buca", neighborhoodId = neighborhoodId,
        // Deliberately equal labels prove that filters use identity rather than a label.
        neighborhoodName = "Aynı görünen ad"
    )
}
