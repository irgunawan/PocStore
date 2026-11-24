#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_HOME:-$ROOT/../sdk}"
BT="$SDK/build-tools/34.0.0"
JAR="$SDK/platforms/android-34/android.jar"
JAVAC="${JAVAC:-$ROOT/../tools/jdk/bin/javac}"

cd "$ROOT"
rm -rf build gen classes
mkdir -p build gen classes build/dexout

"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -o build/base.apk -I "$JAR" --manifest AndroidManifest.xml --java gen \
  --min-sdk-version 21 --target-sdk-version 34 build/res.zip

"$JAVAC" -source 11 -target 11 -cp "$JAR" -d classes \
  gen/com.pocindonesia.pocstore/R.java \
  src/com.pocindonesia.pocstore/DialerActivity.java

"$BT/d8" --lib "$JAR" --output build/dexout $(find classes -name '*.class')

python3 - <<'PY'
import zipfile
with zipfile.ZipFile('build/base.apk') as zin, \
     zipfile.ZipFile('build/withdex.apk', 'w', zipfile.ZIP_DEFLATED) as zout:
    for item in zin.infolist():
        zout.writestr(item, zin.read(item.filename))
    zout.write('build/dexout/classes.dex', 'classes.dex')
PY

"$BT/zipalign" -f -p 4 build/withdex.apk build/POC-STORE-aligned.apk
"$BT/apksigner" sign \
  --ks "${KEYSTORE:-$ROOT/../keystore.jks}" \
  --ks-pass file:"${KEYPASS_FILE:-$ROOT/../keypass.txt}" \
  --key-pass file:"${KEYPASS_FILE:-$ROOT/../keypass.txt}" \
  --out POC-STORE.apk build/POC-STORE-aligned.apk

"$BT/apksigner" verify POC-STORE.apk
echo "Selesai: $ROOT/POC-STORE.apk"
