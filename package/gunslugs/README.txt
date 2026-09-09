GUNSLUGS — EXPERIMENTAL PORTMASTER ADAPTATION

This is a work-in-progress Android/libGDX adaptation. Hardware gameplay testing
is pending for the new display handling. It targets compatible AArch64 firmware
with PortMaster on Anbernic, R36S, TrimUI and other ARM64 Linux handhelds.
The user reports boot/gameplay on RG34XX SP/muOS; other models need testing.
Original 32-bit RG35XX firmware
and other 32-bit userlands are outside this package's scope.

INSTALL
1. Use prepared private data, or copy your supported APK as gunslugs/gunslugs.apk.
   The new launcher prepares missing game data on first launch using Java.
2. For muOS (including RG34XX SP), extract the muOS package into the SD card
   root: data goes in /ports/gunslugs and the menu script in
   /roms/PORTS/Gunslugs.sh. The launcher prefers data on its own SD card.
   For ArkOS/dArkOS, put Gunslugs.sh and gunslugs beside each other in the
   firmware's ports folder. Keep all names and the complete data tree.
3. Open PortMaster and allow it to install/update these runtimes:
     weston_pkg_0.2.squashfs (Westonpack 0.2.5 or newer)
     zulu17.54.21-ca-jre17.0.13-linux.squashfs
4. Launch Gunslugs from Ports. A missing runtime is requested on first launch.
   Network access is required only if the runtime has not yet been installed.

First-launch preparation creates gamedata/game.jar and gamedata/assets.
It requires the exact supported APK fingerprint from the project README.
Allow several minutes; do not power off. The original APK is retained.
The package supplies runtime/lib and runtime/prepare; no device compiler is needed.
The Android libgdx.so files are not Linux desktop native libraries.

CONTROLS (MOVEMENT/JUMP/FIRE CHECKED ON PC; DEVICE TESTING PENDING)
D-pad / left stick: move (W/A/S/D)
A / R2: fire / confirm (X)
B: jump (W), also available on D-pad Up
X / L1 / L2: Weapon Swap (Tab), the game's named default binding
Y: Special Ability (Space), the game's named default binding
R1: Options (O)
Start: menu confirmation (Enter)
Select: Escape
Select + Start: PortMaster exit combination

Physical A/B labels can differ by firmware. Edit gunslugs.gptk if required.
Weapon Swap and Special Ability follow the game's default control table;
their behavior has not yet been exercised. Handheld input still needs testing.

SAVES AND DIAGNOSTICS
Saves and Java's home directory are kept in gunslugs/saves.
Temporary native libraries and caches are kept in gunslugs/cache.
The latest launch output is gunslugs/log.txt. Copy this log before relaunching
if the game fails. Include the device model, firmware name/version, whether
video/audio appeared, and the exact step at which it failed.
Do not delete the saves folder during an update.

No original game code or assets are licensed by this port. Only use data from
your own copy; prepared game.jar and assets remain private game data.

SPEED FIX (0.2.0)
The bridge restores the Android minimum 24 ms update interval (about 41.7
updates/second), independent of vsync. Existing game data can be reused.
Actual speed and APK preparation memory/time need handheld verification.

DISPLAY SUPPORT (0.3.0)
Automatic sizing covers 640x480, 720x480, 720x720, 1024x768 and 1280x720,
and uses the same fitting logic for other screen sizes. Square and 4:3 screens
fit the minimum 3:2 game view with black bars; widescreen expands the view.
This preserves the game's tutorial text/HUD without stretching or cropping.
The original desktop resolution settings cannot override the handheld mode.
If detection is wrong, create resolution.txt in this directory containing
one line such as 720x720. Use auto or remove the file to restore detection.
The requested size and actual game viewport are recorded in log.txt.
The port still requires 64-bit ARM userland, compatible GPU drivers, glibc
2.27 or newer for its native libraries, Java 17 and Westonpack. It cannot run
on every ARM Linux device solely because the screen resolution is listed.
