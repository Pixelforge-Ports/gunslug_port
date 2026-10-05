"""Verify PC import, APK/PC selection, cache recovery and preservation of saves."""
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
    parser.add_argument('--dat', type=Path, required=True, help='Locally owned supported GOG gunslugs.dat')
    parser.add_argument('--prepare-classpath', help='Compiled importer classpath; default: built preparation tools')
    args = parser.parse_args()
    java = args.jdk / 'bin' / ('java.exe' if os.name == 'nt' else 'java')
    cp = args.prepare_classpath or str(ROOT / 'build/artifacts/package/gunslugs/runtime/prepare/*')
    game = ROOT / 'build/pc-tests' / str(uuid.uuid4()) / 'gunslugs'
    data = game / 'gamedata'
    data.mkdir(parents=True)
    (game / 'saves').mkdir()
    save = game / 'saves/keep.txt'
    save.write_text('existing APK and PC saves must survive')
    apk_jar = data / 'GAME.JAR'
    with zipfile.ZipFile(apk_jar, 'w') as jar:
        for name in ('B/j.class', 'i/h.class', 'org/portmaster/gunslugs/apk/com/badlogic/gdx/utils/BufferUtils.class'):
            jar.writestr(name, b'fixture')
    logo = data / 'assets/logo.png'
    logo.parent.mkdir()
    logo.write_bytes(b'APK asset must survive PC import')
    original_apk = apk_jar.read_bytes()

    def run(*arguments, success=True):
        result = subprocess.run([str(java), '-Xmx128m', '-cp', cp, 'PrepareDevice', *map(str, arguments)],
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=90)
        assert (result.returncode == 0) == success, result.stdout
        assert save.read_text() == 'existing APK and PC saves must survive'
        return result.stdout

    assert run('--mode', data).strip() == 'apk'
    dat = data / 'GUNSLUGS.DAT'
    dat.write_bytes(b'invalid PC input')
    assert run('--mode', data).strip() == 'pc'
    run('--check', data, success=False)
    assert 'Unsupported PC fingerprint' in run(game, success=False)
    assert apk_jar.read_bytes() == original_apk and logo.read_bytes() == b'APK asset must survive PC import'
    shutil.copyfile(args.dat, dat)
    assert 'PC_PREPARATION_OK' in run(game)
    assert apk_jar.read_bytes() == original_apk and logo.read_bytes() == b'APK asset must survive PC import'
    prepared = data / 'pc/GAME.JAR'
    with zipfile.ZipFile(prepared) as jar, zipfile.ZipFile(args.dat) as source:
        names = jar.namelist()
        assert 'com/orangepixel/gunslugs/myCanvas.class' in names
        assert not any(n.startswith(('org/lwjgl/', 'com/codedisaster/')) or n.endswith(('.so', '.dll', '.dylib')) for n in names)
        assert not any(n.startswith('com/badlogic/gdx/') and not n.startswith('com/badlogic/gdx/controllers/') for n in names)
        assets = [n for n in source.namelist() if not n.endswith('/') and (n.startswith('audio/') or ('/' not in n and n.endswith('.png')))]
        for name in assets:
            assert jar.read(name) == source.read(name), name
    digest = hashlib.sha256(prepared.read_bytes()).hexdigest()
    stamp = prepared.stat().st_mtime_ns
    run('--check', data)
    assert 'PC_PREPARATION_REUSED' in run(game)
    assert prepared.stat().st_mtime_ns == stamp
    prepared.write_bytes(b'interrupted PC output')
    run('--check', data, success=False)
    assert 'PC_PREPARATION_OK' in run(game)
    assert hashlib.sha256(prepared.read_bytes()).hexdigest() == digest
    assert any((p / 'GAME.JAR').read_bytes() == b'interrupted PC output' for p in data.glob('.previous-pc-*'))
    assert not list(data.glob('.prepare-*'))
    dat.unlink()
    assert run('--mode', data).strip() == 'apk'
    run('--check', data)
    apk_jar.unlink()
    logo.unlink()
    assert run('--mode', data).strip() == 'pc'
    run('--check', data)
    assert 'PC_PREPARATION_REUSED' in run(game)
    (data / 'gunslugs.apk').write_bytes(b'new APK input')
    assert run('--mode', data).strip() == 'apk'
    assert 'Unsupported APK fingerprint' in run(game, success=False)
    assert hashlib.sha256(prepared.read_bytes()).hexdigest() == digest
    print(f'PC_IMPORT_CHECKS_OK: {len(assets)} assets matched; dual-input selection, reuse, recovery and saves verified')


if __name__ == '__main__':
    main()
