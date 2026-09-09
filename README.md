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

## Speed fix (bridge 0.2.0)

The original Android render callback (`C.a.f`) runs one game update per frame and sleeps to reach a minimum of 24 ms per update (about 41.7 updates/second). The desktop application type skips that Android sleep; bridge 0.1.0 instead targeted 60 FPS. Bridge 0.2.0 enforces a minimum 24 ms between updates with a monotonic clock, disables vsync to avoid rounding this interval to display refresh multiples, and never replays missed updates after a stall or resume. This corrects the identified timing mismatch; the user's reported 2–3× speed has not been measured on hardware.

To update muOS, merge the new private muOS archive into the SD card root. Preserve `ports/gunslugs/saves`. Existing game data does not need conversion again. A smaller `gunslugs-speed-fix-muos.zip` contains only the updated bridge and installs at the SD card root.

## Prepare game data on the handheld

The new launcher can convert the owner's APK using the existing PortMaster Java runtime; no Python, JDK or PC conversion is needed on the handheld. Install `gunslugs-byo-data-muos.zip` at the SD card root for muOS, or `gunslugs-byo-data.zip` in the ports directory for other supported firmware. Copy the supported APK to `ports/gunslugs/gunslugs.apk` and launch Gunslugs. The first launch verifies the APK fingerprint, converts its code and extracts its assets; later launches reuse the prepared data. Allow several minutes and do not power off during preparation. The APK is retained and can be removed after success.

Only the exact APK fingerprint documented below is accepted. Already prepared data is reused. If preparation is interrupted during final installation, back up and move incomplete `gamedata/game.jar` and `gamedata/assets` before retrying; keep `saves`. The converter uses up to 256 MB of Java heap plus a separate 128 MB preparation process and native overhead. Actual peak RAM and preparation time on the RG34XX SP remain unmeasured. See `gunslugs/log.txt` for progress or failure details.

## Build and prepare PortMaster ZIPs from source

### 1. Prepare your build computer

Extract `gunslugs-port-source.zip`, or clone/download this source repository. Open a terminal in the `gunslugs-port` directory containing this README and `tools/build.py`.

You need:

- Python 3.8 or newer. The examples use `python` on Windows and `python3` on Linux.
- A full JDK 8 or newer containing `bin/java`, `bin/javac` and `bin/jar`. The build has been tested with JDK 8 on Windows; a JRE alone cannot compile the source.
- The extracted **binary distribution** of [dex2jar 2.4](https://github.com/pxb1988/dex2jar/releases/tag/v2.4), with its `lib` directory, `LICENSE.txt` and `NOTICE.txt` intact.
- Your own supported Gunslugs APK. Place it outside the source directory, for example beside `gunslugs-port`.
- An internet connection for the dependency and license downloads. Java and Weston runtime images are installed separately through PortMaster on the handheld.

The supported APK is package `com.orangepixel.gunslugshandy`, version **3.2.4**, version code **52**, with SHA-256:

```text
d2c857b479a4f7a19bc59840e74bfc6350316a46f8ff579c69da281e8a2933e8
```

Other APK fingerprints are rejected because obfuscated method names can change even within the same version label. The current full-build command requires this APK even when the ZIP you intend to share will exclude game data.

### 2. Download dependencies and build

Replace the JDK and dex2jar paths below with the directories you extracted. Pass the JDK directory itself, not its `bin` directory. Keep quotes around paths containing spaces.

**Windows PowerShell**, from the source directory:

```powershell
python tools/fetch_dependencies.py
python tools/fetch_licenses.py
python tools/build.py --apk "../Gunslugs 3.2.4.apk" --jdk "C:/tools/jdk8" --dex-tools "C:/tools/dex-tools-v2.4"
python tools/verify_package.py
```

**Linux**, from the source directory:

```bash
python3 tools/fetch_dependencies.py
python3 tools/fetch_licenses.py
python3 tools/build.py --apk "../Gunslugs 3.2.4.apk" --jdk "/path/to/jdk" --dex-tools "/path/to/dex-tools-v2.4"
python3 tools/verify_package.py
```

Run each command only after the previous command succeeds. Dependency versions are specified in `tools/fetch_dependencies.py`; it verifies repository checksums and records downloaded SHA-256 values in `dependencies.lock.json`. The license tool supplies the dependency notices and matching source materials for packaging.

The builder compiles the desktop bridge and on-device preparation tools, converts your APK, extracts its assets, and writes the install ZIPs into `dist/`. No ARM cross-compiler is required: the Java code is compiled on your computer, and the downloaded runtime libraries include Linux ARM64 natives. The source archive intentionally omits the generated runtime and game data, so a fresh source checkout must complete the full build before packaging.

`verify_package.py` should print `PACKAGE_VERIFICATION_OK archives=4`. It checks ZIP integrity, launcher layout, executable permissions, required payloads and exclusion of game data from the bring-your-own-data packages. This does not replace testing the port on a handheld.

### 3. Choose the generated ZIP

| File in `dist/` | Purpose | Where to extract |
| --- | --- | --- |
| `gunslugs-byo-data.zip` | PortMaster-style package for sharing; includes the bridge and APK preparation tools, excludes game code/assets | The firmware's ports directory |
| `gunslugs-private-portmaster.zip` | Personal installation with game data prepared from your APK | The firmware's ports directory |

The builder also writes `dist/SHA256SUMS.txt` with checksums for these ZIPs. Keep the private install ZIP, APK and prepared game data private. For a public downloadable PortMaster-style release, use `gunslugs-byo-data.zip`. Generating these files does not submit the port to the PortMaster catalogue.

The standard PortMaster ZIP extracts with this layout (without an extra outer folder):

```text
Gunslugs.sh
gunslugs/
  port.json
  gameinfo.xml
  README.txt
  gunslugs.gptk
  licenses/
  runtime/lib/
  runtime/prepare/
  gamedata/
```

Install the PortMaster Java and Weston runtimes described above. With `gunslugs-byo-data.zip`, copy the supported APK into the installed `gunslugs` directory as `gunslugs.apk`, then launch to prepare the missing data. With `gunslugs-private-portmaster.zip`, the data is already prepared.

### 4. Repackage an existing build

After a successful full build, documentation, launcher, metadata or control-map changes can be packaged again without reconverting the APK:

```powershell
python tools/build.py --package-only
python tools/verify_package.py
```

Use `python3` instead on Linux. This recreates the ZIPs and checksums from the existing `package/` contents. **It does not compile Java or prepare missing data.** After changing Java source or preparation tools, rerun the full build command from step 2 before packaging. Always preserve the handheld's `gunslugs/saves/` directory when installing an update.

### How the adaptation works

The builder converts DEX to JVM bytecode, relocates the APK's libGDX JNI wrapper classes, and replaces their native entry points with calls to matching desktop libGDX natives. It retains the game's engine and logic. The bridge maps the original interfaces to desktop graphics, audio, file access, preferences and input. It selects the APK's own empty controller manager, allowing PortMaster's keyboard mapper to provide input without Android controller services.

No game code, assets, Android libraries or APK are downloaded by the tools. Decompiled game code is not part of the distributable source package.

## Validation and references

See [VALIDATION.md](VALIDATION.md) for actual checks and remaining device checks, and [runtime-reference.md](docs/runtime-reference.md) for runtime sources and implementation details.

This follows [Westonpack's LibGDX example](https://github.com/binarycounter/Westonpack/wiki/LibGDX-Example) and [muOS's PortMaster layout](https://muos.dev/installation/portmaster).

Adapter code is MIT licensed. Game content remains the property of OrangePixel. Dependency licenses and source information are in `package/gunslugs/licenses/`.
