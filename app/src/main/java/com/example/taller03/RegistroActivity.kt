package com.example.taller03

import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.IntentSenderRequest
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.example.taller03.databinding.ActivityRegistroBinding
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.io.File
import java.util.regex.Pattern

class RegistroActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegistroBinding
    private lateinit var auth: FirebaseAuth
    private var uri: Uri? = null
    private lateinit var database: DatabaseReference
    private lateinit var storageRef : StorageReference

    private var notid = 0

    //Imagen de perfil
    private val galeriaLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) {
        if (it != null) loadImage(it)
    }

    private val camaraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) {
        if (it) {
            uri?.let { nonNullUri -> loadImage(nonNullUri) }
        }
    }

    //Location
    private lateinit var locationClient : FusedLocationProviderClient
    private lateinit var locationRequest : LocationRequest
    private lateinit var locationCallback : LocationCallback
    private var latitudActual: Double = 0.0
    private var longitudActual: Double = 0.0

    val locationSettings = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
        ActivityResultCallback {
            if(it.resultCode == RESULT_OK){
                startLocationUpdates()
            } else {
                Toast.makeText(this, "The GPS is turned off", Toast.LENGTH_SHORT).show()
            }
        }
    )

    val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
        ActivityResultCallback {
            if(it){
                locationSettings()
            } else {
                Toast.makeText(this, "There is no permission to access the GPS", Toast.LENGTH_SHORT).show()
            }
        }
    )

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegistroBinding.inflate(layoutInflater)
        setContentView(binding.root)

        locationClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = createLocationRequest()
        locationCallback = createLocationCallback()
        locationPermission.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference
        storageRef = FirebaseStorage.getInstance().reference.child("imagenes_perfil")
        setUpBinding()

        if (auth.currentUser != null) {
            val intent = Intent(this, MapaActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        auth.currentUser?.let {
            val intent = Intent(this, MapaActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        binding.imagenRegistro.setOnClickListener {
            mostrarOpcionesImagen()
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

    private fun mostrarOpcionesImagen() {
        val opciones = arrayOf("Tomar foto", "Elegir de galería")
        AlertDialog.Builder(this)
            .setTitle("Seleccionar imagen de perfil")
            .setItems(opciones) { _, which ->
                when (which) {
                    0 -> abrirCamara()
                    1 -> abrirGaleria()
                }
            }
            .show()
    }

    private fun abrirGaleria() {
        galeriaLauncher.launch("image/*")
    }

    private fun abrirCamara() {
        val file = File(filesDir, "picFromCamera.jpg")
        if (!file.exists()) {
            file.createNewFile()
        }
        uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        uri?.let {
            camaraLauncher.launch(it)
        }
    }


    private fun loadImage(uri: Uri) {
        this.uri = uri
        Glide.with(this)
            .load(uri)
            .circleCrop()
            .into(binding.imagenRegistro)
    }


    private fun validarYRegistrarUsuario() {
        val nombre = binding.nombreRegistro.text.toString()
        val apellidos = binding.apellidosRegistro.text.toString()
        val id = binding.identificacionRegistro.text.toString()
        val email = binding.correoRegistro.text.toString()
        val password = binding.contrasenaRegistro.text.toString()

        if (nombre.isEmpty() || apellidos.isEmpty() || id.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Por favor, completa todos los campos", Toast.LENGTH_SHORT).show()
            return
        }

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null && ::uri != null) {
                        subirImagenYGuardarUsuario(user.uid, nombre, apellidos, id, user.email ?: "", uri)
                    } else {
                        guardarDatosUsuario(nombre, apellidos, id, "")
                    }
                } else {
                    val exceptionMessage = task.exception?.message ?: "Error desconocido"
                    Log.e("RegistroActivity", "Error en el registro: $exceptionMessage")
                    Toast.makeText(this, "Error en el registro", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun subirImagenYGuardarUsuario(
        uid: String,
        nombre: String,
        apellidos: String,
        id: String,
        email: String,
        uri: Uri?
    ) {
        uri?.let{
            storageRef.child(uid).putFile(it)
                .addOnSuccessListener { task ->
                    task.metadata!!.reference!!.downloadUrl
                        .addOnSuccessListener { url ->
                            val imageUrl = url.toString()
                            Log.d("Upload", "URL de descarga obtenida: $imageUrl")
                            guardarDatosUsuario(nombre, apellidos, id, imageUrl)

                            database.child(uid).setValue(uid)
                                .addOnSuccessListener {
                                    Toast.makeText(
                                        baseContext,
                                        "Gurardado con exito",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                .addOnFailureListener {
                                    Toast.makeText(baseContext, "Error ${it.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                }
        }

    }


    private fun guardarDatosUsuario(nombre: String, apellidos: String, id: String, imagenUrl: String) {
        val usuario = auth.currentUser
        val uid = usuario?.uid ?: return
        if (uid != null) {
            val datosUsuario = mapOf(
                "nombre" to nombre,
                "apellidos" to apellidos,
                "identificacion" to id,
                "correo" to usuario.email,
                "latitud" to latitudActual,
                "longitud" to longitudActual,
                "disponibilidad" to "No Disponible",
                "imagenUrl" to imagenUrl
            )

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

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        }
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
}
