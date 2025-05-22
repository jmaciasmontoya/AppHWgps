package com.example.apphwgps

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                viewModel.onPermissionGranted()
            }
            else -> {
                viewModel.onPermissionDenied()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        viewModel = viewModel,
                        onRequestPermission = { requestLocationPermission() }
                    )
                }
            }
        }
    }

    private fun requestLocationPermission() {
        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onRequestPermission: () -> Unit
) {
    val context = LocalContext.current
    val locationData by viewModel.locationData.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val permissionState by viewModel.permissionState.collectAsStateWithLifecycle()
    val gpsState by viewModel.gpsState.collectAsStateWithLifecycle()
    val lastSavedLocation by viewModel.lastSavedLocation.collectAsStateWithLifecycle()
    val exportStatus by viewModel.exportStatus.collectAsStateWithLifecycle()
    var showHistoryDialog by remember { mutableStateOf(false) }

    val locationPermissionState = rememberPermissionState(
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    LaunchedEffect(permissionState) {
        if (permissionState == PermissionState.GRANTED) {
            viewModel.startLocationUpdates()
        }
    }

    LaunchedEffect(gpsState) {
        if (gpsState == GpsState.ENABLED) {
            viewModel.startLocationUpdates()
        }
    }

    LaunchedEffect(exportStatus) {
        exportStatus?.let {
            // Mostrar mensaje de estado de exportación
            viewModel.clearExportStatus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Título
        Text(
            text = "GPS Tracker",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Estado de permisos
        when (permissionState) {
            PermissionState.DENIED -> {
                PermissionRequestCard(
                    onRequestPermission = onRequestPermission
                )
            }
            PermissionState.GRANTED -> {
                // Estado del GPS
                when (gpsState) {
                    GpsState.DISABLED -> {
                        GpsDisabledCard(
                            onOpenSettings = { viewModel.openLocationSettings() }
                        )
                    }
                    GpsState.ENABLED -> {
                        // Mostrar datos de ubicación
                        locationData?.let { data ->
                            LocationDataCard(data = data)
                        }
                    }
                    GpsState.CHECKING -> {
                        CircularProgressIndicator()
                    }
                }

                // Botón de actualizar
                Button(
                    onClick = { viewModel.refreshGpsState() },
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Actualizar"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Actualizar Ubicación")
                }

                // Historial de ubicaciones
                LocationHistoryCard(
                    lastSavedLocation = lastSavedLocation,
                    onViewHistory = { showHistoryDialog = true },
                    onExportCsv = {
                        viewModel.exportToCsv()?.let { uri ->
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/csv"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Compartir CSV"))
                        }
                    }
                )
            }
            PermissionState.UNKNOWN -> {
                CircularProgressIndicator()
            }
        }

        // Mostrar errores
        error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }

    // Diálogo de historial
    if (showHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            title = { Text("Historial de Ubicaciones") },
            text = {
                Text(
                    text = viewModel.getLocationHistory(),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = { showHistoryDialog = false }) {
                    Text("Cerrar")
                }
            }
        )
    }
}

@Composable
fun PermissionRequestCard(onRequestPermission: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Se requiere permiso de ubicación",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onRequestPermission) {
                Text("Solicitar Permiso")
            }
        }
    }
}

@Composable
fun GpsDisabledCard(onOpenSettings: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "GPS Desactivado",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onOpenSettings) {
                Text("Activar GPS")
            }
        }
    }
}

@Composable
fun LocationDataCard(data: LocationData) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Ubicación Actual",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text("Latitud: ${data.latitude}")
            Text("Longitud: ${data.longitude}")
            Text("Precisión: ${String.format("%.1f", data.accuracy)} metros")
            Text("Proveedor: ${data.getProviderInfo()}")
            Text("Simulado: ${if (data.isMock) "Sí" else "No"}")
            Text("Última actualización: ${data.getFormattedDateTime()}")
        }
    }
}

@Composable
fun LocationHistoryCard(
    lastSavedLocation: LocationData?,
    onViewHistory: () -> Unit,
    onExportCsv: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Historial de Ubicaciones",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            lastSavedLocation?.let { savedLocation ->
                Text("Última ubicación guardada:")
                Text("Latitud: ${savedLocation.latitude}")
                Text("Longitud: ${savedLocation.longitude}")
                Text("Guardado: ${savedLocation.getFormattedDateTime()}")
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = onViewHistory) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Ver Historial"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ver Historial")
                }

                Button(onClick = onExportCsv) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Exportar CSV"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Exportar CSV")
                }
            }
        }
    }
}