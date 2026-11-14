#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
DIST_DIR="$ROOT_DIR/build/runtime-dist"
mkdir -p "$DIST_DIR"

echo "=== Building Storm Launcher ARM64 Native JNI Bridge ==="
if [ -n "${ANDROID_NDK_HOME:-}" ]; then
  cmake -S "$ROOT_DIR/native" -B "$ROOT_DIR/build/native-arm64" \
    -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=android-24 \
    -DANDROID_STL=c++_shared
  cmake --build "$ROOT_DIR/build/native-arm64" -j"$(nproc)"
else
  echo "WARN: ANDROID_NDK_HOME not set; skipping standalone NDK CMake build."
fi

echo "=== Runtime Upstream Scripts Available ==="
echo "  - $ROOT_DIR/runtime/box64/build_box64_arm64.sh"
echo "  - $ROOT_DIR/runtime/wine/build_wine_wow64.sh"
echo "  - $ROOT_DIR/runtime/dxvk/build_dxvk_d3d9.sh"
echo "  - $ROOT_DIR/runtime/rootfs/build_rootfs_arm64.sh"
