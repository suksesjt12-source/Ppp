# Game Configuration Guide (`NSUNSR_OPTIMIZED`)

## Default Profile (`NSUNSR_OPTIMIZED`)
Designed for **Infinix Hot 50 4G (MediaTek Helio G100, Mali-G57 MC2, 8 GB RAM)**:
- **Resolution:** `960x540` (16:9 native scaling, zero HUD clipping)
- **FPS Limit:** `30 FPS` (matches game logic stability without thermal throttling)
- **VSync:** `Off` (minimizes input latency at 30 FPS)
- **Graphics Backend:** `Automatic` (DXVK when Mali Vulkan is verified, otherwise WineD3D)
- **CPU Mode:** `Balanced` (`BOX64_DYNAREC_BIGBLOCK=2`, `BOX64_DYNAREC_STRONGMEM=1`)
- **Audio:** `PulseAudio` with `64 ms` anti-underrun buffer (`>= 48 ms` minimum)
- **Shader Cache:** `Enabled` (`files/cache/shaders/`)

## Presets
| Preset | Resolution | Target FPS | Filtering | CPU DynaRec |
| --- | --- | --- | --- | --- |
| **LOW** | `854x480` | `30 FPS` | Bilinear (1x) | Balanced |
| **BALANCED** | `960x540` | `30 FPS` | Anisotropic 4x | Balanced |
| **HIGH** | `1280x720` | `60 FPS` | Anisotropic 8x | Performance |

## Wine Prefix Isolation
All registry hives, `drive_c`, and Direct3D 9 overrides live inside private app storage (`files/prefix/`). Use **Create / Repair**, **Backup ZIP**, **Restore ZIP**, or **Reset** under **Configuration** without affecting external files.
