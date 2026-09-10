"""Exercise the shipped BYO importer with a locally owned APK; never package data."""
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
    parser.add_argument('--apk', type=Path, required=True)
    args = parser.parse_args()
    fixture = ROOT / 'build/extraction-tests' / str(uuid.uuid4())
    with zipfile.ZipFile(ROOT / 'dist/gunslugs.zip') as package:
        package.extractall(fixture)
    game = fixture / 'gunslugs'
    (game / 'cache').mkdir()
    (game / 'saves').mkdir()
    sentinel = game / 'saves/keep.txt'
    sentinel.write_text('keep this save')
    env = dict(os.environ, PATH=str(args.bash.resolve().parent) + os.pathsep + os.environ['PATH'])
    command = [str(args.bash.resolve()), str(game / 'extracted.sh'), str(game), str(args.jdk.resolve())]
    def run(success, message):
        result = subprocess.run(command, env=env, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=240)
        (fixture / ('run-' + message + '.log')).write_text(result.stdout, encoding='utf-8')
        assert (result.returncode == 0) == success, result.stdout
        assert sentinel.read_text() == 'keep this save'
        print(message + ': passed', flush=True)
        return result.stdout
    assert 'Copy your backed-up' in run(False, 'missing-apk')
    apk = game / 'Gunslugs backup.APK'
    apk.write_bytes(b'invalid')
    assert 'Unsupported APK fingerprint' in run(False, 'invalid-apk')
    other = game / 'another.apk'
    other.write_bytes(b'invalid')
    assert 'Multiple APKs' in run(False, 'ambiguous-apk')
    other.unlink()
    shutil.copyfile(args.apk, apk)
    assert 'DEVICE_PREPARATION_OK' in run(True, 'first-extraction')
    data = game / 'gamedata'
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
    run(True, 'reuse-without-apk')
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
