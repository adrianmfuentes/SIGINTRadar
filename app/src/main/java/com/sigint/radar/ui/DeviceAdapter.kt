package com.sigint.radar.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sigint.radar.R
import com.sigint.radar.model.DetectedDevice

class DeviceAdapter(
    private val onDeviceClick: (DetectedDevice) -> Unit = {}
) : ListAdapter<DetectedDevice, DeviceAdapter.DeviceViewHolder>(DeviceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(view, onDeviceClick)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DeviceViewHolder(
        itemView: View,
        private val onDeviceClick: (DetectedDevice) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.deviceNameText)
        private val addressText: TextView = itemView.findViewById(R.id.deviceAddressText)
        private val detailsText: TextView = itemView.findViewById(R.id.deviceDetailsText)
        private val riskIndicator: View = itemView.findViewById(R.id.riskIndicator)
        private val riskText: TextView = itemView.findViewById(R.id.riskText)

        fun bind(device: DetectedDevice) {
            itemView.setOnClickListener { onDeviceClick(device) }

            nameText.text = device.name
            addressText.text = device.address

            // Detalles según tipo
            val details = when (device.type) {
                DetectedDevice.DeviceType.WIFI -> buildWifiDetails(device)
                DetectedDevice.DeviceType.BLUETOOTH -> buildBluetoothDetails(device)
                DetectedDevice.DeviceType.BEACON -> buildBeaconDetails(device)
            }
            detailsText.text = details

            // Indicador de riesgo
            riskIndicator.setBackgroundColor(device.riskLevel.color)
            riskText.text = device.riskLevel.displayName
        }

        private fun buildWifiDetails(device: DetectedDevice): String {
            return buildString {
                append("${device.manufacturer}\n")
                append("RSSI: ${device.rssi} dBm | ")
                append("~${String.format("%.1f", device.distanceMeters)}m\n")
                append("Ch: ${device.channel} | ")
                append("${device.frequency} MHz")
                device.channelWidth?.let {
                    append(" | ${it.name}")
                }
            }
        }

        @SuppressLint("DefaultLocale")
        private fun buildBluetoothDetails(device: DetectedDevice): String {
            return buildString {
                append("${device.manufacturer}\n")
                append("RSSI: ${device.rssi} dBm | ")
                append("~${String.format("%.1f", device.distanceMeters)}m\n")
                device.txPower?.let {
                    append("Tx: $it dBm | ")
                }
                append("Signal: ${device.signalQuality}%")
            }
        }

        @SuppressLint("DefaultLocale")
        private fun buildBeaconDetails(device: DetectedDevice): String {
            return buildString {
                append("${device.beaconType?.name ?: "BEACON"}\n")
                append("RSSI: ${device.rssi} dBm | ")
                append("~${String.format("%.1f", device.distanceMeters)}m\n")
                append("Tracking Device")
            }
        }
    }

    private class DeviceDiffCallback : DiffUtil.ItemCallback<DetectedDevice>() {
        override fun areItemsTheSame(oldItem: DetectedDevice, newItem: DetectedDevice): Boolean {
            return oldItem.address == newItem.address
        }

        override fun areContentsTheSame(oldItem: DetectedDevice, newItem: DetectedDevice): Boolean {
            // Solo comparar campos visibles, ignorar timestamp para evitar pulsaciones
            return oldItem.address == newItem.address &&
                    oldItem.name == newItem.name &&
                    oldItem.type == newItem.type &&
                    oldItem.rssi == newItem.rssi &&
                    oldItem.manufacturer == newItem.manufacturer &&
                    oldItem.riskLevel == newItem.riskLevel &&
                    oldItem.distanceMeters == newItem.distanceMeters
        }
    }
}