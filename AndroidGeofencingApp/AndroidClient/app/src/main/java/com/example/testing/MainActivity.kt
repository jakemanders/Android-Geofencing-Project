package com.example.testing

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.example.testing.ui.theme.TestingTheme
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.maps.android.compose.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.UUID

// --- NETWORKING --- 
data class LocationUpdate(val deviceId: String, val latitude: Double, val longitude: Double)

interface LocationApiService {
    @POST("updateLocation")
    fun updateLocation(@Body locationUpdate: LocationUpdate): Call<Void>
}

object RetrofitInstance {
    private const val BASE_URL = "http://10.0.2.2:8080/" //localhost

    val api: LocationApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit.create(LocationApiService::class.java)
    }
}
// --- END NETWORKING ---

data class TracePoint(val latitude: Double, val longitude: Double)

@SuppressLint("MissingPermission")
class MainActivity : ComponentActivity() {

    private lateinit var geofencingClient: GeofencingClient
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationSimulator: LocationSimulator

    private val currentLocation = mutableStateOf<Location?>(null)

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            Log.d("MainActivity", "Location update received by callback.")
        }
    }

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(this, MyBroadcastReceiver::class.java)
        PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
    }

    private val permissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false)) {
            checkAndRequestBackgroundLocation()
        } else {
            Log.e("Geofence", "Foreground location permission denied.")
        }
    }

    private val backgroundLocationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            addSingleGeofence()
        } else {
            Log.e("Geofence", "Background location permission denied.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        geofencingClient = LocationServices.getGeofencingClient(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationSimulator = LocationSimulator(this, fusedLocationClient) { location ->
            currentLocation.value = location
        }

        createNotificationChannel()
        requestPermissions()

        setContent {
            TestingTheme {
                MapView(
                    geofenceCenter = LatLng(40.997, -75.173),
                    geofenceRadius = 100.0,
                    currentLocation = currentLocation.value
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationSimulator.stop()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun requestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionRequest.launch(permissionsToRequest.toTypedArray())
        } else {
            checkAndRequestBackgroundLocation()
        }
    }

    private fun checkAndRequestBackgroundLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                backgroundLocationPermissionRequest.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else {
                addSingleGeofence()
            }
        } else {
            addSingleGeofence()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Geofence Notifications"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("geofence_channel", name, importance)
            val notificationManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun addSingleGeofence() {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }

        if (!hasPermission) {
            Log.e("Geofence", "Permissions not granted, cannot add geofence.")
            return
        }

        val geofence = Geofence.Builder()
            .setRequestId("ESU")
            .setCircularRegion(40.997, -75.173, 100f)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()

        val geofencingRequest = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent).run {
            addOnSuccessListener {
                Log.d("Geofence", "Geofence added successfully. Starting simulation and updates.")
                locationSimulator.start()
                startLocationUpdatesForTesting()
            }
            addOnFailureListener { e -> Log.e("Geofence", "Failed to add geofence", e) }
        }
    }

    private fun startLocationUpdatesForTesting() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 15000).build()
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        Log.d("MainActivity", "Starting location updates to process mock locations.")
    }
}

private class LocationSimulator(
    private val context: Context, 
    private val fusedLocationClient: FusedLocationProviderClient,
    private val onLocationUpdate: (Location) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null
    private var step = 0
    private var route: List<Location> = emptyList()
    private val deviceId: String

    init {
        deviceId = getOrCreateDeviceId(context)
        route = readTraceFile().map { tracePoint ->
            Location("mock").apply {
                latitude = tracePoint.latitude
                longitude = tracePoint.longitude
                speed = 15.0f
                accuracy = 1.0f
            }
        }
    }

    private fun getOrCreateDeviceId(context: Context): String {
        val sharedPrefs = context.getSharedPreferences("device_id_prefs", Context.MODE_PRIVATE)
        var id = sharedPrefs.getString("device_id", null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            sharedPrefs.edit().putString("device_id", id).apply()
            Log.d("LocationSimulator", "Generated new device ID: $id")
        }
        return id
    }

    private fun readTraceFile(): List<TracePoint> {
        return try {
            val jsonString = context.assets.open("trace.json").bufferedReader().use { it.readText() }
            val listType = object : TypeToken<List<TracePoint>>() {}.type
            Gson().fromJson(jsonString, listType)
        } catch (e: Exception) {
            Log.e("LocationSimulator", "Error reading trace from JSON", e)
            emptyList()
        }
    }

    private fun sendLocationToServer(location: Location) {
        val locationUpdate = LocationUpdate(deviceId, location.latitude, location.longitude)
        RetrofitInstance.api.updateLocation(locationUpdate).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    Log.d("Network", "Location update for $deviceId sent successfully to server.")
                } else {
                    Log.e("Network", "Failed to send location update for $deviceId to server: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                Log.e("Network", "Error sending location update for $deviceId to server", t)
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (route.isEmpty()) {
            Log.e("LocationSimulator", "Route is empty. Cannot start simulation.")
            return
        }

        Log.d("LocationSimulator", "Starting location simulation for device $deviceId with ${route.size} points.")
        fusedLocationClient.setMockMode(true).addOnSuccessListener {
            Log.d("LocationSimulator", "Mock mode enabled.")
            runnable = object : Runnable {
                override fun run() {
                    if (step >= route.size) {
                        step = 0 // Loop the route
                    }
                    val location = route[step]
                    location.time = System.currentTimeMillis()
                    location.elapsedRealtimeNanos = System.nanoTime()
                    
                    onLocationUpdate(location)
                    sendLocationToServer(location)

                    Log.d("LocationSimulator", "Setting mock location: lat=${location.latitude}, lon=${location.longitude}")
                    fusedLocationClient.setMockLocation(location)
                    step++
                    handler.postDelayed(this, 15000) // Move every 15 seconds
                }
            }
            handler.post(runnable!!)
        }.addOnFailureListener { e ->
            Log.e("LocationSimulator", "Failed to enable mock mode.", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        Log.d("LocationSimulator", "Stopping location simulation.")
        runnable?.let { handler.removeCallbacks(it) }
        fusedLocationClient.setMockMode(false).addOnSuccessListener {
            Log.d("LocationSimulator", "Mock mode disabled.")
        }.addOnFailureListener { e ->
            Log.e("LocationSimulator", "Failed to disable mock mode.", e)
        }
    }
}

@Composable
fun MapView(geofenceCenter: LatLng, geofenceRadius: Double, currentLocation: Location?) {
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(geofenceCenter, 13f)
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState
    ) {
        // Draw the geofence circle
        Circle(
            center = geofenceCenter,
            radius = geofenceRadius,
            strokeColor = Color.Blue,
            strokeWidth = 5f,
            fillColor = Color.Blue.copy(alpha = 0.2f)
        )

        // Draw the marker for the current location
        currentLocation?.let {
            Marker(
                state = MarkerState(position = LatLng(it.latitude, it.longitude)),
                title = "Current Location",
                snippet = "Lat: ${it.latitude}, Lng: ${it.longitude}"
            )
        }
    }
}
