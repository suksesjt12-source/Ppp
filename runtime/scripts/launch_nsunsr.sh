#!/usr/bin/env sh
set -eu

# Launch wrapper invoked inside Storm Launcher runtime environment
RUNTIME_DIR="${1:?runtime dir required}"
PREFIX_DIR="${2:?prefix dir required}"
GAME_EXE="${3:?NSUNSR.exe path required}"

export WINEPREFIX="$PREFIX_DIR"
export WINEARCH="${WINEARCH:-win64}"
export BOX64_DYNAREC="${BOX64_DYNAREC:-1}"
export BOX64_DYNAREC_BIGBLOCK="${BOX64_DYNAREC_BIGBLOCK:-2}"
export BOX64_DYNAREC_STRONGMEM="${BOX64_DYNAREC_STRONGMEM:-1}"
export BOX64_DYNAREC_FASTNAN="${BOX64_DYNAREC_FASTNAN:-1}"
export BOX64_DYNAREC_FASTROUND="${BOX64_DYNAREC_FASTROUND:-1}"

exec "$RUNTIME_DIR/box64/bin/box64" "$RUNTIME_DIR/wine/bin/wine64" "$GAME_EXE"
