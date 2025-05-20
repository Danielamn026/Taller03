package com.example.taller03

import android.app.Notification
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.IntentSenderRequest
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import com.example.taller03.databinding.ActivityRegistroBinding
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
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.Firebase
import com.google.firebase.database.database
import java.util.regex.Pattern

class RegistroActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegistroBinding
    private lateinit var auth: FirebaseAuth

    private var notid = 0

    //Location
    private lateinit var locationClient : FusedLocationProviderClient
    private lateinit var locationRequest : LocationRequest
    private lateinit var locationCallback : LocationCallback
    private var latitudActual: Double = 0.0
    private var longitudActual: Double = 0.0

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


    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegistroBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicializar cliente de ubicación
        locationClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = createLocationRequest()
        locationCallback = createLocationCallback()


        // Solicitar permiso de ubicación
        locationPermission.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)

        auth = FirebaseAuth.getInstance()
        setUpBinding()

        val currentUser = auth.currentUser
        if (currentUser != null) {
            val intent = Intent(this, MapaActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        binding.botonBackRegistro.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        binding.ingresarRegistro.setOnClickListener {
            validarYRegistrarUsuario()
        }
        binding.cancelarRegistro.setOnClickListener {
            finish()
        }
    }


    private fun validarYRegistrarUsuario() {
        val nombre = binding.nombreRegistro.text.toString()
        val apellidos = binding.apellidosRegistro.text.toString()
        val id = binding.identificacionRegistro.text.toString()
        val email = binding.correoRegistro.text.toString()
        val password = binding.contrasenaRegistro.text.toString()

        if (nombre.isEmpty() || apellidos.isEmpty() || id.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Por favor, completa todos los campos", Toast.LENGTH_SHORT).show()
        } else {
            registerUser(nombre, apellidos, id, email, password)
        }
    }

    private fun registerUser(nombre: String, apellidos: String, id: String, email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user: FirebaseUser? = auth.currentUser
                    guardarDatosUsuario(nombre, apellidos, id)
                    Toast.makeText(this, "Registro exitoso", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, AutenticacionActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this, "Error en el registro", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun guardarDatosUsuario(nombre: String, apellidos: String, id: String) {
        val usuario = FirebaseAuth.getInstance().currentUser
        val uid = usuario?.uid
        if (uid != null) {
            val datosUsuario = mapOf(
                "nombre" to nombre,
                "apellidos" to apellidos,
                "identificacion" to id,
                "correo" to usuario.email,
                "latitud" to latitudActual,
                "longitud" to longitudActual,
                "disponibilidad" to "No Disponible",
                "imagenUrl" to "",
            )

            val database = Firebase.database.reference
            val userRef = database.child("usuarios").child(uid)
            userRef.setValue(datosUsuario)
                .addOnSuccessListener {
                    Toast.makeText(baseContext, "Registro exitoso", Toast.LENGTH_SHORT).show()
                    // Aquí continuar a la pantalla principal
                }
                .addOnFailureListener { e ->
                    println("Error al guardar los datos del usuario: ${e.message}")
                }
        } else {
            println("No hay un usuario autenticado.")
        }
    }

    private fun setUpBinding() {
        binding.correoRegistro.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val email = s.toString()
                val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$"
                val pattern = Pattern.compile(emailRegex)

                if (email.isNotEmpty() && !pattern.matcher(email).matches()) {
                    binding.correoRegistro.error = "Correo no válido"
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.contrasenaRegistro.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val password = s.toString()
                if (password.isNotEmpty() && password.length < 6) {
                    binding.contrasenaRegistro.error = "Mínimo 6 caracteres"
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
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
                    latitudActual = it.latitude
                    longitudActual = it.longitude
                    binding.latLngRegistro.text = "${it.latitude}, ${it.longitude}"
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
}