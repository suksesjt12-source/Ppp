#!/usr/bin/env sh
set -eu

RUNTIME_ROOT="${1:?usage: launch_nsunsr.sh <runtime_root> <wineprefix> <nsunsr_exe>}"
WINEPREFIX="${2:?missing wineprefix}"
NSUNSR_EXE="${3:?missing NSUNSR.exe path}"

. "$RUNTIME_ROOT/graphics/mali_env.sh"

export WINEPREFIX
export WINEARCH="${WINEARCH:-win64}"
export WINEESYNC="${WINEESYNC:-1}"
export BOX64_DYNAREC="${BOX64_DYNAREC:-1}"
export BOX64_DYNAREC_FASTNAN="${BOX64_DYNAREC_FASTNAN:-1}"
export BOX64_DYNAREC_BIGBLOCK="${BOX64_DYNAREC_BIGBLOCK:-1}"
export BOX64_DYNAREC_STRONGMEM="${BOX64_DYNAREC_STRONGMEM:-1}"
export PATH="$RUNTIME_ROOT/wine/bin:$RUNTIME_ROOT/box64/bin:$RUNTIME_ROOT/rootfs/bin:$PATH"
export LD_LIBRARY_PATH="$RUNTIME_ROOT/libs/arm64:$RUNTIME_ROOT/wine/lib:$RUNTIME_ROOT/graphics:${LD_LIBRARY_PATH:-}"

BOX64_BIN="${STORM_BOX64_BIN:-$RUNTIME_ROOT/box64/bin/box64}"
WINE_BIN="$RUNTIME_ROOT/wine/bin/wine"

exec "$BOX64_BIN" "$WINE_BIN" "$NSUNSR_EXE"
