# 📡 SIGINT Radar

<div align="center">

![SIGINT Radar](https://img.shields.io/badge/Android-8.0%2B-green?style=for-the-badge&logo=android)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9.20-purple?style=for-the-badge&logo=kotlin)
![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)
![Build](https://img.shields.io/badge/Build-Passing-success?style=for-the-badge)

**Advanced WiFi & Bluetooth Security Scanner for Android**

[Features](#-features) • [Installation](#-installation) • [Usage](#-usage) • [Screenshots](#-screenshots) • [Documentation](#-documentation)

</div>

---

## 📋 Overview

**SIGINT Radar** is a powerful open-source Android application designed for security professionals, network administrators, and privacy-conscious users. It provides real-time scanning and analysis of WiFi and Bluetooth Low Energy (BLE) devices in your vicinity, with advanced threat detection capabilities.

### 🎯 Key Highlights

- **Real-time Scanning**: Continuous monitoring of WiFi networks and Bluetooth devices
- **Advanced Threat Detection**: Identifies Evil Twin attacks, Deauth patterns, Karma/Jasager attacks, and more
- **Interactive Radar Visualization**: Military-style radar interface with heat map support
- **Comprehensive Analytics**: Manufacturer statistics, pattern detection, and historical tracking
- **Export & Share**: Multiple export formats (JSON, CSV) with easy sharing options
- **Responsive Design**: Optimized for phones and tablets with adaptive layouts

---

## ✨ Features

### 🔍 Detection Capabilities

#### WiFi Security Analysis
- **Evil Twin Detection**: Identifies rogue access points mimicking legitimate networks
- **Deauth Attack Detection**: Recognizes suspicious disconnection patterns
- **Karma/Jasager Detection**: Spots fake APs responding to probe requests
- **Rogue AP Identification**: Detects unauthorized access points
- **Channel Analysis**: Monitors 2.4GHz, 5GHz, and 6GHz WiFi bands
- **Encryption Assessment**: Evaluates security protocols (WPA2, WPA3, Open)

#### Bluetooth Threat Analysis
- **BLE Device Scanning**: Comprehensive Bluetooth Low Energy monitoring
- **Beacon Detection**: Identifies iBeacon, Eddystone, and tracking beacons
- **MAC Randomization Detection**: Spots devices using privacy features
- **Service UUID Analysis**: Examines advertised Bluetooth services

### 📊 Analytics & Visualization

#### Interactive Radar View
- **Military-Style Display**: 360° radar with distance rings (0-50m)
- **Heat Map Overlay**: Visualizes device density with color gradients
- **Real-time Animation**: Smooth scanning animation with device tracking
- **Risk-Based Coloring**: Color-coded threats (Critical, High, Medium, Low)
- **Touch Interactions**: Tap devices for detailed information

#### Advanced Statistics
- **Manufacturer Analysis**: Device counts and metrics by vendor (OUI database)
- **Historical Tracking**: Scan history with comparison tools
- **Pattern Detection**: Co-occurrence analysis and behavioral patterns
- **Risk Assessments**: Automated security scoring and threat levels

### 💾 Data Management

#### Export Options
- **JSON Format**: Structured data with full metadata
- **CSV Format**: Spreadsheet-compatible exports
- **Text Summaries**: Quick overview reports
- **Share Integration**: Direct sharing via email, cloud, messaging apps

#### Database Features
- **Local Storage**: Room Database with efficient SQLite backend
- **Scan History**: Automatic saving with timestamps
- **Known Devices**: Whitelist/blacklist management with trust levels
- **Pattern Storage**: Device co-occurrence and relationship tracking

### 🎨 User Interface

#### Responsive Design
- **Adaptive Layouts**: Optimized for phones, phablets, and tablets
- **Dark Theme**: Eye-friendly interface for all lighting conditions
- **Material Components**: Modern Material Design 3 elements
- **Accessibility**: Large touch targets and readable fonts

#### Smart Controls
- **Compact Menu**: Organized options in a popup menu
- **Quick Actions**: One-tap access to common functions
- **Filter System**: Multi-criteria device filtering
- **Debug Mode**: Enhanced scanning with simulated devices

---

## 🚀 Installation

### Prerequisites

- **Android Device**: Android 8.0 (API 26) or higher
- **Permissions**: Location, Bluetooth, WiFi access
- **Storage**: ~10 MB for app + data

### Option 1: Install APK

1. Download the latest APK from [Releases](releases/)
   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```

2. Enable installation from unknown sources:
   - Settings → Security → Unknown Sources ✓

3. Install the APK and grant required permissions

### Option 2: Build from Source

#### Requirements
- **JDK 17**: Oracle JDK or OpenJDK 17
- **Android SDK**: Platform 34, Build Tools 34.0.0
- **Gradle**: 8.13 (included via wrapper)

#### Build Steps

```bash
# Clone the repository
git clone https://github.com/yourusername/sigint-radar.git
cd sigint-radar

# Build debug APK
./gradlew assembleDebug

# Or use the convenience script (Windows)
compile.bat

# Install on connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Windows Quick Build:**
```cmd
compile.bat
```

**Install on Device:**
```cmd
install.bat
```

---

## 📖 Usage

### First Launch

1. **Grant Permissions**: Allow Location, Bluetooth, and WiFi access
2. **Start Scanning**: Tap the green "Start Scan" button
3. **View Results**: Devices appear on the radar and in the list below

### Basic Operations

#### Scanning
- **Start**: Tap "Start Scan" button (turns red when active)
- **Stop**: Tap "Stop Scan" → Choose save option
- **Auto-Save**: Results automatically saved to history

#### Filtering
1. Tap **☰ Menu** → **⚙ Filters**
2. Select device types and risk levels
3. Tap **Apply** to filter results

#### Viewing Details
- **Tap any device** on radar or list
- See detailed information:
  - Signal strength (RSSI)
  - Distance estimation
  - Manufacturer (OUI lookup)
  - Security capabilities
  - Risk factors

#### Statistics
1. Tap **☰ Menu** → **📊 Statistics**
2. View manufacturer breakdown
3. See device counts, RSSI averages, risk distribution

#### Heat Map
1. Tap **☰ Menu** → **🔥 Heat Map**
2. Toggle density visualization
3. Colors: Blue (low) → Red (high density)

### Advanced Features

#### Sharing Results
1. Tap **☰ Menu** → **📤 Share**
2. Choose format:
   - JSON (technical data)
   - CSV (spreadsheet)
   - Text (summary)
3. Select app to share with

#### Debug Mode
1. Tap **☰ Menu** → **⚡ Debug Mode**
2. Enables simulated devices for testing
3. Useful for UI development

#### Known Devices Management
- **Import/Export**: Manage trusted device lists
- **Trust Levels**: TRUSTED, SUSPICIOUS, BLOCKED, UNKNOWN
- **Alerts**: Enable notifications for specific devices

---

## 📸 Screenshots

<div align="center">

| Radar View | Device List | Statistics |
|------------|-------------|------------|
| ![Radar](docs/screenshots/radar.png) | ![List](docs/screenshots/list.png) | ![Stats](docs/screenshots/stats.png) |

| Heat Map | Filters | Device Details |
|----------|---------|----------------|
| ![Heat](docs/screenshots/heatmap.png) | ![Filters](docs/screenshots/filters.png) | ![Details](docs/screenshots/details.png) |

</div>

---

## 🏗️ Architecture

### Technology Stack

- **Language**: Kotlin 1.9.20
- **UI Framework**: Android View System with Material Components
- **Database**: Room 2.6.1 with SQLite
- **Async**: Kotlin Coroutines + Flow
- **DI**: Manual dependency injection
- **Build**: Gradle 8.13 with Kotlin DSL

### Project Structure

```
app/src/main/java/com/sigint/radar/
├── MainActivity.kt              # Main activity with scanning logic
├── database/
│   ├── RadarDatabase.kt        # Room database configuration
│   ├── dao/                    # Data Access Objects
│   │   ├── ScanHistoryDao.kt
│   │   ├── KnownDeviceDao.kt
│   │   └── DevicePatternDao.kt
│   └── entities/               # Database entities
├── model/
│   └── DetectedDevice.kt       # Device data model
├── repository/
│   ├── ScanHistoryRepository.kt
│   ├── KnownDeviceRepository.kt
│   └── PatternRepository.kt
├── scanner/
│   ├── WifiScanner.kt          # WiFi scanning implementation
│   └── BluetoothScanner.kt     # BLE scanning implementation
├── service/
│   └── ScannerService.kt       # Foreground service
├── ui/
│   ├── RadarView.kt            # Custom radar view
│   └── DeviceAdapter.kt        # RecyclerView adapter
└── util/
    ├── AttackDetector.kt       # Threat detection algorithms
    ├── PatternDetector.kt      # Behavioral analysis
    ├── OuiDatabase.kt          # MAC vendor lookup
    └── CsvExporter.kt          # Export utilities
```

### Key Components

#### ScannerService
- Foreground service for continuous scanning
- Manages WiFi and Bluetooth scanners
- Emits device data via Kotlin Flow
- Handles permissions and state management

#### RadarView
- Custom View with Canvas drawing
- 360° polar coordinate system
- Heat map grid (36 sectors × 10 rings)
- Touch event handling for device selection

#### AttackDetector
- Evil Twin detection (same SSID, different MAC/manufacturer)
- Deauth pattern analysis (device disappearance rates)
- Karma/Jasager detection (multi-SSID responses)
- Rogue AP identification

#### Room Database
- **Entities**: ScanHistory, DeviceHistory, KnownDevice, DevicePattern
- **DAOs**: CRUD operations with coroutine support
- **Repositories**: Business logic layer
- **Migrations**: Schema versioning support

---

## 🔒 Security & Privacy

### Data Collection
- **All data stays on device** - No cloud uploads
- **No analytics tracking** - Zero telemetry
- **No ads** - 100% ad-free

### Permissions Required

| Permission | Purpose | Required |
|------------|---------|----------|
| `ACCESS_FINE_LOCATION` | WiFi scanning (Android requirement) | ✅ Yes |
| `ACCESS_COARSE_LOCATION` | General location context | ✅ Yes |
| `BLUETOOTH_SCAN` | BLE device scanning (Android 12+) | ✅ Yes |
| `BLUETOOTH_CONNECT` | BLE device information | ✅ Yes |
| `ACCESS_WIFI_STATE` | WiFi adapter state | ✅ Yes |
| `CHANGE_WIFI_STATE` | Trigger WiFi scans | ✅ Yes |
| `FOREGROUND_SERVICE` | Background scanning | ✅ Yes |
| `POST_NOTIFICATIONS` | Scan notifications | ⚠️ Optional |

### Best Practices
- Use in controlled environments only
- Respect local wireless regulations
- Don't use for unauthorized network testing
- Keep scan data encrypted if exported

---

## 🛠️ Development

### Setup Development Environment

1. **Install Android Studio**: Arctic Fox or newer
2. **Configure JDK 17**:
   ```
   File → Settings → Build Tools → Gradle → Gradle JDK → jbr-17
   ```
3. **Sync Gradle**: File → Sync Project with Gradle Files

### Building

```bash
# Debug build
./gradlew assembleDebug

# Release build (requires signing config)
./gradlew assembleRelease

# Run tests
./gradlew test

# Clean build
./gradlew clean
```

### Code Style
- **Kotlin Coding Conventions**: Official Kotlin style guide
- **Indentation**: 4 spaces
- **Max Line Length**: 120 characters
- **Naming**: camelCase for variables, PascalCase for classes

### Testing
```bash
# Unit tests
./gradlew testDebugUnitTest

# Instrumentation tests (requires device)
./gradlew connectedAndroidTest
```

---

## 📚 Documentation

### API Documentation
- [MainActivity.kt](docs/api/MainActivity.md) - Main activity reference
- [DetectedDevice.kt](docs/api/DetectedDevice.md) - Device model
- [AttackDetector.kt](docs/api/AttackDetector.md) - Threat detection

### User Guides
- [Getting Started](docs/guides/getting-started.md)
- [Advanced Features](docs/guides/advanced-features.md)
- [Troubleshooting](docs/guides/troubleshooting.md)

### Build Scripts
- [compile.bat](SCRIPTS_README.md) - Compilation guide
- [install.bat](SCRIPTS_README.md) - Installation helper
- [clean.bat](SCRIPTS_README.md) - Cache cleaning

---

## 🤝 Contributing

Contributions are welcome! Please follow these guidelines:

1. **Fork the repository**
2. **Create a feature branch**: `git checkout -b feature/amazing-feature`
3. **Commit changes**: `git commit -m 'Add amazing feature'`
4. **Push to branch**: `git push origin feature/amazing-feature`
5. **Open Pull Request**

### Contribution Areas
- 🐛 Bug fixes
- ✨ New features
- 📝 Documentation improvements
- 🌍 Translations
- 🎨 UI/UX enhancements

---

## 🐛 Known Issues

### Android Limitations
- **Background Scanning**: Limited on Android 9+ due to OS restrictions
- **WiFi Scanning Throttling**: Android 9+ limits scan frequency to ~4/2min
- **BLE Permissions**: Android 12+ requires new permission model
- **Location Required**: Android mandates location for WiFi scanning

### Workarounds
- Use foreground service for continuous scanning
- Enable Developer Options → Keep screen on while charging
- Disable battery optimization for SIGINT Radar

---

## 📝 Changelog

### Version 1.0.0 (2026-01-05)
- 🎉 Initial release
- ✅ WiFi & BLE scanning
- ✅ Radar visualization with heat map
- ✅ Advanced threat detection (Evil Twin, Deauth, Karma/Jasager)
- ✅ Manufacturer statistics
- ✅ Export/import functionality
- ✅ Responsive UI for phones and tablets
- ✅ Known devices management
- ✅ Pattern detection and analysis

---

## 📄 License

This project is licensed under the **MIT License** - see the [LICENSE](LICENSE) file for details.

```
MIT License

Copyright (c) 2026 SIGINT Radar Contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
```
---

## ⚠️ Disclaimer

**SIGINT Radar is for educational and authorized security testing purposes only.**

- Use only on networks you own or have permission to test
- Comply with local laws and regulations regarding wireless monitoring
- The developers are not responsible for misuse of this software
- This tool should not be used for illegal surveillance or unauthorized access

**Always obtain proper authorization before security testing.**

---

## 🌟 Star History

[![Star History Chart](https://api.star-history.com/svg?repos=adrianmfuentes/SIGINTRadar&type=Date)](https://star-history.com/adrianmfuentes/SIGINTRadar&Date)

---

<div align="center">

[⬆ Back to Top](#-sigint-radar)

</div>

