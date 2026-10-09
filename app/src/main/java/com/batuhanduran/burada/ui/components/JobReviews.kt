package com.batuhanduran.burada.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.batuhanduran.burada.data.model.JobReview
import java.text.DateFormat
import java.util.Date

@Composable
fun JobReviewActions(
    requestId: String, status: String, reviewed: Boolean, busy: Boolean,
    onReview: (Int, String) -> Unit
) {
    var editing by remember(requestId) { mutableStateOf(false) }
    var rating by remember(requestId) { mutableIntStateOf(0) }
    var comment by remember(requestId) { mutableStateOf("") }
    LaunchedEffect(reviewed) { if (reviewed) editing = false }
    if (status == "COMPLETED") {
        if (reviewed) Text("Değerlendirmeniz kaydedildi", modifier = Modifier.testTag("review_sent_$requestId"))
        else Button(onClick = { editing = true }, enabled = !busy,
            modifier = Modifier.fillMaxWidth().testTag("review_job_$requestId")) { Text("Hizmeti değerlendir") }
    }
    if (editing && !reviewed) AlertDialog(
        onDismissRequest = { if (!busy) editing = false }, title = { Text("Deneyiminizi paylaşın") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Yalnızca tamamladığınız hizmeti değerlendirin. Gönderimden sonra puan değiştirilemez. Tamamlanmadan itibaren 30 gününüz var.")
            Row {
                (1..5).forEach { score ->
                    IconButton(onClick = { rating = score }, enabled = !busy,
                        modifier = Modifier.size(40.dp).testTag("review_star_$score")) {
                        Icon(if (score <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "$score yıldız", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Text(if (rating == 0) "Bir puan seçin" else "$rating / 5")
            OutlinedTextField(value = comment, onValueChange = { if (it.length <= 2000) comment = it },
                enabled = !busy, label = { Text("Yorumunuz (isteğe bağlı)") },
                supportingText = { Text("${comment.length}/2000 • Telefon, adres veya kişisel bilgi yazmayın.") },
                modifier = Modifier.fillMaxWidth().testTag("review_comment"), minLines = 3, maxLines = 5)
        } },
        confirmButton = { TextButton(onClick = { onReview(rating, comment) }, enabled = rating in 1..5 && !busy,
            modifier = Modifier.testTag("submit_review")) { Text(if (busy) "Gönderiliyor…" else "Değerlendirmeyi gönder") } },
        dismissButton = { TextButton(onClick = { editing = false }, enabled = !busy) { Text("Daha sonra") } }
    )
}

@Composable
fun ProviderReviews(reviews: List<JobReview>, total: Int, onMore: () -> Unit, onReport: (String, String) -> Unit) {
    var reportId by remember { mutableStateOf<String?>(null) }
    var reason by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Text("Müşteri değerlendirmeleri", style = MaterialTheme.typography.titleMedium)
        Text("Yalnızca tamamlanan işlerin müşterileri değerlendirebilir.", style = MaterialTheme.typography.bodySmall)
        if (total == 0) Text("Henüz değerlendirme yok. İlk gerçek müşteri yorumu burada görünecek.")
        else if (reviews.isEmpty()) Text("Değerlendirmeler henüz yüklenmedi. Bağlantınızı kontrol edin.")
        reviews.forEach { review ->
            OutlinedCard(modifier = Modifier.fillMaxWidth().testTag("public_review_${review.id}")) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("★ ${review.rating}/5 • Doğrulanmış iş", style = MaterialTheme.typography.titleSmall)
                    Text("Doğrulanmış müşteri • ${DateFormat.getDateInstance().format(Date(review.createdAt))}",
                        style = MaterialTheme.typography.labelSmall)
                    if (review.comment.isNotEmpty()) Text(review.comment)
                    TextButton(onClick = { reportId = review.id; reason = "" }) { Text("Yorumu bildir") }
                }
            }
        }
        if (reviews.isNotEmpty() && reviews.size < total) TextButton(onClick = onMore) { Text("Daha fazla değerlendirme") }
    }
    if (reportId != null) AlertDialog(onDismissRequest = { reportId = null }, title = { Text("Değerlendirmeyi bildir") },
        text = { OutlinedTextField(value = reason, onValueChange = { if (it.length <= 1000) reason = it }, label = { Text("Bildirim gerekçesi") }) },
        confirmButton = { TextButton(onClick = { reportId?.let { onReport(it, reason) }; reportId = null },
            enabled = reason.trim().length >= 3) { Text("İncelemeye gönder") } },
        dismissButton = { TextButton(onClick = { reportId = null }) { Text("Vazgeç") } })
}
