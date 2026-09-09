"""Export the public PortMaster repository tree and official install ZIP.

The private build input remains in package/. Only the BYO payload is exported.
"""
from pathlib import Path
import io
import json
import re
import struct
import tarfile
import zipfile
import xml.etree.ElementTree as ET

def entry(out, name, data, executable=False):
    info=zipfile.ZipInfo(name,(2026,9,10,0,0,0));info.create_system=3
    info.external_attr=(0o100755 if executable else 0o100644)<<16
    info.compress_type=zipfile.ZIP_DEFLATED;out.writestr(info,data)

def export(root):
    root=Path(root);package=root/'package'
    meta=json.loads((package/'port.json').read_text(encoding='utf-8'))
    port=meta['name'][:-4];script=next(n for n in meta['items'] if n.endswith('.sh'))
    assert re.fullmatch(r'[a-z0-9][a-z0-9._]*',port)
    files={}
    with zipfile.ZipFile(root/'dist'/(port+'-byo-data.zip')) as source:
        for name in source.namelist():
            if name.endswith('/') or Path(name).name=='.gitkeep':continue
            assert not Path(name).is_absolute() and '..' not in Path(name).parts
            if name.startswith(port+'/') and name[len(port)+1:] in ('port.json','README.md','gameinfo.xml','screenshot.png',port+'.md'):continue
            if '/saves/' in name or '/userdata/' in name or '/cache/' in name:raise ValueError('Personal data in public payload: '+name)
            files[name]=source.read(name)
    # Archive source trees with their original paths, leaving licenses/ flat.
    nested={n:d for n,d in files.items() if n.startswith(port+'/licenses/') and '/' in n[len(port+'/licenses/'): ]}
    if nested:
        buf=io.BytesIO()
        with tarfile.open(fileobj=buf,mode='w:gz') as tar:
            for n,data in sorted(nested.items()):
                info=tarfile.TarInfo(n[len(port+'/licenses/'):]);info.size=len(data);info.mtime=0;info.mode=0o644
                tar.addfile(info,io.BytesIO(data));del files[n]
        files[port+'/licenses/component-sources.tar.gz']=buf.getvalue()
    for name in ('port.json','README.md','gameinfo.xml','screenshot.png'):
        files[name]=(package/name).read_bytes()
    assert struct.unpack('>II',files['screenshot.png'][16:24])==(640,480)
    xml=ET.fromstring(files['gameinfo.xml']);assert xml.findtext('game/path')=='./'+script
    assert xml.findtext('game/image')=='./'+port+'/screenshot.png'
    assert meta['attr']['availability']=='paid' and meta['attr']['rtr'] is False
    for name,data in files.items():
        if Path(name).suffix.lower() in ('.jar','.dat','.apk','.exe','.dll') and not name.startswith(port+'/runtime/'):raise ValueError('Unexpected game binary in public export: '+name)
        if len(data)>90*1024*1024:raise ValueError('Use upstream build_data.py for files over 90 MB: '+name)
        if name.endswith(('.sh','.gptk','.inc')) and b'\r' in data:raise ValueError('CRLF script: '+name)
    target=root/'ports'/port;target.mkdir(parents=True,exist_ok=True)
    manifest=root/'build/portmaster-export.json';manifest.parent.mkdir(exist_ok=True)
    previous=json.loads(manifest.read_text()) if manifest.exists() else []
    for name in previous:
        path=(target/name).resolve();path.relative_to(target.resolve())
        if name not in files and path.is_file():path.unlink()
    for name,data in files.items():
        dest=target/name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(data)
    manifest.write_text(json.dumps(sorted(files)),encoding='utf-8')
    with zipfile.ZipFile(root/'dist'/(port+'-portmaster-submission.zip'),'w') as out:
        for name,data in sorted(files.items()):entry(out,'ports/'+port+'/'+name,data)
    with zipfile.ZipFile(root/'dist'/(port+'.zip'),'w') as out:
        for name,data in sorted(files.items()):
            installed=name
            if '/' not in name and name!=script:installed=port+'/'+(port+'.md' if name=='README.md' else name)
            entry(out,installed,data,name.endswith('.sh'))
    print('PortMaster export:',target)

if __name__=='__main__':export(Path(__file__).resolve().parents[1])
