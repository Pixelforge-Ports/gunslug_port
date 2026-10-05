"""Check the only release archive, metadata, helpers and absence of game data."""
from pathlib import Path
import configparser
import hashlib
import io
import json
import struct
import zipfile
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]

def verify():
    from portmaster_package import verify_runtime_libraries
    runtime_files=verify_runtime_libraries(ROOT)
    assert [p.name for p in (ROOT/'dist').glob('*.zip')]==['gunslugs.zip'], 'Release directory must contain only gunslugs.zip'
    with zipfile.ZipFile(ROOT/'dist/gunslugs.zip') as z:
        assert z.testzip() is None
        names=z.namelist()
        assert 'gunslugs/testing_thread.txt' not in names
        assert not any(n.lower().endswith('/readme.txt') for n in names)
        assert not any(n.startswith('gunslugs/licenses/') and n.endswith(('.zip','.gz','.jar','.json')) for n in names)
        assert all(n=='Gunslugs.sh' or n.startswith('gunslugs/') for n in names)
        assert not any(n.lower().endswith(('.apk','.dat','/game.jar','/source.json','.gptk')) or '/assets/' in n
                       or '/saves/' in n or '/cache/' in n or 'natives-windows' in n for n in names)
        launcher=z.read('Gunslugs.sh')
        assert b'$GPTOKEYB2 ' in launcher and b'gunslugs.ini' in launcher
        assert b'PrepareDevice --mode "$GAMEDATADIR"' in launcher and b'org.portmaster.gunslugs.PcMain' in launcher
        assert b'gunslugs-pc.ini' in launcher and b'$GAMEDATADIR/pc/GAME.JAR' in launcher
        assert b'> "$GAMEDIR/log.txt" && exec > >(tee "$GAMEDIR/log.txt") 2>&1' in launcher
        assert b'runtime_check "${weston_runtime}.squashfs"' in launcher
        assert b'runtime_check "${java_runtime}.squashfs"' in launcher
        assert b'mount "$controlfolder/libs/${weston_runtime}.squashfs" "$weston_dir"' in launcher
        assert b'mount "$controlfolder/libs/${java_runtime}.squashfs" "$JAVA_HOME"' in launcher
        assert b'if [[ "$PM_CAN_MOUNT" != "N" ]]' in launcher
        assert b'LD_LIBRARY_PATH=$GAMEDIR/libs.${DEVICE_ARCH}:$LD_LIBRARY_PATH' in launcher
        assert b'if command -v getconf >/dev/null 2>&1; then' in launcher
        assert b'[ -z "$userland_bits" ] || [ "$userland_bits" = 64 ]' in launcher
        for name in runtime_files:
            assert z.read(name)==(ROOT/'package'/name).read_bytes(), name
        jpeg=z.read('gunslugs/libs.aarch64/libjpeg.so.8')
        assert jpeg[:6]==b'\x7fELF\x02\x01' and struct.unpack('<H',jpeg[18:20])[0]==183
        assert hashlib.sha256(jpeg).hexdigest()=='bd56375d246a0e3b8807c34dd4ed8f65bfbb6fea014220e62d2befa6f92fb9da'
        jpeg_license=z.read('gunslugs/licenses/LICENSE-libjpeg-turbo.txt')
        assert b'Independent JPEG Group' in jpeg_license and b'NO WARRANTY' in jpeg_license
        for name in names:
            if name.endswith(('.sh','.inc','.ini')):
                assert b'\r' not in z.read(name) and not z.read(name).startswith(b'\xef\xbb\xbf'),name
                if name.endswith('.sh'): assert z.getinfo(name).external_attr>>16 & 0o111,name
        for name in ('extracted.sh','display.inc'):
            assert 'gunslugs/'+name in names
        metadata=json.loads(z.read('gunslugs/port.json'))
        assert list(metadata)==['version','name','items','items_opt','attr']
        assert metadata['name']=='gunslugs.zip' and metadata['items']==['Gunslugs.sh','gunslugs']
        a=metadata['attr']
        assert list(a)==['title','porter','desc','desc_md','inst','inst_md','genres','image','rtr','exp','runtime','store','availability','reqs','arch','min_glibc']
        assert a['porter']==['Ronax']
        assert a['rtr'] is False and a['exp'] is False and a['availability']=='paid'
        assert a['arch']==['aarch64'] and a['runtime']==['weston_pkg_0.2.squashfs','zulu17.54.21-ca-jre17.0.13-linux.squashfs']
        assert all(isinstance(s,dict) and {'name','gameurl','developerurl'}<=s.keys() for s in a['store'])
        config=configparser.ConfigParser();config.read_string(z.read('gunslugs/gunslugs.ini').decode())
        assert config['controls']['a']=='x' and config['controls']['b']=='w'
        pc_config=configparser.ConfigParser();pc_config.read_string(z.read('gunslugs/gunslugs-pc.ini').decode())
        assert pc_config['controls']['a']=='x' and pc_config['controls']['b']=='up'
        info=ET.fromstring(z.read('gunslugs/gameinfo.xml'));assert info.findtext('game/path')=='./Gunslugs.sh'
        assert info.findtext('game/image')=='./gunslugs/cover.png'
        assert struct.unpack('>II',z.read('gunslugs/screenshot.png')[16:24])==(640,480)
        assert struct.unpack('>II',z.read('gunslugs/cover.png')[16:24])==(640,480)
        for name,required in [('gunslugs/runtime/lib/gunslugs-bridge.jar','org/portmaster/gunslugs/FramePacer.class'),
                              ('gunslugs/runtime/prepare/gunslugs-prepare.jar','PrepareDevice.class')]:
            with zipfile.ZipFile(io.BytesIO(z.read(name))) as jar:
                assert required in jar.namelist()
                assert ('org/portmaster/gunslugs/PcMain.class' if name.endswith('gunslugs-bridge.jar') else 'PreparePc.class') in jar.namelist()
                assert not any('Smoke' in n or 'VerifyBridge' in n for n in jar.namelist())
        readme=z.read('gunslugs/README.md').decode()
        assert 'ports/gunslugs/gamedata/' in readme, 'README must document the game-data folder'
        assert '<ports directory>/gunslugs/gamedata/' in a['inst']
        assert "The A/B assignments preserve the previous port's layout." not in readme
        assert 'AnExplorer' in readme and 'Epic Games Store' in readme and 'Internal Storage/Backup/Apps/' in readme
        assert 'gunslugs.dat' in readme and '3.3.0' in readme and 'GOG' in readme and 'gunslugs.dat' in a['inst']
        assert '## Compile' not in readme
    print('PACKAGE_VERIFICATION_OK: one universal BYO ZIP; controls, metadata, extraction tools and privacy verified')

if __name__=='__main__': verify()
