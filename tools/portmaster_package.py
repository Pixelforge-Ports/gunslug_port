"""Create the PortMaster source layout and ONE universal BYO install archive."""
from pathlib import Path
from pathlib import PurePosixPath
import json
import os
import stat
import zipfile

TEXT_SUFFIXES={'.sh','.inc','.ini','.md','.json','.xml','.txt'}

def entry(out,name,data):
    info=zipfile.ZipInfo(name,(2026,9,10,0,0,0)); info.create_system=3
    info.external_attr=(0o100755 if name.endswith('.sh') else 0o100644)<<16
    info.compress_type=zipfile.ZIP_DEFLATED; out.writestr(info,data)

def export(root, generated_artifacts=None):
    root=Path(root); package=root/'package'; files={}
    meta=json.loads((package/'port.json').read_text(encoding='utf-8'))
    assert meta['name']=='gunslugs.zip' and meta['items']==['Gunslugs.sh','gunslugs']
    for file in sorted(package.rglob('*')):
        if not file.is_file(): continue
        name=file.relative_to(package).as_posix()
        parts=file.relative_to(package).parts
        if any(p in ('saves','cache','logs','userdata','__pycache__') or p.startswith(('.prepare-','.previous-')) for p in parts): continue
        if name.startswith('gunslugs/gamedata/') and name!='gunslugs/gamedata/PLACE_GAMEDATA_HERE.txt': continue
        if name == 'testing_thread.txt': continue
        if name.startswith('gunslugs/runtime/lib/'): continue
        if name.startswith('gunslugs/runtime/prepare/'): continue
        if file.name.lower()=='readme.txt': continue
        if name.startswith('gunslugs/licenses/') and (len(parts)>3 or file.suffix.lower() in ('.zip','.gz','.jar','.json')): continue
        if file.suffix.lower() in ('.apk','.dat','.gptk','.log','.pyc') or file.name in ('log.txt','.gitkeep'): continue
        if 'natives-windows' in name: continue
        data=file.read_bytes()
        if file.suffix.lower() in TEXT_SUFFIXES:
            data=data.replace(b'\r\r\n',b'\n').replace(b'\r\n',b'\n').replace(b'\r',b'\n')
        files[name]=data
    artifact_root=Path(generated_artifacts) if generated_artifacts is not None else root/'build/artifacts/package'
    if artifact_root.is_dir():
        for file in sorted(artifact_root.rglob('*')):
            if file.is_symlink(): raise ValueError('Generated artifacts must not contain symlinks: '+str(file))
            if file.is_file():
                name=file.relative_to(artifact_root).as_posix()
                relative=PurePosixPath(name)
                if relative.is_absolute() or '..' in relative.parts or not relative.parts:
                    raise ValueError('Unsafe generated artifact path: '+name)
                data=file.read_bytes()
                if file.suffix.lower() in TEXT_SUFFIXES:
                    data=data.replace(b'\r\r\n',b'\n').replace(b'\r\n',b'\n').replace(b'\r',b'\n')
                files[name]=data
    required=['Gunslugs.sh','README.md','port.json','gameinfo.xml','screenshot.png','cover.png',
              'gunslugs/extracted.sh','gunslugs/display.inc','gunslugs/gunslugs.ini','gunslugs/gunslugs-pc.ini',
              'gunslugs/gamedata/PLACE_GAMEDATA_HERE.txt',
              'gunslugs/runtime/lib/gunslugs-bridge.jar','gunslugs/runtime/prepare/gunslugs-prepare.jar']
    for name in required:
        if name not in files: raise ValueError('Missing package file: '+name)
    target=root/'ports/gunslugs'
    ports=root/'ports';ports.mkdir(parents=True,exist_ok=True)
    try: target.resolve().relative_to(ports.resolve())
    except ValueError as error: raise ValueError('Generated port path escapes ports/: '+str(target)) from error
    if target.resolve()==ports.resolve() or target.is_symlink(): raise ValueError('Refusing unsafe generated port path: '+str(target))
    target.mkdir(parents=True,exist_ok=True)
    data_directory=(target/'gunslugs/gamedata').resolve()
    data_directory.relative_to(target.resolve())
    manifest=root/'build/portmaster-export.json';manifest.parent.mkdir(parents=True,exist_ok=True)
    previous=json.loads(manifest.read_text(encoding='utf-8')) if manifest.is_file() else []
    if not isinstance(previous,list) or not all(isinstance(name,str) for name in previous):
        raise ValueError('Invalid generated port manifest: '+str(manifest))
    for name in previous:
        if name in files: continue
        relative=PurePosixPath(name)
        if relative.is_absolute() or '..' in relative.parts or not relative.parts:
            raise ValueError('Unsafe path in generated port manifest: '+name)
        path=target.joinpath(*relative.parts)
        resolved=path.resolve();resolved.relative_to(target.resolve())
        if resolved==data_directory or data_directory in resolved.parents: continue
        if path.is_symlink(): raise ValueError('Refusing to remove generated path through a symlink: '+str(path))
        if path.is_file():
            path.chmod(path.stat().st_mode|stat.S_IWRITE)
            path.unlink()
    for name,data in files.items():
        path=target/name
        if path.is_symlink(): raise ValueError('Refusing to overwrite generated path through a symlink: '+str(path))
        path.resolve().relative_to(target.resolve())
        path.parent.mkdir(parents=True,exist_ok=True)
        if path.exists(): path.chmod(path.stat().st_mode|stat.S_IWRITE)
        path.write_bytes(data)
    for directory in sorted((path for path in target.rglob('*') if path.is_dir()),
                            key=lambda path:len(path.parts),reverse=True):
        resolved=directory.resolve()
        if resolved==data_directory or data_directory in resolved.parents or resolved in data_directory.parents: continue
        try: directory.rmdir()
        except OSError: pass
    temporary_manifest=manifest.with_suffix('.json.tmp')
    temporary_manifest.write_text(json.dumps(sorted(files))+'\n',encoding='utf-8')
    os.replace(temporary_manifest,manifest)
    dist=root/'dist';dist.mkdir(exist_ok=True)
    archive=dist/'gunslugs.zip'
    with zipfile.ZipFile(archive,'w') as out:
        for name,data in sorted(files.items()):
            installed=name
            if '/' not in name and name!='Gunslugs.sh':
                installed='gunslugs/'+name
            entry(out,installed,data)
    checksum=dist/'SHA256SUMS.txt'
    if checksum.is_file(): checksum.unlink()
    print('Built universal BYO-data archive:',archive)

if __name__=='__main__': export(Path(__file__).resolve().parents[1])
