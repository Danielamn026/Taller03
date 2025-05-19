package com.example.taller03

import android.app.UiModeManager
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.widget.Toast
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.example.taller03.databinding.ActivityMapaUsuariosBinding
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.TilesOverlay

class MapaUsuariosActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMapaUsuariosBinding
    private lateinit var map: MapView

    private var marcadorUsuario: Marker? = null
    private var marcadorActual: Marker? = null

    private var latitudUsuario = 0.0
    private var longitudUsuario = 0.0

    private lateinit var locationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    private val locationSettings = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
        ActivityResultCallback {
            if (it.resultCode == RESULT_OK) {
                startLocationUpdates()
            } else {
                Toast.makeText(this, "El GPS está apagado", Toast.LENGTH_SHORT).show()
            }
        }
    )

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
        ActivityResultCallback { granted ->
            if (granted) {
                locationSettings()
            } else {
                Toast.makeText(this, "No se concedió el permiso de ubicación", Toast.LENGTH_SHORT).show()
            }
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapaUsuariosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configuración de osmdroid
        Configuration.getInstance().load(applicationContext, PreferenceManager.getDefaultSharedPreferences(applicationContext))

        map = binding.mapView
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)

        // Inicializar cliente de ubicación
        locationClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = createLocationRequest()
        locationCallback = createLocationCallback()

        // Obtener datos del usuario a seguir
        latitudUsuario = intent.getDoubleExtra("latitud", 0.0)
        longitudUsuario = intent.getDoubleExtra("longitud", 0.0)
        val nombre = intent.getStringExtra("nombre") ?: "Usuario"

        // Lanzar solicitud de permisos
        locationPermission.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)

        // Marcador del usuario objetivo
        marcadorUsuario = Marker(map).apply {
            position = GeoPoint(latitudUsuario, longitudUsuario)
            title = nombre
            icon = ContextCompat.getDrawable(this@MapaUsuariosActivity, R.drawable.ic_navigation)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
        map.overlays.add(marcadorUsuario)
        map.controller.setZoom(15.0)
        map.controller.setCenter(marcadorUsuario!!.position)
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
        if ((getSystemService(Context.UI_MODE_SERVICE) as UiModeManager).nightMode == UiModeManager.MODE_NIGHT_YES) {
            map.overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)
        }
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
        stopLocationUpdates()
    }

    private fun createLocationRequest(): LocationRequest {
        return LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setWaitForAccurateLocation(true)
            .setMinUpdateIntervalMillis(5000)
            .build()
    }

    private fun createLocationCallback(): LocationCallback {
        return object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    actualizarUbicacionPropia(location)
                    actualizarDistancia(location)
                }
            }
        }
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        }
    }

    private fun stopLocationUpdates() {
        locationClient.removeLocationUpdates(locationCallback)
    }

    private fun locationSettings() {
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        val client: SettingsClient = LocationServices.getSettingsClient(this)
        val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener { startLocationUpdates() }
        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                try {
                    val isr = IntentSenderRequest.Builder(exception.resolution).build()
                    locationSettings.launch(isr)
                } catch (sendEx: IntentSender.SendIntentException) {
                    Toast.makeText(this, "No se pudo abrir configuración de GPS", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "GPS no disponible", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun actualizarUbicacionPropia(location: Location) {
        val geoPoint = GeoPoint(location.latitude, location.longitude)

        if (marcadorActual == null) {
            marcadorActual = Marker(map).apply {
                icon = ContextCompat.getDrawable(this@MapaUsuariosActivity, R.drawable.ic_my_location1)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "Tú"
            }
            map.overlays.add(marcadorActual)
        }

        marcadorActual!!.position = geoPoint

        map.controller.animateTo(geoPoint)
        map.invalidate()
    }

    private fun actualizarDistancia(location: Location) {
        val destino = Location("").apply {
            latitude = latitudUsuario
            longitude = longitudUsuario
        }

        val distancia = location.distanceTo(destino)
        Toast.makeText(this, "Distancia al usuario: %.2f metros".format(distancia), Toast.LENGTH_SHORT).show()
    }
}
