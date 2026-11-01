# Build the APK using only an Android phone (GitHub Actions)

This repository includes `.github/workflows/android-apk.yml` so GitHub can build the APK in the cloud. You do not need Android Studio or a computer.

## Run / download
1. Open the repository's **Actions** tab.
2. Select **Build Android APK**. If it has not run automatically, use **Run workflow**.
3. Wait for the run to finish successfully.
4. Open the completed run and find **Artifacts**.
5. Download `TextToThermalPrint-debug-apk`, extract it, and install `app-debug.apk`.

## Important
- This produces a debug APK for testing, not a Play Store release.
- Pair your printer in Android Settings → Bluetooth before printing.
- The app uses Bluetooth Classic SPP and ESC/POS raster printing. BLE-only or proprietary printer protocols may not work.
- Real-device printing and Hindi glyph quality must be verified with the actual printer model.
