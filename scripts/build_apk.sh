#!/bin/bash
# HMR Standalone APK Builder Script
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
APP_DIR="$ROOT_DIR/app"
MODULE_DIR="$ROOT_DIR/module"
BUILD_DIR="$ROOT_DIR/build"
OUT_APK="$ROOT_DIR/HMR-v1.2.4.apk"

# Resolve Android SDK Tools
AAPT2="${AAPT2:-$(which aapt2 2>/dev/null || find /tmp/notify-android-sdk/ -name aapt2 2>/dev/null | head -n 1)}"
D8="${D8:-$(which d8 2>/dev/null || find /tmp/notify-android-sdk/ -name d8 2>/dev/null | head -n 1)}"
ZIPALIGN="${ZIPALIGN:-$(which zipalign 2>/dev/null || find /tmp/notify-android-sdk/ -name zipalign 2>/dev/null | head -n 1)}"
APKSIGNER="${APKSIGNER:-$(which apksigner 2>/dev/null || find /tmp/notify-android-sdk/ -name apksigner 2>/dev/null | head -n 1)}"
ANDROID_JAR="${ANDROID_JAR:-$(find /tmp/notify-android-sdk/platforms/ /android-sdk/platforms/ -name "android.jar" 2>/dev/null | tail -n 1)}"

echo "=== Building HMR Standalone Root APK ==="
echo "AAPT2: $AAPT2"
echo "D8: $D8"
echo "ZIPALIGN: $ZIPALIGN"
echo "APKSIGNER: $APKSIGNER"
echo "ANDROID_JAR: $ANDROID_JAR"

# Prepare build dirs
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/gen" "$BUILD_DIR/classes" "$BUILD_DIR/dex"
mkdir -p "$APP_DIR/src/main/assets/webroot" "$APP_DIR/src/main/assets/bin"

# Sync assets from module directory
cp -r "$MODULE_DIR/webroot/"* "$APP_DIR/src/main/assets/webroot/"
cp "$MODULE_DIR/bin/hotspot-ctl" "$APP_DIR/src/main/assets/bin/"
cp "$MODULE_DIR/bin/hotspot_helper.jar" "$APP_DIR/src/main/assets/bin/"
cp "$MODULE_DIR/module.prop" "$APP_DIR/src/main/assets/"

# 1. Compile Resources
"$AAPT2" compile --dir "$APP_DIR/src/main/res" -o "$BUILD_DIR/compiled_res.zip"

# 2. Link Resources
"$AAPT2" link -I "$ANDROID_JAR" \
    "$BUILD_DIR/compiled_res.zip" \
    --manifest "$APP_DIR/src/main/AndroidManifest.xml" \
    -o "$BUILD_DIR/unaligned.apk" \
    --java "$BUILD_DIR/gen" \
    -A "$APP_DIR/src/main/assets" \
    --auto-add-overlay

# 3. Compile Java Source
javac -cp "$ANDROID_JAR:$BUILD_DIR/gen" \
    -d "$BUILD_DIR/classes" \
    "$APP_DIR"/src/main/java/com/uniqueselect/hmr/*.java \
    "$BUILD_DIR"/gen/com/uniqueselect/hmr/R.java

# 4. Convert classes to DEX
CLASS_FILES=$(find "$BUILD_DIR/classes" -name "*.class")
"$D8" --lib "$ANDROID_JAR" --output "$BUILD_DIR/dex" --min-api 30 $CLASS_FILES

# 5. Package classes.dex
cd "$BUILD_DIR/dex"
zip -u "$BUILD_DIR/unaligned.apk" classes.dex
cd "$ROOT_DIR"

# 6. Zipalign
"$ZIPALIGN" -p -f 4 "$BUILD_DIR/unaligned.apk" "$BUILD_DIR/aligned.apk"

# 7. Generate release key if needed and sign
if [ ! -f "$BUILD_DIR/release.jks" ]; then
    keytool -genkeypair -v -keystore "$BUILD_DIR/release.jks" \
        -alias hmr \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -storepass hmrpassword \
        -keypass hmrpassword \
        -dname "CN=uniqueselect, OU=Anti-Gravity, O=HMR, L=Global, ST=Global, C=US"
fi

"$APKSIGNER" sign --ks "$BUILD_DIR/release.jks" \
    --ks-pass pass:hmrpassword \
    --key-pass pass:hmrpassword \
    --ks-key-alias hmr \
    --out "$OUT_APK" \
    "$BUILD_DIR/aligned.apk"

"$APKSIGNER" verify --verbose "$OUT_APK"
echo "=== Success! Output APK: $OUT_APK ==="
