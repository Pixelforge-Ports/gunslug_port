"""Run desktop gameplay at the five handheld resolutions (requires a display/GL)."""
import argparse
import os
from pathlib import Path
import struct
import subprocess

ROOT = Path(__file__).resolve().parents[1]
SIZES = [(640, 480), (720, 480), (720, 720), (1024, 768), (1280, 720)]

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jdk', type=Path, required=True)
    parser.add_argument('--gamedata', type=Path, required=True, help='Private extraction test fixture')
    args = parser.parse_args()
    suffix = '.exe' if os.name == 'nt' else ''
    jdk = args.jdk.resolve()
    lib = ROOT / 'build/artifacts/package/gunslugs/runtime/lib'
    classes = ROOT / 'build/test-classes'
    classes.mkdir(parents=True, exist_ok=True)
    subprocess.run([str(jdk/'bin'/('javac'+suffix)), '-encoding', 'UTF-8',
                    '-cp', str(lib/'*'), '-d', str(classes),
                    str(ROOT/'tests/GameplaySmoke.java')], check=True)
    classpath = os.pathsep.join([str(ROOT/'build/windows-libs'/'*')] if os.name == 'nt' else [])
    classpath = os.pathsep.join(filter(None, [classpath, str(classes), str(lib/'*'),
                              str(args.gamedata.resolve()/'GAME.JAR')]))
    for width, height in SIZES:
        output = ROOT/'build/resolutions'/f'{width}x{height}'
        output.mkdir(parents=True, exist_ok=True)
        command = [str(jdk/'bin'/('java'+suffix)), f'-Dgunslugs.width={width}',
                   f'-Dgunslugs.height={height}', '-Dgunslugs.lockDisplay=true',
                   f'-Dgunslugs.assets={args.gamedata.resolve() / "assets"}',
                   f'-Dgunslugs.saves={output / "saves"}', f'-Dgunslugs.testOutput={output}',
                   '-cp', classpath, 'org.portmaster.gunslugs.GameplaySmoke']
        print(f'Checking {width}x{height}', flush=True)
        with (output/'run.log').open('w', encoding='utf-8') as log:
            subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, check=True, timeout=180)
        log = (output/'run.log').read_text(encoding='utf-8')
        assert 'GAMEPLAY_DRIVER_OK' in log, log
        assert f'GAME_RESIZE_OK {width}x{height}' in log, log
        for frame in (95, 170, 250, 400, 510, 550):
            png = (output/f'gameplay-{frame}.png').read_bytes()
            assert png[:8] == b'\x89PNG\r\n\x1a\n'
            assert struct.unpack('>II', png[16:24]) == (width, height)
        print(f'RESOLUTION_OK {width}x{height}: 560 frames, movement/jump/fire, six captures', flush=True)
    print('RESOLUTION_MATRIX_OK (desktop validation; inspect captures, then test hardware)')

if __name__ == '__main__':
    main()
