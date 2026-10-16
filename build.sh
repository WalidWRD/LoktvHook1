#!/usr/bin/env bash
# NexHookPro v7.0.2 — one-command build: ./build.sh
# Output: dist/NexHookPro-v7.0.2.apk (signed v1+v2+v3) + dist/NexHookPro-v7.0.2-src.zip
set -euo pipefail
cd "$(dirname "$0")"

OUT_NAME="NexHookPro-v7.0.2"
SDK="${ANDROID_HOME:-/usr/local/lib/android/sdk}"
BT="$SDK/build-tools/35.0.0"
KS="keystore/nexhookpro.jks"
KS_PASS="${NEXHOOK_KEY_PASS:-nexhook2026}"
KS_ALIAS="nexhook"

mkdir -p dist keystore

# 0) Keystore (generated once; keep it for updates — same signature required)
if [ ! -f "$KS" ]; then
    echo "[build] generating keystore $KS"
    keytool -genkeypair \
        -keystore "$KS" -alias "$KS_ALIAS" \
        -storepass "$KS_PASS" -keypass "$KS_PASS" \
        -keyalg RSA -keysize 2048 -validity 10950 \
        -dname "CN=NexHookPro, O=NexHook, C=EG"
fi

# 1) Gradle build (unsigned release)
echo "[build] gradle assembleRelease"
./gradlew :app:assembleRelease --no-daemon -x lint -q
UNSIGNED="app/build/outputs/apk/release/app-release-unsigned.apk"
[ -f "$UNSIGNED" ] || { echo "missing $UNSIGNED"; exit 1; }

# 2) zipalign
echo "[build] zipalign"
"$BT/zipalign" -p -f 4 "$UNSIGNED" dist/aligned.apk

# 3) sign v1+v2+v3
echo "[build] apksigner (v1+v2+v3)"
"$BT/apksigner" sign \
    --ks "$KS" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
    --ks-key-alias "$KS_ALIAS" \
    --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
    --out "dist/$OUT_NAME.apk" \
    dist/aligned.apk
rm -f dist/aligned.apk

# 4) verify (apksigner + jarsigner for v1/JAR)
"$BT/apksigner" verify --print-certs "dist/$OUT_NAME.apk"
"$BT/apksigner" verify -v "dist/$OUT_NAME.apk" | grep -E "Verified using"
jarsigner -verify "dist/$OUT_NAME.apk" 2>&1 | grep -E "verified" | head -n 1

# 5) source zip (code + build files + docs, no build outputs / keystore / secrets)
echo "[build] source zip"
rm -f "dist/$OUT_NAME-src.zip"
zip -r -q "dist/$OUT_NAME-src.zip" \
    settings.gradle build.gradle gradle.properties gradlew gradlew.bat \
    gradle/wrapper app/src app/libs app/build.gradle app/proguard-rules.pro \
    build.sh docs README.md CHANGELOG.md .gitignore \
    -x '*/build/*'
rm -f dist/*.idsig 2>/dev/null || true

echo "DONE => dist/$OUT_NAME.apk"
ls -la "dist/$OUT_NAME.apk" "dist/$OUT_NAME-src.zip"
