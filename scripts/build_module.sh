#!/bin/bash
# HMR Magisk / KernelSU / APatch Module Builder Script
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
OUT_ZIP="$ROOT_DIR/root_hotspot_manager_vanilla.zip"

echo "=== Packaging HMR Magisk / KernelSU / APatch Module ==="
cd "$ROOT_DIR"
rm -f "$OUT_ZIP"

zip -r9 "$OUT_ZIP" \
    module.prop \
    customize.sh \
    action.sh \
    service.sh \
    uninstall.sh \
    bin/ \
    webroot/

unzip -t "$OUT_ZIP"
echo "=== Success! Output Module ZIP: $OUT_ZIP ==="
