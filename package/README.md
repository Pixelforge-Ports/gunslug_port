## Notes
Thanks to [Orangepixel](https://orangepixel.net/) for creating Gunslugs, a pixel-art action game with chaotic shootouts and destructible scenery. PortMaster adaptation by **Ronax**.

## Get the APK
On your Android device, buy and install **Gunslugs** from the [Epic Games Store mobile app](https://store.epicgames.com/mobile/android). This port requires **Gunslugs 3.2.4**. Check the installed version before backing it up. [Gunslugs store page](https://store.epicgames.com/p/gunslugs-2b6459).
Back up the installed APK with [AnExplorer](https://anexplorer.io/solve/backup-apps-apk):

1. Open **AnExplorer**.
2. On the Home screen, tap **Apps**. This shows all installed applications on your device.
3. Find **Gunslugs** by scrolling the list or using the search bar. Long-press the app icon or name.
4. Tap **Backup** from the context menu.
5. AnExplorer saves the APK to **`Internal Storage/Backup/Apps/[AppName].apk`**. Or **`Internal Storage/Download/AnExplore/backup/[AppName].apk`**

## First launch

Copy the backed-up APK into the installed **`ports/gunslugs/gamedata/`** folder and launch **Gunslugs** from Ports. You may keep its filename, or rename it to `gunslugs.apk`. If several APKs are present, name the intended one `gunslugs.apk`.

First launch prepares **`gamedata/GAME.JAR`** and **`gamedata/assets/`** on the handheld. Allow several minutes and do not power off during preparation.

The adapter supports version code **52**, with APK SHA-256 `d2c857b479a4f7a19bc59840e74bfc6350316a46f8ff579c69da281e8a2933e8`.
Saves remain in **`gunslugs/saves/`**; preserve that folder when updating.

Screen size is detected automatically. To override incorrect firmware detection, put one line such as `640x480`, `720x480`, `720x720`, or `1280x720` in **`gunslugs/resolution.txt`**. Use `auto` to restore detection. The port preserves the game view without stretching or cropping; square and 4:3 screens show borders.

## Controls

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
