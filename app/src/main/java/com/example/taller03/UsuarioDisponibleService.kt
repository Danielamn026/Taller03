package com.example.taller03

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.example.taller03.R

class UsuarioDisponibleService : Service() {

    private lateinit var dbRef: DatabaseReference
    private lateinit var listener: ValueEventListener
    private val canalId = "canal_usuarios"
    private val usuariosNotificados = mutableSetOf<String>()
    private var notid = 0

    override fun onCreate() {
        super.onCreate()
        Log.d("Servicio", "Servicio creado")

        dbRef = FirebaseDatabase.getInstance().getReference("usuarios")
        crearCanal()

        listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (usuarioSnap in snapshot.children) {
                    val uid = usuarioSnap.key ?: continue
                    val disponible = usuarioSnap.child("disponible").getValue(Boolean::class.java) ?: false
                    if (disponible && !usuariosNotificados.contains(uid)) {
                        val nombre = usuarioSnap.child("nombre").getValue(String::class.java) ?: "Usuario"
                        val latitud = usuarioSnap.child("latitud").getValue(Double::class.java) ?: 0.0
                        val longitud = usuarioSnap.child("longitud").getValue(Double::class.java) ?: 0.0

                        usuariosNotificados.add(uid)
                        val notification = buildNotification(
                            "Usuario disponible",
                            "$nombre está ahora disponible para seguimiento",
                            R.drawable.ic_person, // usa el mismo ícono que en tu recurso
                            Intent(this@UsuarioDisponibleService, MapaUsuariosActivity::class.java).apply {
                                putExtra("uid", uid)
                                putExtra("nombre", nombre)
                                putExtra("latitud", latitud)
                                putExtra("longitud", longitud)
                            }
                        )
                        notify(notification)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Servicio", "Error al leer: ${error.message}")
            }
        }

        dbRef.addValueEventListener(listener)
    }

    private fun crearCanal() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                canalId,
                "Usuarios disponibles",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifica cuando un usuario está disponible"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(canal)
        }
    }

    private fun buildNotification(title: String, message: String, icon: Int, intent: Intent): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, canalId)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun notify(notification: Notification) {
        notid++
        val notificationManager = NotificationManagerCompat.from(this)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify(notid, notification)
        } else {
            Log.d("Servicio", "No tiene permiso para notificaciones")
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        dbRef.removeEventListener(listener)
        Log.d("Servicio", "Servicio destruido")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
