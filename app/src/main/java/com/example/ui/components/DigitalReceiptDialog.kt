package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.DigitalReceiptEntity
import com.example.ui.theme.*

@Composable
fun DigitalReceiptDialog(
    receipt: DigitalReceiptEntity,
    onDismiss: () -> Unit,
    onReleaseFunds: (receiptCode: String) -> Unit,
    onDisputeClick: () -> Unit
) {
    val isCompleted = receipt.escrowStatus == "RELEASED"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = TealPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Dijital Hizmet Fişi (Demo)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isCompleted) SafeBadgeGreenContainer else TealContainer
                ) {
                    Text(
                        text = if (isCompleted) "✓ DEMO TAMAMLANDI" else "DEMO • TEST",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCompleted) SafeBadgeText else OnTealContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, SurfaceCardBorder, RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header Stamp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("MAHALLEM DEMO", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TealDark)
                        Text("Yerel test fişi • Gerçek belge değildir", fontSize = 10.sp, color = Slate500)
                    }
                    Text(
                        text = receipt.receiptCode,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Slate700
                    )
                }

                HorizontalDivider(color = SurfaceCardBorder, thickness = 0.8.dp)

                // Details
                ReceiptRow("Hizmet / İş:", receipt.jobTitle)
                ReceiptRow("Müşteri:", receipt.customerName)
                ReceiptRow("Hizmet Sağlayıcı:", "${receipt.providerName} (${receipt.providerTitle})")
                ReceiptRow("Konum:", receipt.district)
                ReceiptRow("Tarih:", receipt.createdAtDate)
                ReceiptRow("Garanti Belgesi:", receipt.warrantyInfo)

                HorizontalDivider(color = SurfaceCardBorder, thickness = 0.8.dp)

                // Amount
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Demo işlem tutarı:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(receipt.totalAmount, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TealPrimary)
                }

                // QR Verification Box simulation
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Slate100,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.QrCode2, contentDescription = null, tint = Slate700, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Demo doğrulama alanı", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate700)
                            Text("Bu alan yerel test içindir; gerçek QR doğrulaması yapmaz.", fontSize = 9.sp, color = Slate500)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!isCompleted) {
                Button(
                    onClick = { onReleaseFunds(receipt.receiptCode) },
                    colors = ButtonDefaults.buttonColors(containerColor = SafeBadgeGreen),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("btn_release_escrow")
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Demo işi onayla & demo ödemeyi serbest bırak", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(onClick = onDismiss, shape = RoundedCornerShape(10.dp)) {
                    Text("Tamam")
                }
            }
        },
        dismissButton = {
            if (!isCompleted) {
                TextButton(onClick = onDisputeClick) {
                    Text("Sorun Bildir (İtiraz Et)", color = EmergencyRed, fontSize = 11.5.sp)
                }
            }
        }
    )
}

@Composable
private fun ReceiptRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 11.sp, color = Slate500)
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Slate800,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
