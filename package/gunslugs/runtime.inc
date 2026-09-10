# SPDX-License-Identifier: MIT
# Sourced by Gunslugs.sh. PortMaster owns firmware and driver configuration.
# shellcheck disable=SC2034,SC2154
export JAVA_HOME=/tmp/gunslugs-java
weston_dir=/tmp/gunslugs-weston
weston_started=0
gunslugs_mapper=""
gunslugs_mounts=()
gunslugs_audio_env=()
[[ -n "${XDG_RUNTIME_DIR:-}" ]] && gunslugs_audio_env=("XDG_RUNTIME_DIR=$XDG_RUNTIME_DIR")

gunslugs_fail() {
    printf 'Gunslugs: %s\n' "$*"
    if declare -F pm_message >/dev/null; then pm_message "Gunslugs: $*"; fi
    sleep 5
    exit 1
}

gunslugs_mount() {
    local runtime="$1" target="$2" probe="$3"
    [[ -x "$target/$probe" ]] && return 0
    if [[ ! -f "$controlfolder/libs/$runtime.squashfs" ]]; then
        [[ -f "$controlfolder/harbourmaster" ]] || gunslugs_fail "Update PortMaster to install $runtime."
        $ESUDO "$controlfolder/harbourmaster" --quiet --no-check runtime_check "$runtime.squashfs" || gunslugs_fail "Cannot install $runtime."
    fi
    $ESUDO mkdir -p "$target" || gunslugs_fail "Cannot create runtime mount point."
    $ESUDO mount "$controlfolder/libs/$runtime.squashfs" "$target" || gunslugs_fail "Cannot mount $runtime."
    gunslugs_mounts+=("$target")
    [[ -x "$target/$probe" ]] || gunslugs_fail "Reinstall the $runtime runtime in PortMaster."
}

# shellcheck disable=SC2329
gunslugs_cleanup() {
    local result=$? target
    trap - EXIT INT TERM
    if [[ -n "$gunslugs_mapper" ]]; then
        kill "$gunslugs_mapper" 2>/dev/null || true
        wait "$gunslugs_mapper" 2>/dev/null || true
    fi
    [[ "$weston_started" == 0 ]] || $ESUDO "$weston_dir/westonwrap.sh" cleanup
    if [[ "${PM_CAN_MOUNT:-Y}" != N ]]; then
        for target in "${gunslugs_mounts[@]}"; do $ESUDO umount "$target" || true; done
    fi
    pm_finish
    exit "$result"
}
