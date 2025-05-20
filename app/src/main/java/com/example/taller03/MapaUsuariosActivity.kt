package com.example.taller03

import android.app.UiModeManager
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
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

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private val userId = FirebaseAuth.getInstance().currentUser?.uid

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

        //Extraer datos de FireBase
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference

        // Lanzar solicitud de permisos
        locationPermission.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)

        // Obtener datos del usuario a seguir
        val correo = intent.getStringExtra("correo") ?: "Sin Correo"
        val nombre = intent.getStringExtra("nombre") ?: "Sin Nombre"

        val usuarioRef = database.child("usuarios")
        usuarioRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                for(snapshot in dataSnapshot.children) {
                    val usuario = snapshot.getValue(Usuario::class.java)
                    if (usuario != null && usuario.correo == correo) {
                        latitudUsuario = usuario.latitud
                        longitudUsuario = usuario.longitud

                        //Marcador del usuario objetivo
                        if (marcadorUsuario == null) {
                            marcadorUsuario = Marker(map).apply {
                                position = GeoPoint(latitudUsuario, longitudUsuario)
                                title = nombre
                                icon = ContextCompat.getDrawable(this@MapaUsuariosActivity, R.drawable.ic_poi)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            }
                            map.overlays.add(marcadorUsuario)
                            map.controller.setZoom(15.0)
                            map.controller.setCenter(marcadorUsuario!!.position)
                        } else {
                            val nuevaPosicion = GeoPoint(latitudUsuario, longitudUsuario)
                            if (marcadorUsuario!!.position != nuevaPosicion) {
                                marcadorUsuario!!.position = nuevaPosicion
                                map.controller.animateTo(nuevaPosicion)
                            }
                        }
                        map.invalidate()
                        break
                    }
                }
            }
            override fun onCancelled(databaseError: DatabaseError) {
                Toast.makeText(this@MapaUsuariosActivity, "Error al obtener datos del usuarioSeg", Toast.LENGTH_SHORT).show()
            }
        })
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
                super.onLocationResult(result)
                val newLocation = result.lastLocation

                if (newLocation == null) {
                    Toast.makeText(this@MapaUsuariosActivity, "Ubicación no detectada", Toast.LENGTH_SHORT).show()
                    return
                }

                database.child("usuarios").child(userId!!).child("latitud").setValue(newLocation.latitude)
                database.child("usuarios").child(userId!!).child("longitud").setValue(newLocation.longitude)
                    .addOnSuccessListener {
                        Log.e("GeoMapaUsuario", "Geo actualizados")
                    }
                    .addOnFailureListener {
                        Log.e("GeoMapaUsuario", "Error al actualizar geo")
                    }
                    actualizarUbicacionPropia(newLocation)
                    actualizarDistancia(newLocation)
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
        //map.controller.animateTo(geoPoint)
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
