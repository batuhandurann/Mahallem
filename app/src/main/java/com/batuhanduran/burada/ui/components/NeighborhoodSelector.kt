package com.batuhanduran.burada.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.batuhanduran.burada.data.model.NeighborhoodRef
import com.batuhanduran.burada.data.model.PilotNeighborhoodCatalog

@Composable
fun NeighborhoodSelector(
    selected: NeighborhoodRef?,
    onSelected: (NeighborhoodRef) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier) {
        Text("Mahalle", style = MaterialTheme.typography.labelLarge)
        Text("Pilot bölge: İzmir / Buca · 5 mahalle", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().testTag("btn_select_neighborhood")
        ) { Text(selected?.displayLabel ?: "Mahallenizi seçin") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PilotNeighborhoodCatalog.neighborhoods.forEach { neighborhood ->
                DropdownMenuItem(
                    text = { Text(neighborhood.neighborhoodName) },
                    onClick = { onSelected(neighborhood); expanded = false },
                    modifier = Modifier.testTag("neighborhood_${neighborhood.neighborhoodId}")
                )
            }
        }
        Text("Açık adresiniz ve kesin konumunuz ilan akışında paylaşılmaz.", style = MaterialTheme.typography.bodySmall)
    }
}
