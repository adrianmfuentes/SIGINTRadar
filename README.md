# 📡 SIGINT Radar - Android Native App

Aplicación Android nativa para detección y análisis de dispositivos WiFi/Bluetooth cercanos con capacidades SIGINT.

## 🎯 Características Implementadas

### ✅ Core Features
- **Escaneo WiFi en tiempo real** (1-3 segundos de actualización)
- **Escaneo Bluetooth LE** con detección de beacons
- **Radar visual táctico** con animación 60fps
- **Análisis de riesgo automático** (4 niveles: CRITICAL, HIGH, MEDIUM, LOW)
- **Estimación de distancia** mediante RSSI y modelos de propagación
- **Identificación de fabricantes** (OUI database con 100+ entradas)
- **Foreground Service** para escaneo continuo en background
- **UI/UX nativa** con Material Design

### 🔍 Análisis Avanzado
- Detección de cámaras de vigilancia (Hikvision, Dahua, Axis, etc.)
- Identificación de dispositivos IoT vulnerables (ESP32/ESP8266)
- Análisis de seguridad WiFi (WEP/WPA/WPA2/WPA3)
- Detección de beacons de tracking (iBeacon, Eddystone)
- Channel width detection (20/40/80/160 MHz)
- Soporte para WiFi 6GHz

---

## 📁 Estructura del Proyecto

```
app/src/main/
├── java/com/sigint/radar/
│   ├── MainActivity.kt              # Actividad principal
│   ├── model/
│   │   └── DetectedDevice.kt        # Modelo de datos
│   ├── scanner/
│   │   ├── WifiScanner.kt           # Escáner WiFi
│   │   └── BluetoothScanner.kt      # Escáner BLE
│   ├── service/
│   │   └── ScannerService.kt        # Servicio en foreground
│   ├── ui/
│   │   ├── RadarView.kt             # Vista custom del radar
│   │   └── DeviceAdapter.kt         # Adapter de RecyclerView
│   └── util/
│       └── OuiDatabase.kt           # Base de datos de fabricantes
│
├── res/
│   ├── layout/
│   │   ├── activity_main.xml        # Layout principal
│   │   └── item_device.xml          # Layout de item de lista
│   ├── values/
│   │   ├── colors.xml               # Colores del tema
│   │   └── strings.xml              # Strings
│   └── drawable/
│       ├── ic_radar.xml             # Ícono del radar
│       └── risk_badge.xml           # Badge de riesgo
│
└── AndroidManifest.xml              # Manifest con permisos
```

---

## 🚀 Instalación

### Opción 1: Android Studio (Recomendado)

1. **Instalar Android Studio**
   - Descargar desde: https://developer.android.com/studio
   - Versión mínima: Arctic Fox (2020.3.1) o superior

2. **Crear nuevo proyecto**
   ```
   File → New → New Project → Empty Activity
   ```
   - Name: SIGINT Radar
   - Package name: com.sigint.radar
   - Language: Kotlin
   - Minimum SDK: API 26 (Android 8.0)

3. **Copiar archivos**
   - Copia todos los archivos `.kt` a sus respectivas carpetas
   - Copia los archivos XML a `res/`
   - Reemplaza `AndroidManifest.xml`
   - Reemplaza ambos `build.gradle.kts`

4. **Sincronizar Gradle**
   ```
   File → Sync Project with Gradle Files
   ```

5. **Compilar**
   ```
   Build → Make Project (Ctrl+F9)
   ```

6. **Ejecutar**
   - Conecta tu dispositivo Android con USB debugging activado
   - Run → Run 'app' (Shift+F10)

### Opción 2: Compilación desde línea de comandos

```bash
# Clonar o descargar el proyecto
git clone https://github.com/tu-usuario/sigint-radar.git
cd sigint-radar

# Compilar APK debug
./gradlew assembleDebug

# APK generado en:
# app/build/outputs/apk/debug/app-debug.apk

# Instalar en dispositivo conectado
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## 📱 Configuración del Dispositivo

### Permisos Necesarios

La app solicitará estos permisos en tiempo de ejecución:

1. **Ubicación (Fine/Coarse)**
   - Necesario para escaneo WiFi (limitación de Android)
   - Settings → Apps → SIGINT Radar → Permissions → Location → Allow

2. **Bluetooth Scan** (Android 12+)
   - Para escaneo Bluetooth LE
   - Se solicita automáticamente

3. **Notificaciones** (Android 13+)
   - Para el Foreground Service
   - Opcional pero recomendado

### Configuración de Desarrollador

Para mejorar el rendimiento del escaneo:

```
Settings → Developer Options:
- Keep screen awake when charging: ON
- Background process limit: Standard limit
```

---

## 🎮 Uso de la Aplicación

### Flujo Básico

1. **Abrir la app**
   - Conceder permisos cuando se soliciten

2. **Iniciar escaneo**
   - Tap en "Start Scan"
   - El botón cambia a rojo y dice "Stop Scan"

3. **Ver dispositivos**
   - **Radar visual**: Muestra posición y riesgo
   - **Lista inferior**: Detalles técnicos de cada dispositivo

4. **Detener escaneo**
   - Tap en "Stop Scan"
   - Los datos se limpian

### Interpretación del Radar

#### Colores de Riesgo
- 🔴 **CRITICAL**: Cámaras de vigilancia, dispositivos de espionaje
- 🟠 **HIGH**: Redes sin seguridad, dispositivos de tracking
- 🟡 **MEDIUM**: IoT vulnerable, redes sospechosas
- 🟢 **LOW**: Dispositivos seguros, redes WPA3

#### Círculos Concéntricos
- Cada círculo = 10 metros
- 5 círculos = 50 metros máximo

#### Íconos
- 📶 WiFi
- 🔵 Bluetooth
- 📍 Beacon

---

## 🔧 Personalización

### Modificar Intervalo de Escaneo

En `ScannerService.kt` línea 24:
```kotlin
private const val SCAN_INTERVAL_MS = 2000L  // Cambiar a 1000L para 1s
```

### Ajustar Modelo de Distancia

En `WifiScanner.kt` línea 69:
```kotlin
val pathLossExponent = 3.5  // Reducir a 2.5 para espacios abiertos
```

### Añadir Fabricantes a OUI Database

En `OuiDatabase.kt`:
```kotlin
private val database = mapOf(
    "AABBCC" to "Tu Fabricante",
    // ... más entradas
)
```

Encuentra códigos OUI en: https://standards-oui.ieee.org/

### Cambiar Colores del Tema

En `res/values/colors.xml`:
```xml
<color name="green_primary">#00FF41</color>  <!-- Cambiar a tu color -->
```

---

## 📊 Datos Técnicos

### Precisión de Distancia

| Método | Precisión | Requisitos |
|--------|-----------|------------|
| RSSI básico | ±40-50% | Ninguno |
| RSSI mejorado | ±30-40% | Tx Power real |
| WiFi RTT | ±1-2m | Android 9+, AP compatible |

### Consumo de Batería

- **Modo activo**: ~15-20% por hora
- **Optimización**: Usar Foreground Service en lugar de Activity

### Limitaciones de Android

1. **WiFi Scan Throttling** (Android 9+)
   - Máximo 4 scans cada 2 minutos en background
   - **Solución**: Foreground Service (implementado)

2. **Bluetooth Scan Throttling** (Android 10+)
   - Menos restrictivo que WiFi
   - ~1 scan por segundo posible

3. **Permisos de Ubicación**
   - Android requiere ubicación para WiFi scan
   - No se usa GPS, solo el permiso

---

## 🐛 Troubleshooting

### No aparecen dispositivos

**Causa**: Permisos no otorgados

**Solución**:
```
Settings → Apps → SIGINT Radar → Permissions
- Location: Allow
- Bluetooth: Allow (Android 12+)
```

### "Location permission required"

**Causa**: Ubicación desactivada

**Solución**:
```
Settings → Location → ON
```

### Crash al iniciar escaneo

**Causa**: Runtime crash por permisos

**Solución**: Reinstalar app y otorgar permisos

### Escaneo muy lento (>15s)

**Causa**: Throttling de Android

**Solución**:
- Mantener app en primer plano
- Verificar que el Foreground Service esté activo
- Desactivar "Battery Optimization" para la app

### Dispositivos desaparecen rápidamente

**Causa**: Timeout muy corto

**Solución**: En `ScannerService.kt` línea 93:
```kotlin
now - device.timestamp > 30_000  // Cambiar a 60_000 (1 minuto)
```

---

## 🔐 Consideraciones de Seguridad y Legal

### ⚠️ Disclaimer Legal

Esta aplicación es **exclusivamente para uso educativo y auditorías de seguridad autorizadas**:

✅ **Permitido**:
- Escanear tus propios dispositivos
- Auditar tu red doméstica
- Investigación académica
- Detección de dispositivos no autorizados en tu propiedad

❌ **NO permitido**:
- Interceptar comunicaciones de terceros
- Acceder a redes sin autorización
- Vigilancia de personas
- Tracking de dispositivos ajenos

**El usuario es el único responsable del uso de esta herramienta.**

### Privacidad

- ✅ No recopila datos personales
- ✅ No envía información a servidores externos
- ✅ Todos los datos se quedan en el dispositivo
- ✅ No requiere conexión a internet

---

## 🚧 Roadmap / Features Futuras

### En desarrollo
- [ ] WiFi RTT para precisión ±1-2m (Android 9+)
- [ ] Exportar datos a CSV/JSON
- [ ] Historial de escaneos con gráficos
- [ ] Modo de alerta automática (beep cuando aparece CRITICAL)
- [ ] Widget para home screen

### Planeadas
- [ ] Triangulación multi-punto
- [ ] Detección de Evil Twin avanzada
- [ ] Análisis de patrones temporales
- [ ] Integración con Wigle.net API (opcional)
- [ ] Modo offline map (OpenStreetMap)

---

## 📚 Recursos Adicionales

### Documentación Android
- [WifiManager API](https://developer.android.com/reference/android/net/wifi/WifiManager)
- [BluetoothLeScanner API](https://developer.android.com/reference/android/bluetooth/le/BluetoothLeScanner)
- [Foreground Services](https://developer.android.com/guide/components/foreground-services)

### RF y Propagación
- [Path Loss Models](https://en.wikipedia.org/wiki/Log-distance_path_loss_model)
- [WiFi RTT](https://source.android.com/devices/tech/connect/wifi-rtt)
- [iBeacon Specification](https://developer.apple.com/ibeacon/)

### Seguridad WiFi
- [WPA3 Overview](https://www.wi-fi.org/discover-wi-fi/security)
- [IEEE 802.11 Standards](https://www.ieee802.org/11/)

---

## 🤝 Contribuciones

Pull requests bienvenidas para:
- Nuevas entradas en OUI database
- Mejoras en algoritmos de distancia
- Optimizaciones de performance
- Traducciones
- Corrección de bugs

---

## 📄 Licencia

MIT License - Ver archivo `LICENSE`

---


**⚠️ RECUERDA**: Respeta la privacidad ajena y las leyes locales sobre interceptación de comunicaciones.