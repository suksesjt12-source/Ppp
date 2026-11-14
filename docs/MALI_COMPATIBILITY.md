# ARM Mali-G57 MC2 GPU Compatibility Guide

## Why Turnip / Freedreno Is Blocked
Qualcomm Turnip is an open-source Mesa Vulkan driver exclusively for **Adreno** GPUs. Using Turnip on a **MediaTek Helio G100 (ARM Mali-G57 MC2)** will immediately fail Vulkan initialization or crash `libvulkan`. Storm Launcher detects Mali hardware via `EGL14`/`GLES20` and enforces Mali-compatible paths.

## Rendering Paths on Mali-G57
1. **DXVK (D3D9 -> Vulkan via System Mali ICD)**
   - Active when `FEATURE_VULKAN_HARDWARE_LEVEL` & `libvulkan.so` are verified and `d3d9.dll` is installed.
   - Uses `DXVK_STATE_CACHE=1` and `d3d9.deferSurfaceCreation=True` to reduce shader stutter.
2. **WineD3D (D3D9 -> OpenGL ES / OpenGL Fallback)**
   - Automatically selected if Vulkan extensions needed by DXVK fail or if DXVK is not installed.
   - Configured with `WINE_D3D_CONFIG="renderer=gl;csmt=on"`.

## 32-Bit x86 Execution (`NSUNSR.exe`) on ARM64
`NSUNSR.exe` is a **32-bit x86 (`PE32 / 0x014C`)** executable. On Android 14 ARM64-only kernels (`arm64-v8a`), raw 64-bit Box64 cannot run 32-bit Windows binaries alone. Storm Launcher inspects the PE header first and routes `PE32` exclusively through **Box64 + Wine WoW64 (`i386-windows` + `x86_64-unix`)** or **Box86** (if 32-bit ARM multiarch is supported).
