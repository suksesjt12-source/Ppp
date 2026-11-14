#!/usr/bin/env bash
set -euo pipefail

# Build Box86 (32-bit ARMv7 fallback for devices with 32-bit multiarch sysroot)
# Note: On Android 14 ARM64-only kernels/devices, Wine WoW64 + Box64 is preferred.
# Reference: https://github.com/ptitSeb/box86
BOX86_TAG="${BOX86_TAG:-v0.3.8}"
OUT_DIR="${1:-$(pwd)/out/box86}"
BUILD_ROOT="${BUILD_ROOT:-/tmp/storm-build-box86}"

mkdir -p "$BUILD_ROOT" "$OUT_DIR/bin"
cd "$BUILD_ROOT"

if [ ! -d "box86" ]; then
  git clone --depth 1 --branch "$BOX86_TAG" https://github.com/ptitSeb/box86.git
fi

mkdir -p box86/build && cd box86/build
cmake .. \
  -DARM_DYNAREC=ON \
  -DRPI4ARM64=OFF \
  -DCMAKE_BUILD_TYPE=RelWithDebInfo \
  -DCMAKE_C_FLAGS="-march=armv8-a+crc -mfloat-abi=hard"
cmake --build . -j"$(nproc)"
cp box86 "$OUT_DIR/bin/box86"
chmod 755 "$OUT_DIR/bin/box86"

echo "Box86 ${BOX86_TAG} built at $OUT_DIR/bin/box86"
