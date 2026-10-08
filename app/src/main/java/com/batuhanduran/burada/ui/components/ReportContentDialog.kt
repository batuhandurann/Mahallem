package com.batuhanduran.burada.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.batuhanduran.burada.moderation.MAX_REPORT_DETAILS
import com.batuhanduran.burada.moderation.ReportReason

@Composable
fun ReportContentDialog(
    title: String,
    onDismiss: () -> Unit,
    onSubmit: (ReportReason, String) -> Unit
) {
    var selectedReason by remember { mutableStateOf(ReportReason.HARASSMENT) }
    var details by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Şikayet et") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(title)
                Text("Şikayetiniz yalnızca inceleme ekibi tarafından değerlendirilir.", style = MaterialTheme.typography.bodySmall)
                ReportReason.entries.forEach { reason ->
                    Row(
                        Modifier.fillMaxWidth().clickable { selectedReason = reason },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selectedReason == reason, onClick = { selectedReason = reason })
                        Text(reason.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                OutlinedTextField(
                    value = details,
                    onValueChange = { if (it.length <= MAX_REPORT_DETAILS) details = it },
                    label = { Text("Açıklama (isteğe bağlı)") },
                    supportingText = { Text("${details.length}/$MAX_REPORT_DETAILS") },
                    modifier = Modifier.fillMaxWidth().testTag("input_report_details"),
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(selectedReason, details.trim()) },
                modifier = Modifier.testTag("btn_confirm_structured_report")
            ) { Text("Şikayeti gönder") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}
