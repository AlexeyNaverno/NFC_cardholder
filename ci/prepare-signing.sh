#!/usr/bin/env bash
#
# Prepares release signing for CI.
#
# When the repository secrets RELEASE_KEYSTORE_BASE64, RELEASE_STORE_PASSWORD,
# RELEASE_KEY_ALIAS and RELEASE_KEY_PASSWORD are set, the real project keystore is used, so users
# can upgrade between releases. When they are not set, a throw-away keystore is generated for this
# single build and the release notes say so explicitly.
#
# Either way a `keystore.properties` file is written, which app/build.gradle.kts reads.
set -euo pipefail

cd "$(dirname "$0")/.."

mkdir -p .secrets

if [ -n "${RELEASE_KEYSTORE_BASE64:-}" ]; then
    echo "::notice::Signing with the project keystore from repository secrets."
    printf '%s' "$RELEASE_KEYSTORE_BASE64" | base64 --decode > .secrets/release.jks
    cat > keystore.properties <<EOF
storeFile=$PWD/.secrets/release.jks
storePassword=${RELEASE_STORE_PASSWORD}
keyAlias=${RELEASE_KEY_ALIAS}
keyPassword=${RELEASE_KEY_PASSWORD}
EOF
    NOTE="Signed with the project release key (the same key signs every release, updates install cleanly)."
else
    echo "::warning::No RELEASE_KEYSTORE_BASE64 secret: generating a temporary key for this build only."
    PASSWORD="$(head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | cut -c1-20)"
    keytool -genkeypair \
        -keystore .secrets/release.jks \
        -storetype PKCS12 \
        -storepass "$PASSWORD" \
        -keypass "$PASSWORD" \
        -alias nfc-cardholder \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -dname "CN=NFC Cardholder CI, OU=Release, O=NFC Cardholder"
    cat > keystore.properties <<EOF
storeFile=$PWD/.secrets/release.jks
storePassword=$PASSWORD
keyAlias=nfc-cardholder
keyPassword=$PASSWORD
EOF
    NOTE="This APK is signed with a throw-away CI key. Installing a later release built with a different key requires uninstalling first. Add the RELEASE_KEYSTORE_BASE64 repository secret to sign every release with the same key."
fi

mkdir -p build
printf '%s\n' "$NOTE" > build/signing-note.txt
chmod 600 keystore.properties .secrets/release.jks
