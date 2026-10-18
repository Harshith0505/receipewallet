# USB tablet preview

Connect the Android tablet by USB with USB debugging enabled, and accept its computer authorization prompt. Select the physical tablet in Android Studio's device dropdown and run `app`.

For a tablet that already has an app signed by another teammate, use `-PpreviewInstall=true` (or `previewInstall=true` in your local Gradle user-home properties). This builds **Recipe Wallet Preview**, package `com.recipewallet.app.preview`, alongside the existing app without clearing its data. Normal builds retain the original application ID.

For the local backend, start `npm start` in `backend/` (port 4242 by default). Then run `python3 tools/tablet-connection.py --serial YOUR_TABLET_SERIAL --watch`. Find the serial with `adb devices`. If adb is not on PATH, set `ADB` to its executable path. The helper checks backend health and restores forwarding after USB reconnects without restarting the app or clearing its data. Leave it running during the presentation. Stop with Ctrl-C.

USB reconnects or an adb restart can remove the tunnel, causing both meal planning and pantry photo recognition to fail with connection errors even when the Mac backend is healthy. The Mac must stay awake and connected by USB for this local setup. If the backend deliberately uses another port, pass `--port 4244`; the APK still connects to tablet port 4242. The preview has its own pantry, journal and preferences.

Weekly planning now offers **Automatically prepare my weekly grocery cart**. This saves a cart snapshot when a plan is generated or a dinner is swapped, including combined quantities and the household size. It never places an order or pays. Pantry quantities still require review, and checkout remains available per dinner.

Food images are generated illustrative food photographs, bundled offline as transparent cutouts. They represent recipe ideas rather than exact merchant products. The top quotes have been removed; shared panels use ruled paper and margin lines.
