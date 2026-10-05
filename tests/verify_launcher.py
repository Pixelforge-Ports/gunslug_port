"""Check AmberELEC launch compatibility with mocked runtimes and no game data."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def shell_path(path):
    value = Path(path).resolve().as_posix()
    return '/' + value[0].lower() + value[2:] if os.name == 'nt' else value


def write(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open('w', encoding='utf-8', newline='\n') as stream:
        stream.write(text)


def verify(root=ROOT):
    bash = r'C:\Program Files\Git\bin\bash.exe' if os.name == 'nt' else shutil.which('bash')
    if not bash or not Path(bash).is_file():
        raise SystemExit('Bash is required for the launcher checks')
    tests = root/'build/launcher-tests'
    tests.mkdir(parents=True, exist_ok=True)
    for build in ('apk', 'pc'):
        for case in ('success', 'missing-getconf', 'failed-getconf', '32-bit', 'wrong-arch'):
            with tempfile.TemporaryDirectory(prefix=build+'-'+case+'-', dir=tests) as temporary:
                folder = Path(temporary)
                sf = shell_path(folder)
                game = folder/'ports/gunslugs'
                pm = folder/'home/.local/share/PortMaster'
                (folder/'events').touch()
                write(game/'extracted.sh', '#!/bin/bash\nexit 0\n')
                shutil.copyfile(root/'package/gunslugs/display.inc', game/'display.inc')
                write(game/'libs.aarch64/libjpeg.so.8', 'fixture only')
                for runtime in ('weston_pkg_0.2', 'zulu17.54.21-ca-jre17.0.13-linux'):
                    write(pm/'libs'/(runtime+'.squashfs'), '')
                write(pm/'control.txt', '''get_controls() { :; }
pm_message() { echo "$*"; }
pm_finish() { echo finish >> "$TEST_ROOT/events"; }
pm_platform_helper() { echo platform >> "$TEST_ROOT/events"; }
command() {
  if [[ "$TEST_CASE" == missing-getconf && "$1 $2" == '-v getconf' ]]; then return 1; fi
  builtin command "$@"
}
getconf() {
  [[ "$TEST_CASE" == failed-getconf ]] && return 127
  [[ "$TEST_CASE" == 32-bit ]] && { echo 32; return; }
  echo 64
}
sleep() { :; }
mkdir() {
  (
    cd "$TEST_ROOT" || exit 1
    local args=() arg
    for arg in "$@"; do args+=("${arg#"$TEST_ROOT/"}"); done
    /usr/bin/mkdir "${args[@]}"
  )
}
mount() {
  echo mount >> "$TEST_ROOT/events"
  mkdir -p "$2/bin"
  if [[ "$1" == *weston* ]]; then
    cp "$TEST_ROOT/probes/westonwrap.sh" "$2/westonwrap.sh"
  else
    cp "$TEST_ROOT/probes/java" "$2/bin/java"
  fi
}
umount() { echo unmount >> "$TEST_ROOT/events"; }
test_mapper() {
  [[ "$1" == java && "$2" == -c && "$3" == "$TEST_ROOT/ports/gunslugs/$TEST_CONTROLS" ]] || exit 7
  echo mapper >> "$TEST_ROOT/events"
}
GPTOKEYB2=test_mapper
ESUDO=
directory="$TEST_ROOT"
DEVICE_ARCH=aarch64
[[ "$TEST_CASE" == wrong-arch ]] && DEVICE_ARCH=armhf
PM_CAN_MOUNT=Y
CFW_NAME=AmberELEC
DISPLAY_WIDTH=720
DISPLAY_HEIGHT=480
''')
                write(folder/'probes/java', '#!/bin/bash\nprintf "%s\\n" "$TEST_BUILD"\n')
                write(folder/'probes/westonwrap.sh', '''#!/bin/bash
if [[ "$1" == cleanup ]]; then echo cleanup >> "$TEST_ROOT/events"; exit 0; fi
[[ "$1 $2 $3 $4" == 'headless noop kiosk crusty_glx_gl4es' ]] || exit 3
[[ "$LD_LIBRARY_PATH" == "$TEST_ROOT/ports/gunslugs/libs.aarch64:/original/libs" ]] || exit 4
[[ "$WESTON_HEADLESS_WIDTH" == 720 && "$WESTON_HEADLESS_HEIGHT" == 480 ]] || exit 5
[[ "${!#}" == "$TEST_MAIN" ]] || exit 6
echo game >> "$TEST_ROOT/events"
exit 0
''')
                launcher = (root/'package/Gunslugs.sh').read_text(encoding='utf-8')
                launcher = launcher.replace('weston_dir=/tmp/weston', 'weston_dir="'+sf+'/weston"')
                launcher = launcher.replace('/tmp/javaruntime/', sf+'/java/')
                launcher = launcher.replace('/opt/system/Tools/PortMaster', sf+'/absent1')
                launcher = launcher.replace('/opt/tools/PortMaster', sf+'/absent2')
                write(folder/'launcher.sh', launcher)
                env = dict(os.environ, TEST_ROOT=sf, TEST_CASE=case, TEST_BUILD=build,
                           TEST_CONTROLS='gunslugs-pc.ini' if build == 'pc' else 'gunslugs.ini',
                           TEST_MAIN='org.portmaster.gunslugs.'+('PcMain' if build == 'pc' else 'Main'))
                command = '''export PATH=/usr/bin:$PATH
export HOME="$TEST_ROOT/home" XDG_DATA_HOME="$TEST_ROOT/home/.local/share"
export LD_LIBRARY_PATH=/original/libs
chmod +x "$TEST_ROOT/probes/"*
bash "$TEST_ROOT/launcher.sh"
'''
                result = subprocess.run([bash, '-c', command], env=env, capture_output=True,
                                        text=True, timeout=20)
                events = (folder/'events').read_text(encoding='utf-8').splitlines()
                ran = case in ('success', 'missing-getconf', 'failed-getconf')
                assert result.returncode == (0 if ran else 1), (build, case, result.stdout, result.stderr)
                assert events.count('game') == int(ran), (build, case, events, result.stderr)
                assert events.count('mount') == 2*int(ran), (build, case, events)
                assert events.count('cleanup') == int(ran) and events.count('finish') == int(ran), events
                if ran:
                    assert events.count('mapper') == 1 and events.count('platform') == 1, events
                    assert events.index('game') < events.index('cleanup') < events.index('finish'), events
                else:
                    assert '64-bit' in result.stdout and not events, (build, case, result.stdout, events)
                print('LAUNCHER_OK', build, case, flush=True)


if __name__ == '__main__':
    verify()
