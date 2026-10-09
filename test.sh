#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build/test
if command -v javac >/dev/null; then
  javac --release 8 -d build/test app/src/main/java/com/mgkamera/diagnostic/CameraMath.java app/src/main/java/com/mgkamera/diagnostic/ChromaDenoise.java tests/CameraMathTest.java tests/ChromaDenoiseTest.java
else
  : "${MG_ECJ_JAR:?Set MG_ECJ_JAR}"; : "${MG_ANDROID_PLATFORM:?Set MG_ANDROID_PLATFORM}"
  java -jar "$MG_ECJ_JAR" -source 8 -target 8 -bootclasspath "$MG_ANDROID_PLATFORM/android.jar" -d build/test app/src/main/java/com/mgkamera/diagnostic/CameraMath.java app/src/main/java/com/mgkamera/diagnostic/ChromaDenoise.java tests/CameraMathTest.java tests/ChromaDenoiseTest.java
fi
java -cp build/test CameraMathTest

java -cp build/test ChromaDenoiseTest
