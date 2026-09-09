"""Check the public PortMaster tree and both exported archive layouts."""
from pathlib import Path
import hashlib
import json
import struct
import zipfile
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
meta=json.loads((ROOT/'package/port.json').read_text(encoding='utf-8'))
port=meta['name'][:-4];tree=ROOT/'ports'/port
script=next(n for n in meta['items'] if n.endswith('.sh'))
assert meta['items']==[script,port]
assert meta['attr']['porter']==['ronaxdevil']
assert meta['attr']['runtime']==['weston_pkg_0.2.squashfs','zulu17.54.21-ca-jre17.0.13-linux.squashfs']
assert meta['attr']['availability']=='paid' and meta['attr']['rtr'] is False
assert meta['attr']['arch']==['aarch64']
assert {p.name for p in tree.iterdir()}=={'README.md','screenshot.png','gameinfo.xml','port.json',script,port}
assert struct.unpack('>II',(tree/'screenshot.png').read_bytes()[16:24])==(640,480)
xml=ET.parse(tree/'gameinfo.xml')
assert xml.findtext('game/path')=='./'+script
assert xml.findtext('game/image')=='./'+port+'/screenshot.png'
assert xml.findtext('game/name')==meta['attr']['title']
assert not any(p.is_dir() for p in (tree/port/'licenses').iterdir())
for p in tree.rglob('*'):
    if not p.is_file():continue
    assert p.name!='.gitkeep' and p.stat().st_size<=90*1024*1024
    assert not any(x in ('saves','userdata','cache','.git','__pycache__') for x in p.relative_to(tree).parts)
    if p.suffix in ('.sh','.gptk','.inc'):assert b'\r' not in p.read_bytes(),p
    if p.suffix in ('.jar','.dat','.apk','.exe','.dll'):assert 'runtime' in p.relative_to(tree).parts,p
launch=(tree/script).read_text(encoding='utf-8')
assert 'get_controls' in launch and 'pm_platform_helper' in launch and 'pm_finish' in launch
assert 'mapper_pid' not in launch and '\ntrap ' not in launch
manifest=json.loads((ROOT/'build/portmaster-export.json').read_text())
for suffix,submission in [('.zip',False),('-portmaster-submission.zip',True)]:
    with zipfile.ZipFile(ROOT/'dist'/(port+suffix)) as z:
        assert z.testzip() is None
        names=z.namelist();assert len(names)==len(set(names))==len(manifest)
        for name in manifest:
            target='ports/'+port+'/'+name if submission else name
            if not submission and '/' not in name and name!=script:target=port+'/'+(port+'.md' if name=='README.md' else name)
            assert z.read(target)==(tree/name).read_bytes(),target
            if submission:assert (z.getinfo(target).external_attr>>16)&0o777==0o644
for line in (ROOT/'dist/SHA256SUMS.txt').read_text().splitlines():
    sha,name=line.split('  ',1);assert hashlib.sha256((ROOT/'dist'/name).read_bytes()).hexdigest()==sha,name
print('PORTMASTER_PACKAGE_OK',port)
