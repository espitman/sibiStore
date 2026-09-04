#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/env.sh"
SIBI_FIXTURE_VERSION="${1:-1}"
cd "$SIBI_ANDROID_ROOT"
./gradlew --no-daemon :fixture:assembleDebug "-PfixtureVersionCode=$SIBI_FIXTURE_VERSION"
mkdir -p test-results/fixtures
cp fixture/build/outputs/apk/debug/fixture-debug.apk "test-results/fixtures/sibi-test-$SIBI_FIXTURE_VERSION.apk"
echo "Fixture APK: mobile/test-results/fixtures/sibi-test-$SIBI_FIXTURE_VERSION.apk"
