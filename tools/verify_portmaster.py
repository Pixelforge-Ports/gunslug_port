"""Check the unpacked PortMaster source layout against the sole install ZIP."""
from pathlib import Path
import hashlib
import json
import zipfile
from verify_package import verify

ROOT=Path(__file__).resolve().parents[1]
verify()
tree=ROOT/'ports/gunslugs'
assert {p.name for p in tree.iterdir()}=={'README.md','screenshot.png','gameinfo.xml','port.json','testing_thread.txt','Gunslugs.sh','gunslugs'}
assert not any(p.is_dir() for p in (tree/'gunslugs/licenses').iterdir())
manifest=json.loads((ROOT/'build/portmaster-export.json').read_text())
with zipfile.ZipFile(ROOT/'dist/gunslugs.zip') as z:
    assert len(z.namelist())==len(manifest)
    for name in manifest:
        target=name
        if '/' not in name and name!='Gunslugs.sh': target='gunslugs/'+('gunslugs.md' if name=='README.md' else name)
        assert z.read(target)==(tree/name).read_bytes(),target
sha,name=(ROOT/'dist/SHA256SUMS.txt').read_text().strip().split('  ',1)
assert name=='gunslugs.zip' and hashlib.sha256((ROOT/'dist'/name).read_bytes()).hexdigest()==sha
print('PORTMASTER_TREE_OK: exported files and checksum match gunslugs.zip')
