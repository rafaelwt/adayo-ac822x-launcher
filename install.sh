#!/usr/bin/env bash
# Build the launcher and install it on the ADAYO AC822X head unit over ADB (Wi-Fi, port 5555).
# Usage: RADIO_HOST=<ip de la radio> ./install.sh
set -euo pipefail
cd "$(dirname "$0")"
HOST="${RADIO_HOST:?Falta RADIO_HOST=<ip de la radio>}"
TOOLS=../cleanyourtv/tools   # the ADB client from the CleanYourTV repo
PY=$TOOLS/venv/bin/python
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home)}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"

./gradlew -q assembleRelease
APK=app/build/outputs/apk/release/app-release.apk
# Android 4.2 here has no base64 or md5sum, so b64push.py cannot verify: push over ADB sync (the APK is small)
# and check it with the radio's own md5.
TV_HOST=$HOST "$PY" -I "$TOOLS/tv.py" --push "$APK" /data/local/tmp/radiohome.apk
WANT=$(md5 -q "$APK")
GOT=$(TV_HOST=$HOST "$PY" -I "$TOOLS/tv.py" "md5 /data/local/tmp/radiohome.apk" | awk '{print $1}')
[ "$WANT" = "$GOT" ] || { echo "md5 mismatch: $GOT != $WANT" >&2; exit 1; }
TV_HOST=$HOST "$PY" -I "$TOOLS/tv.py" "pm install -r /data/local/tmp/radiohome.apk; rm /data/local/tmp/radiohome.apk"
# Android 4.2 has no command to set the default Home: pressing Home shows the chooser.
TV_HOST=$HOST "$PY" -I "$TOOLS/tv.py" "input keyevent KEYCODE_HOME"
echo "Installed. On the radio pick 'Inicio Swift' and tap 'Siempre'."
