#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
: "${MG_ANDROID_PLATFORM:?Set MG_ANDROID_PLATFORM to the Android API 35 platform directory}"
: "${MG_ANDROID_BUILD_TOOLS:?Set MG_ANDROID_BUILD_TOOLS to Android build-tools 35.0.0 directory}"
mkdir -p build/classes build/dex
if command -v javac >/dev/null; then
  javac -encoding UTF-8 --release 8 -classpath "$MG_ANDROID_PLATFORM/android.jar:third_party/onnxruntime/classes.jar" -d build/classes $(find app/src/main/java -name '*.java')
else
  : "${MG_ECJ_JAR:?Set MG_ECJ_JAR to Eclipse compiler ecj-3.39.0.jar when javac is unavailable}"
  java -jar "$MG_ECJ_JAR" -encoding UTF-8 -source 8 -target 8 -classpath third_party/onnxruntime/classes.jar -bootclasspath "$MG_ANDROID_PLATFORM/android.jar:$MG_ANDROID_BUILD_TOOLS/core-lambda-stubs.jar" -d build/classes $(find app/src/main/java -name '*.java')
fi
"$MG_ANDROID_BUILD_TOOLS/d8" --lib "$MG_ANDROID_PLATFORM/android.jar" --min-api 31 --output build/dex $(find build/classes -name '*.class') third_party/onnxruntime/classes.jar
"$MG_ANDROID_BUILD_TOOLS/aapt2" link -I "$MG_ANDROID_PLATFORM/android.jar" --manifest app/src/main/AndroidManifest.xml -o build/unsigned.apk
python - <<'PY'
import zipfile
with zipfile.ZipFile('build/unsigned.apk','a',compression=zipfile.ZIP_DEFLATED) as z:
    from pathlib import Path
    for p in Path('build/dex').glob('*.dex'):
        z.write(p,p.name)
    for p in Path('app/src/main/assets').glob('*'):
        if p.is_file(): z.write(p,'assets/'+p.name)
    for p in Path('app/src/main/jniLibs').rglob('*.so'):
        z.write(p,'lib/'+str(p.relative_to('app/src/main/jniLibs')))
PY
"$MG_ANDROID_BUILD_TOOLS/zipalign" -f 4 build/unsigned.apk build/aligned.apk
if [ ! -f build/debug.jks ]; then
  keytool -genkeypair -keystore build/debug.jks -alias mg-debug -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 3650 -dname 'CN=MG Kamera Development' >/dev/null
fi
"$MG_ANDROID_BUILD_TOOLS/apksigner" sign --ks build/debug.jks --ks-key-alias mg-debug --ks-pass pass:android --key-pass pass:android --out build/MG-Kamera-Tanilama.apk build/aligned.apk
"$MG_ANDROID_BUILD_TOOLS/apksigner" verify --verbose build/MG-Kamera-Tanilama.apk
