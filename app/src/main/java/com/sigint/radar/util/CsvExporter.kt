package com.sigint.radar.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.sigint.radar.model.DetectedDevice
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*

class CsvExporter(private val context: Context) {

    @SuppressLint("DefaultLocale")
    fun exportToCSV(devices: List<DetectedDevice>): String {
        val stringWriter = StringWriter()
        val csvFormat = CSVFormat.DEFAULT.builder()
            .setHeader(
                "Name", "MAC Address", "Type", "Manufacturer",
                "RSSI (dBm)", "Signal Quality (%)", "Distance (m)",
                "Risk Level", "Risk Score",
                "Channel", "Frequency (MHz)", "Channel Width",
                "Capabilities", "6GHz", "TX Power",
                "Services", "Beacon Type",
                "Timestamp"
            )
            .build()

        val csvPrinter = CSVPrinter(stringWriter, csvFormat)

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date())

        devices.forEach { device ->
            csvPrinter.printRecord(
                device.name,
                device.address,
                device.type.name,
                device.manufacturer,
                device.rssi,
                device.signalQuality,
                String.format("%.2f", device.distanceMeters),
                device.riskLevel.name,
                device.getRiskScore(),
                device.channel ?: "",
                device.frequency,
                device.channelWidth?.name ?: "",
                device.capabilities ?: "",
                device.is6GHz,
                device.txPower ?: "",
                device.serviceUuids?.joinToString(";") ?: "",
                device.beaconType?.name ?: "",
                timestamp
            )
        }

        csvPrinter.flush()
        return stringWriter.toString()
    }

    fun saveCSV(csvContent: String, filename: String): Result<String> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ - Usar MediaStore
                val resolver = context.contentResolver
                val contentValues = android.content.ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }

                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

                uri?.let {
                    resolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.write(csvContent.toByteArray())
                    }
                    Result.success("Downloads/$filename")
                } ?: Result.failure(Exception("Failed to create file"))
            } else {
                // Android 9 y anteriores
                val downloadsDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                )
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }

                val outputFile = java.io.File(downloadsDir, filename)
                outputFile.writeText(csvContent)
                Result.success(outputFile.absolutePath)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun exportAndSave(devices: List<DetectedDevice>): Result<String> {
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
            .format(Date())
        val filename = "SIGINT_Scan_$timestamp.csv"

        val csvContent = exportToCSV(devices)
        return saveCSV(csvContent, filename)
    }
}

