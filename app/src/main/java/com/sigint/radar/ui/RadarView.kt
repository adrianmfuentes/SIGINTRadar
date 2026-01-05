package com.sigint.radar.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.sigint.radar.model.DetectedDevice
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import kotlin.math.sqrt

class RadarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var devices = listOf<DetectedDevice>()
    private var scanAngle = 0f
    private var centerX = 0f
    private var centerY = 0f
    private var maxRadius = 0f
    private var isScanning = false
    private var onDeviceClickListener: ((DetectedDevice) -> Unit)? = null

    private val backgroundPaint = Paint().apply {
        color = Color.rgb(10, 14, 26)
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint().apply {
        color = Color.argb(50, 0, 255, 65)
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    private val scanLinePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    private val devicePaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.rgb(0, 255, 65)
        textSize = 32f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        maxRadius = minOf(w, h) / 2f - 80f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        // Círculos concéntricos
        drawDistanceCircles(canvas)

        // Líneas radiales
        drawRadialLines(canvas)

        // Línea de barrido animada (solo si está escaneando)
        if (isScanning) {
            drawScanLine(canvas)
        }

        // Dispositivos detectados
        drawDevices(canvas)

        // Labels
        drawDistanceLabels(canvas)
        drawCardinalLabels(canvas)

        // Actualizar animación solo si está escaneando
        if (isScanning) {
            scanAngle = (scanAngle + 2f) % 360f
            invalidate()
        }
    }

    private fun drawDistanceCircles(canvas: Canvas) {
        val rings = 5
        for (i in 1..rings) {
            val radius = (maxRadius / rings) * i
            canvas.drawCircle(centerX, centerY, radius, gridPaint)
        }
    }

    private fun drawRadialLines(canvas: Canvas) {
        val lines = 8
        for (i in 0 until lines) {
            val angle = (i * 360f / lines) * (PI / 180).toFloat()
            val endX = centerX + cos(angle) * maxRadius
            val endY = centerY + sin(angle) * maxRadius
            canvas.drawLine(centerX, centerY, endX, endY, gridPaint)
        }
    }

    private fun drawScanLine(canvas: Canvas) {
        val angleRad = (scanAngle * PI / 180).toFloat()
        val endX = centerX + cos(angleRad) * maxRadius
        val endY = centerY + sin(angleRad) * maxRadius

        // Línea principal
        scanLinePaint.color = Color.argb(180, 0, 255, 65)
        canvas.drawLine(centerX, centerY, endX, endY, scanLinePaint)

        // Efecto fade trail
        for (i in 1..15) {
            val alpha = maxOf(0, 180 - i * 12)
            val trailAngle = scanAngle - i * 2f
            val trailRad = (trailAngle * PI / 180).toFloat()
            val trailX = centerX + cos(trailRad) * maxRadius
            val trailY = centerY + sin(trailRad) * maxRadius

            scanLinePaint.color = Color.argb(alpha, 0, 255, 65)
            scanLinePaint.strokeWidth = 2f
            canvas.drawLine(centerX, centerY, trailX, trailY, scanLinePaint)
        }
    }

    private fun drawDevices(canvas: Canvas) {
        devices.forEach { device ->
            val angle = device.getRadarAngle()
            val angleRad = (angle * PI / 180).toFloat()
            val distance = device.getNormalizedDistance(50f)
            val radius = distance * maxRadius

            val x = centerX + cos(angleRad) * radius
            val y = centerY + sin(angleRad) * radius

            // Color según nivel de riesgo
            devicePaint.color = device.riskLevel.color

            // Punto principal
            devicePaint.style = Paint.Style.FILL
            canvas.drawCircle(x, y, 12f, devicePaint)

            // Anillo exterior
            devicePaint.style = Paint.Style.STROKE
            devicePaint.strokeWidth = 3f
            canvas.drawCircle(x, y, 18f, devicePaint)

            // Efecto pulso para dispositivos críticos/high
            if (device.riskLevel == DetectedDevice.RiskLevel.CRITICAL ||
                device.riskLevel == DetectedDevice.RiskLevel.HIGH) {
                val pulsePhase = (System.currentTimeMillis() % 1000) / 1000f
                val pulseRadius = 18f + pulsePhase * 20f
                val pulseAlpha = (255 * (1f - pulsePhase)).toInt()

                devicePaint.color = Color.argb(
                    pulseAlpha,
                    Color.red(device.riskLevel.color),
                    Color.green(device.riskLevel.color),
                    Color.blue(device.riskLevel.color)
                )
                canvas.drawCircle(x, y, pulseRadius, devicePaint)
            }

            // Ícono del tipo
            drawDeviceIcon(canvas, x, y - 30, device.type)
        }
    }

    private fun drawDeviceIcon(canvas: Canvas, x: Float, y: Float, type: DetectedDevice.DeviceType) {
        textPaint.textSize = 28f
        textPaint.color = Color.rgb(0, 255, 65)

        val icon = when (type) {
            DetectedDevice.DeviceType.WIFI -> "📶"
            DetectedDevice.DeviceType.BLUETOOTH -> "🔵"
            DetectedDevice.DeviceType.BEACON -> "📍"
        }
        canvas.drawText(icon, x, y, textPaint)
    }

    private fun drawDistanceLabels(canvas: Canvas) {
        textPaint.textSize = 22f
        textPaint.color = Color.argb(180, 0, 255, 65)
        textPaint.textAlign = Paint.Align.LEFT

        val rings = 5
        for (i in 1..rings) {
            val radius = (maxRadius / rings) * i
            val distance = (50 / rings) * i

            // Colocar las etiquetas en diagonal superior derecha (45 grados)
            // Para evitar solapamiento con la "E"
            val angle = 45f * (PI / 180).toFloat()
            val labelX = centerX + cos(angle) * radius + 5
            val labelY = centerY - sin(angle) * radius - 5

            canvas.drawText(
                "${distance}m",
                labelX,
                labelY,
                textPaint
            )
        }
    }

    private fun drawCardinalLabels(canvas: Canvas) {
        textPaint.textSize = 32f
        textPaint.color = Color.rgb(0, 255, 65)
        textPaint.textAlign = Paint.Align.CENTER

        // N
        canvas.drawText("N", centerX, centerY - maxRadius - 30, textPaint)

        // E
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("E", centerX + maxRadius + 20, centerY + 10, textPaint)

        // S
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("S", centerX, centerY + maxRadius + 50, textPaint)

        // W
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("W", centerX - maxRadius - 20, centerY + 10, textPaint)
    }

    fun updateDevices(newDevices: List<DetectedDevice>) {
        devices = newDevices
        invalidate()
    }

    fun startScanning() {
        isScanning = true
        invalidate()
    }

    fun stopScanning() {
        isScanning = false
        invalidate()
    }

    fun setOnDeviceClickListener(listener: (DetectedDevice) -> Unit) {
        onDeviceClickListener = listener
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val touchX = event.x
            val touchY = event.y

            // Find device closest to touch point
            devices.forEach { device ->
                val angle = device.getRadarAngle()
                val angleRad = (angle * PI / 180).toFloat()
                val distance = device.getNormalizedDistance(50f)
                val radius = distance * maxRadius

                val deviceX = centerX + cos(angleRad) * radius
                val deviceY = centerY + sin(angleRad) * radius

                // Calculate distance from touch to device point
                val dx = touchX - deviceX
                val dy = touchY - deviceY
                val touchDistance = sqrt(dx * dx + dy * dy)

                // If touch is within 40px of device point, trigger click
                if (touchDistance < 40f) {
                    onDeviceClickListener?.invoke(device)
                    performClick()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}