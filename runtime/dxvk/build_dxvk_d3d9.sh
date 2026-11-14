#!/usr/bin/env bash
set -euo pipefail

# Build DXVK (Direct3D 9 -> Vulkan DLLs for NSUNSR.exe)
# Reference: https://github.com/doitsujin/dxvk
DXVK_TAG="${DXVK_TAG:-v2.4.1}"
OUT_DIR="${1:-$(pwd)/out/dxvk}"
BUILD_ROOT="${BUILD_ROOT:-/tmp/storm-build-dxvk}"

mkdir -p "$BUILD_ROOT" "$OUT_DIR/x32"
cd "$BUILD_ROOT"

if [ ! -d "dxvk" ]; then
  git clone --depth 1 --recursive --branch "$DXVK_TAG" https://github.com/doitsujin/dxvk.git
fi

cd dxvk
./package-release.sh "$DXVK_TAG" "$BUILD_ROOT/pkg" --no-package
cp "$BUILD_ROOT/pkg/dxvk-${DXVK_TAG}/x32/d3d9.dll" "$OUT_DIR/x32/d3d9.dll"

echo "DXVK ${DXVK_TAG} x32 d3d9.dll staged in $OUT_DIR/x32/"
