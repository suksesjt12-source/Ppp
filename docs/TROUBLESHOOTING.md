# Troubleshooting Guide

## Common Statuses & Solutions

- **`PLAY LOCKED (RUNTIME MISSING)`**
  - Open **Runtime Manager** and verify that **Wine**, **Box64**, **WoW64** (or **Box86**), and **RootFS** all show `Verified`.
- **`Game Validation Failed`**
  - Confirm `NSUNSR.exe` is a valid Windows PE32 executable (not an HTML redirect or corrupted stub) and `data_win32/*.cpk` archives exist in the selected folder.
- **`Vulkan unavailable` / Black Screen with DXVK**
  - Open **Graphics Settings**, switch **DirectX 9 Translation Backend** to **WineD3D**, and clear the shader cache.
- **Audio Crackling / Stutter**
  - Open **Configuration -> Audio Engine** and increase the **Anti-Underrun Buffer** to `80 ms` or `96 ms`.
- **Exit Code 132 (`SIGILL`) or 139 (`SIGSEGV`)**
  - Lower **CPU Translation Mode** from `Performance` to `Conservative` (`BOX64_DYNAREC_STRONGMEM=2`, `BIGBLOCK=1`) and inspect `[WINE]`/`[BOX64]` entries in **Log Viewer**.
- **Generating a Diagnostic Report**
  - Open **Debug -> Run Diagnostic** to capture sanitized hardware, GPU, prefix, runtime, and exit-code findings, then tap **Export** to save a `.log` file under `files/logs/`.
