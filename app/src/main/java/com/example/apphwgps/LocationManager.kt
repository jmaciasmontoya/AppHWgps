package com.example.apphwgps

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.text.SimpleDateFormat
import java.util.*

/**
 * Clase de datos que representa la información de ubicación.
 * Esta clase es inmutable (data class) y contiene todos los datos relevantes de una ubicación.
 *
 * @property latitude Latitud de la ubicación
 * @property longitude Longitud de la ubicación
 * @property accuracy Precisión de la ubicación en metros
 * @property provider Proveedor de la ubicación (GPS, Red, etc.)
 * @property isMock Indica si la ubicación es simulada
 * @property timestamp Momento en que se obtuvo la ubicación
 */
data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val provider: String?,
    val isMock: Boolean,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Formatea la fecha y hora de la ubicación en un formato legible.
     * @return String con la fecha y hora formateada (ej: "25/03/2024 15:30:45")
     */
    fun getFormattedDateTime(): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    /**
     * Obtiene información legible del proveedor de ubicación.
     * @return String con el nombre del proveedor (GPS, Red, Pasivo o Desconocido)
     */
    fun getProviderInfo(): String {
        return when (provider) {
            LocationManager.GPS_PROVIDER -> "GPS"
            LocationManager.NETWORK_PROVIDER -> "Red"
            LocationManager.PASSIVE_PROVIDER -> "Pasivo"
            else -> "Desconocido"
        }
    }
}

/**
 * Clase principal para manejar la ubicación en la aplicación.
 * Utiliza las APIs más recientes de Google Play Services para ubicación.
 */
class LocationManager(private val context: Context) {
    private val TAG = "LocationManager"
    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    /**
     * Verifica si la aplicación tiene permisos de ubicación.
     */
    fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Verifica si el GPS está activado.
     */
    fun isGpsEnabled(): Boolean {
        return try {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        } catch (e: Exception) {
            Log.e(TAG, "Error al verificar GPS: ${e.message}")
            false
        }
    }

    /**
     * Verifica si la ubicación por red está activada.
     */
    fun isNetworkEnabled(): Boolean {
        return try {
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            Log.e(TAG, "Error al verificar red: ${e.message}")
            false
        }
    }

    /**
     * Obtiene el Intent para abrir la configuración de ubicación.
     */
    fun getLocationSettingsIntent(): Intent {
        return Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
    }

    /**
     * Obtiene la última ubicación conocida.
     */
    @SuppressLint("MissingPermission")
    fun getLastLocation(): Flow<LocationData> = callbackFlow {
        try {
            if (!hasLocationPermission()) {
                throw SecurityException("No hay permisos de ubicación")
            }

            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    location?.let {
                        val locationData = LocationData(
                            latitude = it.latitude,
                            longitude = it.longitude,
                            accuracy = it.accuracy,
                            provider = it.provider,
                            isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                it.isMock
                            } else {
                                it.isFromMockProvider
                            },
                            timestamp = it.time
                        )
                        trySend(locationData)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error al obtener última ubicación: ${e.message}")
                    close(e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar obtención de última ubicación: ${e.message}")
            close(e)
        }

        awaitClose()
    }

    /**
     * Inicia las actualizaciones de ubicación.
     */
    @SuppressLint("MissingPermission")
    fun getLocationUpdates(intervalMs: Long = 5000): Flow<LocationData> = callbackFlow {
        try {
            if (!hasLocationPermission()) {
                throw SecurityException("No hay permisos de ubicación")
            }

            if (!isGpsEnabled() && !isNetworkEnabled()) {
                throw IllegalStateException("GPS y Red desactivados")
            }

            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
                .setMinUpdateIntervalMillis(intervalMs / 2)
                .setMaxUpdateDelayMillis(intervalMs * 2)
                .setMinUpdateDistanceMeters(0f)
                .build()

            val locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    locationResult.lastLocation?.let { location ->
                        val locationData = LocationData(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            accuracy = location.accuracy,
                            provider = location.provider,
                            isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                location.isMock
                            } else {
                                location.isFromMockProvider
                            },
                            timestamp = location.time
                        )
                        trySend(locationData)
                    }
                }
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            ).addOnFailureListener { e ->
                Log.e(TAG, "Error al solicitar actualizaciones: ${e.message}")
                close(e)
            }

            awaitClose {
                Log.d(TAG, "Deteniendo actualizaciones de ubicación")
                fusedLocationClient.removeLocationUpdates(locationCallback)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar actualizaciones: ${e.message}")
            close(e)
        }
    }
} 