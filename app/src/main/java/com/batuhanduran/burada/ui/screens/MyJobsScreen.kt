package com.batuhanduran.burada.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.batuhanduran.burada.data.local.JobRequestEntity
import com.batuhanduran.burada.data.model.JobLifecycle
import com.batuhanduran.burada.data.model.jobStatusLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyJobsScreen(requests: List<JobRequestEntity>, jobs: List<JobLifecycle>, onBack: () -> Unit, onOpenJob: (String) -> Unit) {
    BackHandler { onBack() }
    var filter by remember { mutableStateOf("ACTIVE") }
    val states = jobs.associateBy { it.requestId }
    val visible = requests.filter {
        val status = states[it.id]?.status ?: it.status
        when (filter) {
            "COMPLETED" -> status == "COMPLETED"
            "CANCELLED" -> status == "CANCELLED"
            else -> status !in listOf("COMPLETED", "CANCELLED")
        }
    }.sortedByDescending { it.createdAt }
    Scaffold(topBar = { TopAppBar(title = { Text("Aldığım işler") }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") }
    }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("my_jobs"), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ACTIVE" to "Devam eden", "COMPLETED" to "Tamamlanan", "CANCELLED" to "İptal edilen").forEach { (id, label) ->
                        FilterChip(selected = filter == id, onClick = { filter = id }, label = { Text(label) })
                    }
                }
            }
            if (visible.isEmpty()) item { Text("Bu bölümde henüz iş yok. Müşterinin kabul ettiği teklifler burada görünür.") }
            items(visible, key = { it.id }) { request ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(request.title, style = MaterialTheme.typography.titleMedium)
                        Text(jobStatusLabel(states[request.id]?.status ?: request.status))
                        Text("${request.eventOrJobDate} • ${request.eventTime} • ${request.district}")
                        Button(onClick = { onOpenJob(request.id) }, modifier = Modifier.testTag("open_job_${request.id}")) { Text("İşi yönet") }
                    }
                }
            }
        }
    }
}
