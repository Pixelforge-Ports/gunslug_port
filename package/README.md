## Notes

Thanks to [Orangepixel](https://orangepixel.net/) for creating Gunslugs (experimental APK adaptation). Port packaging by ronaxdevil.

Place Gunslugs.sh beside gunslugs in your firmware's ports folder. On muOS put gunslugs in /ports and the launcher in /roms/PORTS. Copy your supported APK to gunslugs/gunslugs.apk for first-launch preparation, or use your privately prepared data. Requires 64-bit ARM Linux, compatible graphics, PortMaster, Westonpack and Java 17. Screen size is detected automatically; resolution.txt can override it. No original game content is distributed.

Experimental ARM64 port. Physical handheld testing remains pending. The screenshot is a 640x480 desktop gameplay capture with the port's display scaling.

## Controls

The editable `gunslugs/gunslugs.gptk` maps the device to the game's built-in keyboard controls. A fires/confirms, B jumps, D-pad/left stick moves, Start confirms, and Select goes back/pauses. Select + Start uses PortMaster's exit chord. See the packaged README for the complete mapping. Game options can change the keyboard bindings; resetting them restores the expected defaults.

## Compile

Build the adaptation from its source checkout using `python tools/build.py` with the owner-input and JDK arguments documented in the source README. Then run `python tools/build.py --package-only` to export this public repository layout. Original commercial game files are not included.
