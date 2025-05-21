package com.example.taller03

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

const val channelId = "notification_channel"
const val channelName = "com.example.taller03"

class MyFirebaseMessagingService: FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val title: String?
        val body: String?
        val correo: String?

        if (remoteMessage.data.isNotEmpty()) {
            title = remoteMessage.data["title"]
            body = remoteMessage.data["body"]
            //correo = remoteMessage.data["correo"]
        } else {
            title = remoteMessage.notification?.title
            body = remoteMessage.notification?.body
            //correo = null
        }

        if (!title.isNullOrEmpty() && !body.isNullOrEmpty() /*&& !correo.isNullOrEmpty()*/) {
            generateNotification(title, body/*, correo*/)
        }
    }

    @SuppressLint("RemoteViewLayout")
    fun getRemoteView(title: String, message: String): RemoteViews {
        val remoteView = RemoteViews(channelName, R.layout.notification)
        remoteView.setTextViewText(R.id.title, title)
        remoteView.setTextViewText(R.id.message, message)
        //remoteView.setImageViewResource(R.id.app_logo, R.drawable.ic_notificacion)
        return remoteView
    }

    fun generateNotification(title: String, message: String){
        val isUserLoggedIn = FirebaseAuth.getInstance().currentUser != null

        val intent = if (isUserLoggedIn) {
            Intent(this, MapaUsuariosActivity::class.java).apply {
                //putExtra("correo", userId)
            }
        } else {
            Intent(this, AutenticacionActivity::class.java)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val pendingIntentFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_ONE_SHOT
        }

        val pendingIntent = PendingIntent.getActivity(this, 0, intent, pendingIntentFlag)

        //Vista personalizada
        val remoteView = getRemoteView(title, message)

        var builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(longArrayOf(1000, 1000, 1000, 1000, 1000))
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)

            //Lo que se ve en la barra de notificaciones
            .setContentTitle(title)
            .setContentText(message)

            //La vista personalizada
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(remoteView)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Notificaciones de usuarios disponibles"
            }
            notificationManager.createNotificationChannel(notificationChannel)
        }
        notificationManager.notify(0, builder.build())
    }

}