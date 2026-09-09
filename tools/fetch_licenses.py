"""Fetch upstream notices and matching LGPL sources for the packaged runtime."""
import concurrent.futures
import hashlib
import json
import pathlib
import tarfile
import urllib.request
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT = ROOT / 'package/gunslugs/licenses'
OUT.mkdir(parents=True, exist_ok=True)
MAVEN = 'https://repo.maven.apache.org/maven2/'
RAW = 'https://raw.githubusercontent.com/'
FILES = {
    'LIBGDX-LICENSE.txt': RAW + 'libgdx/libgdx/1.13.1/LICENSE',
    'LIBGDX-THIRDPARTY.txt': RAW + 'libgdx/libgdx/1.13.1/THIRDPARTY',
    'LIBGDX-AUTHORS.txt': RAW + 'libgdx/libgdx/1.13.1/AUTHORS',
    'GDX-JNIGEN-LICENSE.txt': RAW + 'libgdx/gdx-jnigen/2.5.2/LICENSE',
    'LWJGL-LICENSE.txt': RAW + 'LWJGL/lwjgl3/3.3.3/LICENSE.md',
    'GLFW-LICENSE.txt': RAW + 'LWJGL/lwjgl3/3.3.3/modules/lwjgl/glfw/glfw_license.txt',
    'JEMALLOC-LICENSE.txt': RAW + 'LWJGL/lwjgl3/3.3.3/modules/lwjgl/jemalloc/jemalloc_license.txt',
    'OPENAL-SOFT-LICENSE.txt': RAW + 'LWJGL/lwjgl3/3.3.3/modules/lwjgl/openal/openal_soft_license.txt',
    'KHRONOS-LICENSE.txt': RAW + 'LWJGL/lwjgl3/3.3.3/modules/lwjgl/opengl/khronos_license.txt',
    'LIBFFI-LICENSE.txt': RAW + 'LWJGL/lwjgl3/3.3.3/modules/lwjgl/core/libffi_license.txt',
    'LGPL-2.1.txt': 'https://www.gnu.org/licenses/old-licenses/lgpl-2.1.txt',
    'GPL-2.0.txt': 'https://www.gnu.org/licenses/old-licenses/gpl-2.0.txt',
    'sources/openal-soft-1.23.1.tar.gz': 'https://codeload.github.com/kcat/openal-soft/tar.gz/refs/tags/1.23.1',
    'sources/jorbis-0.0.17-sources.jar': MAVEN + 'org/jcraft/jorbis/0.0.17/jorbis-0.0.17-sources.jar',
    'sources/jlayer-1.0.1-gdx-sources.jar': MAVEN + 'com/badlogicgames/jlayer/jlayer/1.0.1-gdx/jlayer-1.0.1-gdx-sources.jar',
    'sources/jorbis-0.0.17.pom': MAVEN + 'org/jcraft/jorbis/0.0.17/jorbis-0.0.17.pom',
    'sources/jlayer-1.0.1-gdx.pom': MAVEN + 'com/badlogicgames/jlayer/jlayer/1.0.1-gdx/jlayer-1.0.1-gdx.pom',
    'sources/lwjgl-openal-3.3.3-sources.jar': MAVEN + 'org/lwjgl/lwjgl-openal/3.3.3/lwjgl-openal-3.3.3-sources.jar',
    'sources/lwjgl-stb-3.3.3-sources.jar': MAVEN + 'org/lwjgl/lwjgl-stb/3.3.3/lwjgl-stb-3.3.3-sources.jar',
}
for name in ('stb_dxt.h', 'stb_easy_font.h', 'stb_image.h', 'stb_image_resize.h',
             'stb_image_write.h', 'stb_perlin.h', 'stb_rect_pack.h', 'stb_truetype.h', 'stb_vorbis.c'):
    FILES['sources/lwjgl-stb/' + name] = RAW + 'LWJGL/lwjgl3/3.3.3/modules/lwjgl/stb/src/main/c/' + name

def download(item):
    filename, url = item
    target = OUT / filename
    target.parent.mkdir(parents=True, exist_ok=True)
    if not target.exists():
        with urllib.request.urlopen(url, timeout=90) as response:
            data = response.read()
        if not data: raise ValueError('Empty download: ' + url)
        target.write_bytes(data)
    data = target.read_bytes()
    if target.suffix == '.jar':
        with zipfile.ZipFile(target) as jar:
            bad = jar.testzip()
            if bad: raise ValueError('Corrupt source JAR entry: ' + bad)
    return {'file': filename, 'url': url, 'sha256': hashlib.sha256(data).hexdigest()}

if __name__ == '__main__':
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        results = list(pool.map(download, FILES.items()))
    (OUT / 'sources.lock.json').write_text(json.dumps(results, indent=2) + '\n', encoding='utf-8')
    # Retain exact notices from the matching distributed sources.
    for name, version in (('jorbis', '0.0.17'), ('jlayer', '1.0.1-gdx')):
        headers = []
        with zipfile.ZipFile(OUT / 'sources' / (name + '-' + version + '-sources.jar')) as jar:
            for member in jar.namelist():
                if member.endswith('.java'):
                    header = jar.read(member).decode('utf-8', 'replace').split('package ', 1)[0].strip()
                    if header and header not in headers:
                        headers.append(header)
        (OUT / (name.upper() + '-NOTICES.txt')).write_text('\n\n'.join(headers) + '\n', encoding='utf-8')
    with tarfile.open(OUT / 'sources/openal-soft-1.23.1.tar.gz') as source:
        (OUT / 'OPENAL-SOFT-COPYING.txt').write_bytes(source.extractfile('openal-soft-1.23.1/COPYING').read())
    stb = (OUT / 'sources/lwjgl-stb/stb_image.h').read_text(encoding='utf-8')
    (OUT / 'STB-LICENSE.txt').write_text(stb[stb.rindex('This software is available under 2 licenses'):].rstrip('*/\n ') + '\n', encoding='utf-8')
    print('Downloaded and checksummed %d notices/source artifacts.' % len(results))
