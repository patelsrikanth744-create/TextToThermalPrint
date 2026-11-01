package com.tasteoftown.texttoprint

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.io.OutputStream
import java.util.UUID
import kotlin.math.ceil

class MainActivity : Activity() {
    private val sppUuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val requestBluetooth = 501
    private val prefs by lazy { getSharedPreferences("print_settings", MODE_PRIVATE) }
    private lateinit var header: EditText
    private lateinit var body: EditText
    private lateinit var footer: EditText
    private lateinit var deviceSpinner: Spinner
    private lateinit var status: TextView
    private lateinit var paperSpinner: Spinner
    private var devices: List<BluetoothDevice> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        loadPairedDevices()
    }

    private fun buildScreen() {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(24))
        }
        scroll.addView(root)
        setContentView(scroll)

        root.addView(TextView(this).apply {
            text = "TEXT TO THERMAL PRINT"
            textSize = 22f; gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(16)); contentDescription = text
            if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
        })
        root.addView(TextView(this).apply {
            text = "Hindi + English | Offline Bluetooth Printing"
            textSize = 14f; gravity = Gravity.CENTER
        })

        addLabel(root, "Bluetooth printer")
        deviceSpinner = Spinner(this).apply {
            contentDescription = "Bluetooth printer selection. Select a paired printer."
        }
        root.addView(deviceSpinner, matchWidth())
        addButton(root, "Refresh paired printers") { loadPairedDevices() }
        addButton(root, "Open Bluetooth settings") {
            try { startActivity(android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)) }
            catch (_: Exception) { status.text = "Could not open Bluetooth settings." }
        }

        addLabel(root, "Paper width")
        paperSpinner = Spinner(this).apply {
            contentDescription = "Paper width selection"
        }
        paperSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            listOf("58mm / 2-inch (384 dots)", "80mm (576 dots)"))
        root.addView(paperSpinner, matchWidth())

        addLabel(root, "Header (optional)")
        header = edit(root, "उदाहरण: TASTE OF TOWN\nदमोह", 3)
        addLabel(root, "Your text / अपना टेक्स्ट")
        body = edit(root, "यहाँ हिंदी या English में लिखें...", 9)
        addLabel(root, "Footer (optional)")
        footer = edit(root, "उदाहरण: धन्यवाद! / THANK YOU!", 3)

        addButton(root, "PRINT DIRECTLY / सीधे प्रिंट करें") { saveAndPrint() }
        addButton(root, "Save header and footer") {
            prefs.edit().putString("header", header.text.toString())
                .putString("footer", footer.text.toString()).apply()
            status.text = "Header and footer saved on this phone."
        }
        status = TextView(this).apply {
            textSize = 15f; setPadding(0, dp(16), 0, 0)
            contentDescription = "Printer status"
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        root.addView(status)
        header.setText(prefs.getString("header", ""))
        footer.setText(prefs.getString("footer", ""))
    }

    private fun addLabel(parent: LinearLayout, value: String) {
        parent.addView(TextView(this).apply {
            text = value; textSize = 16f; setPadding(0, dp(14), 0, dp(6))
        })
    }

    private fun edit(parent: LinearLayout, hintText: String, lines: Int): EditText {
        val field = EditText(this).apply {
            hint = hintText; minLines = lines; gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            textSize = 16f; setPadding(dp(10), dp(10), dp(10), dp(10))
            contentDescription = hintText
        }
        parent.addView(field, matchWidth())
        return field
    }

    private fun addButton(parent: LinearLayout, label: String, action: () -> Unit) {
        val button = Button(this).apply { text = label; textSize = 16f; setOnClickListener { action() } }
        parent.addView(button, matchWidth().apply { topMargin = dp(10) })
    }

    private fun matchWidth() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun hasBluetoothPermission(): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }

    private fun loadPairedDevices() {
        if (!hasBluetoothPermission()) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), requestBluetooth)
            return
        }
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter == null) { status.text = "This phone does not support Bluetooth."; return }
            if (!adapter.isEnabled) { status.text = "Turn on Bluetooth, then refresh paired printers."; return }
            devices = adapter.bondedDevices.sortedBy { it.name ?: it.address }
            val labels = if (devices.isEmpty()) listOf("No paired printers. Pair in Android Bluetooth settings first.")
                else devices.map { "${it.name ?: "Bluetooth printer"} (${it.address})" }
            deviceSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
            status.text = if (devices.isEmpty()) "No paired Bluetooth devices found." else "${devices.size} paired device(s) found."
        } catch (_: SecurityException) { status.text = "Bluetooth permission is required." }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestBluetooth && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) loadPairedDevices()
        else status.text = "Bluetooth permission denied."
    }

    private fun saveAndPrint() {
        if (!hasBluetoothPermission()) { loadPairedDevices(); return }
        if (devices.isEmpty() || deviceSpinner.selectedItemPosition !in devices.indices) {
            status.text = "Pair your printer in Android Settings, then tap Refresh paired printers."; return
        }
        if (header.text.isBlank() && body.text.isBlank() && footer.text.isBlank()) {
            status.text = "Please enter some text before printing."; return
        }
        prefs.edit().putString("header", header.text.toString()).putString("footer", footer.text.toString()).apply()
        val width = if (paperSpinner.selectedItemPosition == 1) 576 else 384
        val selectedDevice = devices[deviceSpinner.selectedItemPosition]
        val headerText = header.text.toString()
        val bodyText = body.text.toString()
        val footerText = footer.text.toString()
        status.text = "Connecting and preparing print..."

        Thread {
            var socket: BluetoothSocket? = null
            try {
                val bitmap = createReceiptBitmap(width, headerText, bodyText, footerText)
                val raster = bitmapToEscPos(bitmap)
                val adapter = BluetoothAdapter.getDefaultAdapter()
                try { adapter?.cancelDiscovery() } catch (_: Exception) {}
                socket = selectedDevice.createRfcommSocketToServiceRecord(sppUuid)
                socket.connect()
                val out: OutputStream = socket.outputStream
                out.write(byteArrayOf(0x1B, 0x40)) // initialize
                out.write(raster)
                out.write(byteArrayOf(0x0A, 0x0A, 0x0A))
                out.write(byteArrayOf(0x1D, 0x56, 0x00)) // cut command; ignored by printers without cutter
                out.flush()
                runOnUiThread { status.text = "Print data sent. Check the printer output." }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Print failed: ${e.message ?: "Check pairing, printer power and compatibility."}" }
            } finally { try { socket?.close() } catch (_: Exception) {} }
        }.start()
    }

    // Android's system font shaping renders Devanagari and Latin together into a bitmap.
    private fun createReceiptBitmap(width: Int, head: String, main: String, foot: String): Bitmap {
        val pad = 12
        val contentWidth = width - pad * 2
        val sections = mutableListOf<Pair<StaticLayout, Int>>()

        fun layout(text: String, size: Float, alignment: Layout.Alignment, bold: Boolean, gap: Int) {
            if (text.isBlank()) return
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                color = Color.BLACK
                textSize = size
                typeface = if (bold) android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
                           else android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
            }
            val sl = StaticLayout.Builder.obtain(text, 0, text.length, paint, contentWidth)
                .setAlignment(alignment)
                .setIncludePad(true)
                .setLineSpacing(2f, 1.0f)
                .setBreakStrategy(android.text.Layout.BREAK_STRATEGY_HIGH_QUALITY)
                .build()
            sections.add(sl to gap)
        }

        layout(head, 27f, Layout.Alignment.ALIGN_CENTER, true, 14)
        layout(main, 24f, Layout.Alignment.ALIGN_NORMAL, false, 14)
        layout(foot, 22f, Layout.Alignment.ALIGN_CENTER, true, 10)
        val totalHeight = (sections.sumOf { it.first.height + it.second } + 24).coerceAtLeast(80)
        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        var y = 12f
        sections.forEach { (sl, gap) ->
            canvas.save()
            canvas.translate(pad.toFloat(), y)
            sl.draw(canvas)
            canvas.restore()
            y += sl.height + gap
        }
        return bitmap
    }

    // ESC/POS GS v 0 raster bit image command. This avoids printer code-page limitations for Hindi.
    private fun bitmapToEscPos(bitmap: Bitmap): ByteArray {
        val widthBytes = ceil(bitmap.width / 8.0).toInt()
        val height = bitmap.height
        val result = java.io.ByteArrayOutputStream()
        var top = 0
        while (top < height) {
            val rows = minOf(256, height - top)
            result.write(byteArrayOf(0x1D, 0x76, 0x30, 0x00,
                (widthBytes and 0xFF).toByte(), ((widthBytes shr 8) and 0xFF).toByte(),
                (rows and 0xFF).toByte(), ((rows shr 8) and 0xFF).toByte()))
            for (y in top until top + rows) {
                for (xb in 0 until widthBytes) {
                    var value = 0
                    for (bit in 0..7) {
                        val x = xb * 8 + bit
                        if (x < bitmap.width) {
                            val pixel = bitmap.getPixel(x, y)
                            val luminance = (Color.red(pixel) * 299 + Color.green(pixel) * 587 + Color.blue(pixel) * 114) / 1000
                            if (luminance < 160) value = value or (0x80 shr bit)
                        }
                    }
                    result.write(value)
                }
            }
            top += rows
        }
        bitmap.recycle()
        return result.toByteArray()
    }
}
