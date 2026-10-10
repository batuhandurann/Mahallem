package com.batuhanduran.burada.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.batuhanduran.burada.data.local.QuoteEntity
import com.batuhanduran.burada.payment.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch

@Composable
fun HostedPaymentDialog(quote: QuoteEntity, jobTitle: String, uid: String, onDismiss: () -> Unit) {
    val repository = remember(uid) { PaymentRepository(uid) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var availability by remember(uid, quote.requestId) { mutableStateOf<PaymentAvailability?>(null) }
    var checkout by remember(uid, quote.requestId) { mutableStateOf<HostedCheckout?>(null) }
    var busy by remember(uid, quote.requestId) { mutableStateOf(false) }
    var error by remember(uid, quote.requestId) { mutableStateOf<String?>(null) }

    suspend fun refresh(start: Boolean = false) {
        if (busy) return
        busy = true
        error = null
        try {
            val capability = repository.availability()
            availability = capability
            if (quote.status == "ACCEPTED") {
                checkout = if (start && capability.available) repository.start(quote.requestId)
                    else repository.status(quote.requestId)
            }
        } catch (_: TimeoutCancellationException) {
            error = "Yanıt gecikti. Ödeme sonucunu yeniden kontrol edin; tekrar ödeme başlatmayın."
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) {
            error = "Ödeme durumu doğrulanamadı. Bağlantınızı ve hesabınızı kontrol edip yeniden deneyin."
        } finally { busy = false }
    }
    LaunchedEffect(uid, quote.requestId) { refresh() }

    HostedPaymentContent(quote, jobTitle, availability, checkout, busy, error,
        onRefresh = { scope.launch { refresh() } },
        onStart = { scope.launch { refresh(start = true) } },
        onOpen = {
            try {
                repository.requireAccount()
                val verified = requireNotNull(checkout)
                check(verified.status == CheckoutStatus.READY && HostedCheckoutPolicy.allowedUrl(verified.paymentPageUrl, verified.environment))
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(verified.paymentPageUrl)).addCategory(Intent.CATEGORY_BROWSABLE))
            } catch (_: Exception) { error = "Güvenli ödeme sayfası açılamadı. Durumu yeniden kontrol edin." }
        }, onDismiss = onDismiss)
}

@Composable
fun HostedPaymentContent(quote: QuoteEntity, jobTitle: String, availability: PaymentAvailability?,
    checkout: HostedCheckout?, busy: Boolean, error: String?, onRefresh: () -> Unit,
    onStart: () -> Unit, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val sandbox = checkout?.environment == CheckoutEnvironment.SANDBOX || availability?.environment == CheckoutEnvironment.SANDBOX
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (sandbox) "Test ödemesi · iyzico" else "Ödeme bilgisi") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Hizmet: $jobTitle")
                Text("Teklif: ${quote.price}")
                if (sandbox) Text("Bu işlem iyzico test ortamındadır. Gerçek ödeme veya hizmet verene para aktarımı değildir.")
                Text("Teklifi kabul etmek ödeme yapmaz. Kart bilgileri yalnızca iyzico'nun güvenli sayfasında girilir.")
                if (busy) Text("Ödeme durumu kontrol ediliyor…", Modifier.testTag("payment_loading"))
                else if (availability?.available != true) Text("Ödeme henüz kullanılamıyor", Modifier.testTag("payment_unavailable_notice"))
                if (quote.status != "ACCEPTED") Text("Ödeme işlemi için önce teklifi kabul edin.")
                checkout?.let { result ->
                    Text(when (result.status) {
                        CheckoutStatus.NOT_STARTED -> "Bu iş için ödeme başlatılmadı."
                        CheckoutStatus.READY -> "Ödeme sayfası hazır. Tarayıcıdan döndükten sonra sonucu kontrol edin."
                        CheckoutStatus.INITIALIZING -> "Ödeme hazırlanıyor. Sonucu kontrol edin."
                        CheckoutStatus.UNKNOWN -> "Sonuç henüz doğrulanamadı. Yeni ödeme başlatmadan sonucu kontrol edin."
                        CheckoutStatus.PAID -> if (sandbox) "Sunucu test ödemesini doğruladı." else "Ödeme sunucu tarafından doğrulandı."
                        CheckoutStatus.REVIEW -> "Ödeme inceleme bekliyor. Yeni ödeme başlatmayın."
                        CheckoutStatus.FAILED -> "Ödeme sağlayıcısı işlemi tamamlamadı. Destek ile görüşün."
                    }, Modifier.testTag("payment_server_status"))
                }
                error?.let { Text(it, Modifier.testTag("payment_error")) }
                // No retry/start after an uncertain result, even if the browser claims success.
                if (!busy && error == null && availability?.available == true && quote.status == "ACCEPTED" && checkout?.status == CheckoutStatus.NOT_STARTED) {
                    Button(onClick = onStart, modifier = Modifier.testTag("payment_start")) { Text(if (sandbox) "Test ödeme sayfasını hazırla" else "Ödeme sayfasını hazırla") }
                }
                if (!busy && error == null && availability?.available == true && checkout?.status == CheckoutStatus.READY && HostedCheckoutPolicy.allowedUrl(checkout.paymentPageUrl, checkout.environment)) {
                    Button(onClick = onOpen, modifier = Modifier.testTag("payment_open")) { Text("iyzico sayfasını aç") }
                }
                TextButton(onClick = onRefresh, enabled = !busy, modifier = Modifier.testTag("payment_refresh")) { Text("Ödeme sonucunu kontrol et") }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat") } })
}
