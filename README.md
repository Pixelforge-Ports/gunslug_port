## Notes

Thanks to [Orangepixel](https://orangepixel.net/) for creating Gunslugs, a pixel-art action game with chaotic shootouts and destructible scenery. PortMaster adaptation by **Ronax**.

This is a universal BYO-data PortMaster package for compatible **64-bit ARM Linux handhelds**. Install `gunslugs.zip` through PortMaster's `autoinstall` folder. PortMaster installs the same ZIP using your firmware's folder layout and provides the Java 17 and Westonpack runtimes. Keep PortMaster updated for gptokeyb2 support.

## Get the PC data

The supported Windows build is **GOG Gunslugs 3.3.0**, GOG build ID **58441296934612083**. Buy or download [Gunslugs from your GOG library](https://www.gog.com/en/game/gunslugs), download the Windows offline backup installer and install it on a PC. Copy **`gunslugs.dat`** from its installation folder to **`<ports directory>/gunslugs/gamedata/gunslugs.dat`**. The Windows EXE, bundled JRE and other installation files are not needed on the handheld.

Supported DAT SHA-256: `d4492bd452c0e81e8ac1696d9c0e439b0a074489555b764298e1e2381343af8a`. The importer rejects other PC builds before preparing data. Only this supplied GOG build has been checked.

## Get the APK

On your Android device, buy and install **Gunslugs** from the [Epic Games Store mobile app](https://store.epicgames.com/mobile/android). This port requires **Gunslugs 3.2.4**. Check the installed version before backing it up. [Gunslugs store page](https://store.epicgames.com/p/gunslugs-2b6459).

Back up the installed APK with [AnExplorer](https://anexplorer.io/solve/backup-apps-apk):

1. Open **AnExplorer**.
2. On the Home screen, tap **Apps**. This shows all installed applications on your device.
3. Find **Gunslugs** by scrolling the list or using the search bar.
4. Long-press the app icon or name.
5. Tap **Backup** from the context menu.
6. AnExplorer saves the APK to **`Internal Storage/Backup/Apps/[AppName].apk`**.
7. A confirmation appears showing the file was saved successfully.

## First launch

The launcher detects game data inside **`ports/gunslugs/gamedata/`**. Supply either the supported PC **`gunslugs.dat`** or the supported Android APK. If both are present, **`gunslugs.dat` takes priority**.

PC first launch prepares **`gamedata/pc/GAME.JAR`**, containing the game code and assets, and keeps the original DAT. It replaces the Windows graphics and audio backend with the port's ARM Linux runtime. The PC version retains its original **30 FPS** cap. APK preparation continues to create **`gamedata/GAME.JAR`** and **`gamedata/assets/`**, retaining the original Android timing. Preparation runs entirely on the handheld using Java 17; no PC conversion is needed. A progress bar reports preparation, and later launches reuse the prepared data.

APK saves remain in **`gunslugs/saves/`**. PC saves use **`gunslugs/saves/pc/`** to keep both builds separate. Preserve the whole saves folder when updating. To return to the APK version, remove `gunslugs.dat` and supply the APK or keep its previously prepared `GAME.JAR` and `assets/`. Prepared PC data can run without the DAT when no APK or prepared APK data is present. To force a fresh PC import, remove only `gamedata/pc/`, retain `gunslugs.dat`, and launch again.

Copy the backed-up APK into the installed **`ports/gunslugs/gamedata/`** folder and launch **Gunslugs** from Ports. You may keep its filename, or rename it to `gunslugs.apk`. If several APKs are present, name the intended one `gunslugs.apk`.

The included **`extracted.sh`** automatically converts the APK into **`gamedata/GAME.JAR`** and extracts the assets into **`gamedata/assets/`** on the handheld. A PortMaster progress bar shows the current preparation phase and advances as assets are extracted. Allow several minutes and do not power off during preparation. Later launches reuse the prepared files. The original APK is retained and may be removed after a successful launch. Existing working data from earlier port versions is also reused.

The adapter supports version code **52**, with APK SHA-256 `d2c857b479a4f7a19bc59840e74bfc6350316a46f8ff579c69da281e8a2933e8`. The importer checks this fingerprint before conversion; another build labeled 3.2.4 can have incompatible code. If import fails, keep **`gunslugs/log.txt`** for diagnosis and retry after correcting the APK. Incomplete data from an interrupted import is backed up automatically before replacement. Saves remain in **`gunslugs/saves/`**; preserve that folder when updating.

Screen size is detected automatically. To override incorrect firmware detection, put one line such as `640x480`, `720x480`, `720x720`, or `1280x720` in **`gunslugs/resolution.txt`**. Use `auto` to restore detection. The port preserves the game view without stretching or cropping; square and 4:3 screens show borders. Universal packaging does not add support for 32-bit firmware.

## Controls

### APK controls

Bindings are editable in **`gunslugs/gunslugs.ini`**.

| Button | Action |
|--|--|
| D-pad / left stick | Move; Up also jumps |
| A | Fire / confirm |
| B | Jump |
| X | Weapon swap |
| Y | Special ability |
| L1 / L2 | Weapon swap |
| R1 | Options |
| R2 | Fire |
| Start | Confirm |
| Select | Back / pause |
| Select + Start | Exit through PortMaster |

If firmware swaps the physical labels, edit the INI to match. Reset the game's keyboard settings if you changed its default bindings.

### PC controls

The PC version uses **`gunslugs/gunslugs-pc.ini`** and its original keyboard input. Its game does not implement the APK's weapon-swap and special-ability shortcuts.

| Button | PC action |
|--|--|
| D-pad / left stick | Move / menu navigation; Up jumps |
| A / X / R2 | Fire / confirm |
| B / L2 | Jump |
| Y / R1 | Options |
| Start | Confirm |
| Select / L1 | Back / pause |
| Select + Start | Exit through PortMaster |

Keep the PC game's default keyboard bindings. Its defaults use arrow keys for movement, X for fire/confirm, Escape for back and O for options. The APK continues to use `gunslugs.ini` and the table above.

## Build the PortMaster ZIP

Install Python, a JDK (Java 17 or newer recommended), and dex2jar 2.4. `--dex-tools` must point to the extracted dex2jar **root folder** containing `lib/`, `LICENSE.txt` and `NOTICE.txt`.

```powershell
python tools/fetch_dependencies.py
python tools/fetch_licenses.py
python tools/build.py --jdk "C:\Program Files\Java\jdk-26.0.2.1" --dex-tools "C:\path\to\dex-tools-v2.4"
python tools/verify_portmaster.py
```

Builds compile both adapters and the importer without an APK or DAT. The result is one universal BYO-data `dist/gunslugs.zip` and the unpacked `ports/gunslugs/` folder. Generated runtime JARs live in `build/artifacts/package/`; the build does not change source files in `package/`. The same ZIP supports both game versions; users add their owned data after installing it.
