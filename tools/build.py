"""Compile the port and on-device importer; emit one universal BYO-data ZIP.

No APK is accepted by the release build and no game data is prepared here.
"""
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]

def run(args):
    subprocess.run([str(x) for x in args], cwd=ROOT, check=True)

def java_tool(jdk, name):
    path=jdk/'bin'/(name+('.exe' if os.name=='nt' else ''))
    if not path.is_file(): raise SystemExit('JDK tool missing: '+str(path))
    return path

def package_snapshot():
    package=ROOT/'package'
    return {
        path.relative_to(package).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest()
        for path in package.rglob('*') if path.is_file()
    }

def ensure_package_unchanged(before):
    after=package_snapshot()
    if before != after:
        changed=sorted(set(before)^set(after) | {name for name in before.keys() & after.keys() if before[name] != after[name]})
        raise SystemExit('Build changed package/ file(s): '+', '.join(changed))
    print('PACKAGE_UNCHANGED',len(after),'files')

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--jdk',type=Path)
    p.add_argument('--dex-tools',type=Path)
    p.add_argument('--package-only',action='store_true')
    args=p.parse_args()
    package_before=package_snapshot()
    artifacts=ROOT/'build/artifacts/package'
    if not args.package_only:
        if not args.jdk or not args.dex_tools: p.error('--jdk and --dex-tools are required')
        jdk=args.jdk.resolve(); dex=args.dex_tools.resolve()
        javac=java_tool(jdk,'javac'); jar=java_tool(jdk,'jar')
        build=ROOT/'build'; build.mkdir(exist_ok=True)
        prepare=artifacts/'gunslugs/runtime/prepare'; prepare.mkdir(parents=True,exist_ok=True)
        (prepare/'dex').mkdir(exist_ok=True)
        for dependency in (dex/'lib').glob('*.jar'): shutil.copy2(dependency,prepare/'dex'/dependency.name)
        for name in ('asm-9.7.1.jar','asm-commons-9.7.1.jar'):
            candidate=build/'tools'/name
            if candidate.is_file(): shutil.copy2(candidate,prepare/name)
            if not (prepare/name).is_file(): raise SystemExit('Run tools/fetch_dependencies.py first: '+name)
        for name in ('LICENSE.txt','NOTICE.txt'):
            licenses=artifacts/'gunslugs/licenses'; licenses.mkdir(parents=True,exist_ok=True)
            shutil.copy2(dex/name,licenses/('dex2jar-'+name))
        # A fresh compilation directory prevents stale classes from shipping and
        # avoids Windows locks on output directories from previous invocations.
        import tempfile
        compilation=Path(tempfile.mkdtemp(prefix='compile-',dir=build))
        classes=compilation/'classes'; toolclasses=compilation/'tool-classes'
        for folder in (classes,toolclasses): folder.mkdir()
        run([javac,'-encoding','UTF-8','-source','8','-target','8','-cp',str(prepare/'*'),
             '-d',toolclasses,ROOT/'tools/java/PrepareGame.java',ROOT/'tools/java/PrepareDevice.java'])
        prepare_temp=prepare/'gunslugs-prepare.jar.tmp'
        run([jar,'cf',prepare_temp,'-C',toolclasses,'.'])
        prepare_temp.replace(prepare/'gunslugs-prepare.jar')
        lib=artifacts/'gunslugs/runtime/lib'; lib.mkdir(parents=True,exist_ok=True)
        run([javac,'-encoding','UTF-8','-source','8','-target','8','-cp',str(lib/'*'),
             '-d',classes,*sorted((ROOT/'src').rglob('*.java'))])
        bridge_temp=lib/'gunslugs-bridge.jar.tmp'
        run([jar,'cf',bridge_temp,'-C',classes,'.'])
        bridge_temp.replace(lib/'gunslugs-bridge.jar')
    from portmaster_package import export
    export(ROOT,artifacts)
    from verify_package import verify
    verify()
    ensure_package_unchanged(package_before)

if __name__=='__main__': main()
