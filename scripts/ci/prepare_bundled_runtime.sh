#!/usr/bin/env bash
set -euo pipefail

# Stage real open-source ARM64 runtime binaries directly into APK assets & jniLibs
# so users only need to select their PC NSUNSR folder and press PLAY.
ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
ASSETS_RT="$ROOT_DIR/app/src/main/assets/runtime"
JNI_ARM64="$ROOT_DIR/app/src/main/jniLibs/arm64-v8a"
WORK_DIR="${WORK_DIR:-/tmp/storm-bundled-runtime}"

mkdir -p \
  "$WORK_DIR" \
  "$JNI_ARM64" \
  "$ASSETS_RT/box64/bin" \
  "$ASSETS_RT/dxvk/x32" \
  "$ASSETS_RT/wine" \
  "$ASSETS_RT/rootfs/bin" \
  "$ASSETS_RT/libs/arm64" \
  "$ASSETS_RT/graphics" \
  "$ASSETS_RT/audio/bin" \
  "$ASSETS_RT/scripts"

echo "=== 1/5 Staging Mali Graphics & Launch Scripts ==="
cp -f "$ROOT_DIR/runtime/graphics/mali_graphics_env.sh" "$ASSETS_RT/graphics/mali_env.sh"
cp -f "$ROOT_DIR/runtime/scripts/launch_nsunsr.sh" "$ASSETS_RT/scripts/launch_nsunsr.sh"
chmod 755 "$ASSETS_RT/graphics/mali_env.sh" "$ASSETS_RT/scripts/launch_nsunsr.sh"

echo "=== 2/5 Building Box64 (ARM64 + BOX32) via Android NDK ==="
if [ -n "${ANDROID_NDK_HOME:-}" ] && [ -d "$ANDROID_NDK_HOME" ]; then
  "$ROOT_DIR/runtime/box64/build_box64_arm64.sh" "$ASSETS_RT/box64"
  if [ -f "$ASSETS_RT/box64/bin/box64" ]; then
    cp -f "$ASSETS_RT/box64/bin/box64" "$JNI_ARM64/libstorm_box64.so"
    chmod 755 "$JNI_ARM64/libstorm_box64.so"
  fi
else
  echo "WARN: ANDROID_NDK_HOME not set; skipping Box64 NDK build."
fi

echo "=== 3/5 Fetching Official DXVK v2.4.1 (d3d9.dll x32) ==="
DXVK_TAG="${DXVK_TAG:-v2.4.1}"
DXVK_VER="${DXVK_TAG#v}"
DXVK_TGZ="$WORK_DIR/dxvk-${DXVK_VER}.tar.gz"
if [ ! -f "$DXVK_TGZ" ]; then
  curl -fsSL --retry 3 "https://github.com/doitsujin/dxvk/releases/download/${DXVK_TAG}/dxvk-${DXVK_VER}.tar.gz" -o "$DXVK_TGZ"
fi
tar -xzf "$DXVK_TGZ" -C "$WORK_DIR"
cp -f "$WORK_DIR/dxvk-${DXVK_VER}/x32/d3d9.dll" "$ASSETS_RT/dxvk/x32/d3d9.dll"
cp -f "$WORK_DIR/dxvk-${DXVK_VER}/x32/dxgi.dll" "$ASSETS_RT/dxvk/x32/dxgi.dll"

echo "=== 4/5 Fetching Wine 9.20 WoW64 Runtime ==="
WINE_VER="${WINE_VER:-9.20}"
WINE_TXZ="$WORK_DIR/wine-${WINE_VER}-amd64-wow64.tar.xz"
if [ ! -f "$WINE_TXZ" ]; then
  curl -fsSL --retry 3 "https://github.com/Kron4ek/Wine-Builds/releases/download/${WINE_VER}/wine-${WINE_VER}-amd64-wow64.tar.xz" -o "$WINE_TXZ"
fi
rm -rf "$WORK_DIR/wine-unpack" && mkdir -p "$WORK_DIR/wine-unpack"
tar -xf "$WINE_TXZ" -C "$WORK_DIR/wine-unpack"
WINE_SRC_DIR=$(find "$WORK_DIR/wine-unpack" -mindepth 1 -maxdepth 1 -type d | head -n 1)
if [ -n "$WINE_SRC_DIR" ]; then
  rm -rf "$ASSETS_RT/wine" && mkdir -p "$ASSETS_RT/wine"
  cp -a "$WINE_SRC_DIR/bin" "$ASSETS_RT/wine/"
  cp -a "$WINE_SRC_DIR/lib" "$ASSETS_RT/wine/"
  if [ -d "$WINE_SRC_DIR/share/wine" ]; then
    mkdir -p "$ASSETS_RT/wine/share"
    cp -a "$WINE_SRC_DIR/share/wine" "$ASSETS_RT/wine/share/"
  fi
  # Trim unused static/debug artifacts to keep APK compact
  find "$ASSETS_RT/wine" -type f \( -name "*.a" -o -name "*.pdb" -o -name "*.def" \) -delete || true
  chmod -R a+rX "$ASSETS_RT/wine"
fi

echo "=== 5/5 Staging ARM64 BusyBox RootFS & Shared Runtime Libs ==="
if command -v aarch64-linux-gnu-gcc >/dev/null 2>&1; then
  BUSYBOX_TGZ="$WORK_DIR/busybox-1_36_1.tar.gz"
  if curl -fsSL --retry 3 "https://github.com/mirror/busybox/archive/refs/tags/1_36_1.tar.gz" -o "$BUSYBOX_TGZ"; then
    rm -rf "$WORK_DIR/busybox-1_36_1"
    tar -xzf "$BUSYBOX_TGZ" -C "$WORK_DIR"
    pushd "$WORK_DIR/busybox-1_36_1" >/dev/null
    make ARCH=arm64 CROSS_COMPILE=aarch64-linux-gnu- defconfig >/dev/null
    sed -i 's/^# CONFIG_STATIC is not set/CONFIG_STATIC=y/' .config
    sed -i 's/^CONFIG_TC=y/# CONFIG_TC is not set/' .config
    make ARCH=arm64 CROSS_COMPILE=aarch64-linux-gnu- -j"$(nproc)" >/dev/null
    cp -f busybox "$ASSETS_RT/rootfs/bin/busybox"
    cp -f busybox "$JNI_ARM64/libstorm_busybox.so"
    chmod 755 "$ASSETS_RT/rootfs/bin/busybox" "$JNI_ARM64/libstorm_busybox.so"
    popd >/dev/null
  fi
fi

# Copy ARM64 C++ standard libraries from Android NDK & GNU toolchain
if [ -n "${ANDROID_NDK_HOME:-}" ]; then
  LIBCXX=$(find "$ANDROID_NDK_HOME" -path "*/aarch64-linux-android/libc++_shared.so" | head -n 1 || true)
  if [ -n "$LIBCXX" ] && [ -f "$LIBCXX" ]; then
    cp -f "$LIBCXX" "$ASSETS_RT/libs/arm64/libc++_shared.so"
  fi
fi
GNU_LIBSTDCXX=$(find /usr/aarch64-linux-gnu /usr/lib/gcc-cross/aarch64-linux-gnu -name "libstdc++.so.6*" -type f 2>/dev/null | head -n 1 || true)
if [ -n "$GNU_LIBSTDCXX" ] && [ -f "$GNU_LIBSTDCXX" ]; then
  cp -L "$GNU_LIBSTDCXX" "$ASSETS_RT/libs/arm64/libstdc++.so.6"
elif [ -f "$ASSETS_RT/libs/arm64/libc++_shared.so" ]; then
  cp -f "$ASSETS_RT/libs/arm64/libc++_shared.so" "$ASSETS_RT/libs/arm64/libstdc++.so.6"
fi

cat > "$ASSETS_RT/bundled_manifest.txt" <<EOF
bundle_version=1.0.0-storm-arm64-$(date -u +%Y%m%d%H%M)
profile=helio-g100-mali-g57
wine_version=${WINE_VER}
dxvk_version=${DXVK_TAG}
box64_bundled=$([ -f "$ASSETS_RT/box64/bin/box64" ] && echo true || echo false)
EOF

echo "=== Bundled Runtime Staging Complete ==="
du -sh "$ASSETS_RT" "$JNI_ARM64"
