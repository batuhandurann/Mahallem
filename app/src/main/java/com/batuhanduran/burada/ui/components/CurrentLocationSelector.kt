package com.batuhanduran.burada.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import com.batuhanduran.burada.data.model.GeoCoordinate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Consent is user initiated; no background tracking or automatic cloud write occurs here. */
@Composable
fun CurrentLocationSelector(
    onSelected: (GeoCoordinate) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Konum isteğe bağlıdır. Seçtiğiniz mahalleyle konum izni vermeden devam edebilirsiniz.") }

    fun fetchLocation() {
        if (loading) return
        loading = true
        scope.launch {
            try {
                val coordinate = withTimeout(15_000) { readApproximateDeviceLocation(context) }
                onSelected(coordinate)
                message = "Yaklaşık konum alındı. Yalnızca geniş bölge bilgisi ilanla paylaşılacak."
            } catch (_: TimeoutCancellationException) {
                message = "Konum alınamadı. Cihaz konumunu açıp tekrar deneyin veya mahalle seçerek devam edin."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                message = "Konum izni verilmedi. Mahalle seçerek devam edebilir veya cihaz ayarlarından izin verebilirsiniz."
            } catch (_: Exception) {
                message = "Yaklaşık konum sağlayıcısı kullanılamıyor. Mahalle seçerek devam edebilirsiniz."
            } finally {
                loading = false
            }
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) fetchLocation()
        else message = "Konum izni verilmedi. Mahalle seçerek devam edebilir veya cihaz ayarlarından izin verebilirsiniz."
    }
    Column(modifier) {
        Text("İsteğe bağlı yaklaşık cihaz konumu", style = MaterialTheme.typography.labelLarge)
        Text("Bu düğme konum izni ister. İlanınız yayımlanırken yalnızca yaklaşık bölge paylaşılır; kesin koordinat yayımlanmaz.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(
            enabled = !loading,
            onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) fetchLocation()
                else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            },
            modifier = Modifier.fillMaxWidth().testTag("btn_use_approximate_location")
        ) { Text(if (loading) "Konum alınıyor…" else "Yaklaşık cihaz konumumu kullan") }
        Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("location_permission_status"))
    }
}

@SuppressLint("MissingPermission") // Permission is checked immediately before registering the foreground listener.
private suspend fun readApproximateDeviceLocation(context: Context): GeoCoordinate {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        throw SecurityException("Approximate location permission is required.")
    }
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: error("Location service unavailable")
    check(manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) { "Network location disabled" }
    return suspendCancellableCoroutine { continuation ->
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                // Ignore stale fixes, including provider cache delivered as an initial update.
                if (!isFreshLocationFix(location.elapsedRealtimeNanos, SystemClock.elapsedRealtimeNanos())) return
                if (!continuation.isActive) return
                val coordinate = runCatching { GeoCoordinate(location.latitude, location.longitude) }
                runCatching { manager.removeUpdates(this) }
                coordinate.fold(onSuccess = { continuation.resume(it) }, onFailure = { continuation.resumeWithException(it) })
            }
            override fun onProviderDisabled(provider: String) {
                runCatching { manager.removeUpdates(this) }
                if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Location disabled"))
            }
            override fun onProviderEnabled(provider: String) = Unit
            @Deprecated("Legacy provider status callback")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        continuation.invokeOnCancellation { runCatching { manager.removeUpdates(listener) } }
        try {
            manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, listener, Looper.getMainLooper())
            // Cancellation can race with registration; remove a just-registered listener as well.
            if (!continuation.isActive) runCatching { manager.removeUpdates(listener) }
        } catch (error: Exception) {
            runCatching { manager.removeUpdates(listener) }
            if (continuation.isActive) continuation.resumeWithException(error)
        }
    }
}
