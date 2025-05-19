package com.example.taller03

import adapters.UsuarioAdapter
import android.app.UiModeManager
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.PreferenceManager
import com.example.taller03.databinding.ActivityMapaUsuariosBinding
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsResponse
import com.google.android.gms.location.Priority
import com.google.android.gms.location.SettingsClient
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import models.Usuario
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
    private val bogota = GeoPoint(4.62, -74.07)

    private var latitudUsuario = 0.0
    private var longitudUsuario = 0.0

    //Location
    private lateinit var locationClient : FusedLocationProviderClient
    private lateinit var locationRequest : LocationRequest
    private lateinit var locationCallback : LocationCallback

    // Registra resultado para manejar activacion de GPS, si usuario acepta,se inician updates
    val locationSettings = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
        ActivityResultCallback {
            if(it.resultCode == RESULT_OK){
                startLocationUpdates()
            }else{
                Toast.makeText(this, "The GPS is turned off", Toast.LENGTH_SHORT).show()
            }
        }
    )

    // Solicita permiso de ubicacion, si concede, verifica configuracion GPS
    val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
        ActivityResultCallback {
            if(it){
                locationSettings()
            }else{
                Toast.makeText(this, "There is no permission to access the GPS", Toast.LENGTH_SHORT).show()
            }
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapaUsuariosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Configuration.getInstance().load(applicationContext, PreferenceManager.getDefaultSharedPreferences(applicationContext))

        map = binding.mapView
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)

        // Inicializar cliente de ubicación
        locationClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = createLocationRequest()
        locationCallback = createLocationCallback()

        //Datos usurio del seguimiento
        latitudUsuario = intent.getDoubleExtra("latitud", 0.0)
        longitudUsuario = intent.getDoubleExtra("longitud", 0.0)
        val nombre = intent.getStringExtra("nombre") ?: "Usuario"

        locationPermission.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)

        val controller = map.controller
        controller.setZoom(15.0)
        controller.setCenter(GeoPoint(latitudUsuario, longitudUsuario))

        // Marcador del usuario a que se le hace seguimiento
        marcadorUsuario = Marker(map)
        marcadorUsuario!!.position = GeoPoint(latitudUsuario, longitudUsuario)
        marcadorUsuario!!.title = nombre
        marcadorUsuario!!.icon = ContextCompat.getDrawable(this, R.drawable.ic_navigation)
        map.overlays.add(marcadorUsuario)
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
        map.controller.setZoom(18.0)
        map.controller.animateTo(bogota)
        val uims = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        if (uims.nightMode == UiModeManager.MODE_NIGHT_YES) {
            map.overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)
        }
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
        stopLocationUpdates()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    //Location
    private fun createLocationRequest(): com.google.android.gms.location.LocationRequest {
        val request = com.google.android.gms.location.LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setWaitForAccurateLocation(true)
            .setMinUpdateIntervalMillis(5000)
            .build()
        return request
    }

    private fun createLocationCallback(): LocationCallback {
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                super.onLocationResult(result)
                result.lastLocation?.let {
                    updateUI(it)
                }
            }
        }
        return callback
    }

    fun startLocationUpdates(){
        if(ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)== PackageManager.PERMISSION_GRANTED){
            locationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        }
    }

    fun stopLocationUpdates(){
        locationClient.removeLocationUpdates(locationCallback)
    }

    fun locationSettings(){
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        val client: SettingsClient = LocationServices.getSettingsClient(this)
        val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener {
            startLocationUpdates()
        }
        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                try {
                    val isr : IntentSenderRequest = IntentSenderRequest.Builder(exception.resolution).build()
                    locationSettings.launch(isr)
                } catch (sendEx: IntentSender.SendIntentException) {
                    Toast.makeText(this, "There is no GPS hardware", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "No se pudo acceder a la configuración de GPS", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUI(location: Location) {
        val geoPoint = GeoPoint(location.latitude, location.longitude)
        map.controller.setZoom(16.0)
        map.controller.setCenter(geoPoint)

        val marcadorUsuario = Marker(map).apply {
            position = geoPoint
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Mi ubicación"
            icon = ContextCompat.getDrawable(this@MapaUsuariosActivity, R.drawable.ic_my_location1)
        }
        map.overlays.clear()
        map.overlays.add(marcadorUsuario)
        map.invalidate()
    }


    private fun actualizarUbicacionPropia(location: Location) {
        if (marcadorActual == null) {
            marcadorActual = Marker(map)
            marcadorActual!!.icon = ContextCompat.getDrawable(this, R.drawable.ic_my_location1)
            map.overlays.add(marcadorActual)
        }
        marcadorActual!!.position = GeoPoint(location.latitude, location.longitude)
        marcadorActual!!.title = "Tú"
        map.invalidate()
    }

    private fun actualizarDistancia(location: Location) {
        val locUsuario = Location("").apply {
            latitude = latitudUsuario
            longitude = longitudUsuario
        }

        val distancia = location.distanceTo(locUsuario) // en metros
        Toast.makeText(this, "Distancia: %.2f metros".format(distancia), Toast.LENGTH_SHORT).show()
    }

}
