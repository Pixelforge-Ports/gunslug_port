"""Create the PortMaster source layout and ONE universal BYO install archive."""
from pathlib import Path
import hashlib
import json
import zipfile

def entry(out,name,data):
    info=zipfile.ZipInfo(name,(2026,9,10,0,0,0)); info.create_system=3
    info.external_attr=(0o100755 if name.endswith('.sh') else 0o100644)<<16
    info.compress_type=zipfile.ZIP_DEFLATED; out.writestr(info,data)

def export(root):
    root=Path(root); package=root/'package'; files={}
    meta=json.loads((package/'port.json').read_text(encoding='utf-8'))
    assert meta['name']=='gunslugs.zip' and meta['items']==['Gunslugs.sh','gunslugs']
    for file in sorted(package.rglob('*')):
        if not file.is_file(): continue
        name=file.relative_to(package).as_posix()
        parts=file.relative_to(package).parts
        if any(p in ('saves','cache','logs','userdata','__pycache__') or p.startswith(('.prepare-','.previous-')) for p in parts): continue
        if name.startswith('gunslugs/gamedata/'): continue
        if file.name.lower()=='readme.txt': continue
        if name.startswith('gunslugs/licenses/') and (len(parts)>3 or file.suffix.lower() in ('.zip','.gz','.jar','.json')): continue
        if file.suffix.lower() in ('.apk','.gptk','.log','.pyc') or file.name in ('log.txt','.gitkeep'): continue
        if 'natives-windows' in name: continue
        files[name]=file.read_bytes()
    required=['Gunslugs.sh','README.md','port.json','gameinfo.xml','screenshot.png','testing_thread.txt',
              'gunslugs/extracted.sh','gunslugs/runtime.inc','gunslugs/display.inc','gunslugs/gunslugs.ini',
              'gunslugs/runtime/lib/gunslugs-bridge.jar','gunslugs/runtime/prepare/gunslugs-prepare.jar']
    for name in required:
        if name not in files: raise ValueError('Missing package file: '+name)
    target=root/'ports/gunslugs';target.mkdir(parents=True,exist_ok=True)
    manifest=root/'build/portmaster-export.json';manifest.parent.mkdir(exist_ok=True)
    previous=json.loads(manifest.read_text()) if manifest.exists() else []
    for name in previous:
        path=(target/name).resolve();path.relative_to(target.resolve())
        if name not in files and path.is_file(): path.unlink()
    for name,data in files.items():
        path=target/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
    manifest.write_text(json.dumps(sorted(files))+'\n',encoding='utf-8')
    dist=root/'dist';dist.mkdir(exist_ok=True)
    archive=dist/'gunslugs.zip'
    with zipfile.ZipFile(archive,'w') as out:
        for name,data in sorted(files.items()):
            installed=name
            if '/' not in name and name!='Gunslugs.sh':
                installed='gunslugs/'+('gunslugs.md' if name=='README.md' else name)
            entry(out,installed,data)
    (dist/'SHA256SUMS.txt').write_text(hashlib.sha256(archive.read_bytes()).hexdigest()+'  gunslugs.zip\n')
    print('Built universal BYO-data archive:',archive)

if __name__=='__main__': export(Path(__file__).resolve().parents[1])
