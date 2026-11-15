#!/usr/bin/env sh
# Mali-G57 MC2 Graphics Environment Profile (MediaTek Helio G100, ARM64)
# Explicitly avoids Adreno-only Freedreno/Turnip variables.

export VK_ICD_FILENAMES="${VK_ICD_FILENAMES:-/vendor/etc/vulkan/icd.d/mali.json}"
export DXVK_STATE_CACHE="${DXVK_STATE_CACHE:-1}"
export DXVK_FRAME_RATE="${DXVK_FRAME_RATE:-30}"
export MESA_VK_WSI_PRESENT_MODE="${MESA_VK_WSI_PRESENT_MODE:-immediate}"

# WineD3D fallback defaults when Vulkan is unavailable or incompatible
export WINE_D3D_CONFIG="${WINE_D3D_CONFIG:-renderer=gl;csmt=on}"
