#!/usr/bin/env bash
set -euo pipefail

# Build Wine (WoW64 enabled: i386-windows + x86_64-unix) for Storm Launcher
# Reference: https://www.winehq.org/ | https://github.com/winebox64/winlator
WINE_VERSION="${WINE_VERSION:-9.20}"
OUT_DIR="${1:-$(pwd)/out/wine}"
BUILD_ROOT="${BUILD_ROOT:-/tmp/storm-build-wine}"

mkdir -p "$BUILD_ROOT" "$OUT_DIR"
cd "$BUILD_ROOT"

if [ ! -d "wine-${WINE_VERSION}" ]; then
  curl -fsSL "https://dl.winehq.org/wine/source/9.x/wine-${WINE_VERSION}.tar.xz" -o "wine-${WINE_VERSION}.tar.xz"
  tar -xf "wine-${WINE_VERSION}.tar.xz"
fi

# Step 1: Build wine-tools for host
mkdir -p build-tools && cd build-tools
"../wine-${WINE_VERSION}/configure" --enable-win64 --without-x --without-freetype
make -j"$(nproc)" __tooldeps__

# Step 2: Build Wine with experimental WoW64 (x86_64 host + i386 PE modules)
# Executed on Android ARM64 via Box64 + ARM64 glibc RootFS
cd "$BUILD_ROOT"
mkdir -p build-wow64 && cd build-wow64
"../wine-${WINE_VERSION}/configure" \
  --prefix="$OUT_DIR" \
  --with-wine-tools="$BUILD_ROOT/build-tools" \
  --enable-archs=i386,x86_64 \
  --disable-tests \
  --without-oss \
  --without-cups \
  --without-sane \
  --without-gphoto \
  --without-krb5 \
  --with-vulkan \
  --with-pulse

make -j"$(nproc)"
make install-lib DESTDIR=""

echo "Wine ${WINE_VERSION} WoW64 staged in: $OUT_DIR"
