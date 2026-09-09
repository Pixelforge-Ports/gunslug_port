# PortMaster packaging

The generated `ports/gunslugs/` is the public repository export. It includes the host and redistributable dependencies, excludes owner game data and saves, and uses flat license storage. Component source directories are preserved in a source archive in `licenses/`. The metadata credits ronaxdevil and the game developer; `availability` is `paid`, `rtr` is false, and `exp` is true.

Java and Weston runtime names match the official registry. ARM64 remains explicit because these hosts require ARM64 native libraries; Java alone does not make this payload architecture independent. The gameplay screenshot came from the earlier desktop test and preserves the actual 4:3 display borders.

## Build and check

Build the host as explained in the source README, then run:

```sh
python tools/build.py --package-only
python tools/verify_package.py
python tools/verify_portmaster.py
bash tests/verify_display.sh
```

Do not place proprietary game files in the generated public tree. Regenerate exports after changing launcher, metadata, licenses or screenshots in `package/`. Keep compiled build output and private packages out of your source repository.

## Local output only

No upload, fork, commit or pull request is performed. The public install ZIP goes into the firmware's ports folder. The `-portmaster-submission.zip` is a local archive of the repository layout; its name does not imply it was submitted anywhere. The ordinary source ZIP is for the adaptation source repository. Private ZIPs contain owner game data and remain for personal use.

The upstream checker is `python3 tools/build_release.py --do-check` in a prepared PortMaster-New checkout. The local export checker covers the file layout without downloading that repository. These packages remain experimental until physical device testing is completed.

## Device validation still required

| Firmware | Physical test status |
|---|---|
| Rocknix | Not tested |
| muOS | Not tested in this packaging pass |
| dArkOS | Not tested |
| Knulli | Not tested |
| AmberELEC | Not tested |
| ArkOS | Not tested |

Test H700, RK3326, RK3566 and other intended CPUs with their actual graphics drivers. Cover launch, controls, audio, save/restart, exit cleanup and sleep/resume. Prior desktop resolution results are recorded separately in VALIDATION.md.

References: [packaging guide](https://portmaster.games/packaging.html), [PortMaster-New](https://github.com/PortsMaster/PortMaster-New), [sparse checkout guide](https://gist.github.com/JeodC/7a51211ad94ad6084d14042d80a62549).
