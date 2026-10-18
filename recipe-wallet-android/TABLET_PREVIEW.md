# USB tablet preview

Connect the Android tablet by USB with USB debugging enabled, and accept its computer authorization prompt. Select the physical tablet in Android Studio's device dropdown and run `app`.

For a tablet that already has an app signed by another teammate, use `-PpreviewInstall=true` (or `previewInstall=true` in your local Gradle user-home properties). This builds **Recipe Wallet Preview**, package `com.recipewallet.app.preview`, alongside the existing app without clearing its data. Normal builds retain the original application ID.

For the local backend, run it on port 4244 and use `adb -d reverse tcp:4242 tcp:4244`. The preview has its own pantry, journal and preferences.

Weekly planning now offers **Automatically prepare my weekly grocery cart**. This saves a cart snapshot when a plan is generated or a dinner is swapped, including combined quantities and the household size. It never places an order or pays. Pantry quantities still require review, and checkout remains available per dinner.

Food images are generated illustrative food photographs, bundled offline as transparent cutouts. They represent recipe ideas rather than exact merchant products. The top quotes have been removed; shared panels use ruled paper and margin lines.
