#!/usr/bin/env bash
# Sourced by every Mac entry point. Prefer a supported Node runtime without changing system settings.
set -euo pipefail
if ! node -e 'const [a,b]=process.versions.node.split(".").map(Number);process.exit(a>22||(a===22&&b>=12)?0:1)' 2>/dev/null; then
  for SIBI_NODE_DIR in "$HOME"/.nvm/versions/node/v{26,25,24,23,22}*/bin /opt/homebrew/opt/node/bin; do
    if [[ -x "$SIBI_NODE_DIR/node" ]] && "$SIBI_NODE_DIR/node" -e 'const [a,b]=process.versions.node.split(".").map(Number);process.exit(a>22||(a===22&&b>=12)?0:1)'; then
      export PATH="$SIBI_NODE_DIR:$PATH"; break
    fi
  done
fi
node -e 'const [a,b]=process.versions.node.split(".").map(Number);if(!(a>22||(a===22&&b>=12))){console.error("Node 22.12+ is required");process.exit(1)}'
