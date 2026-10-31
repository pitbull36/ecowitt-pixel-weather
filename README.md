# Ecowitt atmosphere for Google Pixel 10

A native Kotlin and Jetpack Compose Android application designed for the Google Pixel 10 and your Ecowitt GW1200 weather station with external WH31 temperature and humidity sensors.

## Features

1. **Interactive 3D isometric room and outdoor dioramas (`RoomDioramaCanvas.kt`)**
   - Supports six room and outdoor presets: **Living room**, **Master bedroom**, **Home office**, **Kitchen and dining**, **Bathroom**, and **Balcony and garden**.
   - **Pixel 10 gyroscope tilt parallax and touch orbit**: gently tilts the 3D cutaway room as you move your phone, or drag with your finger to orbit the camera.
   - **Android AGSL atmospheric shader**: renders thermal convection waves, humidity mist plumes, and day or night sky lighting.
   - **Dynamic particle systems**: water-vapour droplet density scales with relative humidity, thermal waves rise from the floor, and window panes develop condensation droplets when dew point approaches surface temperatures.
   - **Whole-home 3D stack toggle**: view a single room in detail or switch to a stacked multi-floor 3D cutaway of all sensors at once.

2. **Smart psychrometric and comfort insights (`WeatherModels.kt`)**
   - Calculates **feels-like temperature**, **dew point** (Magnus-Tetens formula), **absolute humidity** ($\text{g/m}^3$), **vapour pressure deficit** (kPa), **mould and condensation risk**, and an **ASHRAE 55 comfort score**.
   - **Smart window ventilation advisor**: compares indoor absolute moisture ($\text{g/m}^3$) and temperature against your outdoor or balcony sensor to tell you whether opening a window will dry and cool the room.
   - **GW1200 barometric forecast**: tracks 3-hour pressure trends (`hPa` or `inHg`) to forecast approaching rain fronts or clearing skies.

3. **Comprehensive climate analytics (`WeatherCharts.kt`)**
   - **Multi-room cubic spline chart**: smooth overlaid curves for temperature, humidity, dew point, and pressure over 24 hours or 7 days with finger scrubbing and Pixel haptic ticks.
   - **Psychrometric comfort matrix**: 2D scatter plot of temperature vs humidity showing the ASHRAE 55 comfort polygon, mould risk boundary, and 24-hour sensor trails.
   - **Pixel Weather daily range bars**: 7-day min-to-max gradient bars for temperature and humidity.
   - **7-day × 24-hour calendar heatmap**: visualises hourly thermal or moisture patterns across the week, plus one-tap **CSV export**.

4. **Hybrid GW1200 local LAN and Ecowitt Cloud API v3 engine (`EcowittApiClient.kt`)**
   - Queries `http://<gateway-ip>/get_livedata_info` directly over home WiFi for instant local updates, with a concurrent `/24` subnet scanner to auto-discover your GW1200 IP.
   - Automatically falls back to `https://api.ecowitt.net/api/v3/device/real_time` and `/api/v3/device/history` when away from home.
   - Includes an **interactive simulation mode** with live sliders for time of day, temperature, and humidity so you can test every 3D animation and graph immediately.

5. **Responsive Material You desktop widget (`EcowittGlanceWidget.kt`)**
   - Built with Jetpack Glance supporting **Compact (2×2)**, **Medium (4×2)**, and **Expanded (4×3)** home screen layouts with one-tap sync.

## Building the signed APK

```bash
cd ~/ecowitt-pixel-weather
export JAVA_HOME="$HOME/.local/jdk/jdk-21"
export ANDROID_HOME="$HOME/Android/Sdk"
./gradlew assembleRelease
```

The signed release APK is output to:
`app/build/outputs/apk/release/app-release.apk`
