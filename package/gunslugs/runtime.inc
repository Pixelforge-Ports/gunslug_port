# SPDX-License-Identifier: MIT
# Sourced by Gunslugs.sh. PortMaster owns firmware and driver configuration.
# shellcheck disable=SC2034,SC2154
weston_started=0
gunslugs_mapper=""
gunslugs_audio_env=()
[[ -n "${XDG_RUNTIME_DIR:-}" ]] && gunslugs_audio_env=("XDG_RUNTIME_DIR=$XDG_RUNTIME_DIR")

gunslugs_fail() {
    printf 'Gunslugs: %s\n' "$*"
    if declare -F pm_message >/dev/null; then pm_message "Gunslugs: $*"; fi
    sleep 5
    gunslugs_cleanup
    exit 1
}

gunslugs_cleanup() {
    if [[ -n "$gunslugs_mapper" ]]; then
        kill "$gunslugs_mapper" 2>/dev/null || true
        wait "$gunslugs_mapper" 2>/dev/null || true
    fi
    if [[ "$weston_started" != 0 ]]; then
        $ESUDO "$weston_dir/westonwrap.sh" cleanup || true
    fi
    if [[ "$PM_CAN_MOUNT" != "N" ]]; then
        $ESUDO umount "${weston_dir}" 2>/dev/null || true
        $ESUDO umount "${JAVA_HOME}" 2>/dev/null || true
    fi
    pm_finish
}
