"""Check the shipped launcher's process exit handling without a handheld."""
import argparse
from pathlib import Path
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--bash', required=True)
args = parser.parse_args()
launcher = (Path(__file__).resolve().parents[1] / 'package/Gunslugs.sh').read_text()
handling = launcher[launcher.index('status=$?'):]
for status in (0, 143, 1, 139):
    script = 'gunslugs_fail() { echo "ERROR: $*"; exit 1; };\n'
    script += f'(exit {status})\n' + handling
    result = subprocess.run([args.bash, '-c', script], capture_output=True, text=True)
    normal = status in (0, 143)
    assert (result.returncode == 0) == normal, (status, result)
    assert ('ERROR:' in result.stdout) != normal, (status, result)
print('EXIT_HANDLING_OK: normal exit and SIGTERM succeed; genuine errors remain visible')
