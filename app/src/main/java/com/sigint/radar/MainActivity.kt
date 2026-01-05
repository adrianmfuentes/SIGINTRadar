package com.sigint.radar

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.Window
import android.widget.CheckBox
import android.widget.Toast

import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.sigint.radar.databinding.ActivityMainBinding
import com.sigint.radar.databinding.DialogDeviceDetailBinding
import com.sigint.radar.model.DetectedDevice
import com.sigint.radar.service.ScannerService
import com.sigint.radar.ui.DeviceAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.core.graphics.drawable.toDrawable

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var scannerService: ScannerService? = null
    private var serviceBound = false
    private var debugMode = false
    private var currentDevices: List<DetectedDevice> = emptyList()

    private val deviceAdapter by lazy {
        DeviceAdapter { device -> showDeviceDetails(device) }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ScannerService.LocalBinder
            scannerService = binder.getService()
            serviceBound = true
            observeDevices()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            scannerService = null
            serviceBound = false
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            startScanning()
        } else {
            Toast.makeText(this, getString(R.string.permission_required), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        checkPermissions()
    }

    private fun setupUI() {
        // RecyclerView para lista de dispositivos
        binding.deviceRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = deviceAdapter
            // Deshabilitar animaciones de cambio para evitar pulsaciones
            itemAnimator = null
        }

        // Radar click listener
        binding.radarView.setOnDeviceClickListener { device ->
            showDeviceDetails(device)
        }

        // Botón de escaneo
        binding.scanButton.setOnClickListener {
            if (binding.scanButton.text == getString(R.string.start_scan)) {
                checkPermissions()
            } else {
                stopScanning()
            }
        }

        // Botón de ayuda
        binding.helpButton.setOnClickListener {
            showHelpDialog()
        }

        // Botón de filtros
        binding.filterButton.setOnClickListener {
            showFilterDialog()
        }

        // Botón de modo debug
        binding.btnDebugMode.setOnClickListener {
            debugMode = !debugMode
            Toast.makeText(
                this,
                if (debugMode) getString(R.string.debug_mode_enabled) else getString(R.string.real_mode_enabled),
                Toast.LENGTH_SHORT
            ).show()

            // Si está escaneando, reiniciar con el nuevo modo
            if (binding.scanButton.text == "Stop Scan") {
                stopScanning()
                checkPermissions()
            }
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isEmpty()) {
            startScanning()
        } else {
            permissionLauncher.launch(notGranted.toTypedArray())
        }
    }

    @SuppressLint("SetTextI18n")
    private fun startScanning() {
        binding.scanButton.text = getString(R.string.stop_scan)
        binding.scanButton.setBackgroundColor(getColor(R.color.red_critical))

        // Iniciar animación del radar
        binding.radarView.startScanning()

        // Iniciar servicio con modo debug
        val intent = Intent(this, ScannerService::class.java)
        intent.putExtra("DEBUG_MODE", debugMode)
        startForegroundService(intent)

        // Bind al servicio
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    @SuppressLint("SetTextI18n")
    private fun stopScanning() {
        // Si hay dispositivos detectados, preguntar si quiere guardar
        if (currentDevices.isNotEmpty()) {
            showSaveResultsDialog()
        } else {
            performStopScanning()
        }
    }

    private fun performStopScanning() {
        binding.scanButton.text = getString(R.string.start_scan)
        binding.scanButton.setBackgroundColor(getColor(R.color.green_safe))

        // Detener animación del radar
        binding.radarView.stopScanning()

        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }

        stopService(Intent(this, ScannerService::class.java))

        // Limpiar datos
        currentDevices = emptyList()
        deviceAdapter.submitList(emptyList())
        updateStats(emptyList())
        binding.radarView.updateDevices(emptyList())

        // Restaurar radar a pantalla completa
        animateRadarSize(hasDevices = false)
    }

    private fun observeDevices() {
        lifecycleScope.launch {
            scannerService?.devicesFlow?.collectLatest { devices ->
                // Guardar dispositivos actuales
                currentDevices = devices

                // Aplicar filtros
                val prefs = getSharedPreferences("filters", Context.MODE_PRIVATE)

                val filteredDevices = devices.filter { device ->
                    // ...existing code...
                    val typePass = when (device.type) {
                        DetectedDevice.DeviceType.WIFI -> prefs.getBoolean("wifi", true)
                        DetectedDevice.DeviceType.BLUETOOTH,
                        DetectedDevice.DeviceType.BEACON -> prefs.getBoolean("bluetooth", true)
                    }

                    val riskPass = when (device.riskLevel) {
                        DetectedDevice.RiskLevel.CRITICAL -> prefs.getBoolean("critical", true)
                        DetectedDevice.RiskLevel.HIGH -> prefs.getBoolean("high", true)
                        DetectedDevice.RiskLevel.MEDIUM -> prefs.getBoolean("medium", true)
                        DetectedDevice.RiskLevel.LOW -> prefs.getBoolean("low", true)
                    }

                    typePass && riskPass
                }

                // ...existing code...
                val sorted = filteredDevices.sortedWith(
                    compareBy<DetectedDevice> { it.riskLevel.ordinal }
                        .thenBy { it.distanceMeters }
                )

                kotlinx.coroutines.delay(100)

                deviceAdapter.submitList(sorted.toList())
                updateStats(filteredDevices)
                binding.radarView.updateDevices(filteredDevices)

                animateRadarSize(sorted.isNotEmpty())
            }
        }
    }

    private fun animateRadarSize(hasDevices: Boolean) {
        val guideline = binding.radarGuideline
        val recyclerView = binding.deviceRecyclerView

        // Calcular el porcentaje objetivo
        val targetPercent = if (hasDevices) 0.40f else 0.90f

        // Animar el cambio con una transición suave
        androidx.transition.TransitionManager.beginDelayedTransition(
            binding.root as android.view.ViewGroup,
            androidx.transition.AutoTransition().apply {
                duration = 400 // 400ms de animación
            }
        )

        // Actualizar la posición de la guideline
        val layoutParams = guideline.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
        layoutParams.guidePercent = targetPercent
        guideline.layoutParams = layoutParams

        // Mostrar/ocultar la lista con fade
        if (hasDevices) {
            recyclerView.visibility = android.view.View.VISIBLE
            recyclerView.alpha = 0f
            recyclerView.animate()
                .alpha(1f)
                .setDuration(300)
                .start()
        } else {
            recyclerView.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    recyclerView.visibility = android.view.View.GONE
                }
                .start()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateStats(devices: List<DetectedDevice>) {
        val total = devices.size
        val wifi = devices.count { it.type == DetectedDevice.DeviceType.WIFI }
        val bluetooth = devices.count { it.type == DetectedDevice.DeviceType.BLUETOOTH }

        val riskCounts = devices.groupingBy { it.riskLevel }.eachCount()
        val critical = riskCounts[DetectedDevice.RiskLevel.CRITICAL] ?: 0
        val high = riskCounts[DetectedDevice.RiskLevel.HIGH] ?: 0
        val medium = riskCounts[DetectedDevice.RiskLevel.MEDIUM] ?: 0
        val low = riskCounts[DetectedDevice.RiskLevel.LOW] ?: 0

        binding.apply {
            totalDevicesText.text = total.toString()
            wifiCountText.text = getString(R.string.wifi_count, wifi)
            bluetoothCountText.text = getString(R.string.ble_count, bluetooth)

            criticalCountText.text = critical.toString()
            highCountText.text = high.toString()
            mediumCountText.text = medium.toString()
            lowCountText.text = low.toString()
        }
    }

    @SuppressLint("SetTextI18n", "DefaultLocale")
    private fun showDeviceDetails(device: DetectedDevice) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        val dialogBinding = DialogDeviceDetailBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        // Hacer el diálogo casi de ancho completo (95% de la pantalla)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.95).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        // Populate dialog with device data
        dialogBinding.apply {
            detailDeviceNameText.text = device.name
            detailDeviceAddressText.text = device.address
            detailRiskBadge.text = device.riskLevel.displayName
            detailRiskBadge.setBackgroundColor(device.riskLevel.color)

            // Risk score
            val riskScore = device.getRiskScore()
            detailRiskScoreText.text = riskScore.toString()
            detailRiskScoreText.setTextColor(device.riskLevel.color)
            detailRiskProgressBar.progress = riskScore
            detailRiskProgressBar.progressTintList =
                android.content.res.ColorStateList.valueOf(device.riskLevel.color)

            // Device info
            detailDeviceTypeText.text = when (device.type) {
                DetectedDevice.DeviceType.WIFI -> getString(R.string.type_wifi)
                DetectedDevice.DeviceType.BLUETOOTH -> getString(R.string.type_bluetooth)
                DetectedDevice.DeviceType.BEACON -> getString(R.string.type_beacon)
            }
            detailManufacturerText.text = device.manufacturer
            detailRssiText.text = "${device.rssi} ${getString(R.string.dbm)}"
            detailDistanceText.text = String.format("%.1f ${getString(R.string.meters)}", device.distanceMeters)

            // Technical details
            val technicalDetails = buildString {
                when (device.type) {
                    DetectedDevice.DeviceType.WIFI -> {
                        device.channel?.let { append("${getString(R.string.channel)}: $it | ") }
                        append("${getString(R.string.frequency)}: ${device.frequency} ${getString(R.string.mhz)}\n")
                        device.channelWidth?.let { append("${getString(R.string.width)}: ${it.name} | ") }
                        append("${getString(R.string.signal_quality)}: ${device.signalQuality}%")
                    }
                    DetectedDevice.DeviceType.BLUETOOTH -> {
                        device.txPower?.let { append("${getString(R.string.tx_power)}: $it ${getString(R.string.dbm)} | ") }
                        append("${getString(R.string.signal_quality)}: ${device.signalQuality}%\n")
                        device.serviceUuids?.let {
                            if (it.isNotEmpty()) {
                                append("${getString(R.string.services)}: ${it.size}")
                            }
                        }
                    }
                    DetectedDevice.DeviceType.BEACON -> {
                        device.beaconType?.let { append("${getString(R.string.type)}: ${it.name}\n") }
                        append("RSSI: ${device.rssi} ${getString(R.string.dbm)}")
                    }
                }
            }
            detailTechnicalText.text = technicalDetails

            // Risk factors (internacionalizados)
            val riskFactorIds = device.getRiskFactorIds()
            if (riskFactorIds.isNotEmpty()) {
                detailRiskFactorsText.text = riskFactorIds.joinToString("\n") { "• ${getString(it)}" }
                detailRiskFactorsLayout.visibility = android.view.View.VISIBLE
            } else {
                detailRiskFactorsLayout.visibility = android.view.View.GONE
            }

            // Close button
            detailCloseButton.setOnClickListener {
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    @SuppressLint("SetTextI18n")
    private fun showHelpDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        val dialogBinding = com.sigint.radar.databinding.DialogHelpBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        // Hacer el diálogo casi de ancho completo (95% de la pantalla)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.95).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        dialogBinding.apply {
            // Habilitar HTML en el TextView
            helpContentText.text = android.text.Html.fromHtml(
                getString(R.string.help_content),
                android.text.Html.FROM_HTML_MODE_LEGACY
            )

            // Close button
            helpCloseButton.setOnClickListener {
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showSaveResultsDialog() {
        val dialog = AlertDialog.Builder(this, R.style.DarkDialogTheme)
            .setTitle(R.string.save_scan_results)
            .setMessage(getString(R.string.save_results_message, currentDevices.size))
            .setPositiveButton(R.string.save) { dialog, _ ->
                saveResults()
                performStopScanning()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.discard) { dialog, _ ->
                Toast.makeText(this, R.string.results_discarded, Toast.LENGTH_SHORT).show()
                performStopScanning()
                dialog.dismiss()
            }
            .setCancelable(false)
            .create()

        dialog.show()

        // Aplicar color blanco a los botones
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.WHITE)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.WHITE)
    }

    @SuppressLint("DefaultLocale")
    private fun saveResults() {
        try {
            val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.getDefault())
                .format(java.util.Date())
            val fileName = "SIGINT_Scan_$timestamp.json"

            // Crear JSON con formato bonito
            val jsonObject = org.json.JSONObject().apply {
                put("app", "SIGINT Radar")
                put("version", "1.0")
                put("scan_date", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()))
                put("total_devices", currentDevices.size)

                // Estadísticas por tipo
                put("statistics", org.json.JSONObject().apply {
                    put("wifi_count", currentDevices.count { it.type == DetectedDevice.DeviceType.WIFI })
                    put("bluetooth_count", currentDevices.count { it.type == DetectedDevice.DeviceType.BLUETOOTH })
                    put("beacon_count", currentDevices.count { it.type == DetectedDevice.DeviceType.BEACON })

                    // Estadísticas por nivel de riesgo
                    put("risk_levels", org.json.JSONObject().apply {
                        put("critical", currentDevices.count { it.riskLevel == DetectedDevice.RiskLevel.CRITICAL })
                        put("high", currentDevices.count { it.riskLevel == DetectedDevice.RiskLevel.HIGH })
                        put("medium", currentDevices.count { it.riskLevel == DetectedDevice.RiskLevel.MEDIUM })
                        put("low", currentDevices.count { it.riskLevel == DetectedDevice.RiskLevel.LOW })
                    })
                })

                // Dispositivos
                put("devices", org.json.JSONArray().apply {
                    currentDevices.forEach { device ->
                        put(org.json.JSONObject().apply {
                            put("name", device.name)
                            put("mac_address", device.address)
                            put("type", device.type.name)
                            put("manufacturer", device.manufacturer)

                            // Señal y distancia
                            put("signal", org.json.JSONObject().apply {
                                put("rssi", device.rssi)
                                put("rssi_unit", "dBm")
                                put("signal_quality", device.signalQuality)
                                put("signal_quality_unit", "%")
                            })

                            put("distance_meters", String.format("%.2f", device.distanceMeters))

                            // Nivel de riesgo
                            put("risk", org.json.JSONObject().apply {
                                put("level", device.riskLevel.name)
                                put("score", device.getRiskScore())

                                // Factores de riesgo internacionalizados
                                val riskFactorIds = device.getRiskFactorIds()
                                if (riskFactorIds.isNotEmpty()) {
                                    put("factors", org.json.JSONArray().apply {
                                        riskFactorIds.forEach { factorId ->
                                            put(this@MainActivity.getString(factorId))
                                        }
                                    })
                                }
                            })

                            // Detalles técnicos específicos del tipo
                            when (device.type) {
                                DetectedDevice.DeviceType.WIFI -> {
                                    put("wifi_details", org.json.JSONObject().apply {
                                        device.channel?.let { put("channel", it) }
                                        put("frequency", device.frequency)
                                        put("frequency_unit", "MHz")
                                        device.channelWidth?.let { put("channel_width", it.name) }
                                        device.capabilities?.let { put("capabilities", it) }
                                        put("is_6ghz", device.is6GHz)
                                    })
                                }
                                DetectedDevice.DeviceType.BLUETOOTH -> {
                                    put("bluetooth_details", org.json.JSONObject().apply {
                                        device.txPower?.let { put("tx_power", it) }
                                        device.serviceUuids?.let { uuids ->
                                            if (uuids.isNotEmpty()) {
                                                put("services", org.json.JSONArray(uuids))
                                            }
                                        }
                                    })
                                }
                                DetectedDevice.DeviceType.BEACON -> {
                                    put("beacon_details", org.json.JSONObject().apply {
                                        put("is_beacon", true)
                                        device.beaconType?.let { put("beacon_type", it.name) }
                                        device.txPower?.let { put("tx_power", it) }
                                    })
                                }
                            }
                        })
                    }
                })
            }

            // Usar MediaStore para Android 10+ (API 29+) o carpeta de archivos de la app
            val file = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                // Android 10+ - Usar MediaStore
                val resolver = contentResolver
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                }

                val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

                uri?.let {
                    resolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.write(jsonObject.toString(2).toByteArray())
                    }

                    Toast.makeText(
                        this,
                        "${getString(R.string.results_saved)}\n${getString(R.string.downloads_folder)}/$fileName",
                        Toast.LENGTH_LONG
                    ).show()
                } ?: run {
                    throw Exception("Failed to create file")
                }
            } else {
                // Android 9 y anteriores - Usar carpeta de Descargas directamente
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                )
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }

                val outputFile = java.io.File(downloadsDir, fileName)
                outputFile.writeText(jsonObject.toString(2))

                Toast.makeText(
                    this,
                    "${getString(R.string.results_saved)}\n${outputFile.absolutePath}",
                    Toast.LENGTH_LONG
                ).show()
            }

        } catch (e: Exception) {
            // Mostrar el error específico para debugging
            android.util.Log.e("SIGINTRadar", "Error saving results", e)
            Toast.makeText(
                this,
                "${getString(R.string.save_error)}\n${e.message}",
                Toast.LENGTH_LONG
            ).show()
            e.printStackTrace()
        }
    }

    private fun applyFilters() {
        // Obtener todos los dispositivos del servicio
        scannerService?.devicesFlow?.value?.let { allDevices ->
            val prefs = getSharedPreferences("filters", Context.MODE_PRIVATE)

            // Aplicar filtros
            val filteredDevices = allDevices.filter { device ->
                // Filtro por tipo de dispositivo
                val typePass = when (device.type) {
                    DetectedDevice.DeviceType.WIFI -> prefs.getBoolean("wifi", true)
                    DetectedDevice.DeviceType.BLUETOOTH,
                    DetectedDevice.DeviceType.BEACON -> prefs.getBoolean("bluetooth", true)
                }

                // Filtro por nivel de riesgo
                val riskPass = when (device.riskLevel) {
                    DetectedDevice.RiskLevel.CRITICAL -> prefs.getBoolean("critical", true)
                    DetectedDevice.RiskLevel.HIGH -> prefs.getBoolean("high", true)
                    DetectedDevice.RiskLevel.MEDIUM -> prefs.getBoolean("medium", true)
                    DetectedDevice.RiskLevel.LOW -> prefs.getBoolean("low", true)
                }

                typePass && riskPass
            }

            // Ordenar por riesgo y distancia
            val sorted = filteredDevices.sortedWith(
                compareBy<DetectedDevice> { it.riskLevel.ordinal }
                    .thenBy { it.distanceMeters }
            )

            // Actualizar UI
            deviceAdapter.submitList(sorted)
            binding.radarView.updateDevices(filteredDevices)
            updateStats(filteredDevices)

            // Animar tamaño del radar
            animateRadarSize(sorted.isNotEmpty())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (serviceBound) {
            unbindService(serviceConnection)
        }
    }

    private fun showFilterDialog() {
        val builder = AlertDialog.Builder(this, R.style.DarkDialogTheme)
        val dialogView = layoutInflater.inflate(R.layout.dialog_filter, null)

        val filterWifi = dialogView.findViewById<CheckBox>(R.id.filterWifi)
        val filterBluetooth = dialogView.findViewById<CheckBox>(R.id.filterBluetooth)
        val filterCritical = dialogView.findViewById<CheckBox>(R.id.filterCritical)
        val filterHigh = dialogView.findViewById<CheckBox>(R.id.filterHigh)
        val filterMedium = dialogView.findViewById<CheckBox>(R.id.filterMedium)
        val filterLow = dialogView.findViewById<CheckBox>(R.id.filterLow)

        // Cargar estado actual de filtros desde SharedPreferences
        val prefs = getSharedPreferences("filters", Context.MODE_PRIVATE)
        filterWifi.isChecked = prefs.getBoolean("wifi", true)
        filterBluetooth.isChecked = prefs.getBoolean("bluetooth", true)
        filterCritical.isChecked = prefs.getBoolean("critical", true)
        filterHigh.isChecked = prefs.getBoolean("high", true)
        filterMedium.isChecked = prefs.getBoolean("medium", true)
        filterLow.isChecked = prefs.getBoolean("low", true)

        val dialog = builder.setView(dialogView)
            .setTitle(R.string.filter_title)
            .setPositiveButton(R.string.apply) { dialog, _ ->
                // Guardar configuración de filtros
                prefs.edit().apply {
                    putBoolean("wifi", filterWifi.isChecked)
                    putBoolean("bluetooth", filterBluetooth.isChecked)
                    putBoolean("critical", filterCritical.isChecked)
                    putBoolean("high", filterHigh.isChecked)
                    putBoolean("medium", filterMedium.isChecked)
                    putBoolean("low", filterLow.isChecked)
                    apply()
                }

                // Aplicar filtros inmediatamente
                applyFilters()

                val activeFilters = mutableListOf<String>()
                if (!filterWifi.isChecked) activeFilters.add("WiFi")
                if (!filterBluetooth.isChecked) activeFilters.add("Bluetooth")
                if (!filterCritical.isChecked) activeFilters.add(getString(R.string.critical))
                if (!filterHigh.isChecked) activeFilters.add(getString(R.string.high))
                if (!filterMedium.isChecked) activeFilters.add(getString(R.string.medium))
                if (!filterLow.isChecked) activeFilters.add(getString(R.string.low))

                val message = if (activeFilters.isEmpty()) {
                    getString(R.string.showing_all_devices)
                } else {
                    getString(R.string.hiding_filters, activeFilters.joinToString(", "))
                }

                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .create()

        dialog.show()

        // Aplicar color blanco a los botones
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.WHITE)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.WHITE)
    }
}

