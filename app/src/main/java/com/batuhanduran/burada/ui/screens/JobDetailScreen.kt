package com.batuhanduran.burada.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.batuhanduran.burada.data.local.JobRequestEntity
import com.batuhanduran.burada.data.model.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobDetailScreen(
    request: JobRequestEntity?, job: JobLifecycle?, events: List<JobEvent>, currentUid: String,
    busy: Boolean, error: String?, onBack: () -> Unit, onRetry: () -> Unit,
    onAction: (JobAction, Int, String, String) -> Unit
) {
    BackHandler { onBack() }
    var selectedAction by remember(request?.id) { mutableStateOf<JobAction?>(null) }
    val status = job?.status ?: request?.status.orEmpty()
    val version = job?.version ?: 0
    val visibleEvents = events.filter { it.requestId == request?.id }
    val customer = request?.ownerUid == currentUid
    val ready = request != null && (job != null || status in listOf("PENDING", "ACCEPTED"))
    val actions = if (ready) availableJobActions(status, customer, job?.cancellationByUid == currentUid) else emptyList()
    // Never confirm a dialog prepared for an older server version.
    LaunchedEffect(version, status) { selectedAction = null }
    Scaffold(topBar = {
        TopAppBar(title = { Text("İş durumu", fontWeight = FontWeight.Bold) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") }
        })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("job_detail"),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (request == null) {
                item { Text("İş henüz yüklenmedi veya bu hesaba ait değil. Bağlantınızı kontrol ederek yeniden açın.") }
            } else {
                item {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(request.title, style = MaterialTheme.typography.titleLarge)
                            Text(jobStatusLabel(status), fontWeight = FontWeight.Bold, modifier = Modifier.testTag("job_status"))
                            Text("${request.eventOrJobDate} • ${request.eventTime} • ${request.district}")
                            Text(when (status) {
                                "ACCEPTED" -> "Teklif seçildi. Hizmet veren işe başladığını ve tamamladığını buradan bildirebilir."
                                "IN_PROGRESS" -> "İş devam ediyor. Tamamlama bildirimi müşterinin onayına sunulur."
                                "AWAITING_CONFIRMATION" -> if (customer) "Hizmeti kontrol edin. Tamamlandıysa onaylayın; eksik varsa açıklamayla düzeltme isteyin."
                                    else "Tamamlama bildiriminiz gönderildi. Müşterinin yanıtı bekleniyor."
                                "CANCELLATION_REQUESTED" -> if (job?.cancellationByUid == currentUid) "İptal talebiniz yanıt bekliyor. Yanıt verilene kadar geri çekebilirsiniz."
                                    else "Karşı taraf iptal istedi. Açıklamayı inceleyerek kabul edebilir veya neden belirterek reddedebilirsiniz."
                                "COMPLETED" -> "Hizmet verenin tamamlama bildirimi müşteri tarafından onaylandı. İş kaydı kapatıldı."
                                "CANCELLED" -> "İş kaydı kapatıldı. Bu talebe yeni teklif kabul edilemez."
                                else -> "Teklif seçmeden önce ihtiyacınız değişirse talebinizi iptal edebilirsiniz."
                            }, style = MaterialTheme.typography.bodyMedium)
                            job?.note?.takeIf { it.isNotBlank() }?.let { Text(it) }
                            job?.reasonCode?.takeIf { it.isNotBlank() }?.let { Text("Neden: ${jobCancellationReasons[it] ?: it}") }
                        }
                    }
                }
                if (error != null) item {
                    Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("job_action_error"))
                    OutlinedButton(onClick = onRetry, enabled = !busy) { Text("Aynı işlemi tekrar dene") }
                }
                if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth().testTag("job_action_busy")) }
                items(actions, key = { it.name }) { action ->
                    OutlinedButton(onClick = { selectedAction = action }, enabled = !busy,
                        modifier = Modifier.fillMaxWidth().testTag("job_action_${action.name}")) { Text(action.label) }
                }
                if (!ready) item { Text("Güncel iş durumu yükleniyor. İşlem yapmak için bağlantınızı kontrol edin.") }
                item {
                    Text("İşlem geçmişi", style = MaterialTheme.typography.titleMedium)
                    Text("İş açıklamaları ve geçmişi yalnızca işin tarafları tarafından görülebilir.", style = MaterialTheme.typography.bodySmall)
                }
                if (visibleEvents.isEmpty()) item { Text("Henüz durum işlemi yok.", style = MaterialTheme.typography.bodySmall) }
                items(visibleEvents, key = { it.id }) { event ->
                    OutlinedCard(Modifier.fillMaxWidth().testTag("job_event_${event.version}")) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(JobAction.entries.find { it.name == event.action }?.label ?: jobStatusLabel(event.toStatus), fontWeight = FontWeight.Bold)
                            val actor = if (event.actorRole == "CUSTOMER") "Müşteri" else "Hizmet veren"
                            val date = if (event.createdAt > 0) SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.forLanguageTag("tr-TR")).format(Date(event.createdAt)) else ""
                            Text("$actor • $date", style = MaterialTheme.typography.labelMedium)
                            if (event.reasonCode.isNotBlank()) Text(jobCancellationReasons[event.reasonCode] ?: event.reasonCode)
                            if (event.note.isNotBlank()) Text(event.note)
                            Text(jobStatusLabel(event.toStatus), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (visibleEvents.size >= 50) item { Text("Son 50 işlem gösteriliyor. Önceki kayıtlar korunur.", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
    selectedAction?.let { action ->
        JobActionDialog(action, busy, onDismiss = { selectedAction = null }) { note, reason ->
            onAction(action, version, note, reason)
            selectedAction = null
        }
    }
}

@Composable
internal fun JobActionDialog(action: JobAction, busy: Boolean, onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var note by remember(action) { mutableStateOf("") }
    var reason by remember(action) { mutableStateOf("") }
    val valid = (!action.needsNote || note.trim().length >= 10) && note.length <= 1000 && (!action.needsReason || reason in jobCancellationReasons)
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(action.label) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(when (action) {
                    JobAction.CONFIRM_COMPLETION -> "Hizmetin tamamlandığını onaylayınca iş kapatılır. Eksik varsa önce düzeltme isteyin."
                    JobAction.SUBMIT_COMPLETION -> "Yaptığınız işi açıklayın. İş, müşterinin onayından sonra tamamlanmış sayılır."
                    JobAction.CANCEL_OPEN -> "Talep yayından kaldırılır ve yeni teklif kabul edilemez. Bu işlem geri alınamaz."
                    JobAction.REQUEST_CANCEL -> "İptal nedeni karşı tarafa iletilir. İş, karşı taraf kabul ederse iptal edilir."
                    JobAction.ACCEPT_CANCEL -> "İptali kabul edince iş kapatılır. Bu işlem geri alınamaz."
                    JobAction.DECLINE_CANCEL -> "Nedenini açıklayın. İş, iptal talebinden önceki durumuna döner."
                    JobAction.WITHDRAW_CANCEL -> "İptal talebiniz geri çekilir ve iş önceki durumuna döner."
                    JobAction.REQUEST_REVISION -> "Eksik veya düzeltilmesi gereken noktaları açıklayın. İş yeniden devam ediyor durumuna geçer."
                    JobAction.START -> "İşe başladığınız karşı tarafa gösterilir."
                })
                if (action.needsReason) jobCancellationReasons.forEach { (code, label) ->
                    FilterChip(selected = reason == code, onClick = { reason = code }, label = { Text(label) },
                        modifier = Modifier.testTag("job_reason_$code"))
                }
                if (action.needsNote) OutlinedTextField(value = note, onValueChange = { if (it.length <= 1000) note = it },
                    label = { Text("Açıklama") }, supportingText = { Text("En az 10 karakter • ${note.length}/1000") },
                    modifier = Modifier.fillMaxWidth().testTag("job_action_note"), minLines = 3)
                if (action in listOf(JobAction.CANCEL_OPEN, JobAction.ACCEPT_CANCEL, JobAction.CONFIRM_COMPLETION))
                    Text("Bu işlem ödeme veya iade yapmaz.", style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = {
            TextButton(onClick = { onConfirm(note.trim(), reason) }, enabled = valid && !busy,
                modifier = Modifier.testTag("job_action_confirm")) { Text("Onayla") }
        }, dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Vazgeç") } })
}
