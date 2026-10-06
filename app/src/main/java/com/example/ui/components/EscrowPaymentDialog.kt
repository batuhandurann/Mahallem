package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.example.data.local.QuoteEntity
import com.example.ui.theme.*

@Composable
fun EscrowPaymentDialog(
    quote: QuoteEntity,
    jobTitle: String,
    onDismiss: () -> Unit,
    onConfirmPayment: (quote: QuoteEntity, customerEmail: String) -> Unit
) {
    var customerEmail by remember { mutableStateOf("") }
    val isValidEmail = Regex("^[A-Za-z0-9._%+-]{1,100}@[A-Za-z0-9.-]{1,190}\\.[A-Za-z]{2,63}$").matches(customerEmail.trim())

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
                    Text("Güvenli Ödeme", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("PayTR ödeme ekranına yönlendirileceksin", fontSize = 11.sp, color = Slate500)
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
                            text = "Kart bilgilerinizi Mahallem'e girmeyin. Ödeme PayTR'nin güvenli ödeme sayfasında yapılır ve sonucu sunucu webhook ile doğrular.",
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

                OutlinedTextField(
                    value = customerEmail,
                    onValueChange = { customerEmail = it.take(190) },
                    label = { Text("Ödeme e-postası") },
                    placeholder = { Text("ornek@mail.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("PayTR ödeme işlemi için kullanılır.") }
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = SafeBadgeGreen, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Kart bilgileri Mahallem'de saklanmaz.", fontSize = 10.sp, color = Slate500)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = isValidEmail,
                onClick = { onConfirmPayment(quote, customerEmail.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("btn_confirm_escrow_pay")
            ) {
                Text("Güvenli ödeme ekranını aç (${quote.price})", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Vazgeç")
            }
        }
    )
}
