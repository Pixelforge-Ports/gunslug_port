# Runtime and extraction contract

The APK's game logic is Java/libGDX bytecode, so its Android libgdx.so alone cannot run the game. The adapter uses desktop libGDX/LWJGL3 and the [Westonpack LibGDX runtime](https://github.com/binarycounter/Westonpack/wiki/LibGDX-Example). The [NextOS reference ports](https://github.com/NextOs-Ports) use different game-specific native engines.

The main launcher uses PortMaster's standard controlfolder header, `control.txt`, `get_controls`, `$directory`, `$GPTOKEYB2`, `pm_platform_helper` and `pm_finish`. `Gunslugs.sh` handles Java/Weston downloads and mounting, preserves the audio runtime directory, and performs cleanup after the game exits. Helpers have separate responsibilities:

- `extracted.sh`: reuse validated data or invoke the bundled Java importer on the handheld.
- `display.inc`: use firmware dimensions or the owner's validated resolution override.

Java is mounted before extraction; Weston starts only after preparation succeeds. The runtime keys in metadata omit `.squashfs`; the mount/download filenames include it. No private SDL or forced video/audio driver is supplied. The GLX route remains `headless noop kiosk crusty_glx_gl4es`. The original `XDG_RUNTIME_DIR`, if present, is passed to the game after Weston establishes its own socket directory.

The APK importer reads the APK from `gunslugs/gamedata`, checks the exact APK SHA-256, converts DEX with dex2jar 2.4, relocates the APK's JNI wrapper classes with ASM, and extracts `GAME.JAR` and `assets/` into the same `gamedata` folder. On first launch, `extracted.sh` uses PortMaster's `PortMasterDialog` progress interface. APK verification advances by bytes read and asset extraction by files copied; conversion and code preparation show their current phase until each completes. Prepared data skips the progress dialog on later launches. It needs a JRE, not a compiler, Python or Android emulator. The parent uses a 128 MB heap and the converter a separate 256 MB heap. Physical peak RAM and preparation duration remain to be measured.

Preparation is serialized with a file lock. Work is staged within `gamedata`; the validated `GAME.JAR` is published last. A retry retains prior incomplete output under `.previous-*` before replacement. Existing complete `GAME.JAR` or legacy `game.jar` plus assets is reused without an APK. Saves are separate and never touched by extraction. A hard power loss may leave staging directories; these do not prevent retry.

The game classpath uses `runtime/lib/*` and `gamedata/GAME.JAR` (or legacy `game.jar`). Assets are addressed explicitly, so the working directory remains the game folder. Save and cache locations remain local to the port.

Upstream 0.3.0 behavior is retained: a monotonic 24 ms update interval, a minimum 3:2 logical viewport, extra width on widescreens, borders on square/4:3 screens, and unchanged offscreen render targets. Seven Linux native libraries target AArch64 and require at most GLIBC 2.27; OpenAL additionally needs GLIBCXX 3.4.22 / CXXABI 1.3.9. The separately installed Java runtime has its own platform dependencies.

Universal means one package for compatible AArch64 PortMaster firmwares, not certification of every handheld. Upstream records user-reported RG34XX SP/muOS gameplay. This packaging update still needs physical controller, graphics, audio, import, save/restart, exit and sleep/resume checks across target firmwares.

## PC build

`PrepareDevice` chooses the supplied PC `gunslugs.dat` before APK data. The supported GOG 3.3.0 fingerprint is documented in README.md. `PreparePc` checks SHA-256, stages a game-only JAR under `gamedata`, and publishes it as `gamedata/pc/GAME.JAR` with a cache-format marker. The import includes game classes and assets, excluding bundled desktop libGDX, LWJGL, Steam, native libraries and the Windows launcher. Only the old controller API types needed by the game's class signatures are retained. Native discovery is bypassed in the owner's prepared game code; gptokeyb2 uses the original keyboard input through `gunslugs-pc.ini`.

`PcMain` loads the original game reflectively, provides protected display sizing and an offscreen-aware viewport bridge, and uses the PC launcher's original 30 FPS cap. The APK adapter and 24 ms pacing are unchanged. PC preferences are isolated in `saves/pc`; APK preferences remain in `saves`. Both variants use the existing ARM64 runtime dependencies. No game data is needed for compilation or shipped in the universal ZIP.
