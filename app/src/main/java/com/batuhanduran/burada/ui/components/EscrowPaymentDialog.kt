package com.batuhanduran.burada.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.batuhanduran.burada.data.local.QuoteEntity

/**
 * Fail-closed payment experience.
 *
 * No payment processor has been connected, so do not collect card data,
 * invoke an escrow action, or claim that funds have been blocked.
 */
@Composable
fun EscrowPaymentDialog(
    quote: QuoteEntity,
    jobTitle: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Ödeme henüz kullanılamıyor", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().testTag("payment_unavailable_notice"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Bu uygulamada henüz doğrulanmış bir ödeme altyapısı etkin değil. " +
                        "Kart bilgisi girmeyin; ödeme alınmaz ve para havuzda bloke edilmez.",
                    style = MaterialTheme.typography.bodyMedium
                )
                HorizontalDivider()
                Text("Hizmet: $jobTitle", style = MaterialTheme.typography.bodySmall)
                Text("Teklif: ${quote.price}", style = MaterialTheme.typography.bodySmall)
                Text(
                    "Teklifi incelemeye devam edebilirsiniz. Güvenli ödeme aktif olduğunda " +
                        "uygulama içinden ayrıca bilgilendirileceksiniz.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("btn_payment_unavailable_close")
            ) {
                Text("Kapat")
            }
        }
    )
}
