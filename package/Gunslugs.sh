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
[ -f "$controlfolder/mod_${CFW_NAME}.txt" ] && source "$controlfolder/mod_${CFW_NAME}.txt"
get_controls

GAMEDIR="/${directory#/}/ports/gunslugs"
GAMEDATADIR="$GAMEDIR/gamedata"
java_runtime="zulu17.54.21-ca-jre17.0.13-linux"

cd "$GAMEDIR"
> "$GAMEDIR/log.txt" && exec > >(tee "$GAMEDIR/log.txt") 2>&1

SAVEDIR="$GAMEDIR/saves/"
CACHEDIR="$GAMEDIR/cache/"
$ESUDO mkdir -p "$SAVEDIR" "$CACHEDIR"

weston_dir=/tmp/weston
$ESUDO mkdir -p "${weston_dir}"
weston_runtime="weston_pkg_0.2"
if [ ! -f "$controlfolder/libs/${weston_runtime}.squashfs" ]; then
  if [ ! -f "$controlfolder/harbourmaster" ]; then
     { pm_message "Gunslugs: This port requires the latest PortMaster to run, please go to https://portmaster.games/ for more info. See gunslugs/log.txt."; sleep 5; exit 1; }
  fi
  $ESUDO "$controlfolder/harbourmaster" --quiet --no-check runtime_check "${weston_runtime}.squashfs"
fi
if [[ "$PM_CAN_MOUNT" != "N" ]]; then
    $ESUDO umount "${weston_dir}" 2>/dev/null || true
fi
$ESUDO mount "$controlfolder/libs/${weston_runtime}.squashfs" "$weston_dir" \
  || { pm_message "Gunslugs: Cannot mount Weston. See gunslugs/log.txt."; sleep 5; exit 1; }

export JAVA_HOME="/tmp/javaruntime/"
$ESUDO mkdir -p "${JAVA_HOME}"
if [ ! -f "$controlfolder/libs/${java_runtime}.squashfs" ]; then
  if [ ! -f "$controlfolder/harbourmaster" ]; then
    { pm_message "Gunslugs: This port requires the latest PortMaster to run, please go to https://portmaster.games/ for more info. See gunslugs/log.txt."; sleep 5; exit 1; }
  fi
  $ESUDO "$controlfolder/harbourmaster" --quiet --no-check runtime_check "${java_runtime}.squashfs"
fi
if [[ "$PM_CAN_MOUNT" != "N" ]]; then
    $ESUDO umount "${JAVA_HOME}" 2>/dev/null || true
fi
$ESUDO mount "$controlfolder/libs/${java_runtime}.squashfs" "$JAVA_HOME" \
  || { pm_message "Gunslugs: Cannot mount Java. See gunslugs/log.txt."; sleep 5; exit 1; }
export PATH="$JAVA_HOME/bin:$PATH"

bash "$GAMEDIR/extracted.sh" "$GAMEDIR" "$JAVA_HOME" || { pm_message "APK preparation failed. See log.txt."; sleep 5; exit 1; }
gunslugs_audio_env=()
[[ -n "${XDG_RUNTIME_DIR:-}" ]] && gunslugs_audio_env=("XDG_RUNTIME_DIR=$XDG_RUNTIME_DIR")
source "$GAMEDIR/display.inc"
gunslugs_display_setup || { pm_message "Use auto or WIDTHxHEIGHT in resolution.txt."; sleep 5; exit 1; }
GAME_JAR="$GAMEDATADIR/GAME.JAR"
[[ -f "$GAME_JAR" ]] || GAME_JAR="$GAMEDATADIR/game.jar"
export SDL_GAMECONTROLLERCONFIG="${sdl_controllerconfig:-}"
export HOTKEY=back
$GPTOKEYB2 "java" -c "$GAMEDIR/gunslugs.ini" &
pm_platform_helper "$JAVA_HOME/bin/java"
printf 'Firmware: %s; display: %s\n' "$CFW_NAME" "$gunslugs_display_description"

$ESUDO env "${display_env[@]}" "$weston_dir/westonwrap.sh" headless noop kiosk crusty_glx_gl4es \
  "PATH=$JAVA_HOME/bin:$PATH" "JAVA_HOME=$JAVA_HOME" "HOME=$SAVEDIR" \
  "XDG_DATA_HOME=$SAVEDIR" "XDG_CONFIG_HOME=$SAVEDIR/config" \
  "XDG_CACHE_HOME=$CACHEDIR" \
  "${gunslugs_audio_env[@]}" "WAYLAND_DISPLAY=" \
  "$JAVA_HOME/bin/java" -Xms32m -Xmx256m -XX:+UseSerialGC \
  "-Duser.home=$SAVEDIR" "-Djava.io.tmpdir=$CACHEDIR" \
  "-Dgunslugs.assets=$GAMEDATADIR/assets" "-Dgunslugs.saves=$SAVEDIR" \
  -Dgunslugs.fullscreen=true -Dgunslugs.lockDisplay=true "${display_java[@]}" \
  -cp "$GAMEDIR/runtime/lib/*:$GAME_JAR" org.portmaster.gunslugs.Main

$ESUDO "$weston_dir/westonwrap.sh" cleanup
if [[ "$PM_CAN_MOUNT" != "N" ]]; then
  $ESUDO umount "${weston_dir}"
  $ESUDO umount "${JAVA_HOME}"
fi

pm_finish
