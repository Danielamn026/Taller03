package com.example.taller03

import android.app.*
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import android.annotation.SuppressLint

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import models.Usuario

class UsuarioDisponibleService : Service() {

    private lateinit var databaseReference: DatabaseReference
    private val disponibilidadMap = mutableMapOf<String, String>() // idUsuario -> disponibilidad previa

    @SuppressLint("ForegroundServiceType")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        crearCanalNotificacion()

        val notification = NotificationCompat.Builder(this, "canal_usuarios")
            .setContentTitle("Escuchando usuarios")
            .setContentText("El servicio está activo")
            .setSmallIcon(R.drawable.ic_notificacion)
            .build()

        startForeground(1, notification)

        iniciarEscuchaFirebase()

        return START_STICKY
    }

    private fun iniciarEscuchaFirebase() {
        databaseReference = FirebaseDatabase.getInstance().getReference("usuarios")

        databaseReference.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (userSnapshot in snapshot.children) {
                    val usuario = userSnapshot.getValue(Usuario::class.java) ?: continue
                    val id = userSnapshot.key ?: continue
                    val disponibilidadPrev = disponibilidadMap[id]
                    val disponibilidadActual = usuario.disponibilidad

                    if (disponibilidadPrev != "Disponible" && disponibilidadActual == "Disponible") {
                        lanzarNotificacion(this@UsuarioDisponibleService, usuario)
                    }
                    disponibilidadMap[id] = disponibilidadActual
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@UsuarioDisponibleService, "Error en Firebase: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        // No hay que remover listeners explícitamente a menos que uses removeEventListener
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "canal_usuarios",
                "Escucha de Usuarios",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun lanzarNotificacion(context: Context, usuario: Usuario) {
        val currentUser = FirebaseAuth.getInstance().currentUser

        val intent = if (currentUser != null) {
            Intent(context, MapaUsuariosActivity::class.java).apply {
                putExtra("usuario_id", usuario.identificacion)
            }
        } else {
            Intent(context, AutenticacionActivity::class.java)
        }

        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, "canal_usuarios")
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle("Nuevo Usuario Disponible")
            .setContentText("${usuario.nombre} ${usuario.apellidos} ahora está disponible")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationManager = NotificationManagerCompat.from(context)

        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }
}
