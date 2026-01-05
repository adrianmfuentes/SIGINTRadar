package com.sigint.radar

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ComponentName
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.graphics.drawable.toDrawable
import com.sigint.radar.database.RadarDatabase
import com.sigint.radar.repository.KnownDeviceRepository
import com.sigint.radar.repository.ScanHistoryRepository
import com.sigint.radar.util.AttackDetector
import com.sigint.radar.util.CsvExporter
import com.sigint.radar.util.PatternDetector
import androidx.core.graphics.toColorInt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var scannerService: ScannerService? = null
    private var serviceBound = false
    private var debugMode = false
    private var currentDevices: List<DetectedDevice> = emptyList()
    private var previousDevices: List<DetectedDevice> = emptyList()

    // Database and repositories
    private lateinit var database: RadarDatabase
    private lateinit var scanHistoryRepo: ScanHistoryRepository
    private lateinit var knownDeviceRepo: KnownDeviceRepository
    private lateinit var patternDetector: PatternDetector
    private lateinit var attackDetector: AttackDetector
    private lateinit var csvExporter: CsvExporter

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

        // Inicializar base de datos y repositorios
        database = RadarDatabase.getDatabase(this)
        scanHistoryRepo = ScanHistoryRepository(database)
        knownDeviceRepo = KnownDeviceRepository(database)
        patternDetector = PatternDetector(database)
        attackDetector = AttackDetector()
        csvExporter = CsvExporter(this)

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

        // Botón de menú desplegable
        binding.menuButton.setOnClickListener { view ->
            showOptionsMenu(view)
        }
    }

    private fun showOptionsMenu(view: android.view.View) {
        val popup = android.widget.PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.main_menu, popup.menu)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_filter -> {
                    showFilterDialog()
                    true
                }
                R.id.menu_stats -> {
                    showManufacturerStatsDialog()
                    true
                }
                R.id.menu_heatmap -> {
                    toggleHeatMap()
                    true
                }
                R.id.menu_share -> {
                    shareResults()
                    true
                }
                R.id.menu_debug -> {
                    toggleDebugMode()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun toggleHeatMap() {
        binding.radarView.toggleHeatMap()
        val isEnabled = binding.radarView.isHeatMapEnabled()
        Toast.makeText(
            this,
            if (isEnabled) "Heat Map Enabled" else "Heat Map Disabled",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun toggleDebugMode() {
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
        bindService(intent, serviceConnection, BIND_AUTO_CREATE)
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
                // Guardar dispositivos anteriores para detectar cambios
                previousDevices = currentDevices

                // Guardar dispositivos actuales
                currentDevices = devices

                // Detectar patrones y ataques en segundo plano
                launch {
                    try {
                        // Detectar co-ocurrencias de dispositivos
                        patternDetector.detectCoOccurrences(devices)

                        // Detectar Evil Twins
                        val evilTwins = attackDetector.detectEvilTwins(devices)
                        if (evilTwins.isNotEmpty()) {
                            showEvilTwinAlert(evilTwins)
                        }

                        // Detectar Karma/Jasager attacks
                        val karmaAttacks = attackDetector.detectKarmaJasagerAttacks(devices)
                        if (karmaAttacks.isNotEmpty()) {
                            showKarmaAttackAlert(karmaAttacks)
                        }

                        // Detectar posibles deauth attacks
                        if (previousDevices.isNotEmpty()) {
                            val deauthSuspicion = attackDetector.detectDeauthPatterns(devices, previousDevices)
                            deauthSuspicion?.let {
                                if (it.suspicionLevel > 0.7f) {
                                    showDeauthAlert(it)
                                }
                            }
                        }

                        // Actualizar dispositivos conocidos
                        devices.forEach { device ->
                            knownDeviceRepo.updateLastSeen(device.address)
                        }
                    } catch (_: Exception) {
                        // Ignorar errores en detección de patrones
                    }
                }

                // Aplicar filtros
                val prefs = getSharedPreferences("filters", MODE_PRIVATE)

                val filteredDevices = devices.filter { device ->
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

        // Calcular el porcentaje objetivo basado en si hay dispositivos y cantidad
        val targetPercent = when {
            !hasDevices -> 0.85f // Sin dispositivos: radar casi toda la pantalla
            currentDevices.size <= 3 -> 0.65f // Pocos dispositivos: radar grande
            currentDevices.size <= 10 -> 0.50f // Dispositivos moderados: 50/50
            else -> 0.35f // Muchos dispositivos: más espacio para la lista
        }

        // Animar el cambio con una transición suave
        androidx.transition.TransitionManager.beginDelayedTransition(
            binding.root as android.view.ViewGroup,
            androidx.transition.AutoTransition().apply {
                duration = 300 // 300ms de animación suave
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

            criticalCountText?.text = critical.toString()
            highCountText?.text = high.toString()
            mediumCountText?.text = medium.toString()
            lowCountText?.text = low.toString()
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

    @SuppressLint("SetTextI18n")
    private fun showSaveResultsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_save_results, null)

        val tvMessage = dialogView.findViewById<android.widget.TextView>(R.id.tvMessage)
        val btnSaveBoth = dialogView.findViewById<android.widget.Button>(R.id.btnSaveBoth)
        val btnSaveJson = dialogView.findViewById<android.widget.Button>(R.id.btnSaveJson)
        val btnSaveCsv = dialogView.findViewById<android.widget.Button>(R.id.btnSaveCsv)
        val btnHistoryOnly = dialogView.findViewById<android.widget.Button>(R.id.btnHistoryOnly)
        val btnDiscard = dialogView.findViewById<android.widget.Button>(R.id.btnDiscard)
        val btnContinue = dialogView.findViewById<android.widget.Button>(R.id.btnContinue)

        tvMessage.text = "Found ${currentDevices.size} device(s)\n\nChoose action:"

        val dialog = AlertDialog.Builder(this, R.style.DarkDialogTheme)
            .setTitle("💾 Save Results?")
            .setView(dialogView)
            .setCancelable(true)
            .create()

        btnSaveBoth.setOnClickListener {
            saveResultsJSON()
            saveResultsCSV()
            saveToHistory()
            performStopScanning()
            dialog.dismiss()
        }

        btnSaveJson.setOnClickListener {
            saveResultsJSON()
            saveToHistory()
            performStopScanning()
            dialog.dismiss()
        }

        btnSaveCsv.setOnClickListener {
            saveResultsCSV()
            saveToHistory()
            performStopScanning()
            dialog.dismiss()
        }

        btnHistoryOnly.setOnClickListener {
            saveToHistory()
            performStopScanning()
            dialog.dismiss()
        }

        btnDiscard.setOnClickListener {
            Toast.makeText(this, "Results discarded", Toast.LENGTH_SHORT).show()
            performStopScanning()
            dialog.dismiss()
        }

        btnContinue.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun saveToHistory() {
        lifecycleScope.launch {
            try {
                scanHistoryRepo.saveScan(currentDevices)
                Toast.makeText(
                    this@MainActivity,
                    "Scan saved to history",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    "Error saving to history: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun saveResultsCSV() {
        lifecycleScope.launch {
            val result = csvExporter.exportAndSave(currentDevices)
            result.onSuccess { path ->
                Toast.makeText(
                    this@MainActivity,
                    "CSV saved: $path",
                    Toast.LENGTH_LONG
                ).show()

                // Ofrecer compartir
                showShareOptions(path, "text/csv")
            }.onFailure { e ->
                Toast.makeText(
                    this@MainActivity,
                    "Error saving CSV: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun shareResults() {
        if (currentDevices.isEmpty()) {
            Toast.makeText(this, "No devices to share", Toast.LENGTH_SHORT).show()
            return
        }

        // Crear opciones de compartir
        val options = arrayOf("Share as JSON", "Share as CSV", "Share as Text Summary")

        AlertDialog.Builder(this, R.style.DarkDialogTheme)
            .setTitle("Share Scan Results")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> shareAsJSON()
                    1 -> shareAsCSV()
                    2 -> shareAsTextSummary()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
            .apply {
                show()
                getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.WHITE)
            }
    }

    private fun shareAsJSON() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Generar JSON
                val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.getDefault())
                    .format(java.util.Date())
                val fileName = "SIGINT_Scan_$timestamp.json"

                val jsonContent = generateJSONContent()

                // Guardar temporalmente
                val tempFile = java.io.File(cacheDir, fileName)
                tempFile.writeText(jsonContent)

                withContext(Dispatchers.Main) {
                    shareFile(tempFile, "application/json", "Share Scan Results (JSON)")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error sharing: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun shareAsCSV() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.getDefault())
                    .format(java.util.Date())
                val fileName = "SIGINT_Scan_$timestamp.csv"

                val csvContent = csvExporter.exportToCSV(currentDevices)

                val tempFile = java.io.File(cacheDir, fileName)
                tempFile.writeText(csvContent)

                withContext(Dispatchers.Main) {
                    shareFile(tempFile, "text/csv", "Share Scan Results (CSV)")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error sharing: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun shareAsTextSummary() {
        val summary = buildString {
            append("📡 SIGINT RADAR - Scan Summary\n")
            append("=" .repeat(40))
            append("\n\n")
            append("Date: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}\n")
            append("Total Devices: ${currentDevices.size}\n\n")

            append("Risk Breakdown:\n")
            append("  🔴 Critical: ${currentDevices.count { it.riskLevel == DetectedDevice.RiskLevel.CRITICAL }}\n")
            append("  🟠 High: ${currentDevices.count { it.riskLevel == DetectedDevice.RiskLevel.HIGH }}\n")
            append("  🟡 Medium: ${currentDevices.count { it.riskLevel == DetectedDevice.RiskLevel.MEDIUM }}\n")
            append("  🟢 Low: ${currentDevices.count { it.riskLevel == DetectedDevice.RiskLevel.LOW }}\n\n")

            append("Device Types:\n")
            append("  WiFi: ${currentDevices.count { it.type == DetectedDevice.DeviceType.WIFI }}\n")
            append("  Bluetooth: ${currentDevices.count { it.type == DetectedDevice.DeviceType.BLUETOOTH }}\n")
            append("  Beacon: ${currentDevices.count { it.type == DetectedDevice.DeviceType.BEACON }}\n\n")

            append("Devices:\n")
            append("-" .repeat(40))
            append("\n\n")

            currentDevices.sortedByDescending { it.getRiskScore() }.forEach { device ->
                append("Name: ${device.name}\n")
                append("MAC: ${device.address}\n")
                append("Type: ${device.type.name}\n")
                append("Manufacturer: ${device.manufacturer}\n")
                append("RSSI: ${device.rssi} dBm\n")
                append("Distance: ${"%.2f".format(device.distanceMeters)}m\n")
                append("Risk: ${device.riskLevel.name} (${device.getRiskScore()}/100)\n")
                append("\n")
            }
        }

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, summary)
            type = "text/plain"
        }

        val shareIntent = Intent.createChooser(sendIntent, "Share Scan Summary")
        startActivity(shareIntent)
    }

    private fun shareFile(file: java.io.File, mimeType: String, title: String) {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file
        )

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, uri)
            type = mimeType
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(shareIntent, title))
    }

    private fun showShareOptions(filePath: String, mimeType: String) {
        AlertDialog.Builder(this, R.style.DarkDialogTheme)
            .setTitle("File Saved")
            .setMessage("File saved to: $filePath\n\nDo you want to share it?")
            .setPositiveButton("Share") { _, _ ->
                val file = java.io.File(filePath)
                if (file.exists()) {
                    shareFile(file, mimeType, "Share Scan Results")
                }
            }
            .setNegativeButton("Close", null)
            .create()
            .apply {
                show()
                getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor("#00FF00".toColorInt())
                getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.WHITE)
            }
    }

    @SuppressLint("DefaultLocale")
    private fun generateJSONContent(): String {
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
                        put("distance_meters", String.format("%.2f", device.distanceMeters))

                        put("signal", org.json.JSONObject().apply {
                            put("rssi", device.rssi)
                            put("signal_quality", device.signalQuality)
                        })

                        put("risk", org.json.JSONObject().apply {
                            put("level", device.riskLevel.name)
                            put("score", device.getRiskScore())
                        })
                    })
                }
            })
        }
        return jsonObject.toString(2)
    }

    private fun saveResultsJSON() {
        saveResults() // Usar el método existente
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
            val prefs = getSharedPreferences("filters", MODE_PRIVATE)

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
        val prefs = getSharedPreferences("filters", MODE_PRIVATE)
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

    private fun showEvilTwinAlert(evilTwins: List<com.sigint.radar.util.EvilTwinGroup>) {
        val message = buildString {
            append("⚠️ Possible Evil Twin Attack Detected!\n\n")
            evilTwins.forEach { group ->
                append("SSID: ${group.ssid}\n")
                append("Suspicious APs: ${group.accessPoints.size}\n")
                append("Suspicion Level: ${(group.suspicionLevel * 100).toInt()}%\n")
                append("MACs:\n")
                group.accessPoints.forEach { ap ->
                    append("  • ${ap.address} (${ap.manufacturer})\n")
                }
                append("\n")
            }
        }

        AlertDialog.Builder(this, R.style.DarkDialogTheme)
            .setTitle("⚠️ SECURITY ALERT")
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .create()
            .apply {
                show()
                getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.WHITE)
            }
    }

    private fun showDeauthAlert(suspicion: com.sigint.radar.util.DeauthSuspicion) {
        val message = """
            ⚠️ Possible Deauthentication Attack!
            
            ${suspicion.droppedDeviceCount} WiFi devices disappeared suddenly.
            Drop Rate: ${(suspicion.dropRate * 100).toInt()}%
            Suspicion Level: ${(suspicion.suspicionLevel * 100).toInt()}%
            
            This could indicate:
            • Deauth attack in progress
            • Jamming attack
            • Normal network issues
            
            Monitor the situation carefully.
        """.trimIndent()

        AlertDialog.Builder(this, R.style.DarkDialogTheme)
            .setTitle("⚠️ ATTACK DETECTED")
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .create()
            .apply {
                show()
                getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.WHITE)
            }
    }

    private fun showKarmaAttackAlert(karmaAttacks: List<com.sigint.radar.util.KarmaAttackCandidate>) {
        val message = buildString {
            append("⚠️ Possible Karma/Jasager Attack!\n\n")
            append("Detected ${karmaAttacks.size} suspicious device(s):\n\n")

            karmaAttacks.forEach { attack ->
                append("MAC: ${attack.macAddress}\n")
                append("SSIDs broadcasted: ${attack.ssids.size}\n")
                append("Suspicion: ${(attack.suspicionLevel * 100).toInt()}%\n")
                append("Factors:\n")
                attack.suspicionFactors.forEach { factor ->
                    append("  • $factor\n")
                }
                append("\n")
            }

            append("⚠️ DANGER:\n")
            append("Karma attacks respond to all WiFi probes.\n")
            append("Your device may auto-connect to fake networks!\n")
            append("Disable WiFi auto-connect immediately.")
        }

        AlertDialog.Builder(this, R.style.DarkDialogTheme)
            .setTitle("🔴 CRITICAL THREAT")
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .create()
            .apply {
                show()
                getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.WHITE)
            }
    }

    @SuppressLint("SetTextI18n", "DefaultLocale")
    private fun showManufacturerStatsDialog() {
        if (currentDevices.isEmpty()) {
            Toast.makeText(this, "No devices detected yet. Start scanning first.", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = Dialog(this, R.style.DarkDialogTheme)
        dialog.setContentView(R.layout.dialog_manufacturer_stats)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            (resources.displayMetrics.heightPixels * 0.7).toInt()
        )

        val statsContainer = dialog.findViewById<android.widget.LinearLayout>(R.id.statsContainer)
        val btnClose = dialog.findViewById<android.widget.Button>(R.id.btnClose)

        // Agrupar dispositivos por fabricante
        val manufacturerGroups = currentDevices.groupBy { it.manufacturer }

        // Ordenar por cantidad de dispositivos
        val sortedManufacturers = manufacturerGroups.entries.sortedByDescending { it.value.size }

        sortedManufacturers.forEach { (manufacturer, devices) ->
            val itemView = layoutInflater.inflate(R.layout.item_manufacturer_stat, statsContainer, false)

            val tvManufacturer = itemView.findViewById<android.widget.TextView>(R.id.tvManufacturer)
            val tvCount = itemView.findViewById<android.widget.TextView>(R.id.tvCount)
            val tvAvgRssi = itemView.findViewById<android.widget.TextView>(R.id.tvAvgRssi)
            val tvDistance = itemView.findViewById<android.widget.TextView>(R.id.tvDistance)
            val tvRisk = itemView.findViewById<android.widget.TextView>(R.id.tvRisk)

            tvManufacturer.text = manufacturer
            tvCount.text = "Count: ${devices.size}"

            val avgRssi = devices.map { it.rssi }.average().toInt()
            tvAvgRssi.text = "Avg RSSI: $avgRssi dBm"

            val minDistance = devices.minOfOrNull { it.distanceMeters } ?: 0.0
            val maxDistance = devices.maxOfOrNull { it.distanceMeters } ?: 0.0
            tvDistance.text = String.format("Distance: %.1f-%.1fm", minDistance, maxDistance)

            // Calcular nivel de riesgo predominante
            val riskCounts = devices.groupingBy { it.riskLevel }.eachCount()
            val dominantRisk = riskCounts.maxByOrNull { it.value }?.key ?: com.sigint.radar.model.DetectedDevice.RiskLevel.LOW

            tvRisk.text = "Risk: ${dominantRisk.name}"
            tvRisk.setTextColor(when(dominantRisk) {
                DetectedDevice.RiskLevel.CRITICAL -> "#FF0000".toColorInt()
                DetectedDevice.RiskLevel.HIGH -> "#FF6600".toColorInt()
                DetectedDevice.RiskLevel.MEDIUM -> "#FFAA00".toColorInt()
                DetectedDevice.RiskLevel.LOW -> "#00FF00".toColorInt()
            }
            )

            statsContainer.addView(itemView)
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}

