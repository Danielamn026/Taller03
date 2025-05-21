package com.example.taller03

import android.app.*
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.app.JobIntentService
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.IBinder
import android.widget.Toast

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import models.Usuario

class UsuarioDisponibleService : Service(){

    private var listenerRegistration: ListenerRegistration? = null

    @SuppressLint("ForegroundServiceType")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        crearCanalNotificacion()

        val notification = NotificationCompat.Builder(this, "canal_usuarios")
            .setContentTitle("Usuario Disponible")
            .setContentText("Un usuario cambio a disponible")
            .setSmallIcon(R.drawable.ic_notificacion)
            .build()

        startForeground(1, notification)

        iniciarEscuchaFirebase()

        return START_STICKY // El sistema intenta reiniciar el servicio si lo mata
    }

    private fun iniciarEscuchaFirebase() {
        val db = FirebaseFirestore.getInstance()
        listenerRegistration = db.collection("usuarios")
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener

                for (doc in snapshots.documents) {
                    val usuario = doc.toObject(Usuario::class.java)
                    if (usuario != null && usuario.disponibilidad == "Disponible") {
                        lanzarNotificacion(this, usuario)
                    }
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        listenerRegistration?.remove() // Detener la escucha al destruir el servicio
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

        val builder = NotificationCompat.Builder(context, "Test")
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle("Nuevo usuario disponible")
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
