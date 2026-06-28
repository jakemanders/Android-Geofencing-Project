# Multi-Device Geofencing & Real-Time Tracking System

A comprehensive, end-to-end location system that simulates multiple mobile devices, monitors complex geofence transitions, and visualizes real-time data on a centralized dashboard.

# System Architecture

The project is built using a Client-Server-Dashboard architecture:

1. Android Client (/android-client): A Kotlin-based mobile app that fakes GPS movement, monitors geofences locally, and reports telemetry to a central server.

2. Central Server (/server): A lightweight Kotlin/Ktor backend that aggregates real-time location data from all connected devices.

3. Web Dashboard (/dashboard): A front-end monitoring station that visualizes the entire system on a single Google Map via the JavaScript API.

# Key Features

• Dynamic Simulation: Reads movement paths from trace.json and geofence zones from geofences.json (no hardcoding).

• Customizable Geofencing: Supports multiple simultaneous zones with unique, area-specific messages for users upon entry/exit.

• Multi-Device Support: Implements a robust UUID-based identification system to track and distinguish between multiple emulators or physical devices.

• Real-Time Telemetry: Seamlessly transmits high-frequency location data over a local network (using Retrofit and Ktor).

• Visual Monitoring: A unified web dashboard that polls the server to display all active devices in real-time.

# Getting Started

Prerequisites:

• Java JDK 11 or higher.

• Android Studio (latest stable version recommended).

• Google Maps API Key with "Maps SDK for Android" and "Maps JavaScript API" enabled.


1. Configure the API Key

   1. Android: Paste your key in android-client/app/src/main/AndroidManifest.xml inside the com.google.android.geo.API_KEY meta-data tag.

   2. Dashboard: Paste your key in dashboard/dashboard.html at the bottom in the Google Maps script tag.

   • Note: For local development, ensure your API Key restrictions are set to "None" in the Google Cloud Console.


2. Launch the Server
 
   1. Open a terminal in the /server directory.
 
   2. Run the server: Shell Script: ./gradlew run

   3. The server is active when you see: Responding at http://127.0.0.1:8080.


3. Run the Android App
 
   1. Open the /android-client project in Android Studio.

   2. Launch one or more Emulators.

   3. Critical: In the emulator's Developer Options, select this app as the Mock Location App.

   4. Ensure Location Services are turned ON in the emulator settings.

   5. Deploy the app to the emulator(s).

   • Note: The app can also be downloaded to an android device through Android Studio


4. View the Dashboard

   1. Simply open dashboard/dashboard.html in any modern web browser.

   2. The map will automatically refresh every 5 seconds to show the live positions of all connected emulators.

# Project Structure

├── android-client/      # Android Studio project (Kotlin, Jetpack Compose)

│   ├── app/src/main/assets/

│   │   ├── trace.json       # Define the movement path here

│   │   └── geofences.json    # Define geofence coordinates and messages here

├── server/              # Ktor Backend (Kotlin)

│   └── src/main/kotlin/     # Logic for location aggregation

└── dashboard.html       # Visual monitoring tool

# Troubleshooting

• White Map on Emulator: Ensure the API key is correct and that the "Maps SDK for Android" is enabled in your Google Cloud Project.
 
• Server not receiving data: Check that the emulator can access the host machine via 10.0.2.2. Verify that android:usesCleartextTraffic="true" is present in the Android Manifest.

• Dashboard not showing markers: Ensure the server is running and check the browser's JavaScript console (F12) for CORS errors. The provided Ktor server includes a CORS plugin to allow local file access.
