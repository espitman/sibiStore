#!/usr/bin/env bash
# Verify package-scoped launch resolution, not just an explicit Activity launch.
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/env.sh"
SIBI_SERIAL="${1:?Pass an explicit QA device serial}"
SIBI_RESOLVED="$(adb -s "$SIBI_SERIAL" shell cmd package resolve-activity --brief --query-flags 65536 -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p com.sibi.store.mobile)"
[[ "$SIBI_RESOLVED" == *com.sibi.store.mobile/.MainActivity* ]] || { echo "$SIBI_RESOLVED"; exit 1; }
SIBI_LAUNCH="$(adb -s "$SIBI_SERIAL" shell am start -S -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p com.sibi.store.mobile)"
echo "$SIBI_LAUNCH"
[[ "$SIBI_LAUNCH" == *'Status: ok'* ]]
