# Installation & Runtime Setup Guide

## 1. Building the Android Application

### Local Build (with Android NDK & CMake)
```bash
export ANDROID_SDK_ROOT=/path/to/android/sdk
gradle :app:assembleDebug -PenableNativeBridge=true
```
Output APK: `app/build/outputs/apk/debug/app-debug.apk`

### GitHub Actions CI Build
Push to `main` or trigger `.github/workflows/android-build.yml` manually to install NDK 27 + CMake 3.31, run unit/Robolectric tests, build `libstorm_runtime_bridge.so`, and download `storm-launcher-arm64-debug-apk`.

## 2. Building or Preparing Runtime Packages

Run the upstream build scripts in `runtime/` on an ARM64 or cross-compile Linux host:
- `runtime/rootfs/build_rootfs_arm64.sh` — Stages Debian ARM64 glibc RootFS.
- `runtime/box64/build_box64_arm64.sh` — Builds Box64 with ARM64 DynaRec (`BOX32=ON`).
- `runtime/wine/build_wine_wow64.sh` — Builds Wine 9.x with `--enable-archs=i386,x86_64` (WoW64).
- `runtime/dxvk/build_dxvk_d3d9.sh` — Builds DXVK `x32/d3d9.dll`.

Package each staged directory as `.zip` or `.tar.gz` and import them directly inside **Runtime Manager** in the app.

## 3. Importing NSUNSR.exe

1. Copy your legitimate PC installation folder of *NARUTO SHIPPUDEN: Ultimate Ninja STORM Revolution* onto device storage.
2. Open **GAME FILES** in Storm Launcher.
3. Tap **Select Game Folder** and grant access via Android's Storage Access Framework picker.
4. Storm Launcher scans for `NSUNSR.exe`, parses its PE32 COFF header, verifies `data_win32/` / `.cpk` files, checks free internal storage, and marks the game ready once runtime dependencies are verified.
