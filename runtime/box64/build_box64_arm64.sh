#!/usr/bin/env bash
set -euo pipefail

# Build Box64 for Android ARM64 (Cortex-A76/A55 on MediaTek Helio G100)
# Reference: https://github.com/ptitSeb/box64
BOX64_TAG="${BOX64_TAG:-v0.3.2}"
OUT_DIR="${1:-$(pwd)/out/box64}"
BUILD_ROOT="${BUILD_ROOT:-/tmp/storm-build-box64}"
NDK_TOOLCHAIN="${ANDROID_NDK_HOME:-}/build/cmake/android.toolchain.cmake"

mkdir -p "$BUILD_ROOT" "$OUT_DIR/bin"
cd "$BUILD_ROOT"

if [ ! -d "box64" ]; then
  git clone --depth 1 --branch "$BOX64_TAG" https://github.com/ptitSeb/box64.git
fi

mkdir -p box64/build && cd box64/build
CMAKE_ARGS=(
  -DARM_DYNAREC=ON
  -DCMAKE_BUILD_TYPE=RelWithDebInfo
  -DBOX32=ON
  -DBOX32_BINFMT=OFF
)

if [ -n "${ANDROID_NDK_HOME:-}" ] && [ -f "$NDK_TOOLCHAIN" ]; then
  CMAKE_ARGS+=(
    -DCMAKE_TOOLCHAIN_FILE="$NDK_TOOLCHAIN"
    -DANDROID_ABI=arm64-v8a
    -DANDROID_PLATFORM=android-28
    -DBAD_SIGNAL=ON
    -DANDROID=1
  )
else
  CMAKE_ARGS+=(-DLD80BITS=1 -DNOALIGN=1)
fi

cmake .. "${CMAKE_ARGS[@]}"
cmake --build . -j"$(nproc)"
cp box64 "$OUT_DIR/bin/box64"
chmod 755 "$OUT_DIR/bin/box64"

echo "Box64 ${BOX64_TAG} built at $OUT_DIR/bin/box64"
