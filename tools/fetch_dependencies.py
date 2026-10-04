"""Download pinned, unmodified open-source Java runtime dependencies."""
import concurrent.futures
import hashlib
import json
import pathlib
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
REPOSITORY = 'https://repo.maven.apache.org/maven2/'
ARTIFACTS = [
    ('com/badlogicgames/gdx', 'gdx', '1.13.1', ''),
    ('com/badlogicgames/gdx', 'gdx-backend-lwjgl3', '1.13.1', ''),
    ('com/badlogicgames/gdx', 'gdx-platform', '1.13.1', 'natives-desktop'),
    ('com/badlogicgames/gdx', 'gdx-jnigen-loader', '2.5.2', ''),
    ('org/jcraft', 'jorbis', '0.0.17', ''),
    ('com/badlogicgames/jlayer', 'jlayer', '1.0.1-gdx', ''),
    ('org/ow2/asm', 'asm', '9.7.1', ''),
    ('org/ow2/asm', 'asm-commons', '9.7.1', ''),
]
for module in ('lwjgl', 'lwjgl-glfw', 'lwjgl-jemalloc', 'lwjgl-openal', 'lwjgl-opengl', 'lwjgl-stb'):
    for classifier in ('', 'natives-linux-arm64', 'natives-windows'):
        ARTIFACTS.append(('org/lwjgl', module, '3.3.3', classifier))

def fetch(spec):
    group, name, version, classifier = spec
    filename = name + '-' + version + ('-' + classifier if classifier else '') + '.jar'
    url = REPOSITORY + '/'.join((group, name, version, filename))
    folder = ROOT / ('build/windows-libs' if classifier == 'natives-windows' else 'build/artifacts/package/gunslugs/runtime/lib')
    if name.startswith('asm'):
        folder = ROOT / 'build/tools'
    folder.mkdir(parents=True, exist_ok=True)
    target = folder / filename
    expected = urllib.request.urlopen(url + '.sha1', timeout=90).read().decode().split()[0]
    if not target.exists() or hashlib.sha1(target.read_bytes()).hexdigest() != expected:
        temporary = target.with_suffix('.download')
        urllib.request.urlretrieve(url, temporary)
        if hashlib.sha1(temporary.read_bytes()).hexdigest() != expected:
            raise ValueError('Repository checksum mismatch: ' + filename)
        temporary.replace(target)
    return dict(file=filename, url=url, sha256=hashlib.sha256(target.read_bytes()).hexdigest())

if __name__ == '__main__':
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        results = list(pool.map(fetch, ARTIFACTS))
    (ROOT / 'dependencies.lock.json').write_text(json.dumps(results, indent=2) + '\n')
    print('Verified %d dependency jars.' % len(results))
