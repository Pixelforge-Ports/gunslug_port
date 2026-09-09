# Validation — 9 September 2026

## Executed on this Windows PC

The supplied Gunslugs 3.2.4 APK was converted with dex2jar 2.4. The preparation tool produced **390 classes** and adapted **15 native methods**. The adapter and tools compiled with OpenJDK 8.

- `VerifyBridge`: **70 checks passed**, including relocated classes, 15 native signatures, 62 GL20 methods, GL dispatch, original FileHandle reads, preferences persistence and the APK's empty controller manager lifecycle.
- `ServicesCheck`: passed exact asset-byte reads, isolated file roots, preference defaults/write/reopen/clear, every mapped sound/music operation through a mock desktop Audio implementation, and single disposal of audio resources.
- Real libGDX/LWJGL3/OpenGL run: reached the title screen and rendered **180 frames**, then exited normally.
- Real rendering with a separate input driver: rendered **560 frames**, selected a character, entered the tutorial, moved right, jumped and fired. Captured frames were visually inspected. Inputs entered through the bridge's real processor and polling interfaces; the test did not synthesize physical handheld controller events.
- The desktop OpenAL backend initialized and the game loaded its audio without an exception. Audible output was not independently verified.
- ShellCheck 0.11.0 passed for the launcher (firmware-owned sourced files excluded). JSON/XML metadata parsed successfully; launcher and control map use LF line endings without a BOM.
- All seven bundled Linux ARM64 native libraries were inspected as ELF64 AArch64. Their highest glibc symbol requirement is **GLIBC 2.27**. OpenAL also requires **GLIBCXX 3.4.22 / CXXABI 1.3.9** from `libstdc++.so.6`, plus `libgcc_s.so.1`. This inspects the package, not the handheld's installed libraries.

The test driver is under `tests/` and is excluded from the shipped bridge jar. Test progress, preferences and captured images are under `build/`, also excluded from the install archives.

Captured evidence in this workspace:

- `build/smoke-title.png`: title screen.
- `build/gameplay-400.png`: tutorial starting position.
- `build/gameplay-510.png`: character moved forward, jumped and fired at crates; tutorial overlay rendered.

## Still requires RG34XX SP / muOS testing

This is an **experimental device test build**, not a claim of a completed hardware certification. The Windows checks validate APK conversion, Java runtime integration, rendering and game input logic. They do not run the ARM binaries, Weston/gl4es, muOS drivers or PortMaster's physical input mapper.

On the handheld, check title/menu visibility at 720×480, A confirm/fire, B jump, movement and release, sound/music, progress after relaunch, Start+Select exit, and sleep/resume. If anything fails, preserve `ports/gunslugs/log.txt` and report which step failed. Do not assume other Anbernic XX models or R36S firmware have been tested.

Windows sandbox detail: Java 8 reported AccessDeniedException when resolving some allowed workspace paths (including dex2jar's ZIP filesystem close). The output jar was valid; subsequent class/asset verification and graphical tests ran with approved filesystem access and passed. This is a host build detail, not a handheld result.
