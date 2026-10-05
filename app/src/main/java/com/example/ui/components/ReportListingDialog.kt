package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.EmergencyRed

val REPORT_REASONS = listOf(
    "Dolandırıcılık veya Sahte İlan Şüphesi",
    "Yanıltıcı / Gerçek Dışı Fiyat Bilgisi",
    "İletişim Kurulamıyor / Numara Hatalı",
    "Uygunsuz İçerik / Hakaret / Spam",
    "Hizmet Belirtilen Standartta Değil"
)

@Composable
fun ReportListingDialog(
    itemTitle: String,
    onDismiss: () -> Unit,
    onConfirmReport: (reason: String) -> Unit
) {
    var selectedReason by remember { mutableStateOf(REPORT_REASONS.first()) }
    var additionalNotes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = EmergencyRed, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("İlanı Şikayet Et", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column {
                Text(
                    text = "İlan: $itemTitle",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Lütfen şikayet nedeninizi seçin:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                REPORT_REASONS.forEach { reason ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedReason = reason }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedReason == reason,
                            onClick = { selectedReason = reason },
                            colors = RadioButtonDefaults.colors(selectedColor = EmergencyRed)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = reason, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = additionalNotes,
                    onValueChange = { additionalNotes = it },
                    label = { Text("Ek Açıklama (Opsiyonel)") },
                    placeholder = { Text("Detayları belirtebilirsiniz...") },
                    modifier = Modifier.fillMaxWidth().height(80.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmReport("$selectedReason - $additionalNotes") },
                colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                modifier = Modifier.testTag("btn_confirm_report")
            ) {
                Text("Şikayeti İlet")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Vazgeç")
            }
        }
    )
}
