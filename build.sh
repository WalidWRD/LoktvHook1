#!/usr/bin/env bash
# LokTvHookPro v4.1.0 — one-command build: ./build.sh
# Output: dist/LokTvHookPro-v4.1.0.apk (signed v1+v2) + dist/LokTvHookPro-v4.1.0-src.zip
set -euo pipefail
cd "$(dirname "$0")"

OUT_NAME="LokTvHookPro-v4.1.0"
SDK="${ANDROID_HOME:-/usr/local/lib/android/sdk}"
BT="$SDK/build-tools/35.0.0"
KS="keystore/loktvhookpro.jks"
KS_PASS="${LOKTV_KEY_PASS:-loktv2026}"
KS_ALIAS="loktvpro"

mkdir -p dist keystore

# 0) Keystore (generated once; keep it for updates — same signature required)
if [ ! -f "$KS" ]; then
    echo "[build] generating keystore $KS"
    keytool -genkeypair \
        -keystore "$KS" -alias "$KS_ALIAS" \
        -storepass "$KS_PASS" -keypass "$KS_PASS" \
        -keyalg RSA -keysize 2048 -validity 10950 \
        -dname "CN=LokTvHookPro, OU=Dev, O=LokTV, L=NA, ST=NA, C=NA"
fi

# 1) Gradle build (unsigned release)
echo "[build] gradle assembleRelease"
./gradlew :app:assembleRelease --no-daemon -x lint -q
UNSIGNED="app/build/outputs/apk/release/app-release-unsigned.apk"
[ -f "$UNSIGNED" ] || { echo "missing $UNSIGNED"; exit 1; }

# 2) zipalign
echo "[build] zipalign"
"$BT/zipalign" -p -f 4 "$UNSIGNED" dist/aligned.apk

# 3) sign v1+v2
echo "[build] apksigner (v1+v2)"
"$BT/apksigner" sign \
    --ks "$KS" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
    --ks-key-alias "$KS_ALIAS" \
    --v1-signing-enabled true --v2-signing-enabled true \
    --out "dist/$OUT_NAME.apk" \
    dist/aligned.apk
rm -f dist/aligned.apk

# 4) verify (v2+v3 via apksigner, v1/JAR via jarsigner)
"$BT/apksigner" verify --print-certs "dist/$OUT_NAME.apk"
"$BT/apksigner" verify -v "dist/$OUT_NAME.apk" | grep -E "Verified using"
jarsigner -verify "dist/$OUT_NAME.apk" 2>&1 | head -n 1

# 5) source zip (code + build files, no build outputs / keystore / secrets)
echo "[build] source zip"
rm -f "dist/$OUT_NAME-src.zip"
zip -r -q "dist/$OUT_NAME-src.zip" \
    settings.gradle build.gradle gradle.properties gradlew gradlew.bat \
    gradle/wrapper app/src app/build.gradle app/proguard-rules.pro \
    build.sh docs README.md .gitignore \
    -x '*/build/*'
rm -f dist/*.idsig 2>/dev/null || true

echo "DONE => dist/$OUT_NAME.apk"
ls -la "dist/$OUT_NAME.apk" "dist/$OUT_NAME-src.zip"
