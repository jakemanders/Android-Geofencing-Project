package com.example.testing

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

class MyBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)

        if (geofencingEvent?.hasError() == true) {
            Log.e("Geofence", "Geofence error: ${geofencingEvent.errorCode}")
            GeofenceStatus.status.value = "Error"
            return
        }

        when (geofencingEvent?.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                Log.d("Geofence", "User has entered the geofence")
                GeofenceStatus.status.value = "Inside"
                sendNotification(context, "You've entered the geofence!")
            }
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                Log.d("Geofence", "User has exited the geofence")
                GeofenceStatus.status.value = "Outside"
                sendNotification(context, "You've left the geofence.")
            }
        }
    }

    private fun sendNotification(context: Context, message: String) {
        val builder = NotificationCompat.Builder(context, "geofence_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Using a standard system icon to guarantee it builds
            .setContentTitle("Geofence Alert")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        with(NotificationManagerCompat.from(context)) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // The permission is requested in MainActivity, but this is a safeguard.
                return
            }
            // Use a unique ID for each notification to ensure they all appear
            notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }
}
