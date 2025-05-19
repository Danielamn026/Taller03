package com.example.taller03

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.taller03.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        // Si el usuario ya inició sesión
        if (currentUser != null) {
            // Solicita permiso si es necesario, y lanza el servicio luego
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
                } else {
                    iniciarAppConUsuario()
                }
            } else {
                iniciarAppConUsuario()
            }
            return
        }

        // Si el usuario no ha iniciado sesión, muestra opciones
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.registrate.setOnClickListener {
            startActivity(Intent(this, RegistroActivity::class.java))
        }

        binding.iniciarSesion.setOnClickListener {
            startActivity(Intent(this, AutenticacionActivity::class.java))
        }
    }

    private fun iniciarAppConUsuario() {
        // Inicia el servicio de notificación
        startService(Intent(this, UsuarioDisponibleService::class.java))

        // Redirige al mapa
        val intent = Intent(this, MapaActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    // Manejo de la respuesta al permiso
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("Permisos", "Permiso de notificaciones concedido")
            } else {
                Log.d("Permisos", "Permiso de notificaciones denegado")
            }

            // Inicia la app independientemente de la respuesta (el servicio hará verificación antes de mostrar notificación)
            iniciarAppConUsuario()
        }
    }
}
