package com.batuhanduran.burada.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.batuhanduran.burada.data.local.JobRequestEntity
import com.batuhanduran.burada.data.local.ServiceProviderEntity
import com.batuhanduran.burada.data.model.PilotNeighborhoodCatalog

/** Neighborhood discovery uses backend records; map tiles and device distances are not fabricated. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketplaceMapView(
    providers: List<ServiceProviderEntity>,
    requests: List<JobRequestEntity>,
    onBackClick: () -> Unit,
    onProviderClick: (String) -> Unit,
    onChatWithProvider: (ServiceProviderEntity) -> Unit,
    onChatForJobRequest: (JobRequestEntity) -> Unit
) {
    BackHandler { onBackClick() }
    var selectedFilter by rememberSaveable { mutableStateOf("ALL") }
    var selectedNeighborhoodId by rememberSaveable { mutableStateOf<String?>(null) }
    val neighborhoodIds = (providers.map { it.neighborhoodId } + requests.map { it.neighborhoodId }).distinct().sorted()
    fun labelFor(id: String): String = PilotNeighborhoodCatalog.findById(id)?.displayLabel
        ?: providers.firstOrNull { it.neighborhoodId == id }?.let { "${it.neighborhoodName.ifBlank { "Mahalle belirtilmemiş" }}, ${it.district}" }
        ?: requests.firstOrNull { it.neighborhoodId == id }?.let { "${it.neighborhoodName.ifBlank { "Mahalle belirtilmemiş" }}, ${it.district}" }
        ?: "Mahalle belirtilmemiş"

    // A realtime deletion must not leave an orphaned filter hiding all surviving results.
    LaunchedEffect(neighborhoodIds) {
        if (selectedNeighborhoodId != null && selectedNeighborhoodId !in neighborhoodIds) selectedNeighborhoodId = null
    }
    val filteredProviders = providers.filter {
        selectedFilter != "REQUESTS" && (selectedFilter != "EMERGENCY" || it.isEmergencyAvailable) &&
            (selectedNeighborhoodId == null || it.neighborhoodId == selectedNeighborhoodId)
    }
    val filteredRequests = requests.filter {
        selectedFilter != "PROVIDERS" && (selectedFilter != "EMERGENCY" || it.urgencyMode == "EMERGENCY") &&
            (selectedNeighborhoodId == null || it.neighborhoodId == selectedNeighborhoodId)
    }
    val groups = (filteredProviders.map { it.neighborhoodId } + filteredRequests.map { it.neighborhoodId }).distinct().sortedBy(::labelFor)

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Mahallene göre keşfet", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBackClick, modifier = Modifier.testTag("btn_map_back")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                }
            }
        )
    }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).testTag("neighborhood_discovery"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                Text("İlanlar kayıtlı mahallelerine göre listelenir. Açık adresler ve kesin konumlar gösterilmez.", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ALL" to "Tümü", "EMERGENCY" to "Acil", "PROVIDERS" to "Hizmet verenler", "REQUESTS" to "Talepler").forEach { (id, label) ->
                        FilterChip(selected = selectedFilter == id, onClick = { selectedFilter = id }, label = { Text(label) }, modifier = Modifier.testTag("discovery_filter_$id"))
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = selectedNeighborhoodId == null, onClick = { selectedNeighborhoodId = null }, label = { Text("Tüm mahalleler") })
                    neighborhoodIds.forEach { id ->
                        FilterChip(
                            selected = selectedNeighborhoodId == id,
                            onClick = { selectedNeighborhoodId = id },
                            label = { Text(labelFor(id)) },
                            modifier = Modifier.testTag("discovery_neighborhood_$id")
                        )
                    }
                }
            }
            if (groups.isEmpty()) {
                item { Text("Bu filtrelerde henüz ilan yok.", modifier = Modifier.testTag("neighborhood_discovery_empty")) }
            }
            groups.forEach { neighborhoodId ->
                item(key = "neighborhood_$neighborhoodId") { Text(labelFor(neighborhoodId), style = MaterialTheme.typography.titleMedium) }
                items(filteredProviders.filter { it.neighborhoodId == neighborhoodId }, key = { "provider_${it.id}" }) { provider ->
                    OutlinedCard(Modifier.fillMaxWidth().testTag("discovery_provider_${provider.id}")) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(provider.name, fontWeight = FontWeight.Bold)
                            Text(provider.title)
                            Text(provider.hourlyOrBasePrice, style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { onChatWithProvider(provider) }) { Text("Mesaj gönder") }
                                Button(onClick = { onProviderClick(provider.id) }) { Text("Profili gör") }
                            }
                        }
                    }
                }
                items(filteredRequests.filter { it.neighborhoodId == neighborhoodId }, key = { "request_${it.id}" }) { request ->
                    OutlinedCard(Modifier.fillMaxWidth().testTag("discovery_request_${request.id}")) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(request.title, fontWeight = FontWeight.Bold)
                            Text(request.budgetEstimate, style = MaterialTheme.typography.bodySmall)
                            Button(onClick = { onChatForJobRequest(request) }) { Text("Talep için görüş") }
                        }
                    }
                }
            }
        }
    }
}
