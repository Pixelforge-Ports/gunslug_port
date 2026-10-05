"""Check APK lookup and progress handling; optionally extract a locally owned APK."""
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[1]

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jdk', type=Path, required=True)
    parser.add_argument('--bash', type=Path, required=True)
    parser.add_argument('--apk', type=Path, help='Locally owned APK for the full conversion and asset comparison')
    parser.add_argument('--runtime-fixture', type=Path, help='Installed-layout fixture to test without building a ZIP')
    args = parser.parse_args()
    fixture = ROOT / 'build/extraction-tests' / str(uuid.uuid4())
    if args.runtime_fixture:
        shutil.copytree(args.runtime_fixture, fixture)
    else:
        with zipfile.ZipFile(ROOT / 'dist/gunslugs.zip') as package:
            package.extractall(fixture)
    game = fixture / 'gunslugs'
    data = game / 'gamedata'
    data.mkdir(exist_ok=True)
    (game / 'cache').mkdir()
    (game / 'saves').mkdir()
    sentinel = game / 'saves/keep.txt'
    sentinel.write_text('keep this save')
    env = dict(os.environ, PATH=str(args.bash.resolve().parent) + os.pathsep + os.environ['PATH'])
    # Capture calls to the real dialog API without requiring a handheld display.
    control = fixture / 'portmaster'
    control.mkdir()
    (control / 'PortMasterDialog.txt').write_text('''PortMasterDialogInit() { printf 'init\\n' >> "$GAMEDIR/dialog.log"; }
PortMasterDialog() { printf '%s\\t' "$@" >> "$GAMEDIR/dialog.log"; printf '\\n' >> "$GAMEDIR/dialog.log"; }
PortMasterDialogExit() { printf 'exit\\n' >> "$GAMEDIR/dialog.log"; }
''', encoding='utf-8')
    env['controlfolder'] = control.as_posix()
    dialog = game / 'dialog.log'
    command = [str(args.bash.resolve()), str(game / 'extracted.sh'), str(game), str(args.jdk.resolve())]
    def run(success, message, shows_progress=True, exit_code=None):
        dialog.unlink(missing_ok=True)
        result = subprocess.run(command, env=env, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=240)
        (fixture / ('run-' + message + '.log')).write_text(result.stdout, encoding='utf-8')
        assert (result.returncode == 0) == success, result.stdout
        if exit_code is not None: assert result.returncode == exit_code, result.stdout
        assert sentinel.read_text() == 'keep this save'
        if shows_progress:
            calls = dialog.read_text()
            assert calls.startswith('init\n') and calls.endswith('progress_clear\t\nexit\n'), calls
            assert 'progress\tPreparing game data\t0\t100\t\n' in calls, calls
        else:
            assert not dialog.exists(), 'Prepared data must skip the first-launch dialog'
        print(message + ': passed', flush=True)
        return result.stdout
    (game / 'root.apk').write_bytes(b'ignore APKs outside gamedata')
    assert 'into gunslugs/gamedata' in run(False, 'missing-apk')
    apk = data / 'Gunslugs backup.APK'
    apk.write_bytes(b'invalid')
    assert 'Unsupported APK fingerprint' in run(False, 'invalid-apk')
    assert 'progress\tVerifying APK\t100\t100\t\n' in dialog.read_text()
    other = data / 'another.apk'
    other.write_bytes(b'invalid')
    assert 'Multiple APKs' in run(False, 'ambiguous-apk')
    preferred = data / 'gunslugs.apk'
    preferred.write_bytes(b'invalid')
    assert 'Unsupported APK fingerprint' in run(False, 'preferred-apk')
    preferred.unlink()
    other.unlink()

    # Exercise the streaming dialog and exit status independently of APK conversion.
    real_command = command
    fake_jdk = fixture / 'fake-jdk'
    (fake_jdk / 'bin').mkdir(parents=True)
    fake_java = fake_jdk / 'bin/java'
    fake_java.write_text('''#!/bin/bash
[[ " $* " == *" --check "* ]] && exit 1
game_dir="${@: -1}"
printf 'GUNSLUGS_PROGRESS\\t0\\tExtracting game assets\\n'
[[ -f "$game_dir/fail-extraction" ]] && exit 7
printf 'GUNSLUGS_PROGRESS\\t100\\tGunslugs ready\\n'
exit 0
''', encoding='utf-8')
    fake_java.chmod(0o755)
    command = [str(args.bash.resolve()), str(game / 'extracted.sh'), str(game), str(fake_jdk)]
    run(True, 'progress-success')
    assert 'progress\tGunslugs ready\t100\t100\t\n' in dialog.read_text()
    failure = game / 'fail-extraction'
    failure.touch()
    run(False, 'progress-failure', exit_code=7)
    assert 'Gunslugs ready' not in dialog.read_text()
    failure.unlink()
    command = real_command

    # The importer recognizes a complete prepared layout without needing its APK.
    jar = data / 'GAME.JAR'
    with zipfile.ZipFile(jar, 'w') as prepared:
        for name in ('B/j.class', 'i/h.class', 'org/portmaster/gunslugs/apk/com/badlogic/gdx/utils/BufferUtils.class'):
            prepared.writestr(name, b'fixture')
    logo = data / 'assets/logo.png'
    logo.parent.mkdir(exist_ok=True)
    logo.write_bytes(b'fixture')
    stamp = jar.stat().st_mtime_ns
    run(True, 'prepared-shortcut', shows_progress=False)
    assert jar.stat().st_mtime_ns == stamp
    jar.unlink()
    logo.unlink()
    if args.apk is None:
        print('EXTRACTION_CHECKS_OK: gamedata lookup, progress dispatch, failure status and prepared-data reuse; full APK conversion skipped')
        return

    shutil.copyfile(args.apk, apk)
    assert 'DEVICE_PREPARATION_OK' in run(True, 'first-extraction')
    calls = dialog.read_text()
    assert 'progress\tExtracting game assets\t' in calls and 'progress\tGunslugs ready\t100\t100\t\n' in calls
    jar = data / 'GAME.JAR'
    with zipfile.ZipFile(args.apk) as original:
        assets = [n for n in original.namelist() if n.startswith('assets/') and not n.startswith('assets/dexopt/') and not n.endswith('/')]
        for name in assets:
            assert (data / name).read_bytes() == original.read(name), name
    with zipfile.ZipFile(jar) as converted:
        assert 'B/j.class' in converted.namelist()
        assert not any(n.startswith('com/badlogic/gdx/') for n in converted.namelist())
    digest = hashlib.sha256(jar.read_bytes()).hexdigest()
    stamp = jar.stat().st_mtime_ns
    apk.unlink()
    run(True, 'reuse-without-apk', shows_progress=False)
    assert jar.stat().st_mtime_ns == stamp
    jar.write_bytes(b'interrupted output')
    shutil.copyfile(args.apk, apk)
    run(True, 'recover-incomplete-output')
    assert hashlib.sha256(jar.read_bytes()).hexdigest() == digest
    assert any((p / 'GAME.JAR').read_bytes() == b'interrupted output' for p in data.glob('.previous-*'))
    assert not list(data.glob('.prepare-*'))
    assert not list(data.rglob('*.so'))
    (ROOT / 'build/extraction-fixture.txt').write_text(str(data), encoding='utf-8')
    print(f'EXTRACTION_OK: {len(assets)} assets matched; fixture: {data}')

if __name__ == '__main__':
    main()
