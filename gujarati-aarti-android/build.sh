#!/usr/bin/env bash
# Builds the Aarti Sangrah Android APK without Gradle, using only the Android SDK
# build tools. The web app in ../gujarati-aarti is bundled as assets/www.
#
# Requires: JDK 17+, Android SDK with "platforms;android-34" and "build-tools;35.0.0".
# Usage:   ANDROID_HOME=/path/to/sdk ./build.sh
# Signing: set KEYSTORE / KS_PASS / KEY_ALIAS to sign with your own key; otherwise a
#          local key is created in build/ (keep it to publish updates over the same app).
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK}"
BT="$SDK/build-tools/35.0.0"
JAR="$SDK/platforms/android-34/android.jar"
OUT="$HERE/build"
VERSION_CODE="${VERSION_CODE:-4}"
VERSION_NAME="${VERSION_NAME:-1.3}"

rm -rf "$OUT/gen" "$OUT/classes" "$OUT/dex" "$OUT/assets" "$OUT"/*.apk "$OUT/res.zip"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex" "$OUT/assets/www/icons" "$OUT/assets/www/audio"

# 0. Download the freely licensed recordings listed in audio/recordings.js
#    (skipped with SKIP_AUDIO_FETCH=1; missing files are streamed online by the app)
if [ "${SKIP_AUDIO_FETCH:-0}" != "1" ]; then
  bash "$HERE/../gujarati-aarti/audio/fetch-audio.sh" || echo "Audio download incomplete: the app will stream missing recordings online."
fi

# 1. Bundle the web app
cp "$HERE/../gujarati-aarti/index.html" "$HERE/../gujarati-aarti/data.js" \
   "$HERE/../gujarati-aarti/translit.js" "$HERE/../gujarati-aarti/pronounce.js" "$OUT/assets/www/"
cp "$HERE/../gujarati-aarti/icons/"* "$OUT/assets/www/icons/"
cp "$HERE/../gujarati-aarti/audio/"*.js "$HERE/../gujarati-aarti/audio/CREDITS.md" "$OUT/assets/www/audio/"
find "$HERE/../gujarati-aarti/audio" -maxdepth 1 -type f \( -name "*.mp3" -o -name "*.ogg" -o -name "*.oga" -o -name "*.m4a" -o -name "*.opus" -o -name "*.wav" \) -exec cp {} "$OUT/assets/www/audio/" \;   # bundled recordings

# 2. Resources + manifest
"$BT/aapt2" compile --dir "$HERE/res" -o "$OUT/res.zip"
"$BT/aapt2" link -o "$OUT/base.apk" -I "$JAR" --manifest "$HERE/AndroidManifest.xml" \
  -A "$OUT/assets" --java "$OUT/gen" "$OUT/res.zip" \
  --min-sdk-version 24 --target-sdk-version 34 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME"

# 3. Compile Java and convert to dex
javac -source 8 -target 8 -nowarn -Xlint:-options -bootclasspath "$JAR" -classpath "$JAR" -d "$OUT/classes" \
  $(find "$HERE/src" "$OUT/gen" -name '*.java')
(cd "$OUT/classes" && jar cf "$OUT/classes.jar" .)
"$BT/d8" --release --min-api 24 --lib "$JAR" --output "$OUT/dex" "$OUT/classes.jar"

# 4. Package, align, sign
cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && zip -q -u "$OUT/unsigned.apk" classes.dex)
"$BT/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

KEYSTORE="${KEYSTORE:-$OUT/aarti-release.jks}"
KS_PASS="${KS_PASS:-aartisangrah}"
KEY_ALIAS="${KEY_ALIAS:-aarti}"
if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -keystore "$KEYSTORE" -storepass "$KS_PASS" -keypass "$KS_PASS" \
    -alias "$KEY_ALIAS" -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Aarti Sangrah, O=Aarti Sangrah, C=IN" >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" --ks-key-alias "$KEY_ALIAS" \
  --out "$OUT/AartiSangrah.apk" "$OUT/aligned.apk"
"$BT/apksigner" verify "$OUT/AartiSangrah.apk"
echo "Built: $OUT/AartiSangrah.apk"
