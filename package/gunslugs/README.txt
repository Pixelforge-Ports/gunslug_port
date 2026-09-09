GUNSLUGS — EXPERIMENTAL PORTMASTER ADAPTATION

This is a work-in-progress Android/libGDX adaptation. Hardware gameplay testing
is pending. It is intended for AArch64 firmware with current PortMaster,
including muOS on an RG34XX SP and ArkOS/dArkOS on R36S. These are targets,
not claims of working device compatibility. Original 32-bit RG35XX firmware
and other 32-bit userlands are outside this package's scope.

INSTALL
1. Use the project's PC build/preparation tools with your own compatible APK.
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

An APK alone does not run from this folder. The PC preparation step creates
gamedata/game.jar and gamedata/assets, and the build supplies runtime/lib/*.jar.
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
