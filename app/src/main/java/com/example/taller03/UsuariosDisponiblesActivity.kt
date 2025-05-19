package com.example.taller03

import adapters.UsuarioAdapter
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.taller03.databinding.ActivityUsuariosDisponiblesBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import models.Usuario

class UsuariosDisponiblesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityUsuariosDisponiblesBinding
    private lateinit var database: DatabaseReference
    private val userId = FirebaseAuth.getInstance().currentUser?.uid
    val emailActual = FirebaseAuth.getInstance().currentUser?.email ?: ""

    private lateinit var usuarioAdapter: UsuarioAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUsuariosDisponiblesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = FirebaseDatabase.getInstance().reference

        cargarUsuariosDisponibles()
    }

    private fun cargarUsuariosDisponibles() {
        val listaUsuarios = mutableListOf<Usuario>()
        val adapter = UsuarioAdapter(this, listaUsuarios)

        binding.rvUsuarios.adapter = adapter
        binding.rvUsuarios.layoutManager = LinearLayoutManager(this)

        database.child("usuarios").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                listaUsuarios.clear()
                for (usuarioSnap in snapshot.children) {
                    val usuario = usuarioSnap.getValue(Usuario::class.java)
                    if (usuario != null && usuario.disponibilidad == "Disponible" && usuario.correo != emailActual) {
                        listaUsuarios.add(usuario)
                    }
                }
                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@UsuariosDisponiblesActivity, "Error al cargar usuarios", Toast.LENGTH_SHORT).show()
            }
        })
    }


}