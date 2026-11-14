# Storm Launcher — Windows ARM64 Compatibility Launcher

An Android native Kotlin + Jetpack Compose + C++20 JNI compatibility launcher designed to import, validate, configure, and run **NARUTO SHIPPUDEN: Ultimate Ninja STORM Revolution** (`NSUNSR.exe`) on Android ARM64 devices (optimized for **Infinix Hot 50 4G / MediaTek Helio G100 / Mali-G57 MC2 / 8 GB RAM / Android 14**).

> **Important Notice:** No proprietary game files or copyrighted character assets are bundled in this project. Users must supply their own legitimate PC installation of `NSUNSR.exe` via Android Storage Access Framework (SAF) and import compatible open-source ARM64 runtime packages (Wine WoW64, Box64, DXVK, RootFS).

## Architecture Overview

```
Android ARM64 (Infinix Hot 50 4G — Helio G100 / Mali-G57 MC2)
 └─ Storm Launcher (Kotlin + Jetpack Compose + Material 3)
     ├─ C++20 JNI Bridge (app/src/main/cpp/libstorm_runtime_bridge.so)
     │   ├─ runtime_bridge.cpp (ELF inspector, libvulkan.so dlopen probe, sysinfo)
     │   ├─ process_bridge.cpp (POSIX fork/execve/waitpid/kill + non-blocking pipes)
     │   └─ input_bridge.cpp   (AF_UNIX XInput binary packet dispatcher)
     ├─ Game File Manager (SAF DocumentFile scanner + PE32/PE32+ binary parser)
     ├─ Windows Runtime Engine (Wine WoW64 + Box64 / Box86 translation router)
     ├─ Graphics Translation Engine (Mali-safe DXVK D3D9->Vulkan / WineD3D GLES fallback)
     └─ Fullscreen Landscape Viewport + Interactive Virtual Gamepad Overlay
```

## Implemented Modules & Screens

1. **Splash Screen** — Hardware & Vulkan pre-flight probe.
2. **Dashboard** — Live status for Runtime, Game Installation, Game Location, Wine, Box86/Box64/WoW64, Graphics Driver, and strictly gated `PLAY` button.
3. **Game Library** — Android SAF directory importer, non-destructive scanner, PE32 COFF header verifier, `.cpk` & DirectX structure check, storage capacity guard, private sandbox copier.
4. **Game Configuration** — `NSUNSR_OPTIMIZED` profile (`960x540`, `30 FPS`, `Balanced`), `LOW`/`BALANCED`/`HIGH` presets, PulseAudio/ALSA config, isolated Wine Prefix Manager (Create/Repair/Reset/Backup ZIP/Restore ZIP).
5. **Runtime Manager** — Real on-disk ELF/DLL verification for Wine, Box86, Box64, WoW64, DXVK, Vulkan Backend, Graphics Driver, Audio Backend, RootFS, and Runtime Libraries + `.zip`/`.tar.gz`/`.wcp` archive installer.
6. **Graphics Settings** — Real EGL14/GLES20 & Vulkan probe, automatic Mali-G57 protection (Turnip blocked), DXVK vs WineD3D vs Automatic, shader cache management, anisotropic filtering, VSync, and FPS cap.
7. **Controls Settings** — Virtual gamepad profiles, opacity, size, sensitivity, dead zone, response curves, Bluetooth/USB gamepad & keyboard mapping, plus landscape layout editor.
8. **System Information** — Live SoC, CPU cores/load, RAM, storage, thermal throttling status, JNI bridge status, and GPU capabilities.
9. **Debug Console** — 13-condition automated diagnostic audit engine, PII-sanitized log export, clipboard copy, and crash code decoder.
10. **Log Viewer** — Searchable, category-filtered live runtime log stream.

## Documentation

- [Installation & Runtime Setup](docs/INSTALLATION.md)
- [Game Configuration & Presets](docs/GAME_CONFIG.md)
- [Mali-G57 GPU Compatibility Guide](docs/MALI_COMPATIBILITY.md)
- [Troubleshooting & Error Codes](docs/TROUBLESHOOTING.md)
- [Third-Party Notices](THIRD_PARTY_NOTICES)
