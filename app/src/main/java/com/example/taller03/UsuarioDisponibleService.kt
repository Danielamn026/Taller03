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
import android.content.pm.PackageManager

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import models.Usuario

class UsuarioDisponibleService : JobIntentService(){

    companion object {
        private const val JOB_ID = 1000

        fun enqueueWork(context: Context, intent: Intent) {
            enqueueWork(context, UsuarioDisponibleService::class.java, JOB_ID, intent)
        }
    }

    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Canal Usuarios"
            val descriptionText = "Notificaciones de usuarios disponibles"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("Test", name, importance).apply {
                description = descriptionText
            }

            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

    }

    override fun onHandleWork(intent: Intent) {
        val db = FirebaseFirestore.getInstance()

        db.collection("usuarios")
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener

                for (dc in snapshots.documentChanges) {
                    if (dc.type == DocumentChange.Type.MODIFIED) {
                        val usuario = dc.document.toObject(Usuario::class.java)
                        if (usuario.disponibilidad == "Disponible") {
                            lanzarNotificacion(applicationContext, usuario)
                        }
                    }
                }
            }

        // Mantener el servicio "vivo" un rato para escuchar cambios
        try {
            Thread.sleep(10000) // Puedes ajustar este tiempo
        } catch (e: InterruptedException) {
            e.printStackTrace()
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
