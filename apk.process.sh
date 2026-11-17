#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
DEBUG_KEYSTORE="$ROOT_DIR/debug.keystore"

if [[ ! -f "$DEBUG_KEYSTORE" ]]; then
    if ! command -v keytool >/dev/null 2>&1; then
        echo "keytool is required to generate the debug signing keystore." >&2
        exit 1
    fi

    umask 077
    keytool -genkeypair -v -noprompt \
        -keystore "$DEBUG_KEYSTORE" \
        -storetype JKS \
        -storepass android \
        -alias androiddebugkey \
        -keypass android \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -dname "CN=Android Debug,O=Android,C=US"
fi

"$ROOT_DIR/gradlew" clean
"$ROOT_DIR/gradlew" assembleDebug
adb install -r -t "$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"