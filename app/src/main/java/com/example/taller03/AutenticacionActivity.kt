package com.example.taller03

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.taller03.databinding.ActivityAutenticacionBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

class AutenticacionActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAutenticacionBinding
    private lateinit var firebaseAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAutenticacionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseAuth = FirebaseAuth.getInstance()

        binding.buttonIngresar.setOnClickListener {
            validarYAutenticarUsuario()
        }
        binding.buttonCancel.setOnClickListener {
            finish()
        }
        binding.botonBack.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        val currentUser = firebaseAuth.currentUser
        if (currentUser != null) {
            val intent = Intent(this, MapaActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun validarYAutenticarUsuario() {
        val email = binding.editTextEmail.text.toString().trim()
        val password = binding.editTextPassword.text.toString().trim()

        if (email.isEmpty()) {
            binding.editTextEmail.error = "El correo es obligatorio"
            binding.editTextEmail.requestFocus()
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.editTextEmail.error = "Ingrese un correo válido"
            binding.editTextEmail.requestFocus()
            return
        }
        if (password.isEmpty()) {
            binding.editTextPassword.error = "La contraseña es obligatoria"
            binding.editTextPassword.requestFocus()
            return
        }

        firebaseAuth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user: FirebaseUser? = firebaseAuth.currentUser
                    Toast.makeText(this, "Inicio de sesión exitoso.", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, MapaActivity::class.java))
                } else {
                    val errorMessage = task.exception?.message ?: "Error desconocido"
                    Toast.makeText(baseContext, "Falló la autenticación: $errorMessage", Toast.LENGTH_LONG).show()
                }
            }
    }
}