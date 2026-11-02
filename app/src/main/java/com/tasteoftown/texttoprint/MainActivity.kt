package com.tasteoftown.texttoprint

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipboardManager
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
    private lateinit var body: EditText
    private lateinit var status: TextView
    private var devices: List<BluetoothDevice> = emptyList()
    private var settingsPrinterSpinner: Spinner? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        loadPairedDevices(null)
    }

    private fun buildScreen() {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(24))
        }
        scroll.addView(root)
        setContentView(scroll)

        root.addView(TextView(this).apply {
            text = "TEXT TO THERMAL PRINT"
            textSize = 22f
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(8))
            contentDescription = text
            if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
        })
        root.addView(TextView(this).apply {
            text = "Designed and developed by Shrikant Patel"
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(22))
            contentDescription = text
        })

        addLabel(root, "Enter text to print")
        body = EditText(this).apply {
            hint = "Type or paste your text here..."
            minLines = 12
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            textSize = 18f
            setPadding(dp(12), dp(12), dp(12), dp(12))
            contentDescription = "Text to print. Enter Hindi or English text."
        }
        root.addView(body, matchWidth())

        // Quick text actions: clear the editor or paste from the system clipboard.
        val textActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        textActions.addView(Button(this).apply {
            text = "CLEAR ALL"
            textSize = 15f
            minHeight = dp(48)
            contentDescription = "Clear all text from the text box"
            setOnClickListener {
                body.text.clear()
                body.setSelection(0)
                body.requestFocus()
                status.text = "All text cleared."
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(6)
            topMargin = dp(10)
        })
        textActions.addView(Button(this).apply {
            text = "PASTE"
            textSize = 15f
            minHeight = dp(48)
            contentDescription = "Paste text from clipboard into the text box"
            setOnClickListener {
                try {
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = clipboard.primaryClip
                    val pastedText = clip?.takeIf { it.itemCount > 0 }
                        ?.getItemAt(0)?.coerceToText(this@MainActivity)?.toString().orEmpty()
                    if (pastedText.isEmpty()) {
                        status.text = "Clipboard is empty or does not contain text."
                    } else {
                        val editable = body.text
                        val selectionStart = body.selectionStart
                        val selectionEnd = body.selectionEnd
                        val start = if (selectionStart < 0) editable.length else minOf(selectionStart, selectionEnd)
                        val end = if (selectionStart < 0) editable.length else maxOf(selectionStart, selectionEnd)
                        editable.replace(start, end, pastedText)
                        body.setSelection((start + pastedText.length).coerceAtMost(body.length()))
                        body.requestFocus()
                        status.text = "Text pasted from clipboard."
                    }
                } catch (e: Exception) {
                    status.text = "Could not paste text from clipboard."
                }
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(6)
            topMargin = dp(10)
        })
        root.addView(textActions, matchWidth())
        addButton(root, "PRINT") { saveAndPrint() }
        addButton(root, "Settings") { showSettings() }

        status = TextView(this).apply {
            text = "Ready. Enter text, then tap Print."
            textSize = 15f
            setPadding(0, dp(18), 0, 0)
            contentDescription = "Printer status"
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        root.addView(status)
        body.textSize = prefs.getInt("font_size", 24).toFloat()
    }

    private fun showSettings() {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        val scroll = ScrollView(this).apply { addView(panel) }

        addLabel(panel, "Header (optional)")
        val header = edit(panel, "Enter receipt header", 2)
        header.setText(prefs.getString("header", ""))
        addLabel(panel, "Footer (optional)")
        val footer = edit(panel, "Enter receipt footer", 2)
        footer.setText(prefs.getString("footer", ""))

        addLabel(panel, "Print font size")
        val sizes = listOf("18", "20", "22", "24", "26", "28", "30", "32", "36")
        val font = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, sizes)
            setSelection(sizes.indexOf(prefs.getInt("font_size", 24).toString()).coerceAtLeast(0))
            contentDescription = "Print font size"
        }
        panel.addView(font, matchWidth())

        addLabel(panel, "Bluetooth printer")
        val printer = Spinner(this).apply {
            contentDescription = "Select paired Bluetooth thermal printer"
        }
        settingsPrinterSpinner = printer
        panel.addView(printer, matchWidth())
        loadPairedDevices(printer)
        addButton(panel, "Refresh paired printers") { loadPairedDevices(printer) }
        addButton(panel, "Open Bluetooth settings") {
            try {
                startActivity(android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
            } catch (_: Exception) {
                status.text = "Could not open Bluetooth settings."
            }
        }

        addLabel(panel, "Paper width")
        val paper = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("58mm / 2-inch", "80mm"))
            setSelection(prefs.getInt("paper_width_index", 0).coerceIn(0, 1))
            contentDescription = "Paper width"
        }
        panel.addView(paper, matchWidth())

        val dialog = AlertDialog.Builder(this)
            .setTitle("Settings")
            .setView(scroll)
            .setPositiveButton("Save settings", null)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val selected = printer.selectedItemPosition
                if (devices.isEmpty() || selected !in devices.indices) {
                    status.text = "Pair and select a Bluetooth printer in Settings."
                    return@setOnClickListener
                }
                val fontSize = sizes[font.selectedItemPosition].toInt()
                prefs.edit()
                    .putString("header", header.text.toString())
                    .putString("footer", footer.text.toString())
                    .putInt("font_size", fontSize)
                    .putString("printer_address", devices[selected].address)
                    .putInt("paper_width_index", paper.selectedItemPosition)
                    .apply()
                body.textSize = fontSize.toFloat()
                status.text = "Settings saved."
                dialog.dismiss()
            }
        }
        dialog.setOnDismissListener { settingsPrinterSpinner = null }
        dialog.show()
    }

    private fun addLabel(parent: LinearLayout, value: String) {
        parent.addView(TextView(this).apply {
            text = value
            textSize = 16f
            setPadding(0, dp(14), 0, dp(6))
        })
    }

    private fun edit(parent: LinearLayout, hintText: String, lines: Int): EditText {
        val field = EditText(this).apply {
            hint = hintText
            minLines = lines
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            textSize = 16f
            setPadding(dp(10), dp(10), dp(10), dp(10))
            contentDescription = hintText
        }
        parent.addView(field, matchWidth())
        return field
    }

    private fun addButton(parent: LinearLayout, label: String, action: () -> Unit) {
        parent.addView(Button(this).apply {
            text = label
            textSize = 16f
            setOnClickListener { action() }
        }, matchWidth().apply { topMargin = dp(10) })
    }

    private fun matchWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun hasBluetoothPermission(): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }

    private fun loadPairedDevices(target: Spinner? = settingsPrinterSpinner) {
        if (!hasBluetoothPermission()) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), requestBluetooth)
            return
        }
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter == null) {
                status.text = "This phone does not support Bluetooth."
                return
            }
            if (!adapter.isEnabled) {
                status.text = "Turn on Bluetooth, then refresh paired printers."
                return
            }
            devices = adapter.bondedDevices.sortedBy { it.name ?: it.address }
            val labels = if (devices.isEmpty()) listOf("No paired printers. Pair one in Bluetooth settings.")
                else devices.map { "${it.name ?: "Bluetooth printer"} (${it.address})" }
            target?.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
            val saved = prefs.getString("printer_address", "")
            val savedIndex = devices.indexOfFirst { it.address == saved }
            if (target != null && savedIndex >= 0) target.setSelection(savedIndex)
            status.text = if (devices.isEmpty()) "No paired Bluetooth printers found."
                else "${devices.size} paired printer(s) found."
        } catch (_: SecurityException) {
            status.text = "Bluetooth permission is required."
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestBluetooth && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            loadPairedDevices(settingsPrinterSpinner)
        } else {
            status.text = "Bluetooth permission denied."
        }
    }

    private fun saveAndPrint() {
        if (!hasBluetoothPermission()) {
            loadPairedDevices(settingsPrinterSpinner)
            return
        }
        if (body.text.isBlank()) {
            status.text = "Please enter text before printing."
            body.requestFocus()
            return
        }
        val address = prefs.getString("printer_address", "")
        val device = devices.firstOrNull { it.address == address }
        if (device == null) {
            status.text = "Open Settings and select a paired Bluetooth printer first."
            return
        }

        val width = if (prefs.getInt("paper_width_index", 0) == 1) 576 else 384
        val head = prefs.getString("header", "") ?: ""
        val main = body.text.toString()
        val foot = prefs.getString("footer", "") ?: ""
        val fontSize = prefs.getInt("font_size", 24)
        status.text = "Connecting and preparing print..."

        Thread {
            var socket: BluetoothSocket? = null
            try {
                val raster = bitmapToEscPos(createReceiptBitmap(width, head, main, foot, fontSize))
                try { BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery() } catch (_: Exception) {}
                socket = device.createRfcommSocketToServiceRecord(sppUuid)
                socket.connect()
                val out: OutputStream = socket.outputStream
                out.write(byteArrayOf(0x1B, 0x40))
                out.write(raster)
                out.write(byteArrayOf(0x0A, 0x0A, 0x0A))
                out.write(byteArrayOf(0x1D, 0x56, 0x00))
                out.flush()
                runOnUiThread { status.text = "Print data sent. Check the printer output." }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Print failed: ${e.message ?: "Check printer power and pairing."}" }
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }
        }.start()
    }

    private fun createReceiptBitmap(width: Int, head: String, main: String, foot: String, fontSize: Int): Bitmap {
        val pad = 12
        val contentWidth = width - pad * 2
        val sections = mutableListOf<Pair<StaticLayout, Int>>()

        fun addSection(text: String, size: Float, alignment: Layout.Alignment, bold: Boolean, gap: Int) {
            if (text.isBlank()) return
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                color = Color.BLACK
                textSize = size
                typeface = android.graphics.Typeface.create(
                    "sans-serif", if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
                )
            }
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, contentWidth)
                .setAlignment(alignment)
                .setIncludePad(true)
                .setLineSpacing(2f, 1.0f)
                .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
                .build()
            sections.add(layout to gap)
        }

        addSection(head, (fontSize + 4).toFloat(), Layout.Alignment.ALIGN_CENTER, true, 14)
        addSection(main, fontSize.toFloat(), Layout.Alignment.ALIGN_NORMAL, false, 14)
        addSection(foot, (fontSize - 2).coerceAtLeast(14).toFloat(), Layout.Alignment.ALIGN_CENTER, true, 10)

        val height = (sections.sumOf { it.first.height + it.second } + 24).coerceAtLeast(80)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        var y = 12f
        sections.forEach { (layout, _) ->
            canvas.save()
            canvas.translate(pad.toFloat(), y)
            layout.draw(canvas)
            canvas.restore()
            y += layout.height + 14
        }
        return bitmap
    }

    private fun bitmapToEscPos(bitmap: Bitmap): ByteArray {
        val widthBytes = ceil(bitmap.width / 8.0).toInt()
        val height = bitmap.height
        val result = java.io.ByteArrayOutputStream()
        var top = 0
        while (top < height) {
            val rows = minOf(256, height - top)
            result.write(byteArrayOf(
                0x1D, 0x76, 0x30, 0x00,
                (widthBytes and 0xFF).toByte(), ((widthBytes shr 8) and 0xFF).toByte(),
                (rows and 0xFF).toByte(), ((rows shr 8) and 0xFF).toByte()
            ))
            for (y in top until top + rows) {
                for (xb in 0 until widthBytes) {
                    var value = 0
                    for (bit in 0..7) {
                        val x = xb * 8 + bit
                        if (x < bitmap.width) {
                            val pixel = bitmap.getPixel(x, y)
                            val luminance = (Color.red(pixel) * 299 + Color.green(pixel) * 587 +
                                Color.blue(pixel) * 114) / 1000
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
