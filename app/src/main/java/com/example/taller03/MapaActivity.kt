package com.example.taller03

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location

import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.taller03.databinding.ActivityMapaBinding
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsResponse
import com.google.android.gms.location.Priority
import com.google.android.gms.location.SettingsClient
import com.google.android.gms.tasks.Task
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.TilesOverlay

class MapaActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMapaBinding

    lateinit var map: MapView
    private val bogota = GeoPoint(4.62, -74.07)

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private val userId = FirebaseAuth.getInstance().currentUser?.uid
    private lateinit var disponibilidad: String

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

    //Permiso de Notificaciones
    val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
        ActivityResultCallback { isGranted ->
            if (!isGranted) {
                Toast.makeText(this, "Permiso de notificación denegado", Toast.LENGTH_SHORT).show()
            }
        }
    )


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Configuration.getInstance().load(this, androidx.preference.PreferenceManager.getDefaultSharedPreferences(this))

        map = binding.Map
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
       // map.overlays.add(createOverlayEvents())

        // Inicializar cliente de ubicación
        locationClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = createLocationRequest()
        locationCallback = createLocationCallback()

        locationPermission.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
        notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)

        //Extraer datos de FireBase
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference
        val uid = auth.currentUser?.uid

        if (uid == null) {
            Toast.makeText(this, "Usuario no autenticado", Toast.LENGTH_SHORT).show()
            return
        }

        database.child("usuarios").child(uid).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    disponibilidad = snapshot.child("disponibilidad").getValue(String::class.java) ?: ""
                    binding.disponibilidad.text = "$disponibilidad"
                    if (disponibilidad == "Disponible") {
                        binding.disponibilidad.setTextColor(Color.parseColor("#06C513"))
                    } else {
                        binding.disponibilidad.setTextColor(Color.parseColor("#9D0A0A"))
                    }
                } else {
                    Toast.makeText(this, "No hay datos del usuario", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al obtener datos del usuario", Toast.LENGTH_SHORT).show()
            }

        //Acciones del menu
        val toolbar = findViewById<MaterialToolbar>(R.id.topAppBar)

        toolbar.setNavigationOnClickListener {
            val popup = PopupMenu(this, toolbar)
            popup.menuInflater.inflate(R.menu.menu, popup.menu)

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.usuarios_disponibles -> {
                        startActivity(Intent(this, UsuariosDisponiblesActivity::class.java))
                        true
                    }
                    R.id.establecer_disponible -> {
                        if(disponibilidad == "No Disponible") {
                            binding.disponibilidad.text = "Disponible"
                            disponibilidad= "Disponible"
                            binding.disponibilidad.setTextColor(Color.parseColor("#06C513"))
                            database.child("usuarios").child(uid).child("disponibilidad").setValue("Disponible")
                                .addOnSuccessListener { Toast.makeText(this, "Disponibilidad Actualizada", Toast.LENGTH_SHORT).show() }
                                .addOnFailureListener { Toast.makeText(this, "Error al guardar disponibilidad", Toast.LENGTH_SHORT).show() }
                        } else {
                            binding.disponibilidad.text = "No Disponible"
                            disponibilidad= "No Disponible"
                            binding.disponibilidad.setTextColor(Color.parseColor("#9D0A0A"))
                            database.child("usuarios").child(uid).child("disponibilidad").setValue("No Disponible")
                                .addOnSuccessListener { Toast.makeText(this, "*Disponibilidad Actualizada", Toast.LENGTH_SHORT).show() }
                                .addOnFailureListener { Toast.makeText(this, "Error al guardar disponibilidad", Toast.LENGTH_SHORT).show() }
                        }

                        true
                    }
                    R.id.cerrar_sesion -> {
                        auth.signOut()
                        val i = Intent(this, MainActivity::class.java)
                        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        startActivity(i)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
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
        return object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                super.onLocationResult(result)
                val newLocation = result.lastLocation

                if (newLocation == null) {
                    Toast.makeText(this@MapaActivity, "Ubicación no detectada", Toast.LENGTH_SHORT).show()
                    return
                }

                database.child("usuarios").child(userId!!).child("latitud").setValue(newLocation.latitude)
                database.child("usuarios").child(userId!!).child("longitud").setValue(newLocation.longitude)
                    .addOnSuccessListener {
                        Log.e("GeoHome", "Geo actualizados")
                        updateUI(newLocation)
                    }
                    .addOnFailureListener {
                        Log.e("GeoHome", "Error al actualizar geo")
                    }
            }
        }
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
        //map.controller.setZoom(16.0)
        map.controller.setCenter(geoPoint)

        val marcadorUsuario = Marker(map).apply {
            position = geoPoint
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Mi ubicación"
            icon = ContextCompat.getDrawable(this@MapaActivity, R.drawable.ic_my_location1)
        }

        map.overlays.clear()
        map.overlays.add(marcadorUsuario)
        cargarPuntosDeInteres()
        map.invalidate()
    }

    private fun cargarPuntosDeInteres() {
        try {
            // Leer el archivo desde assets
            val inputStream = assets.open("locations.json")
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()

            val json = String(buffer, Charsets.UTF_8)

            // Parsear el JSON
            val jsonObject = org.json.JSONObject(json)
            val locationsArray = jsonObject.getJSONArray("locationsArray")

            for (i in 0 until locationsArray.length()) {
                val locationObj = locationsArray.getJSONObject(i)

                val lat = locationObj.getDouble("latitude")
                val lon = locationObj.getDouble("longitude")
                val name = locationObj.getString("name")

                val punto = GeoPoint(lat, lon)
                val marcador = Marker(map).apply {
                    position = punto
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = name
                    icon = ContextCompat.getDrawable(this@MapaActivity, R.drawable.ic_poi)
                }

                map.overlays.add(marcador)
            }
        } catch (e: Exception) {
            Log.e("MapaActivity", "Error al cargar JSON: ${e.message}")
            Toast.makeText(this, "No se pudieron cargar los puntos de interés", Toast.LENGTH_SHORT).show()
        }
    }
}