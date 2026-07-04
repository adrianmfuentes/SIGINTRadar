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
import com.sigint.radar.database.entities.KnownDeviceEntity
import com.sigint.radar.database.entities.ScanHistoryEntity
import com.sigint.radar.repository.KnownDeviceRepository
import com.sigint.radar.repository.ScanHistoryRepository
import com.sigint.radar.repository.TrustLevel
import com.sigint.radar.util.AttackDetector
import com.sigint.radar.util.CsvExporter
import com.sigint.radar.util.PatternDetector
import androidx.core.graphics.toColorInt
import kotlinx.coroutines.flow.first

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var scannerService: ScannerService? = null
    private var serviceBound = false
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

        val backgroundScanEnabled = getSharedPreferences("settings", MODE_PRIVATE)
            .getBoolean("background_scan_enabled", false)
        popup.menu.findItem(R.id.menu_background_scan).isChecked = backgroundScanEnabled

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
                R.id.menu_known_devices -> {
                    showKnownDevicesDialog()
                    true
                }
                R.id.menu_history -> {
                    showScanHistoryDialog()
                    true
                }
                R.id.menu_security_report -> {
                    showSecurityReportDialog()
                    true
                }
                R.id.menu_background_scan -> {
                    toggleBackgroundScan()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun toggleBackgroundScan() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val newValue = !prefs.getBoolean("background_scan_enabled", false)
        prefs.edit().putBoolean("background_scan_enabled", newValue).apply()

        if (newValue) {
            com.sigint.radar.worker.PeriodicScanWorker.schedule(applicationContext)
            Toast.makeText(
                this,
                "Background scanning enabled (~every 15 min, saves to history)",
                Toast.LENGTH_LONG
            ).show()
        } else {
            com.sigint.radar.worker.PeriodicScanWorker.cancel(applicationContext)
            Toast.makeText(this, "Background scanning disabled", Toast.LENGTH_SHORT).show()
        }
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

        // Iniciar servicio de escaneo
        val intent = Intent(this, ScannerService::class.java)
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
        binding.scanStatusText.visibility = android.view.View.GONE

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
            scannerService?.statusFlow?.collectLatest { status ->
                val message = when {
                    !status.bluetoothEnabled -> "🔵 Bluetooth is off - only WiFi devices will show up"
                    status.wifiThrottled -> "⏳ WiFi scan throttled by Android - showing cached results"
                    else -> null
                }
                binding.scanStatusText.text = message ?: ""
                binding.scanStatusText.visibility =
                    if (message != null) android.view.View.VISIBLE else android.view.View.GONE
            }
        }

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

            // Mark as known device
            detailMarkKnownButton.setOnClickListener {
                showMarkAsKnownDialog(device)
            }

            // Close button
            detailCloseButton.setOnClickListener {
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showMarkAsKnownDialog(device: DetectedDevice) {
        val trustLevels = arrayOf("Trusted ✅", "Suspicious ⚠️", "Blocked 🚫", "Unknown ⬜")
        val values = arrayOf(TrustLevel.TRUSTED, TrustLevel.SUSPICIOUS, TrustLevel.BLOCKED, TrustLevel.UNKNOWN)

        AlertDialog.Builder(this, R.style.DarkDialogTheme)
            .setTitle("Mark \"${device.name}\" as:")
            .setItems(trustLevels) { _, which ->
                lifecycleScope.launch {
                    knownDeviceRepo.addOrUpdateDevice(device, values[which])
                    Toast.makeText(
                        this@MainActivity,
                        "${device.name} marked as ${values[which].name}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
            .apply {
                show()
                getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.WHITE)
            }
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

    // ==================== Known Devices ====================

    private fun showKnownDevicesDialog() {
        val dialog = Dialog(this, R.style.DarkDialogTheme)
        dialog.setContentView(R.layout.dialog_known_devices)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val container = dialog.findViewById<android.widget.LinearLayout>(R.id.knownDevicesContainer)
        val emptyState = dialog.findViewById<android.widget.TextView>(R.id.tvEmptyState)
        val btnClose = dialog.findViewById<android.widget.Button>(R.id.btnClose)

        val collectJob = lifecycleScope.launch {
            knownDeviceRepo.getAllKnownDevices().collectLatest { devices ->
                renderKnownDevices(container, emptyState, devices)
            }
        }

        dialog.setOnDismissListener { collectJob.cancel() }
        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    @SuppressLint("SetTextI18n", "DefaultLocale")
    private fun renderKnownDevices(
        container: android.widget.LinearLayout,
        emptyState: android.widget.TextView,
        devices: List<KnownDeviceEntity>
    ) {
        container.removeAllViews()
        emptyState.visibility = if (devices.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE

        val trustCycle = listOf(TrustLevel.UNKNOWN, TrustLevel.TRUSTED, TrustLevel.SUSPICIOUS, TrustLevel.BLOCKED)

        devices.forEach { entity ->
            val itemView = layoutInflater.inflate(R.layout.item_known_device, container, false)

            val tvDeviceName = itemView.findViewById<android.widget.TextView>(R.id.tvDeviceName)
            val tvMacAddress = itemView.findViewById<android.widget.TextView>(R.id.tvMacAddress)
            val tvMeta = itemView.findViewById<android.widget.TextView>(R.id.tvMeta)
            val tvTrustBadge = itemView.findViewById<android.widget.TextView>(R.id.tvTrustBadge)
            val btnCycleTrust = itemView.findViewById<android.widget.Button>(R.id.btnCycleTrust)
            val btnToggleAlert = itemView.findViewById<android.widget.Button>(R.id.btnToggleAlert)
            val btnDelete = itemView.findViewById<android.widget.Button>(R.id.btnDelete)

            tvDeviceName.text = entity.customName ?: entity.manufacturer ?: entity.deviceType
            tvMacAddress.text = entity.macAddress
            tvMeta.text = "Seen ${entity.seenCount}x | Last seen ${
                java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(entity.lastSeen))
            }"

            val trustLevel = TrustLevel.entries.firstOrNull { it.name == entity.trustLevel } ?: TrustLevel.UNKNOWN
            tvTrustBadge.text = trustLevel.name
            tvTrustBadge.setBackgroundColor(
                when (trustLevel) {
                    TrustLevel.TRUSTED -> "#00E676".toColorInt()
                    TrustLevel.SUSPICIOUS -> "#FFB300".toColorInt()
                    TrustLevel.BLOCKED -> "#FF1744".toColorInt()
                    TrustLevel.UNKNOWN -> "#808080".toColorInt()
                }
            )

            btnToggleAlert.text = if (entity.alertEnabled) "🔔 Alerts On" else "🔕 Alerts Off"

            btnCycleTrust.setOnClickListener {
                val nextLevel = trustCycle[(trustCycle.indexOf(trustLevel) + 1) % trustCycle.size]
                lifecycleScope.launch { knownDeviceRepo.updateTrustLevel(entity.macAddress, nextLevel) }
            }

            btnToggleAlert.setOnClickListener {
                lifecycleScope.launch {
                    knownDeviceRepo.setAlertEnabled(entity.macAddress, !entity.alertEnabled)
                }
            }

            btnDelete.setOnClickListener {
                lifecycleScope.launch { knownDeviceRepo.deleteDevice(entity) }
            }

            container.addView(itemView)
        }
    }

    // ==================== Scan History ====================

    private fun showScanHistoryDialog() {
        val dialog = Dialog(this, R.style.DarkDialogTheme)
        dialog.setContentView(R.layout.dialog_scan_history)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val container = dialog.findViewById<android.widget.LinearLayout>(R.id.historyContainer)
        val emptyState = dialog.findViewById<android.widget.TextView>(R.id.tvEmptyState)
        val btnClose = dialog.findViewById<android.widget.Button>(R.id.btnClose)
        val btnCleanup = dialog.findViewById<android.widget.Button>(R.id.btnCleanup)

        val collectJob = lifecycleScope.launch {
            scanHistoryRepo.getRecentScans(30).collectLatest { scans ->
                renderScanHistory(container, emptyState, scans)
            }
        }

        btnCleanup.setOnClickListener {
            lifecycleScope.launch {
                val removed = scanHistoryRepo.deleteOldScans(30)
                Toast.makeText(this@MainActivity, "Removed $removed scan(s) older than 30 days", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.setOnDismissListener { collectJob.cancel() }
        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    @SuppressLint("SetTextI18n")
    private fun renderScanHistory(
        container: android.widget.LinearLayout,
        emptyState: android.widget.TextView,
        scans: List<ScanHistoryEntity>
    ) {
        container.removeAllViews()
        emptyState.visibility = if (scans.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE

        scans.forEach { scan ->
            val itemView = layoutInflater.inflate(R.layout.item_scan_history, container, false)

            val tvTimestamp = itemView.findViewById<android.widget.TextView>(R.id.tvTimestamp)
            val tvSummary = itemView.findViewById<android.widget.TextView>(R.id.tvSummary)
            val tvRiskSummary = itemView.findViewById<android.widget.TextView>(R.id.tvRiskSummary)
            val btnViewDevices = itemView.findViewById<android.widget.Button>(R.id.btnViewDevices)
            val btnDeleteScan = itemView.findViewById<android.widget.Button>(R.id.btnDeleteScan)

            tvTimestamp.text = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date(scan.timestamp))
            tvSummary.text = "${scan.totalDevices} devices | ${scan.wifiCount} WiFi | ${scan.bluetoothCount} BLE"
            tvRiskSummary.text = "Critical: ${scan.criticalCount} | High: ${scan.highCount} | " +
                "Medium: ${scan.mediumCount} | Low: ${scan.lowCount}"

            btnViewDevices.setOnClickListener {
                lifecycleScope.launch {
                    val devices = scanHistoryRepo.getDevicesByScan(scan.id).first()
                    val riskOrder = listOf("CRITICAL", "HIGH", "MEDIUM", "LOW")
                    val message = if (devices.isEmpty()) {
                        "No device details stored for this scan."
                    } else {
                        devices.sortedBy { riskOrder.indexOf(it.riskLevel) }.joinToString("\n\n") { d ->
                            "${d.name}\n${d.macAddress}\n${d.manufacturer} | ${d.riskLevel} (${d.riskScore}/100)"
                        }
                    }
                    AlertDialog.Builder(this@MainActivity, R.style.DarkDialogTheme)
                        .setTitle("Devices in this scan")
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .create()
                        .apply {
                            show()
                            getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.WHITE)
                        }
                }
            }

            btnDeleteScan.setOnClickListener {
                lifecycleScope.launch { scanHistoryRepo.deleteScan(scan) }
            }

            container.addView(itemView)
        }
    }

    // ==================== Security Report ====================

    private fun showSecurityReportDialog() {
        if (currentDevices.isEmpty()) {
            Toast.makeText(this, "No devices detected yet. Start scanning first.", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val trustedMacs = knownDeviceRepo.getAllKnownDevices().first()
                .filter { it.trustLevel == TrustLevel.TRUSTED.name }
                .map { it.macAddress }
                .toSet()

            val rogueAPs = attackDetector.detectRogueAPs(currentDevices, trustedMacs)
            val btThreats = attackDetector.detectBluetoothThreats(currentDevices)
            val monitorDevices = attackDetector.detectMonitorModeDevices(currentDevices)
            val macRandomization = patternDetector.detectMacRandomization(currentDevices)
            val trackingBeacons = patternDetector.detectTrackingBeacons(currentDevices)

            val message = buildString {
                if (rogueAPs.isEmpty() && btThreats.isEmpty() && monitorDevices.isEmpty() &&
                    macRandomization.isEmpty() && trackingBeacons.isEmpty()
                ) {
                    append("✅ No additional threats detected among the ${currentDevices.size} device(s) currently visible.")
                }

                if (rogueAPs.isNotEmpty()) {
                    append("📡 Possible Rogue Access Points (${rogueAPs.size}):\n")
                    rogueAPs.forEach { append("  • ${it.name} (${it.address})\n") }
                    append("\n")
                }

                if (btThreats.isNotEmpty()) {
                    append("🔵 Bluetooth Threats (${btThreats.size}):\n")
                    btThreats.forEach { threat ->
                        append("  • ${threat.device.name}: ${threat.threatFactors.joinToString(", ")}\n")
                    }
                    append("\n")
                }

                if (monitorDevices.isNotEmpty()) {
                    append("📶 Monitor-Mode Capable Adapters (${monitorDevices.size}):\n")
                    monitorDevices.forEach { append("  • ${it.name} (${it.manufacturer})\n") }
                    append("\n")
                }

                if (macRandomization.isNotEmpty()) {
                    append("🔀 MAC Randomization Detected (${macRandomization.size} group(s)):\n")
                    macRandomization.forEach {
                        append("  • ${it.manufacturer} ${it.deviceType}: ${it.macAddresses.size} addresses\n")
                    }
                    append("\n")
                }

                if (trackingBeacons.isNotEmpty()) {
                    append("📍 Tracking Beacons (${trackingBeacons.size}):\n")
                    trackingBeacons.forEach { append("  • ${it.name} (${it.address})\n") }
                }
            }

            AlertDialog.Builder(this@MainActivity, R.style.DarkDialogTheme)
                .setTitle("🛡 Security Report")
                .setMessage(message.trim())
                .setPositiveButton("OK", null)
                .create()
                .apply {
                    show()
                    getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.WHITE)
                }
        }
    }
}

