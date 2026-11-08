# Aarti Sangrah – Android app

Native Android wrapper for the web app in `../gujarati-aarti`. The prayers are bundled inside the APK, so the app works fully offline.

- **Package:** `com.aartisangrah.app` · **Min Android:** 7.0 (API 24) · **Target:** API 34
- **Read-aloud:** uses Android's built-in Text-to-Speech (Google TTS) through a JavaScript bridge, because Android WebView has no browser speech API. Gujarati text is read with a Gujarati voice when installed, otherwise Hindi.
- YouTube links open in the YouTube app or browser; "Choose audio file" opens the phone's file picker.

## Build
```bash
# needs JDK 17+ and the Android SDK packages "platforms;android-34" and "build-tools;35.0.0"
ANDROID_HOME=/path/to/android-sdk ./build.sh
# output: build/AartiSangrah.apk
```
To sign with your own key: `KEYSTORE=my.jks KS_PASS=*** KEY_ALIAS=mykey ./build.sh`.
Keep the same keystore for every release, or Android will refuse to install updates over the old app.

## Install on a phone
1. Copy `AartiSangrah.apk` to the phone (WhatsApp to self, Google Drive, USB).
2. Tap it → allow **Install unknown apps** for that source when asked → **Install**.
3. For Gujarati read-aloud: Settings → System → Languages & input → Text-to-speech → Google → install the **Gujarati** (and Hindi) voice data.
