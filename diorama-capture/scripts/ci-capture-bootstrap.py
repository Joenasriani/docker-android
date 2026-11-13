"""Build an isolated Android capture project from the checksum-protected source."""
from pathlib import Path
import json
import shutil
import subprocess

payload = Path(__file__).resolve().parent.parent
project = Path.cwd() / 'capture-build'
project.mkdir(exist_ok=True)
for name in ['package.json', 'package-lock.json', 'capacitor.config.json']:
    shutil.copy2(payload / name, project / name)
subprocess.run(['git', 'clone', '--quiet', 'https://github.com/Joenasriani/diorama-game.git', str(project / 'frozen-source')], check=True)
subprocess.run(['git', '-C', str(project / 'frozen-source'), 'checkout', '--quiet', '20f2657268e55fec9d31e02f1e6e0cc83b174b65'], check=True)
(project / 'upstream/runtime').mkdir(parents=True)
manifest = json.loads((payload / 'upstream/SOURCE.json').read_text())
shutil.copy2(payload / 'upstream/SOURCE.json', project / 'upstream/SOURCE.json')
for name in manifest['files']:
    target = project / 'upstream/runtime' / name
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(project / 'frozen-source' / name, target)
for directory in ['scripts', 'docs', 'android-shell']:
    shutil.copytree(payload / directory, project / directory, dirs_exist_ok=True)
subprocess.run(['npm', 'ci'], cwd=project, check=True)
subprocess.run(['npm', 'run', 'prepare:web'], cwd=project, check=True)
subprocess.run(['npx', 'cap', 'add', 'android'], cwd=project, check=True)
# Capacitor's generated example asserts the template package name. It is not a
# DIORAMA test and must not accompany the three owned tests copied below.
shutil.rmtree(project / 'android/app/src/androidTest', ignore_errors=True)
shutil.rmtree(project / 'android/app/src/test', ignore_errors=True)
shutil.copytree(payload / 'android', project / 'android', dirs_exist_ok=True)
subprocess.run(['npx', 'cap', 'sync', 'android'], cwd=project, check=True)
# Launcher-only derivatives from the exact accepted mascot. No game pixels or
# runtime scripts are changed for the capture build.
from PIL import Image
icon = Image.open(project / 'www/assets/icons/icon-512.png').convert('RGBA')
for density, size in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
    target = project / f'android/app/src/main/res/mipmap-{density}'
    target.mkdir(parents=True, exist_ok=True)
    for name in ['ic_launcher.png','ic_launcher_round.png']:
        icon.resize((size,size), Image.Resampling.LANCZOS).save(target / name)
print('Capture project uses the frozen source and current Android wrapper; no owner key is present.')
