"""Compile the port and on-device importer; emit one universal BYO-data ZIP.

No APK is accepted by the release build and no game data is prepared here.
"""
import argparse
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

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--jdk',type=Path)
    p.add_argument('--dex-tools',type=Path)
    p.add_argument('--package-only',action='store_true')
    args=p.parse_args()
    if not args.package_only:
        if not args.jdk or not args.dex_tools: p.error('--jdk and --dex-tools are required')
        jdk=args.jdk.resolve(); dex=args.dex_tools.resolve()
        javac=java_tool(jdk,'javac'); jar=java_tool(jdk,'jar')
        build=ROOT/'build'; build.mkdir(exist_ok=True)
        prepare=ROOT/'package/gunslugs/runtime/prepare'; prepare.mkdir(parents=True,exist_ok=True)
        (prepare/'dex').mkdir(exist_ok=True)
        for dependency in (dex/'lib').glob('*.jar'): shutil.copy2(dependency,prepare/'dex'/dependency.name)
        for name in ('asm-9.7.1.jar','asm-commons-9.7.1.jar'):
            candidate=build/'tools'/name
            if candidate.is_file(): shutil.copy2(candidate,prepare/name)
            if not (prepare/name).is_file(): raise SystemExit('Run tools/fetch_dependencies.py first: '+name)
        for name in ('LICENSE.txt','NOTICE.txt'):
            shutil.copy2(dex/name,ROOT/'package/gunslugs/licenses'/('dex2jar-'+name))
        # A fresh compilation directory prevents stale classes from shipping and
        # avoids Windows locks on output directories from previous invocations.
        import tempfile
        compilation=Path(tempfile.mkdtemp(prefix='compile-',dir=build))
        classes=compilation/'classes'; toolclasses=compilation/'tool-classes'
        for folder in (classes,toolclasses): folder.mkdir()
        run([javac,'-encoding','UTF-8','-source','8','-target','8','-cp',str(prepare/'*'),
             '-d',toolclasses,ROOT/'tools/java/PrepareGame.java',ROOT/'tools/java/PrepareDevice.java'])
        run([jar,'cf',prepare/'gunslugs-prepare.jar','-C',toolclasses,'.'])
        lib=ROOT/'package/gunslugs/runtime/lib'
        run([javac,'-encoding','UTF-8','-source','8','-target','8','-cp',str(lib/'*'),
             '-d',classes,*sorted((ROOT/'src').rglob('*.java'))])
        run([jar,'cf',lib/'gunslugs-bridge.jar','-C',classes,'.'])
    from portmaster_package import export
    export(ROOT)
    from verify_package import verify
    verify()

if __name__=='__main__': main()
