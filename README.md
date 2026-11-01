# Text to Thermal Print (Android)

Android source project for offline Bluetooth Classic ESC/POS thermal printing.

## Features
- Header, text body, and footer fields. Header and footer are saved locally.
- Hindi and English content is rendered by Android as a monochrome bitmap before sending it to the printer.
- Select an already-paired Bluetooth printer.
- 58mm (384-dot) default and optional 80mm (576-dot) raster width.
- Direct Bluetooth socket printing. No PDF, browser, Android Print Service screen, or PDF viewer.
- TalkBack labels and live printer status messages.

## Build APK
The `.github/workflows/android-apk.yml` workflow builds a debug APK on pushes to `main` or by manual workflow dispatch. The APK is uploaded as the `TextToThermalPrint-debug-apk` artifact.

## Printer setup
1. Turn on the printer and pair it from Android Bluetooth settings.
2. Open the app and grant the Nearby devices / Bluetooth permission.
3. Tap **Refresh paired printers** and select the printer.
4. Choose 58mm for most 2-inch printers. Enter header, body, and footer.
5. Tap **PRINT DIRECTLY**. The app sends print data directly and does not open a PDF or a system print dialog.

## Compatibility limits
- Bluetooth Classic SPP and ESC/POS raster command `GS v 0` are required.
- BLE-only devices and proprietary printer protocols may not work.
- Real-device printing and Hindi glyph quality still require testing with the actual printer model.
