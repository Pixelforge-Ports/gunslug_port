# Gunslugs for PortMaster

An experimental **Gunslugs 3.2.4** port targeting **Anbernic RG34XX SP with muOS**, with an AArch64 PortMaster launcher for other compatible Linux firmware. This uses the supplied Android APK's Java game code through a desktop libGDX bridge. It is not an Android emulator.

**Current evidence:** the converted game boots, renders its title/menu and tutorial gameplay, and responds to scripted movement, jump and fire input on Windows through the same Java bridge. Physical ARM/muOS graphics, performance, controller mapping, audio output, suspend and exit still need handheld testing. R36S/ArkOS support is a secondary, untested target; 32-bit firmware is unsupported.

## Install on your RG34XX SP

1. Update PortMaster in muOS. In its Runtime Manager, install **Westonpack** (`weston_pkg_0.2.squashfs`) and **Zulu Java 17** (`zulu17.54.21-ca-jre17.0.13-linux.squashfs`). The launcher can also request these downloads if the device is online.
2. Extract `dist/gunslugs-private-muos.zip` at the **root of the SD card containing your ports**. Merge its `ports` and `roms` directories with the existing directories.
3. The resulting locations must be `ports/gunslugs/` and `roms/PORTS/Gunslugs.sh` on that same card.
4. Open **Explore Content → Ports → Gunslugs**.

The private archive already contains data prepared from your supplied APK. Keep that archive private. `dist/gunslugs-byo-data.zip` excludes the game code and assets; another owner must prepare their own compatible APK.

For firmware using a single ports directory, extract `dist/gunslugs-private-portmaster.zip` there, keeping `Gunslugs.sh` beside the `gunslugs` directory. The Java and Weston runtimes still come from PortMaster.

If launch fails, return **`ports/gunslugs/log.txt`** from the card. The log records the runtime, firmware and Java exception. Progress and settings are stored in **`ports/gunslugs/saves/`**; preserve that directory when updating.

## Controls

The editable `gunslugs/gunslugs.gptk` maps the device to the game's built-in keyboard controls. A fires/confirms, B jumps, D-pad/left stick moves, Start confirms, and Select goes back/pauses. Select + Start uses PortMaster's exit chord. See the packaged README for the complete mapping. Game options can change the keyboard bindings; resetting them restores the expected defaults.

## Build from your APK

Requires Python 3.8+, a JDK 8 or newer, and [dex2jar 2.4](https://github.com/pxb1988/dex2jar/releases/tag/v2.4). No compiler or build tools need to be installed on the handheld.

```text
python tools/fetch_dependencies.py
python tools/build.py --apk "../Gunslugs 3.2.4.apk" --jdk /path/to/jdk --dex-tools /path/to/dex-tools-v2.4
```

Run those commands from this directory. Dependencies are pinned in `dependencies.lock.json`. The supplied APK is package `com.orangepixel.gunslugshandy`, version code `52`, SHA-256 `d2c857b479a4f7a19bc59840e74bfc6350316a46f8ff579c69da281e8a2933e8`. Other APK versions are rejected because obfuscated method names can change even within the same game version label.

The builder converts DEX to JVM bytecode, relocates the APK's libGDX JNI wrapper classes, and replaces their native entry points with calls to matching desktop libGDX natives. It retains the game's engine and logic. The bridge maps the original interfaces to desktop graphics, audio, file access, preferences and input. It selects the APK's own empty controller manager, allowing PortMaster's keyboard mapper to provide input without Android controller services.

No game code, assets, Android libraries or APK are downloaded by the tools. Decompiled game code is not part of the distributable source package.

## Validation and references

See [VALIDATION.md](VALIDATION.md) for actual checks and remaining device checks, and [runtime-reference.md](docs/runtime-reference.md) for runtime sources and implementation details.

This follows [Westonpack's LibGDX example](https://github.com/binarycounter/Westonpack/wiki/LibGDX-Example) and [muOS's PortMaster layout](https://muos.dev/installation/portmaster).

Adapter code is MIT licensed. Game content remains the property of OrangePixel. Dependency licenses and source information are in `package/gunslugs/licenses/`.
