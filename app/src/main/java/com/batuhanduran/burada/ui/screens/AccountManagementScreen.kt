package com.batuhanduran.burada.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.batuhanduran.burada.auth.AuthValidation
import com.batuhanduran.burada.data.local.JobRequestEntity
import com.batuhanduran.burada.data.local.ServiceProviderEntity
import com.batuhanduran.burada.data.remote.AccountManagementViewModel
import com.batuhanduran.burada.data.remote.ManagedListing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountManagementScreen(model: AccountManagementViewModel,providers: List<ServiceProviderEntity>,
    requests: List<JobRequestEntity>,onClose: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { model.loadProfile() }
    var name by rememberSaveable(state.displayName) { mutableStateOf(state.displayName) }
    var bio by rememberSaveable(state.bio) { mutableStateOf(state.bio) }
    var deleteDialog by remember { mutableStateOf(false) }
    BackHandler { if (!state.busy) onClose() }
    Scaffold(topBar = { TopAppBar(title = { Text("Profil ve hesap") },navigationIcon = {
        TextButton(onClick = onClose,enabled = !state.busy) { Text("Geri") }
    }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding = PaddingValues(20.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Sana ait bilgiler",style = MaterialTheme.typography.headlineSmall) }
            item { Text("Hesap profilin özeldir. Keşfette hizmet ilanındaki adın ve açıklaman görünür; bunları ilan yönetiminden düzenleyebilirsin.") }
            if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.error?.let { item { Text(it,color = MaterialTheme.colorScheme.error,modifier = Modifier.testTag("account_error")) } }
            state.message?.let { item { Text(it,modifier = Modifier.testTag("account_message")) } }
            item { TextButton(onClick = model::loadProfile,enabled = !state.busy) { Text("Güncel profili yeniden yükle") } }
            item { OutlinedTextField(name,{ name = it },label = { Text("Ad soyad") },enabled = state.loaded && !state.busy,
                isError = name.isNotEmpty() && AuthValidation.nameError(name) != null,modifier = Modifier.fillMaxWidth().testTag("account_name")) }
            item { OutlinedTextField(bio,{ bio = it },label = { Text("Hakkımda (isteğe bağlı)") },supportingText = { Text("${bio.length}/1000") },
                enabled = state.loaded && !state.busy,isError = bio.length > 1000,modifier = Modifier.fillMaxWidth().testTag("account_bio")) }
            item { Text(state.email); Text("Telefonunu doğrulamak için hesap başlığındaki Telefon doğrulaması seçeneğini kullan.",style = MaterialTheme.typography.bodySmall) }
            item { Button(onClick = { model.saveProfile(name,bio) },enabled = state.loaded && !state.busy && AuthValidation.nameError(name) == null && bio.length <= 1000,
                modifier = Modifier.fillMaxWidth().testTag("save_account_profile")) { Text("Değişiklikleri kaydet") } }
            item { HorizontalDivider(); Text("İlanlarım",style = MaterialTheme.typography.titleLarge) }
            if (providers.isEmpty() && requests.isEmpty()) item { Text("Henüz yönetilecek ilan yok.") }
            items(providers,key = { "provider:${it.id}" }) { listing ->
                OutlinedButton(onClick = { model.openListing("providers",listing.id) },enabled = !state.busy,modifier = Modifier.fillMaxWidth().testTag("manage_provider_${listing.id}")) { Text("Hizmet • ${listing.title}") }
            }
            items(requests,key = { "request:${it.id}" }) { listing ->
                OutlinedButton(onClick = { model.openListing("requests",listing.id) },enabled = !state.busy,modifier = Modifier.fillMaxWidth().testTag("manage_request_${listing.id}")) { Text("Talep • ${listing.title}") }
            }
            item { HorizontalDivider(); Text("Veri ve gizlilik",style = MaterialTheme.typography.titleLarge) }
            item { Text("Hesabını silmek geri alınamaz. İlanların, özel profilin, iletişim bilgilerin ve sana ait sohbet içerikleri kaldırılır. Karşı tarafın mesajları ve güvenlik inceleme kayıtları korunabilir. Devam eden anlaşmalar önce sonuçlandırılmalıdır.") }
            item { TextButton(onClick = { deleteDialog = true },enabled = !state.busy,modifier = Modifier.testTag("open_account_deletion")) { Text("Hesabımı sil",color = MaterialTheme.colorScheme.error) } }
        }
    }
    state.listing?.let { ListingManagementDialog(it,state.busy,state.error,model::closeListing,model::manageListing) }
    if (deleteDialog) {
        var password by remember { mutableStateOf("") }
        var confirmation by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { if (!state.busy) deleteDialog = false },title = { Text("Hesabını kalıcı olarak sil") },text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Onaylandıktan sonra oturumun kapanır ve sunucudaki silme işlemi başlar. Bu işlem geri alınamaz.")
                OutlinedTextField(password,{ password = it },label = { Text("Mevcut şifren") },enabled = !state.busy,
                    visualTransformation = PasswordVisualTransformation(),modifier = Modifier.testTag("delete_account_password"))
                OutlinedTextField(confirmation,{ confirmation = it },label = { Text("HESABIMI SİL yaz") },enabled = !state.busy,modifier = Modifier.testTag("delete_account_confirmation"))
                state.error?.let { Text(it,color = MaterialTheme.colorScheme.error) }
            }
        },confirmButton = { TextButton(onClick = { model.deleteAccount(password,confirmation); password = "" },
            enabled = !state.busy && password.isNotBlank() && confirmation == "HESABIMI SİL",modifier = Modifier.testTag("confirm_account_deletion")) { Text("Kalıcı silme işlemini başlat") } },
            dismissButton = { TextButton(onClick = { deleteDialog = false },enabled = !state.busy) { Text("Vazgeç") } })
    }
}
@Composable
private fun ListingManagementDialog(listing: ManagedListing,busy: Boolean,error: String?,onClose: () -> Unit,onManage: (String,Map<String,String>) -> Unit) {
    var draft by remember(listing.kind,listing.id,listing.revision) { mutableStateOf(listing.fields) }
    var confirmRemove by remember { mutableStateOf(false) }
    val labels = mapOf("name" to "Görünen ad","title" to "İlan başlığı","bio" to "Hizmet açıklaması","hourlyOrBasePrice" to "Başlangıç fiyatı",
        "renovationNotes" to "İşin ayrıntıları","extraServicesRequested" to "Ek hizmetler","budgetEstimate" to "Bütçe","eventOrJobDate" to "Tarih (YYYY-MM-DD)","eventTime" to "Saat (HH:mm)")
    AlertDialog(onDismissRequest = { if (!busy) onClose() },title = { Text(if (confirmRemove) "İlanı kaldır" else "İlan yönetimi") },text = {
        if (confirmRemove) Column {
            Text("İlan keşfetten kaldırılır ve yeni teklif alamaz. Teklif ve sohbet geçmişi hesap silinmediği sürece korunur.")
            error?.let { Text(it,color = MaterialTheme.colorScheme.error) }
        } else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("Durum: ${when(listing.visibility) { "published" -> "Yayında"; "archived" -> "Kaldırıldı"; else -> "Gizli" }}") }
            if (!listing.editable) item { Text("Teklif alınmış, anlaşılmış veya kaldırılmış ilan düzenlenemez. İş anlaşması varsa iş yönetimini kullan.") }
            items(draft.keys.toList()) { field -> OutlinedTextField(draft[field].orEmpty(),{ value -> draft = draft + (field to value) },
                label = { Text(labels[field] ?: field) },enabled = listing.editable && !busy,modifier = Modifier.fillMaxWidth().testTag("listing_edit_$field")) }
            error?.let { item { Text(it,color = MaterialTheme.colorScheme.error) } }
            if (listing.removable) item { TextButton(onClick = { confirmRemove = true },enabled = !busy,modifier = Modifier.testTag("remove_listing")) { Text("İlanı kaldır",color = MaterialTheme.colorScheme.error) } }
        }
    },confirmButton = {
        if (confirmRemove) TextButton(onClick = { onManage("remove",emptyMap()) },enabled = !busy,modifier = Modifier.testTag("confirm_remove_listing")) { Text("Kaldır") }
        else if (listing.editable) TextButton(onClick = { onManage("edit",draft) },enabled = !busy,modifier = Modifier.testTag("save_listing")) { Text("Kaydet") }
    },dismissButton = { TextButton(onClick = { if (confirmRemove) confirmRemove = false else onClose() },enabled = !busy) { Text("Vazgeç") } })
}
