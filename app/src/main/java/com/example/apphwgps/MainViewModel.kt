package com.example.apphwgps

import android.app.Application
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

enum class PermissionState {
    UNKNOWN,
    GRANTED,
    DENIED
}

enum class GpsState {
    ENABLED,
    DISABLED,
    CHECKING
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val locationManager = LocationManager(application)
    
    // Estados
    private val _locationData = MutableStateFlow<LocationData?>(null)
    val locationData: StateFlow<LocationData?> = _locationData

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _permissionState = MutableStateFlow(PermissionState.UNKNOWN)
    val permissionState: StateFlow<PermissionState> = _permissionState

    private val _gpsState = MutableStateFlow(GpsState.CHECKING)
    val gpsState: StateFlow<GpsState> = _gpsState

    private val _lastSavedLocation = MutableStateFlow<LocationData?>(null)
    val lastSavedLocation: StateFlow<LocationData?> = _lastSavedLocation

    private val _exportStatus = MutableStateFlow<String?>(null)
    val exportStatus: StateFlow<String?> = _exportStatus

    init {
        checkPermissionState()
        checkGpsState()
    }

    private fun checkGpsState() {
        _gpsState.value = GpsState.CHECKING
        try {
            val androidLocationManager = getApplication<Application>().getSystemService(Context.LOCATION_SERVICE) as LocationManager
            _gpsState.value = if (androidLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                GpsState.ENABLED
            } else {
                GpsState.DISABLED
            }
        } catch (e: Exception) {
            _gpsState.value = GpsState.DISABLED
            _error.value = "Error al verificar estado del GPS: ${e.message}"
        }
    }

    private fun checkPermissionState() {
        _permissionState.value = if (locationManager.hasLocationPermission()) {
            PermissionState.GRANTED
        } else {
            PermissionState.DENIED
        }
    }

    fun onPermissionGranted() {
        _permissionState.value = PermissionState.GRANTED
        startLocationUpdates()
    }

    fun onPermissionDenied() {
        _permissionState.value = PermissionState.DENIED
        _error.value = "Se requiere permiso de ubicación para usar esta aplicación"
    }

    fun startLocationUpdates() {
        viewModelScope.launch {
            try {
                _gpsState.value = GpsState.CHECKING
                
                // Primero intentamos obtener la última ubicación conocida
                locationManager.getLastLocation()
                    .catch { e ->
                        Log.e("MainViewModel", "Error al obtener última ubicación: ${e.message}")
                    }
                    .collect { locationData ->
                        _locationData.value = locationData
                        saveLocationToFile(locationData)
                    }

                // Luego iniciamos las actualizaciones continuas
                locationManager.getLocationUpdates()
                    .catch { e ->
                        _error.value = "Error al obtener ubicación: ${e.message}"
                        if (e.message?.contains("GPS y Red desactivados") == true) {
                            _gpsState.value = GpsState.DISABLED
                        }
                    }
                    .collect { locationData ->
                        _locationData.value = locationData
                        saveLocationToFile(locationData)
                        _gpsState.value = GpsState.ENABLED
                        _error.value = null
                    }
            } catch (e: SecurityException) {
                _error.value = "Se requieren permisos de ubicación"
                _permissionState.value = PermissionState.DENIED
            } catch (e: Exception) {
                _error.value = "Error al iniciar actualizaciones: ${e.message}"
                if (e.message?.contains("GPS y Red desactivados") == true) {
                    _gpsState.value = GpsState.DISABLED
                }
            }
        }
    }

    private fun saveLocationToFile(locationData: LocationData) {
        try {
            val file = File(getApplication<Application>().filesDir, "location_history.txt")
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            
            FileWriter(file, true).use { writer ->
                writer.append("""
                    ========================================
                    Registro de Ubicación - $timestamp
                    ========================================
                    Fecha y Hora: ${locationData.getFormattedDateTime()}
                    Latitud: ${locationData.latitude}
                    Longitud: ${locationData.longitude}
                    Proveedor: ${locationData.getProviderInfo()}
                    Precisión: ${String.format("%.1f", locationData.accuracy)} metros
                    Ubicación Simulada: ${if (locationData.isMock) "Sí" else "No"}
                    ========================================
                    
                """.trimIndent())
            }
            _lastSavedLocation.value = locationData
        } catch (e: Exception) {
            _error.value = "Error al guardar la ubicación: ${e.message}"
        }
    }

    fun hasLocationPermission(): Boolean {
        return locationManager.hasLocationPermission()
    }

    fun refreshGpsState() {
        checkGpsState()
        if (_gpsState.value == GpsState.ENABLED) {
            startLocationUpdates()
        }
    }

    fun openLocationSettings() {
        getApplication<Application>().startActivity(locationManager.getLocationSettingsIntent().apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun getLocationHistory(): String {
        return try {
            val file = File(getApplication<Application>().filesDir, "location_history.txt")
            if (file.exists()) {
                file.readText()
            } else {
                "No hay historial de ubicaciones guardado"
            }
        } catch (e: Exception) {
            "Error al leer el historial: ${e.message}"
        }
    }

    fun exportToCsv(): Uri? {
        return try {
            val historyFile = File(getApplication<Application>().filesDir, "location_history.txt")
            if (!historyFile.exists()) {
                _exportStatus.value = "No hay historial para exportar"
                return null
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val csvFile = File(getApplication<Application>().filesDir, "location_history_$timestamp.csv")
            
            FileWriter(csvFile).use { writer ->
                // Escribir encabezados
                writer.append("""
                    Fecha,Hora,Latitud,Longitud,Proveedor,Precision (m),Simulado
                """.trimIndent() + "\n")

                // Leer el archivo de historial y convertirlo a CSV
                historyFile.readLines().forEach { line ->
                    when {
                        line.contains("Fecha y Hora:") -> {
                            val dateTime = line.substringAfter(": ").trim()
                            val (date, time) = dateTime.split(" ")
                            writer.append("$date,$time,")
                        }
                        line.contains("Latitud:") -> {
                            writer.append(line.substringAfter(": ").trim() + ",")
                        }
                        line.contains("Longitud:") -> {
                            writer.append(line.substringAfter(": ").trim() + ",")
                        }
                        line.contains("Proveedor:") -> {
                            writer.append(line.substringAfter(": ").trim() + ",")
                        }
                        line.contains("Precisión:") -> {
                            writer.append(line.substringAfter(": ").replace(" metros", "").trim() + ",")
                        }
                        line.contains("Ubicación Simulada:") -> {
                            val isMock = line.substringAfter(": ").trim() == "Sí"
                            writer.append(if (isMock) "Sí" else "No" + "\n")
                        }
                    }
                }
            }

            // Crear URI para compartir el archivo
            val uri = FileProvider.getUriForFile(
                getApplication(),
                "${getApplication<Application>().packageName}.provider",
                csvFile
            )
            
            _exportStatus.value = "Archivo CSV exportado correctamente"
            uri
        } catch (e: Exception) {
            _exportStatus.value = "Error al exportar: ${e.message}"
            null
        }
    }

    fun clearExportStatus() {
        _exportStatus.value = null
    }
} 