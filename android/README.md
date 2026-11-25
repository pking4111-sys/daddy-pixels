# Daddy Pixels for Android

A small WebView browser with the Daddy Pixels and Daddy Fallout skins, the startup intro,
Card Drop, an ad/tracker blocker, the scam/phishing shield, tabs, downloads, full-screen video
and an updater that checks GitHub releases for a newer APK.

The APK is built by GitHub (see `.github/workflows/android.yml`):
- publish a release -> `DaddyPixels-<version>.apk` gets attached to it automatically
- or Actions tab -> "Build Android APK" -> Run workflow

Signing: two repository secrets (Settings -> Secrets and variables -> Actions):
`DP_KEYSTORE_B64` and `DP_KEYSTORE_PASSWORD`. Keep the same key forever, or phones
won't accept updates over the old app.
