"""Lays a recorded voiceover over a video, line by line, with the music ducked under the voice, and
brings the whole mix to YouTube's loudness (about -14 LUFS).

python add_voice.py <video.mp4> <voice recording> <cues.txt> <music track> <out.mp4> [music start] [--plain]

cues.txt has one line per spoken piece: `<start> <end> <at>` in seconds, where start and end are
where the piece sits in the recording and at is where it goes in the video (# starts a comment).
The voice is cleaned gently (rumble cut, light hiss reduction, a little clarity, evened out,
levelled), or with --plain only the rumble cut and one fixed gain, for a voice that sounds
dampened by any clean-up; the music
sits softly under the game's own sounds and dips further while the voice speaks. The video stream
is copied as it is. Music from YouTube's Audio Library only.
"""
import json, subprocess, sys

plain = '--plain' in sys.argv
args = [a for a in sys.argv[1:] if a != '--plain']
video, voice, cues_file, track, out = args[:5]
music_start = float(args[5]) if len(args) > 5 else 0.0
probe = subprocess.run(['ffprobe', '-v', 'error', '-show_entries', 'format=duration', '-of', 'json', video],
                       check=True, capture_output=True, text=True)
length = float(json.loads(probe.stdout)['format']['duration'])

cues = []
for line in open(cues_file, encoding='utf-8'):
    line = line.split('#')[0].strip()
    if line:
        start, end, at = (float(x) for x in line.split())
        cues.append((start, end, at))

parts = []
# The recording, cleaned once, then split into its cues.
if plain:
    # Only the rumble cut and one fixed gain to -18 LUFS, so the voice sounds as recorded.
    meter = subprocess.run(['ffmpeg', '-hide_banner', '-i', voice, '-af', 'highpass=f=80,ebur128', '-f', 'null', '-'],
                           capture_output=True, text=True).stderr
    measured = float(meter.rsplit('I:', 1)[1].split()[0])
    clean = f'highpass=f=80,volume={-18 - measured:.1f}dB,alimiter=limit=0.7,'
else:
    clean = ('highpass=f=100,afftdn=nr=5:nf=-50,'
             # clarity, not warmth: a low boost or stronger hiss reduction makes a phone memo sound dampened
             'equalizer=f=300:t=q:w=1:g=-2,equalizer=f=4000:t=q:w=1:g=3,highshelf=f=8000:g=3,'
             'acompressor=threshold=-24dB:ratio=3:attack=5:release=120,loudnorm=I=-18:TP=-3,')
parts.append('[1:a]aformat=sample_rates=48000:channel_layouts=mono,' + clean +
             f'asplit={len(cues)}' + ''.join(f'[r{i}]' for i in range(len(cues))))
for i, (start, end, at) in enumerate(cues):
    ms = int(at * 1000)
    fade = min(0.04, (end - start) / 4)
    parts.append(f'[r{i}]atrim=start={start}:end={end},asetpts=PTS-STARTPTS,'
                 f'afade=t=in:d={fade}:curve=tri,afade=t=out:st={end - start - fade:.3f}:d={fade},'
                 f'adelay={ms}[c{i}]')
parts.append(''.join(f'[c{i}]' for i in range(len(cues))) +
             f'amix=inputs={len(cues)}:normalize=0,apad=whole_dur={length:.3f},atrim=0:{length:.3f},'
             'aformat=sample_rates=44100:channel_layouts=stereo,asplit=2[v][key]')
# Music: soft under the game, ducked while the voice speaks, faded at the end so the loop doesn't jump.
parts.append(f'[2:a]atrim=start={music_start}:duration={length},asetpts=PTS-STARTPTS,'
             'aformat=sample_rates=44100:channel_layouts=stereo,loudnorm=I=-30:TP=-8,'
             f'afade=t=in:d=0.4,afade=t=out:st={max(0.0, length - 1.0):.3f}:d=1.0[m0]')
parts.append('[m0][key]sidechaincompress=threshold=0.02:ratio=10:attack=15:release=350[m]')
parts.append('[0:a]aformat=sample_rates=44100:channel_layouts=stereo,volume=0.8[g]')
parts.append('[g][m][v]amix=inputs=3:normalize=0,loudnorm=I=-14:TP=-1.5:LRA=11,aresample=44100[a]')

subprocess.run(['ffmpeg', '-y', '-v', 'error', '-i', video, '-i', voice, '-i', track,
                '-filter_complex', ';'.join(parts), '-map', '0:v', '-map', '[a]', '-c:v', 'copy',
                '-c:a', 'aac', '-b:a', '192k', '-shortest', '-movflags', '+faststart', out], check=True)
print(out)
