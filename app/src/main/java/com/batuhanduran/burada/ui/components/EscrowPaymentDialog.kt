package com.batuhanduran.burada.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.batuhanduran.burada.data.local.QuoteEntity

/**
 * Fail-closed until a verified payment provider is integrated.
 *
 * The underlying repository intentionally rejects escrow transactions. The UI must never
 * collect even placeholder card data, imply an active licensed payment processor, or call
 * the old confirmation callback on builds that cannot transfer money.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun EscrowPaymentDialog(
    quote: QuoteEntity,
    jobTitle: String,
    onDismiss: () -> Unit,
    onConfirmPayment: (quote: QuoteEntity) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Ödeme özelliği henüz kullanılamıyor", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.testTag("escrow_unavailable_notice")
            ) {
                Text(
                    "Uygulama şu anda ödeme almıyor, para bloke etmiyor ve dijital ödeme makbuzu oluşturmuyor.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Bu aşamada kart numaranızı, son kullanma tarihini veya güvenlik kodunuzu paylaşmayın.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Hizmet: $jobTitle", style = MaterialTheme.typography.bodyMedium)
                        Text("Hizmet veren: ${quote.providerName}", style = MaterialTheme.typography.bodySmall)
                        Text("Teklif tutarı: ${quote.price}", style = MaterialTheme.typography.bodySmall)
                        Text("Bu bilgi tahsilat veya ödeme onayı değildir.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("btn_payment_unavailable_close")
            ) { Text("Anladım") }
        }
    )
}
