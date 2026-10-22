"""Lays a recorded voiceover over a video, line by line, with the music ducked under the voice, and
brings the whole mix to YouTube's loudness (about -14 LUFS).

python add_voice.py <video.mp4> <voice recording> <cues.txt> <music track> <out.mp4> [music start]

cues.txt has one line per spoken piece: `<start> <end> <at>` in seconds, where start and end are
where the piece sits in the recording and at is where it goes in the video (# starts a comment).
The voice is cleaned gently (rumble cut, light hiss reduction, evened out, levelled); the music
sits softly under the game's own sounds and dips further while the voice speaks. The video stream
is copied as it is. Music from YouTube's Audio Library only.
"""
import json, subprocess, sys

video, voice, cues_file, track, out = sys.argv[1:6]
music_start = float(sys.argv[6]) if len(sys.argv) > 6 else 0.0
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
parts.append('[1:a]aformat=sample_rates=48000:channel_layouts=mono,highpass=f=80,afftdn=nr=10:nf=-45,'
             'acompressor=threshold=-24dB:ratio=3:attack=5:release=120,loudnorm=I=-18:TP=-3,'
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
             'aformat=sample_rates=44100:channel_layouts=stereo,loudnorm=I=-26:TP=-6,'
             f'afade=t=in:d=0.4,afade=t=out:st={max(0.0, length - 1.0):.3f}:d=1.0[m0]')
parts.append('[m0][key]sidechaincompress=threshold=0.03:ratio=6:attack=20:release=300[m]')
parts.append('[0:a]aformat=sample_rates=44100:channel_layouts=stereo,volume=0.8[g]')
parts.append('[g][m][v]amix=inputs=3:normalize=0,loudnorm=I=-14:TP=-1.5:LRA=11,aresample=44100[a]')

subprocess.run(['ffmpeg', '-y', '-v', 'error', '-i', video, '-i', voice, '-i', track,
                '-filter_complex', ';'.join(parts), '-map', '0:v', '-map', '[a]', '-c:v', 'copy',
                '-c:a', 'aac', '-b:a', '192k', '-shortest', '-movflags', '+faststart', out], check=True)
print(out)
