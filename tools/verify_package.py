"""Check install archive layout, JVM payloads and exclusion of private game data."""
from pathlib import Path
import io
import json
import zipfile

ROOT=Path(__file__).resolve().parents[1]

def verify():
    count=0
    for filename,muos,private in [('gunslugs-private-muos.zip',True,True),
                                   ('gunslugs-private-portmaster.zip',False,True),
                                   ('gunslugs-byo-data.zip',False,False),
                                   ('gunslugs-byo-data-muos.zip',True,False)]:
        with zipfile.ZipFile(ROOT/'dist'/filename) as z:
            assert z.testzip() is None, filename+' contains corrupt data'
            names=z.namelist(); prefix='ports/' if muos else ''
            script='roms/PORTS/Gunslugs.sh' if muos else 'Gunslugs.sh'
            assert script in names
            assert b'\r' not in z.read(script), 'Launcher must have LF line endings'
            assert z.getinfo(script).external_attr>>16 & 0o111, 'Launcher execute bit missing'
            assert prefix+'gunslugs/runtime/lib/gunslugs-bridge.jar' in names
            assert prefix+'gunslugs/runtime/prepare/gunslugs-prepare.jar' in names
            assert prefix+'gunslugs/display.sh' in names
            assert b'\r' not in z.read(prefix+'gunslugs/display.sh')
            assert z.getinfo(prefix+'gunslugs/display.sh').external_attr>>16 & 0o111
            assert prefix+'gunslugs/licenses/PORT-LICENSE.txt' in names
            assert prefix+'gunslugs/port.json' in names
            assert all(n == script or n.startswith(prefix+'gunslugs/') for n in names)
            assert not any('natives-windows' in n or n.endswith(('.apk','/log.txt')) or '/cache/' in n or '/saves/' in n or '/build/' in n for n in names)
            bridge=zipfile.ZipFile(io.BytesIO(z.read(prefix+'gunslugs/runtime/lib/gunslugs-bridge.jar')))
            assert 'org/portmaster/gunslugs/Main.class' in bridge.namelist()
            assert 'org/portmaster/gunslugs/DisplayLayout.class' in bridge.namelist()
            assert not any('Smoke' in n or 'VerifyBridge' in n for n in bridge.namelist())
            if private:
                assert prefix+'gunslugs/gamedata/assets/logo.png' in names
                game=zipfile.ZipFile(io.BytesIO(z.read(prefix+'gunslugs/gamedata/game.jar')))
                assert 'B/j.class' in game.namelist()
                assert not any(n.startswith('com/badlogic/gdx/') for n in game.namelist())
                json.loads(z.read(prefix+'gunslugs/gamedata/source.json'))
            else:
                assert not any('/gamedata/assets/' in n or n.endswith('/game.jar') or n.endswith('/source.json') for n in names)
            count+=1
    print('PACKAGE_VERIFICATION_OK archives='+str(count))

if __name__=='__main__': verify()
