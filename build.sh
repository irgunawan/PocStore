#!/usr/bin/env bash
# Build POC STORE tanpa Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
SDK="${POCSTORE_SDK:-$HOME/work/pocdialer/sdk}"
BT="$SDK/build-tools/34.0.0"
JAR="$SDK/platforms/android-34/android.jar"
JAVAC="${JAVAC:-$HOME/work/pocdialer/tools/jdk/bin/javac}"
KS="${KEYSTORE:-$HOME/work/pttin/pttin-release.jks}"
PASS="${KEYPASS_FILE:-$HOME/work/pttin/pttin-release.jks.pass}"
OUT="${1:-POC-STORE-v$(grep -oP 'versionName="\K[^"]+' AndroidManifest.xml).apk}"

cd "$ROOT"
rm -rf build gen classes
mkdir -p build gen classes build/dexout

echo "[1/5] aapt2 compile + link"
"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -o build/base.apk -I "$JAR" --manifest AndroidManifest.xml \
    --java gen --min-sdk-version 21 --target-sdk-version 34 --version-code "$(grep -oP 'versionCode="\K[0-9]+' AndroidManifest.xml)" \
    --version-name "$(grep -oP 'versionName="\K[^"]+' AndroidManifest.xml)" build/res.zip

echo "[2/5] javac"
"$JAVAC" -source 11 -target 11 -nowarn -encoding UTF-8 -cp "$JAR" -d classes \
    gen/com/pocindonesia/pocstore/R.java \
    $(find src -name '*.java')

echo "[3/5] d8"
"$BT/d8" --lib "$JAR" --min-api 21 --output build/dexout $(find classes -name '*.class')

echo "[4/5] kemas apk"
python3 - <<'PY'
import zipfile
with zipfile.ZipFile('build/base.apk') as zin, \
     zipfile.ZipFile('build/withdex.apk', 'w', zipfile.ZIP_DEFLATED) as zout:
    for item in zin.infolist():
        zout.writestr(item, zin.read(item.filename))
    zout.write('build/dexout/classes.dex', 'classes.dex')
PY
"$BT/zipalign" -f -p 4 build/withdex.apk build/aligned.apk

echo "[5/5] tanda tangan"
# tanpa --ks-key-alias: keystore PKCS12 ini hanya punya satu entri (alias "pptin"),
# dan apksigner menolak alias tersebut kalau disebut eksplisit.
"$BT/apksigner" sign --ks "$KS" \
    --ks-pass "file:$PASS" --key-pass "file:$PASS" \
    --v1-signing-enabled true --v2-signing-enabled true \
    --out "$OUT" build/aligned.apk
"$BT/apksigner" verify -v "$OUT" | head -6
echo "Selesai: $ROOT/$OUT"