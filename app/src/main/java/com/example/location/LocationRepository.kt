package com.example.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.tasks.await

data class UserLocation(val latitude: Double, val longitude: Double)

class LocationRepository(private val context: Context) {
    suspend fun getLastKnownLocation(): UserLocation? {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        require(fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) { "Konum izni gerekli." }
        val location = LocationServices.getFusedLocationProviderClient(context).lastLocation.await()
        return location?.let { UserLocation(it.latitude, it.longitude) }
    }
}