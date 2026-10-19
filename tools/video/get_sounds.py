"""Copies the game sounds the Short uses out of Loom's asset cache (filled by any game-test run),
and the vanilla clock's picture out of Loom's game jar.

python3 -I get_sounds.py <out dir>
The sounds and the picture are Mojang's, so they're read from the cache each time rather than kept
in the repo.
"""
import glob, json, os, shutil, sys, zipfile

out = sys.argv[1]
os.makedirs(out, exist_ok=True)
assets = os.path.expanduser('~/.gradle/caches/fabric-loom/assets')
index = sorted(glob.glob(os.path.join(assets, 'indexes', '*.json')))[-1]
objects = json.load(open(index))['objects']
# ui.button.click for the settings, and block.note_block.chime for the soft chime under the dusk warning.
for name, file in {'click': 'random/click_stereo', 'chime': 'note/icechime'}.items():
    h = objects[f'minecraft/sounds/{file}.ogg']['hash']
    source = os.path.join(assets, 'objects', h[:2], h)
    if not os.path.exists(source):
        sys.exit(f'{file}.ogg is not in the asset cache; run a game test with resources.download.minecraft.net reachable')
    shutil.copy(source, os.path.join(out, name + '.ogg'))
    print(name)

# The clock item's picture (one frame of its dial), for the scene where the clock is dropped.
jars = glob.glob(os.path.expanduser('~/.gradle/caches/fabric-loom/*/minecraft-merged.jar'))
if not jars:
    sys.exit('no Minecraft jar in the Loom cache; build the mod once first')
with zipfile.ZipFile(sorted(jars, key=os.path.getmtime)[-1]) as jar, open(os.path.join(out, 'clock.png'), 'wb') as f:
    f.write(jar.read('assets/minecraft/textures/item/clock_00.png'))
print('clock')
