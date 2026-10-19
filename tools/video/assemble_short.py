"""Builds the vertical Short from VideoTour's frames: nightfall-clock-short.mp4 (game sounds, when
get_sounds.py found them), nightfall-clock-short-silent.mp4 and nightfall-clock-short-thumbnail.png.

python3 -I assemble_short.py <video_tour dir> <sounds dir> <out dir>
(set CHROME to a Chrome or Chromium binary if it isn't the default)

The game is filmed 3:4 and set into the 9:16 frame with a blurred copy of itself above and below,
so the clock line sits in the middle of the screen, clear of the Shorts title along the bottom.
Captions and the end tag stay in the middle band too: below the top 12%, left of the buttons on
the right 19% and above the bottom 20%. The Short opens partway into the dusk take and ends just
before that point, so it loops.
"""
import csv, html, os, shutil, subprocess, sys
from PIL import Image, ImageDraw, ImageEnhance, ImageFilter

frames_dir, sounds_dir, out = sys.argv[1:4]
FPS = 20
W, H = 1080, 1920
GAME_H = 1440
TOP = 240  # where the game frame sits; its clock line lands at about 75% of the height
SAFE_RIGHT = 875  # the right 19% holds the Shorts buttons
CHROME = os.environ.get('CHROME', '/opt/pw-browsers/chromium-1194/chrome-linux/chrome')
repo = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
work = os.path.join(out, 'work')
shutil.rmtree(work, ignore_errors=True)
os.makedirs(work)


def run(*args):
    subprocess.run(args, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)


with open(os.path.join(frames_dir, 'frames.tsv')) as f:
    cursor_at = {(r[0], int(r[1])): (r[2], r[3]) for r in csv.reader(f, delimiter='\t')}
with open(os.path.join(frames_dir, 'actions.tsv')) as f:
    actions = [(r[0], int(r[1]), r[2], r[3]) for r in csv.reader(f, delimiter='\t')]


def at(scene, kind):
    return next(f for s, f, k, _ in actions if s == scene and k == kind)


def count(scene):
    return len([n for n in os.listdir(frames_dir) if n.startswith(scene + '_') and n.endswith('.png')])


# The dusk take is cut in two: the Short opens HOLD frames after the warning appears, and ends on
# the frame before that, after racing up to it.
dusk = at('dusk', 'dusk')
HOLD = 40
# Each piece of the Short: (name, filmed scene, first frame, frame after the last, caption).
# Change a caption here if its scene in VideoTour changes.
PIECES = [
    ('open', 'dusk', dusk + HOLD, dusk + HOLD + 60, 'Never get caught out by nightfall.'),
    ('morning', 'morning', 10, 100, 'A clock that’s just… there.'),
    ('items', 'items', 6, at('items', 'clear') + 24, 'Gets out of the way.'),
    ('settings', 'settings', 4, at('settings', 'key') + 14, 'Your spot, your format.'),
    ('end', 'dusk', 0, dusk + HOLD, None),
]
for name, scene, first, last, _ in PIECES:
    assert 0 <= first < last <= count(scene), f'{name}: frames {first}-{last} of {count(scene)}'

STYLE = """
* { margin:0; box-sizing:border-box; }
html,body { width:%dpx; height:%dpx; overflow:hidden; background:transparent; }
body { font-family:'Inter',sans-serif; }
.band { position:absolute; left:0; width:%dpx; display:flex; justify-content:center; }
.cap { max-width:780px; background:rgba(28,22,40,.88); color:#fff3dc; border-radius:28px;
  padding:26px 40px; font-weight:800; font-size:68px; line-height:1.1; letter-spacing:-1px; text-align:center;
  box-shadow:0 14px 40px rgba(0,0,0,.45); border:3px solid rgba(255,170,0,.55); }
.tag { display:flex; align-items:center; gap:26px; background:rgba(28,22,40,.88); border-radius:28px; padding:24px 34px;
  box-shadow:0 14px 40px rgba(0,0,0,.45); border:3px solid rgba(255,170,0,.55); }
.tag img { width:104px; height:104px; image-rendering:pixelated; border-radius:12px; }
.tag h1 { color:#ffbf3f; font-size:58px; font-weight:800; letter-spacing:-1px; line-height:1.05; }
.tag p { color:#f3e6cf; font-size:31px; font-weight:600; line-height:1.4; margin-top:6px; }
.title { color:#ffbf3f; font-size:132px; font-weight:900; letter-spacing:-3px; line-height:.95; text-align:center;
  text-shadow:0 8px 0 #4a2a00, 0 18px 40px rgba(0,0,0,.6); }
"""


def overlay(name, body):
    """A transparent 1080x1920 layer drawn by Chromium from HTML."""
    path = os.path.join(work, name + '.html')
    with open(path, 'w', encoding='utf-8') as f:
        f.write('<!doctype html><html><head><meta charset=utf-8><style>' + STYLE % (W, H, SAFE_RIGHT)
                + '</style></head><body>' + body + '</body></html>')
    png = os.path.join(work, name + '.png')
    run(CHROME, '--headless', '--no-sandbox', '--disable-gpu', '--hide-scrollbars', '--force-device-scale-factor=1',
        '--default-background-color=00000000', f'--window-size={W},{H}', '--screenshot=' + png, 'file:///' + path)
    return Image.open(png).convert('RGBA')


shutil.copy(os.path.join(repo, 'common/src/main/resources/assets/nightfallclock/icon.png'), os.path.join(work, 'icon.png'))
CAPTION_TOP = 300
captions = {name: overlay('cap_' + name, f'<div class="band" style="top:{CAPTION_TOP}px"><div class="cap">{html.escape(c)}</div></div>')
            for name, _, _, _, c in PIECES if c}
tag = overlay('tag', f'<div class="band" style="top:{CAPTION_TOP}px"><div class="tag"><img src="icon.png"><div>'
              '<h1>Nightfall Clock</h1><p>Fabric + NeoForge · 1.20.1 - 26.3<br>client-side</p></div></div></div>')

# A Minecraft-ish pixel arrow for the settings screen's pointer, drawn at 3x.
ARROW = ["2", "22", "212", "2112", "21112", "211112", "2111112", "21111112", "211111112", "2111112222",
         "2112112", "212 2112", "22  2112", "2    2112", "     2112", "      22"]
cursor = Image.new('RGBA', (30, 48), (0, 0, 0, 0))
d = ImageDraw.Draw(cursor)
for y, row in enumerate(ARROW):
    for x, c in enumerate(row):
        if c in '12':
            d.rectangle([x * 3, y * 3, x * 3 + 2, y * 3 + 2], fill=(255, 255, 255, 255) if c == '1' else (0, 0, 0, 255))


def compose(scene, i):
    """One filmed frame set into the tall frame, over a blurred, darker copy of itself."""
    game = Image.open(os.path.join(frames_dir, '%s_%04d.png' % (scene, i))).convert('RGBA')
    s = W / game.width
    if game.size != (W, GAME_H):
        game = game.resize((W, GAME_H), Image.LANCZOS)
    x, y = cursor_at.get((scene, i), ('-', '-'))
    if x != '-':
        game.alpha_composite(cursor, (int(int(x) * s), int(int(y) * s)))
    back = game.resize((W * H // GAME_H, H), Image.BILINEAR).crop(((W * H // GAME_H - W) // 2, 0, (W * H // GAME_H - W) // 2 + W, H))
    back = ImageEnhance.Brightness(back.filter(ImageFilter.GaussianBlur(28))).enhance(0.45)
    back.alpha_composite(game, (0, TOP))
    return back


def fade(layer, a):
    layer = layer.copy()
    layer.putalpha(layer.getchannel('A').point(lambda v: int(v * a)))
    return layer


CAPTION_FADE = 5  # frames for a caption to fade in; the opening one is up from the first frame
starts, t = {}, 0.0
for n, (name, scene, first, last, cap) in enumerate(PIECES):
    dest = os.path.join(work, name)
    os.makedirs(dest)
    for j, i in enumerate(range(first, last)):
        img = compose(scene, i)
        if cap:
            img.alpha_composite(captions[name] if n == 0 else fade(captions[name], min(1.0, (j + 1) / CAPTION_FADE)))
        elif name == 'end':
            # The end tag comes in once the race to dusk is under way, and stays to the last frame.
            img.alpha_composite(fade(tag, max(0.0, min(1.0, (j - 10) / 8))))
        img.convert('RGB').save(os.path.join(dest, '%04d.png' % j))
    starts[name] = (t, scene, first)
    t += (last - first) / FPS
total = t

clips = []
for name, *_ in PIECES:
    clip = os.path.join(work, name + '.mp4')
    run('ffmpeg', '-y', '-framerate', str(FPS), '-i', os.path.join(work, name, '%04d.png'),
        '-vf', 'format=yuv420p', '-r', '30', '-c:v', 'libx264', '-preset', 'slow', '-crf', '18', clip)
    clips.append(clip)
with open(os.path.join(work, 'list.txt'), 'w') as f:
    f.writelines(f"file '{c}'\n" for c in clips)
silent = os.path.join(out, 'nightfall-clock-short-silent.mp4')
run('ffmpeg', '-y', '-f', 'concat', '-safe', '0', '-i', os.path.join(work, 'list.txt'), '-c', 'copy', '-movflags', '+faststart', silent)

# The game's own sounds at the logged frames: a click for each settings button, and a soft chime
# under the dusk warning. Without the sound files only the silent version is made.
cues = []
for piece, (start, scene, first) in starts.items():
    last = next(p[3] for p in PIECES if p[0] == piece)
    for s, frame, kind, what in actions:
        if s != scene or not first <= frame < last:
            continue
        when = start + (frame - first) / FPS
        if kind == 'click':
            cues.append((when, 'click', 1.0, 0.3))
        elif kind == 'dusk':
            cues.append((when, 'chime', 0.75, 0.45))
            cues.append((when + 0.18, 'chime', 1.0, 0.3))
if all(os.path.exists(os.path.join(sounds_dir, s + '.ogg')) for s in ('click', 'chime')):
    inputs, filters = [], []
    for i, (when, sound, pitch, volume) in enumerate(cues):
        inputs += ['-i', os.path.join(sounds_dir, sound + '.ogg')]
        ms = int(when * 1000)
        filters.append(f'[{i}:a]aformat=sample_rates=44100:channel_layouts=stereo,asetrate={44100 * pitch:.0f},aresample=44100,'
                       f'volume={volume},adelay={ms}|{ms}[s{i}]')
    filters.append(''.join(f'[s{i}]' for i in range(len(cues))) + f'amix=inputs={len(cues)}:normalize=0,apad,atrim=0:{total:.3f}[a]')
    audio = os.path.join(work, 'sound.wav')
    run('ffmpeg', '-y', *inputs, '-filter_complex', ';'.join(filters), '-map', '[a]', audio)
    run('ffmpeg', '-y', '-i', silent, '-i', audio, '-map', '0:v', '-map', '1:a', '-c:v', 'copy', '-c:a', 'aac', '-b:a', '160k',
        '-shortest', '-movflags', '+faststart', os.path.join(out, 'nightfall-clock-short.mp4'))
else:
    print('no sounds in', sounds_dir, '- made only the silent version')

# The thumbnail: an opening frame with the dusk warning, the warning's line enlarged in the middle
# and the mod's name large at the top of the safe area.
thumb = compose('dusk', dusk + HOLD + 10)
line_y = TOP + GAME_H - 59 * (W // 270) - 14  # the clock line: 59 GUI pixels above the bottom
strip = thumb.crop((90, line_y - 10, W - 90, line_y + 50))
strip = strip.resize((W - 120, int(strip.height * (W - 120) / strip.width)), Image.NEAREST)
panel = Image.new('RGBA', (strip.width + 24, strip.height + 24), (255, 170, 0, 200))
panel.alpha_composite(strip, (12, 12))
thumb.alpha_composite(panel, ((W - panel.width) // 2, 900))
thumb.alpha_composite(overlay('thumb', f'<div class="band" style="top:330px"><div class="title">Nightfall<br>Clock</div></div>'))
thumb.convert('RGB').save(os.path.join(out, 'nightfall-clock-short-thumbnail.png'))
print('total seconds', round(total, 2))
