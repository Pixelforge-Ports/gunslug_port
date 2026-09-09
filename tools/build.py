"""Build a private playable package from the owner's exact Gunslugs 3.2.4 APK.

Requires Python 3.8+, a JDK 8+ and dex2jar 2.4. Dependencies are fetched separately.
Never downloads or redistributes game data. Outputs remain on the local machine.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
APK_SHA256 = 'd2c857b479a4f7a19bc59840e74bfc6350316a46f8ff579c69da281e8a2933e8'

def run(args):
    subprocess.run([str(x) for x in args], cwd=ROOT, check=True)

def java_tool(jdk, name):
    result = jdk / 'bin' / (name + ('.exe' if os.name == 'nt' else ''))
    if not result.is_file():
        raise SystemExit('JDK tool not found: ' + str(result))
    return result

def add_zip(out, source, target):
    entry = zipfile.ZipInfo(target, date_time=(2026, 9, 9, 0, 0, 0))
    entry.create_system = 3
    entry.external_attr = (0o100755 if target.endswith('.sh') else 0o100644) << 16
    entry.compress_type = zipfile.ZIP_DEFLATED
    out.writestr(entry, source.read_bytes())

def package():
    dist = ROOT / 'dist'
    dist.mkdir(exist_ok=True)
    for name, with_data, muos in [('gunslugs-byo-data.zip',False,False),
                                  ('gunslugs-byo-data-muos.zip',False,True),
                                  ('gunslugs-private-muos.zip',True,True),
                                  ('gunslugs-private-portmaster.zip',True,False)]:
        with zipfile.ZipFile(dist/name, 'w') as out:
            for source in sorted((ROOT/'package').rglob('*')):
                if not source.is_file(): continue
                rel = source.relative_to(ROOT/'package').as_posix()
                if any(segment in rel for segment in ('/saves/', '/logs/', '/cache/')) or rel.endswith(('.log', '/log.txt')): continue
                if not with_data and rel.startswith('gunslugs/gamedata/') and not rel.endswith(('README.txt','.gitkeep')): continue
                # Keep metadata with this game; never overwrite generic files in /ports.
                if '/' not in rel and rel != 'Gunslugs.sh': rel='gunslugs/'+rel
                if muos:
                    target = 'roms/PORTS/' + rel if rel == 'Gunslugs.sh' else 'ports/' + rel
                else: target = rel
                add_zip(out,source,target)
        print('Built',dist/name)
    with zipfile.ZipFile(dist/'gunslugs-speed-fix-muos.zip','w') as out:
        add_zip(out,ROOT/'package/gunslugs/runtime/lib/gunslugs-bridge.jar',
                'ports/gunslugs/runtime/lib/gunslugs-bridge.jar')
    from portmaster_package import export
    export(ROOT)
    with zipfile.ZipFile(dist/'gunslugs-port-source.zip','w') as out:
        for source in sorted(ROOT.rglob('*')):
            if not source.is_file(): continue
            rel=source.relative_to(ROOT).as_posix()
            if rel.split('/')[0] not in ('src','tools','tests','docs','package','README.md',
                                        'VALIDATION.md','LICENSE','dependencies.lock.json','.gitignore','.gitattributes'): continue
            if any(segment in source.relative_to(ROOT).parts for segment in ('build','dist','__pycache__')): continue
            if rel.startswith(('package/gunslugs/runtime/', 'package/gunslugs/gamedata/',
                               'package/gunslugs/saves/', 'package/gunslugs/cache/')): continue
            if rel.endswith(('.apk', '.log', '/log.txt', '.pyc')): continue
            add_zip(out,source,'gunslugs-port/'+rel)
    print('Built',dist/'gunslugs-port-source.zip')
    (dist/'SHA256SUMS.txt').write_text(''.join(
        hashlib.sha256(path.read_bytes()).hexdigest()+'  '+path.name+'\n'
        for path in sorted(dist.glob('*.zip'))), encoding='utf-8')

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk',type=Path)
    parser.add_argument('--jdk',type=Path)
    parser.add_argument('--dex-tools',type=Path)
    parser.add_argument('--package-only',action='store_true')
    args=parser.parse_args()
    if args.package_only:
        package(); return
    if not all([args.apk,args.jdk,args.dex_tools]):
        parser.error('--apk, --jdk and --dex-tools are required')
    apk=args.apk.resolve(); jdk=args.jdk.resolve(); dex=args.dex_tools.resolve()
    if hashlib.sha256(apk.read_bytes()).hexdigest() != APK_SHA256:
        raise SystemExit('Unsupported APK fingerprint. This bridge is specific to the supplied 3.2.4 build; refusing to apply mappings to another version.')
    build=ROOT/'build'; build.mkdir(exist_ok=True)
    classes=build/'classes'; classes.mkdir(exist_ok=True)
    toolclasses=build/'tool-classes'; toolclasses.mkdir(exist_ok=True)
    java=java_tool(jdk,'java'); javac=java_tool(jdk,'javac'); jar=java_tool(jdk,'jar')
    run([java,'-cp',str(dex/'lib/*'),'com.googlecode.dex2jar.tools.Dex2jarCmd',
         '--force','--output',build/'converted.jar',apk])
    run([javac,'-encoding','UTF-8','-source','8','-target','8','-cp',str(build/'tools/*'),
         '-d',toolclasses,ROOT/'tools/java/PrepareGame.java',ROOT/'tools/java/PrepareDevice.java'])
    preparer=ROOT/'package/gunslugs/runtime/prepare'
    preparer.mkdir(parents=True,exist_ok=True)
    (preparer/'dex').mkdir(exist_ok=True)
    run([jar,'cf',preparer/'gunslugs-prepare.jar','-C',toolclasses,'.'])
    for dependency in (dex/'lib').glob('*.jar'):
        shutil.copy2(dependency,preparer/'dex'/dependency.name)
    for dependency in (build/'tools').glob('asm-*.jar'):
        shutil.copy2(dependency,preparer/dependency.name)
    for name in ('LICENSE.txt','NOTICE.txt'):
        shutil.copy2(dex/name,ROOT/'package/gunslugs/licenses'/('dex2jar-'+name))
    data=ROOT/'package/gunslugs/gamedata'; data.mkdir(parents=True,exist_ok=True)
    run([java,'-cp',os.pathsep.join([str(toolclasses),str(build/'tools/*')]),
         'PrepareGame',build/'converted.jar',data/'game.jar'])
    with zipfile.ZipFile(apk) as source:
        for info in source.infolist():
            if not info.filename.startswith('assets/') or info.is_dir(): continue
            if info.filename.startswith('assets/dexopt/'): continue
            relative=Path(info.filename)
            if '..' in relative.parts or relative.is_absolute(): raise ValueError('Unsafe APK asset path')
            target=data/relative
            target.parent.mkdir(parents=True,exist_ok=True)
            with source.open(info) as src,target.open('wb') as dst: shutil.copyfileobj(src,dst)
    lib=ROOT/'package/gunslugs/runtime/lib'
    run([javac,'-encoding','UTF-8','-source','8','-target','8','-cp',str(lib/'*'),
         '-d',classes,*sorted((ROOT/'src').rglob('*.java'))])
    run([jar,'cf',lib/'gunslugs-bridge.jar','-C',classes,'.'])
    (data/'source.json').write_text(json.dumps({'package':'com.orangepixel.gunslugshandy','version':'3.2.4',
        'versionCode':52,'apk_sha256':APK_SHA256,'data_policy':'Owner-provided; private use. Do not distribute this game data.'},indent=2)+'\n')
    package()

if __name__=='__main__': main()
