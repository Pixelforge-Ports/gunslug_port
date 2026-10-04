# Maintainer build

Source base: https://github.com/ronaxdevil/gunslug_port at commit `78df189948136109a8cd611563246c8f09ed73b1`.

The release builder compiles the Java adapter and on-device importer without an APK. It produces exactly **`dist/gunslugs.zip`** and an unpacked `ports/gunslugs/` tree. It has no private, firmware-specific, patch, submission or source ZIP target.

Maintainers need Python 3.8+, a JDK 8+ and [dex2jar 2.4](https://github.com/pxb1988/dex2jar/releases/tag/v2.4). From the source checkout:

```sh
python tools/fetch_dependencies.py
python tools/fetch_licenses.py
python tools/build.py --jdk /path/to/jdk --dex-tools /path/to/dex-tools-v2.4
python tools/verify_portmaster.py
```

These commands compile the redistributable port only. The user installs the ZIP and supplies the APK on the handheld; `extracted.sh` performs all game-data preparation there.

The provided PortMaster reference is treated as packaging guidance. Applicable changes include the standard header, gptokeyb2 INI, metadata runtime catalog keys, paid BYO status, a testing-thread draft, and the existing genuine 640x480 screenshot, reused as the cover image. No hardware-testing or human-authorship attestation is marked complete. The newer upstream timing, display and GL4ES path are preserved rather than replacing a working game-specific graphics integration during packaging.

`package/` is the source layout. Top-level README, metadata, screenshot and cover are stored with the game by the install ZIP; `items` contains only the launcher and data directory. License texts stay flat. Third-party source archives and JSON lock files remain in the source checkout and are excluded from the install ZIP. The installed user guide is `README.md`.

Original game data, APKs, saves, logs, staging directories and host-only test dependencies are excluded. Old releases elsewhere in the workspace are not altered. No remote push or PortMaster submission is part of this build.
