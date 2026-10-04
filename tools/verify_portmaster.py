"""Check the unpacked PortMaster source layout against the sole install ZIP."""
from pathlib import Path
import json
import zipfile
from verify_package import verify

ROOT=Path(__file__).resolve().parents[1]
verify()
tree=ROOT/'ports/gunslugs'
assert {p.name for p in tree.iterdir()}=={'README.md','screenshot.png','cover.png','gameinfo.xml','port.json','Gunslugs.sh','gunslugs'}
assert not any(p.is_dir() for p in (tree/'gunslugs/licenses').iterdir())
manifest=json.loads((ROOT/'build/portmaster-export.json').read_text())
with zipfile.ZipFile(ROOT/'dist/gunslugs.zip') as z:
    assert len(z.namelist())==len(manifest)
    for name in manifest:
        target=name
        if '/' not in name and name!='Gunslugs.sh': target='gunslugs/'+name
        assert z.read(target)==(tree/name).read_bytes(),target
print('PORTMASTER_TREE_OK: exported files match gunslugs.zip')
