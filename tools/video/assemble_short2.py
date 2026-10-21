"""Builds the second YouTube Short, "Caught out", from VideoTour's frames.

python assemble_short2.py <video_tour dir> <sounds dir> <out dir> [<music file> <start seconds> ...]
(sounds from get_sounds.py; needs ffmpeg, Python 3 with Pillow and numpy)

Makes nightfall-clock-short-2.mp4 (the game's own sounds), nightfall-clock-short-2-silent.mp4,
nightfall-clock-short-2-thumbnail.png and, for each music file given, nightfall-clock-short-2-music-<n>.mp4
with that track soft under the game's sounds. All 1080x1920, filmed full frame (no bars).

It opens on the mod's own warning, "The sun's getting real low", coming up while the player chops
a tree at the woods' edge, and ends on the swings just before it, so a replay loops. The mod's text
(the warning, then the clock) is lifted from the HUD line and shown large in the middle of the
screen, pixel for pixel as the game drew it: the HUD line itself sits low, under the Shorts title.

Captions are bold white capitals with a black outline straight on the footage, one word in the
mod's gold. A small Nightfall Clock badge stays in one place all the way through. If a scene
changes in VideoTour, change its caption here too.
"""
import csv, json, os, random, shutil, subprocess, sys
from concurrent.futures import ThreadPoolExecutor
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

frames_dir, sounds_dir, out = sys.argv[1:4]
music = list(zip(sys.argv[4::2], map(float, sys.argv[5::2])))
# A frame a game tick: 20 frames make a second at the game's real pace (VideoTour halves the
# player's speed so the player keeps that pace too). Every frame is a real one; an earlier cut
# smoothed half-speed frames with motion interpolation, and it warped the blocks.
FPS = 20
W, H = 1080, 1920
GOLD = (255, 170, 0)
NAME = 'nightfall-clock-short-2'
repo = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
work = os.path.join(out, 'work-short-2')
shutil.rmtree(work, ignore_errors=True)
os.makedirs(os.path.join(work, 'frames'))

with open(os.path.join(frames_dir, 'actions.tsv')) as f:
    actions = [(r[0], int(r[1]), r[2], r[3]) for r in csv.reader(f, delimiter='\t')]


def at(scene, kind):
    return next(f for s, f, k, _ in actions if s == scene and k == kind)


def count(scene):
    return len([n for n in os.listdir(frames_dir) if n.startswith(scene + '_') and n.endswith('.png')])


dusk, turn = at('s_chop', 'dusk'), at('s_chop', 'turn')
HOUR = ['AN HOUR', '*BEFORE* DARK']
# Each piece: (filmed scene, first frame, frame after the last, caption lines with *gold* words, caption top).
PIECES = [
    ('s_chop', dusk, turn, None, 0),
    ('s_chop', turn, count('s_chop'), HOUR, 400),
    ('s_run1', 0, count('s_run1'), HOUR, 400),
    ('s_run2', 0, count('s_run2'), HOUR, 400),
    ('s_porch', 0, count('s_porch'), ['HOME IN *TIME*'], 400),
    ('s_door', 0, min(count('s_door'), at('s_door', 'door_close') + 16), None, 0),
    # Back to the swings before the warning, at the first shot's angle: it runs into the first frame.
    ('s_chop', 0, dusk, ['FOLLOW FOR MORE', '*COZY* MODS'], 1250),
]
for scene, first, last, *_ in PIECES:
    assert 0 <= first < last <= count(scene), f'{scene}: frames {first}-{last} of {count(scene)}'

FONTS = [os.environ.get('CAPTION_FONT', ''), 'C:/Windows/Fonts/ariblk.ttf', '/usr/share/fonts/truetype/msttcorefonts/Arial_Black.ttf',
         '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf']
BADGE_FONTS = [os.environ.get('BADGE_FONT', ''), 'C:/Windows/Fonts/segoeuib.ttf', '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf']


def font(paths, size):
    for p in paths:
        if p and os.path.exists(p):
            return ImageFont.truetype(p, size)
    sys.exit('no caption font found; set CAPTION_FONT')


def run(*args):
    subprocess.run(args, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)


CAPTION, SIZE, STROKE, LEAD = font(FONTS, 84), 84, 7, 104


def caption(lines, top):
    """A transparent frame-sized layer with the caption lines, centred."""
    layer = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    y = top
    for line in lines:
        parts = [(p, GOLD if i % 2 else (255, 255, 255)) for i, p in enumerate(line.split('*')) if p]
        x = (W - sum(d.textlength(p, font=CAPTION) for p, _ in parts)) / 2
        for text, colour in parts:
            d.text((x, y), text, font=CAPTION, fill=colour, stroke_width=STROKE, stroke_fill=(0, 0, 0))
            x += d.textlength(text, font=CAPTION)
        y += LEAD
    return layer


BADGE_BOX = (40, 250, 520, 350)


def badge():
    """A small mod-list style badge: the icon, the name, the loaders."""
    layer = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    box = BADGE_BOX
    d.rounded_rectangle(box, 18, fill=(20, 20, 24, 225))
    side = box[3] - box[1] - 28
    icon = Image.open(os.path.join(repo, 'common/src/main/resources/assets/nightfallclock/icon.png')).convert('RGBA')
    layer.alpha_composite(icon.resize((side, side), Image.NEAREST), (box[0] + 14, box[1] + 14))
    x = box[0] + side + 32
    d.text((x, box[1] + 12), 'Nightfall Clock', font=font(BADGE_FONTS, 40), fill=(255, 255, 255))
    d.text((x, box[1] + 60), 'Fabric · NeoForge', font=font(BADGE_FONTS, 24), fill=(170, 170, 180))
    return layer


BADGE = badge()

# The HUD line, filmed at GUI scale 4 in survival: 59 GUI pixels above the bottom, 9 tall plus the
# shadow (Hud.extractSelectedItemName). The game draws it in flat colours: the text and its shadow
# at a quarter of the brightness, so it can be cut out by colour alone.
LINE_TOP, LINE_BOTTOM, GUI = H - 59 * 4 - 4, H - 59 * 4 + 44, 4
INKS = [((255, 170, 0), (63, 42, 0)), ((255, 255, 255), (63, 63, 63))]  # (text, its shadow): gold, white
LIFT_Y = 820  # where the lifted line's middle sits: the middle band, clear of the Shorts title
LIFT_MAX_W = 900  # clear of the buttons down the right


def lift(game):
    """The mod's text on the HUD line, cut out and enlarged by a whole number of pixels per GUI pixel."""
    band = np.asarray(game.crop((120, LINE_TOP, W - 120, LINE_BOTTOM)).convert('RGB')).astype(int)
    mask = np.zeros(band.shape[:2], bool)
    for ink, shade in INKS:
        text = np.abs(band - ink).sum(axis=2) < 24
        # A shadow pixel counts only under its letter, one GUI pixel up and left: a grey door band
        # or a dark flower of the shadow's colour on its own doesn't.
        under = np.zeros_like(text)
        under[GUI:, GUI:] = text[:-GUI, :-GUI]
        mask |= text | ((np.abs(band - shade).sum(axis=2) < 24) & under)
    inked = mask.any(axis=0)
    cols = np.where(inked)[0]
    if len(cols) < 20:
        return None
    # The text is centred: grow out from the inked column nearest the middle, across gaps no wider
    # than a space (4 GUI pixels), so a lantern or a flower of the same colour nearby isn't taken in.
    middle = mask.shape[1] // 2
    x0 = x1 = int(cols[np.argmin(np.abs(cols - middle))])
    gap = 5 * GUI
    while True:
        left = cols[(cols < x0) & (cols >= x0 - gap)]
        right = cols[(cols > x1) & (cols <= x1 + gap)]
        if not len(left) and not len(right):
            break
        x0 = int(left.min()) if len(left) else x0
        x1 = int(right.max()) if len(right) else x1
    x1 += 1
    mask[:, :x0] = False
    mask[:, x1:] = False
    rows = np.where(mask.any(axis=1))[0]
    y0, y1 = int(rows[0]), int(rows[-1]) + 1
    text = Image.fromarray(band[y0:y1, x0:x1].astype('uint8')).convert('RGBA')
    text.putalpha(Image.fromarray(mask[y0:y1, x0:x1].astype('uint8') * 255))
    per = int(max(GUI, min(11, LIFT_MAX_W * GUI // max(1, int(x1 - x0)))))  # screen pixels per GUI pixel
    text = text.resize(((x1 - x0) * per // GUI, (y1 - y0) * per // GUI), Image.NEAREST)
    # A soft dark glow round it, so it reads over the sky as well as the grass.
    pad = per * 2
    glow = Image.new('L', (text.width + pad * 2, text.height + pad * 2), 0)
    glow.paste(text.getchannel('A'), (pad, pad))
    glow = glow.filter(ImageFilter.MaxFilter(9)).filter(ImageFilter.GaussianBlur(per))
    layer = Image.new('RGBA', glow.size, (0, 0, 0, 0))
    layer.putalpha(glow.point(lambda v: int(v * 0.7)))
    layer.alpha_composite(text, (pad, pad))
    return layer


CAPS = {}
for scene, first, last, lines, top in PIECES:
    if lines:
        CAPS[tuple(lines)] = caption(lines, top)

FADE_IN = 6  # frames for a caption to fade in; pieces cut hard
OUT_FPS = FPS


def smooth(p):
    """A piece's filmed frames, game footage only, before the text goes on."""
    scene, first, last, *_ = PIECES[p]
    dst = os.path.join(work, 'smooth', str(p))
    os.makedirs(dst)
    run('ffmpeg', '-y', '-framerate', str(FPS), '-start_number', str(first), '-i', os.path.join(frames_dir, scene + '_%04d.png'),
        '-vf', f'trim=end_frame={last - first}',
        os.path.join(dst, '%04d.png'))
    return len(os.listdir(dst))


with ThreadPoolExecutor(4) as pool:
    counts = list(pool.map(smooth, range(len(PIECES))))

lifted = {}


def lifted_for(scene, i):
    """The HUD text of a filmed frame, lifted (cached)."""
    if (scene, i) not in lifted:
        lifted[(scene, i)] = lift(Image.open(os.path.join(frames_dir, '%s_%04d.png' % (scene, i))).convert('RGBA'))
    return lifted[(scene, i)]


jobs = [(p, k) for p in range(len(PIECES)) for k in range(counts[p])]
for p in range(len(PIECES)):
    os.makedirs(os.path.join(work, 'frames', str(p)))


def frame(job):
    p, k = job
    scene, first, last, lines, top = PIECES[p]
    img = Image.open(os.path.join(work, 'smooth', str(p), '%04d.png' % (k + 1))).convert('RGBA')
    text = lifted_for(scene, first + min(last - first - 1, round(k * FPS / OUT_FPS)))
    if text is not None:
        img.alpha_composite(text, ((W - text.width) // 2, LIFT_Y - text.height // 2))
    img.alpha_composite(BADGE)
    if lines:
        layer = CAPS[tuple(lines)]
        # A caption carried on from the piece before doesn't fade in again.
        a = 1.0 if p > 0 and PIECES[p - 1][3] == lines else min(1.0, (k + 1) / FADE_IN)
        if a < 1:
            layer = layer.copy()
            layer.putalpha(layer.getchannel('A').point(lambda v: int(v * a)))
        img.alpha_composite(layer)
    img.convert('RGB').save(os.path.join(work, 'frames', str(p), '%04d.png' % k), compress_level=1)


with ThreadPoolExecutor(os.cpu_count()) as pool:
    list(pool.map(frame, jobs))


def encode(p):
    clip = os.path.join(work, f'piece{p}.mp4')
    run('ffmpeg', '-y', '-framerate', str(OUT_FPS), '-i', os.path.join(work, 'frames', str(p), '%04d.png'),
        '-vf', 'format=yuv420p', '-c:v', 'libx264', '-preset', 'slow', '-crf', '18', clip)
    return clip


with ThreadPoolExecutor(4) as pool:
    clips = list(pool.map(encode, range(len(PIECES))))
lengths = [c / OUT_FPS for c in counts]
total = sum(lengths)
with open(os.path.join(work, 'list.txt'), 'w') as f:
    f.writelines(f"file '{c.replace(os.sep, '/')}'\n" for c in clips)
silent = os.path.join(out, NAME + '-silent.mp4')
run('ffmpeg', '-y', '-f', 'concat', '-safe', '0', '-i', os.path.join(work, 'list.txt'), '-c', 'copy', '-movflags', '+faststart', silent)

# ------------------------------------------------------------------ sound
# The game's own sounds at the logged frames, laid down here rather than in one big ffmpeg graph.
RATE = 44100
cache = {}


def sound(name):
    if name not in cache:
        raw = subprocess.run(['ffmpeg', '-v', 'quiet', '-i', os.path.join(sounds_dir, name + '.ogg'), '-f', 'f32le', '-ac', '2',
                              '-ar', str(RATE), '-'], check=True, capture_output=True).stdout
        cache[name] = np.frombuffer(raw, np.float32).reshape(-1, 2)
    return cache[name]


mix = np.zeros((int(total * RATE) + RATE, 2), np.float32)
random.seed(11)


def cue(when, name, volume, pitch=1.0):
    s = sound(name)
    if pitch != 1.0:
        idx = np.arange(0, len(s) - 1, pitch)
        s = np.stack([np.interp(idx, np.arange(len(s)), s[:, c]) for c in range(2)], axis=1)
    start = int(when * RATE)
    if 0 <= start < len(mix):
        end = min(len(mix), start + len(s))
        mix[start:end] += s[:end - start] * volume


piece_start = 0.0
for (scene, first, last, *_), piece_length in zip(PIECES, lengths):
    def t(frame):
        return piece_start + (frame - first) / FPS
    marks = [(f, k) for s, f, k, _ in actions if s == scene and first <= f < last]
    if scene == 's_chop':
        chop, turn = at('s_chop', 'chop'), at('s_chop', 'turn')
        for f in range(max(first, chop + 3), min(last, turn), 5):
            cue(t(f), f'dig_wood{random.randint(1, 4)}', 0.22, random.uniform(0.75, 0.85))
    for f, k in marks:
        if k == 'log':
            cue(t(f), f'dig_wood{random.randint(1, 4)}', 0.8, random.uniform(0.9, 1.05))
        elif k == 'dusk':
            cue(t(f), 'chime', 0.55, 0.75)
            cue(t(f) + 0.18, 'chime', 0.4, 1.0)
        elif k == 'door_close':
            cue(t(f), 'door_close1', 0.9)
        elif k == 'zombie':
            cue(t(f) + 1.0, 'zombie1', 0.45, 0.95)
    run_at = [f for f, k in marks if k == 'run']
    if run_at or scene in ('s_run1', 's_run2', 's_porch'):
        start = run_at[0] if run_at else first
        stop = last - 12 if scene == 's_porch' else last
        for f in range(max(first, start + 2), stop, 6):
            on_porch = scene == 's_porch' and f > (last - 12) * 0.62
            cue(t(f), (f'step_wood{random.randint(1, 6)}' if on_porch else f'step_grass{random.randint(1, 6)}'),
                0.3, random.uniform(0.95, 1.05))
    piece_start += piece_length
mix = mix[:int(total * RATE)]


def write_wav(path, samples):
    raw = os.path.join(work, 'raw.f32')
    np.clip(samples, -1, 1).astype(np.float32).tofile(raw)
    # About -14 LUFS for the Shorts feed: measured first, then set in one linear step (loudnorm's
    # single pass guesses, and missed by 2 LU here).
    target = 'I=-14:TP=-1.5:LRA=11'
    log = subprocess.run(['ffmpeg', '-f', 'f32le', '-ar', str(RATE), '-ac', '2', '-i', raw, '-af', f'loudnorm={target}:print_format=json',
                          '-f', 'null', '-'], check=True, capture_output=True, text=True).stderr
    m = json.loads(log[log.rindex('{'):log.rindex('}') + 1])
    run('ffmpeg', '-y', '-f', 'f32le', '-ar', str(RATE), '-ac', '2', '-i', raw, '-af',
        f"loudnorm={target}:measured_I={m['input_i']}:measured_TP={m['input_tp']}:measured_LRA={m['input_lra']}"
        f":measured_thresh={m['input_thresh']}:offset={m['target_offset']}:linear=true", '-ar', str(RATE), path)


def mux(wav, name):
    run('ffmpeg', '-y', '-i', silent, '-i', wav, '-map', '0:v', '-map', '1:a', '-c:v', 'copy', '-c:a', 'aac', '-b:a', '160k',
        '-shortest', '-movflags', '+faststart', os.path.join(out, name))


write_wav(os.path.join(work, 'game.wav'), mix)
mux(os.path.join(work, 'game.wav'), NAME + '.mp4')

for k, (track, start) in enumerate(music, 1):
    raw = subprocess.run(['ffmpeg', '-v', 'quiet', '-ss', str(start), '-t', f'{total:.3f}', '-i', track, '-f', 'f32le', '-ac', '2',
                          '-ar', str(RATE), '-'], check=True, capture_output=True).stdout
    m = np.frombuffer(raw, np.float32).reshape(-1, 2).copy()
    m = np.pad(m, ((0, max(0, len(mix) - len(m))), (0, 0)))[:len(mix)]
    fade_in, fade_out = int(0.4 * RATE), int(0.9 * RATE)
    m[:fade_in] *= np.linspace(0, 1, fade_in)[:, None]
    m[-fade_out:] *= np.linspace(1, 0, fade_out)[:, None]
    # Music soft under the game: about a third of the game sounds' peak loudness.
    level = 0.3 * (np.abs(mix).max() or 1) / (np.abs(m).max() or 1)
    write_wav(os.path.join(work, f'music{k}.wav'), mix + m * level)
    mux(os.path.join(work, f'music{k}.wav'), f'{NAME}-music-{k}.mp4')
    print(f'music {k}:', os.path.basename(track))

# ------------------------------------------------------------------ the cover
# An opening frame with the warning lifted, and the mod's name large at the top of the safe area.
thumb = Image.open(os.path.join(frames_dir, 's_chop_%04d.png' % (dusk + 4))).convert('RGBA')
warning = lift(thumb)
thumb.alpha_composite(warning, ((W - warning.width) // 2, LIFT_Y + 60 - warning.height // 2))
d = ImageDraw.Draw(thumb)
size, top = 150, 330
big = font(FONTS, size)
for i, parts in enumerate([[('NIGHTFALL', (255, 255, 255))], [('CLOCK', GOLD)]]):
    x = (W - sum(d.textlength(text, font=big) for text, _ in parts)) / 2
    for text, colour in parts:
        d.text((x, top + i * size * 1.1), text, font=big, fill=colour, stroke_width=size // 12, stroke_fill=(0, 0, 0))
        x += d.textlength(text, font=big)
thumb.convert('RGB').save(os.path.join(out, NAME + '-thumbnail.png'))
print('total seconds', round(total, 2))
for (scene, first, last, *_), piece_length in zip(PIECES, lengths):
    print(f'  {scene} {first}-{last}: {piece_length:.2f} s')
