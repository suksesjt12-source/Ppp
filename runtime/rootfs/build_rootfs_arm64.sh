#!/usr/bin/env bash
set -euo pipefail

# Stage ARM64 Debian/Ubuntu userspace RootFS for Wine, Box64, and PulseAudio
# Reference architecture: Winlator (https://github.com/winebox64/winlator)
SUITE="${SUITE:-bookworm}"
ARCH="arm64"
OUT_DIR="${1:-$(pwd)/out/rootfs}"

if ! command -v debootstrap >/dev/null 2>&1; then
  echo "ERROR: debootstrap is required on Linux host to assemble ARM64 rootfs." >&2
  exit 1
fi

mkdir -p "$OUT_DIR"
debootstrap --arch="$ARCH" --variant=minbase \
  --include=libc6,libstdc++6,libgcc-s1,libvulkan1,libpulse0,pulseaudio,libx11-6,libxext6,libxrandr2,libxi6,libfreetype6 \
  "$SUITE" "$OUT_DIR" http://deb.debian.org/debian

echo "ARM64 RootFS staged at $OUT_DIR"
