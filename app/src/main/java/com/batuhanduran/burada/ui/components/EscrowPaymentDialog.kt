package com.batuhanduran.burada.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.batuhanduran.burada.data.local.QuoteEntity
import com.batuhanduran.burada.ui.theme.*

@Composable
fun EscrowPaymentDialog(
    quote: QuoteEntity,
    jobTitle: String,
    onDismiss: () -> Unit,
    onConfirmPayment: (quote: QuoteEntity) -> Unit
) {
    var cardNumber by remember { mutableStateOf("•••• •••• •••• 4289") }
    var cardExpiry by remember { mutableStateOf("11/28") }
    var cardCvc by remember { mutableStateOf("•••") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = TealPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Burada Güvenli Havuz", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Escrow Korumalı Ödeme", fontSize = 11.sp, color = Slate500)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Info Banner
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = TealContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Paranız ustaya hemen aktarılmaz. Hizmet tamamlanıp siz memnuniyetinizi onaylayana kadar havuzda kilitli kalır.",
                            fontSize = 11.5.sp,
                            color = OnTealContainer,
                            lineHeight = 16.sp
                        )
                    }
                }

                // Summary Card
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("İş: $jobTitle", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text("Usta/Sanatçı: ${quote.providerName}", fontSize = 11.5.sp, color = Slate600)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Toplam Hizmet Bedeli:", fontSize = 12.sp, color = Slate700)
                            Text(quote.price, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TealPrimary)
                        }
                    }
                }

                // Simulated card inputs
                Text("Ödeme Yöntemi", fontWeight = FontWeight.Bold, fontSize = 12.sp)

                OutlinedTextField(
                    value = cardNumber,
                    onValueChange = { cardNumber = it },
                    label = { Text("Kart Numarası") },
                    leadingIcon = { Icon(Icons.Default.CreditCard, contentDescription = null, tint = Slate500) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = cardExpiry,
                        onValueChange = { cardExpiry = it },
                        label = { Text("SKT") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = cardCvc,
                        onValueChange = { cardCvc = it },
                        label = { Text("CVV") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = SafeBadgeGreen, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("256-bit SSL ve BDDK Lisanslı Güvenli Ödeme Altyapısı", fontSize = 10.sp, color = Slate500)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmPayment(quote) },
                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("btn_confirm_escrow_pay")
            ) {
                Text("Havuzda Bloke Et & Başlat (${quote.price})", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Vazgeç")
            }
        }
    )
}
