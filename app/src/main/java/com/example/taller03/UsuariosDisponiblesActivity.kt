package com.example.taller03

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.taller03.databinding.ActivityUsuariosDisponiblesBinding

class UsuariosDisponiblesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityUsuariosDisponiblesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUsuariosDisponiblesBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}