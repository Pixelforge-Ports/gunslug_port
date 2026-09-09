# Runtime choice and reference audit

Inspected on 2026-09-09. This documents an implementation choice, not a
successful device run.

The APK inspection found libGDX and FreeType support libraries, while the game
logic resides in DEX bytecode. Loading `libgdx.so` cannot run that Java game.
The package therefore targets Java 17, the desktop libGDX/LWJGL3 backend, Linux
AArch64 native libraries and PortMaster's Westonpack display environment.
The game bytecode and assets are prepared from the owner's APK on a PC.

## Implemented launch contract

The [Westonpack author's LibGDX example](https://github.com/binarycounter/Westonpack/wiki/LibGDX-Example)
specifies ARM64 libGDX/LWJGL native libraries, OpenGL 2 or GLES2, Java 17 and
Westonpack 0.2.5 or later. The package uses those named PortMaster runtimes:

- `weston_pkg_0.2.squashfs`
- `zulu17.54.21-ca-jre17.0.13-linux.squashfs`

The launcher sources PortMaster's control and firmware helpers, resolves the
data folder after those helpers are available, and supports muOS's split
`ports/gunslugs` and `roms/PORTS/Gunslugs.sh` layout. When the launcher lives
on `/mnt/mmc` or `/mnt/sdcard`, that card's data takes priority. It uses
`harbourmaster runtime_check` for missing runtimes and calls the firmware's
mount helper. It uses separate `/tmp/gunslugs-weston` and
`/tmp/gunslugs-java` destinations. Runtime mounts created by this launch are
unmounted on exit when kernel mounts are available. A runtime already open
at that destination is reused without taking ownership of its mount.

The display command is `westonwrap.sh headless noop kiosk crusty_glx_gl4es`.
The [Westonpack usage contract](https://github.com/binarycounter/Westonpack/wiki)
describes this as a GLX/OpenGL 2.1 route using SDL2 and GL4ES. It also explains
why environment variables must be passed explicitly across privilege changes.
The launcher follows that convention for controller configuration, Java,
display dimensions and save paths. Firmware video/audio backends are not
selected by model name. PortMaster owns the controller mapping and gptokeyb
translates the handheld controls to the adapter's keyboard inputs.

The classpath is `runtime/lib/*:gamedata/game.jar`, with entry point
`org.portmaster.gunslugs.Main`. Working directory is `gamedata/assets`.
`user.home`, `HOME` and XDG data/config paths point inside `saves`; the Java
temporary directory and XDG cache point to `cache`. The adapter also receives
`gunslugs.gamedir`, `gunslugs.assets`, `gunslugs.saves` and
`gunslugs.fullscreen=true` system properties.

Port metadata uses the version-4 runtime array shown in a current
[PortMaster port.json](https://github.com/PortsMaster/PortMaster-New/blob/main/ports/6feetunder/port.json).
It is explicitly marked experimental and not ready-to-run without owner data.

## Scope and unresolved verification

muOS on RG34XX SP and AArch64 ArkOS/dArkOS on R36S are targets. The Westonpack
compatibility matrix lists corresponding firmware display routes, but that
does not prove this particular game or package works. Startup, rendered
frames, audio, input, save persistence, memory use and shutdown all require
hardware verification. Original 32-bit-only firmware is not supported by this
AArch64 package. Matching Linux natives are needed even when the original APK
contains arm64-v8a Android `.so` files.

The packaged game's seven Linux native libraries were inspected as ELF64
AArch64 (`e_machine=183`). Their highest required GLIBC symbol version is
2.27, which is the native-library bound recorded in `port.json`. OpenAL also
requires the firmware's `libstdc++.so.6` and `libgcc_s.so.1`. This audit does
not establish the separately downloaded Java runtime's complete dependencies.

The [Sample-ArkOS-Game](https://github.com/tuananhdeveloper/Sample-ArkOS-Game)
project advertises a custom libGDX SDL2 backend, but its inspected tree
contains the backend and launcher binaries without the corresponding native
source or a project license. It is not vendored or used here.

libGDX identifies an Apache-2.0 license in its
[official repository](https://github.com/libgdx/libgdx). The packaged Java
libraries and native dependencies need their own license notices. Runtime
downloads remain managed by PortMaster, and the owner's game data remains
outside the port's source license.
