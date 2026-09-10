#!/bin/bash

XDG_DATA_HOME=${XDG_DATA_HOME:-$HOME/.local/share}

if [ -d "/opt/system/Tools/PortMaster/" ]; then
  controlfolder="/opt/system/Tools/PortMaster"
elif [ -d "/opt/tools/PortMaster/" ]; then
  controlfolder="/opt/tools/PortMaster"
elif [ -d "$XDG_DATA_HOME/PortMaster/" ]; then
  controlfolder="$XDG_DATA_HOME/PortMaster"
else
  controlfolder="/roms/ports/PortMaster"
fi

source "$controlfolder/control.txt"
get_controls

GAMEDIR="${GUNSLUGS_DATA_DIR:-/${directory#/}/ports/gunslugs}"
[[ -d "$GAMEDIR" ]] || GAMEDIR="$(dirname -- "$(readlink -f -- "$0")")/gunslugs"
cd "$GAMEDIR" || exit 1
mkdir -p saves cache
exec > >(tee "$GAMEDIR/log.txt") 2>&1
# shellcheck source=gunslugs/runtime.inc
source "$GAMEDIR/runtime.inc"
trap gunslugs_cleanup EXIT
trap 'exit 130' INT
trap 'exit 0' TERM
[[ "${DEVICE_ARCH:-$(uname -m)}" == aarch64 ]] || gunslugs_fail "64-bit ARM firmware is required."
[[ -n "${GPTOKEYB2:-}" ]] || gunslugs_fail "Update PortMaster: gptokeyb2 is required."
gunslugs_mount zulu17.54.21-ca-jre17.0.13-linux "$JAVA_HOME" bin/java
bash "$GAMEDIR/extracted.sh" "$GAMEDIR" "$JAVA_HOME" || gunslugs_fail "APK preparation failed. See log.txt."
gunslugs_mount weston_pkg_0.2 "$weston_dir" westonwrap.sh
# shellcheck source=gunslugs/display.inc
source "$GAMEDIR/display.inc"
gunslugs_display_setup || gunslugs_fail "Use auto or WIDTHxHEIGHT in resolution.txt."
GAME_JAR="$GAMEDIR/gamedata/GAME.JAR"
[[ -f "$GAME_JAR" ]] || GAME_JAR="$GAMEDIR/gamedata/game.jar"
export SDL_GAMECONTROLLERCONFIG="${sdl_controllerconfig:-}"
export HOTKEY=back
$GPTOKEYB2 "java" -c "$GAMEDIR/gunslugs.ini" &
gunslugs_mapper=$!
pm_platform_helper "$JAVA_HOME/bin/java"
printf 'Gunslugs 0.4.0 | %s | %s\n' "${CFW_NAME:-unknown}" "$gunslugs_display_description"
weston_started=1
$ESUDO env "${display_env[@]}" "$weston_dir/westonwrap.sh" headless noop kiosk crusty_glx_gl4es \
  "JAVA_HOME=$JAVA_HOME" "HOME=$GAMEDIR/saves" "XDG_DATA_HOME=$GAMEDIR/saves" \
  "XDG_CONFIG_HOME=$GAMEDIR/saves/config" "XDG_CACHE_HOME=$GAMEDIR/cache" \
  "${gunslugs_audio_env[@]}" "WAYLAND_DISPLAY=" \
  "$JAVA_HOME/bin/java" -Xms32m -Xmx256m -XX:+UseSerialGC \
  "-Duser.home=$GAMEDIR/saves" "-Djava.io.tmpdir=$GAMEDIR/cache" \
  "-Dgunslugs.assets=$GAMEDIR/gamedata/assets" "-Dgunslugs.saves=$GAMEDIR/saves" \
  -Dgunslugs.fullscreen=true -Dgunslugs.lockDisplay=true "${display_java[@]}" \
  -cp "$GAMEDIR/runtime/lib/*:$GAME_JAR" org.portmaster.gunslugs.Main
status=$?
[[ "$status" == 143 ]] && status=0 # Select + Start stops Java with SIGTERM.
[[ "$status" == 0 ]] || gunslugs_fail "Game exited with status $status. See log.txt."
exit "$status"
