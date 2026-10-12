# anom terminal launcher

Build the APK without Android Studio:
1. Create a GitHub repo, push this folder to it.
2. Open the Actions tab, wait for "build-apk" to go green.
3. Download the "termlauncher-apk" artifact, unzip, install app-debug.apk on the phone.
4. Press Home, choose "anom launcher", tap Always.

Local build: install JDK 17 + Android SDK 34, then run `gradle assembleDebug`.
