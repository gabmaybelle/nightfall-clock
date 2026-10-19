# The YouTube Short

Makes `nightfall-clock-short.mp4` (with the game's own sounds), `nightfall-clock-short-silent.mp4`
and `nightfall-clock-short-thumbnail.png`, a vertical 1080x1920 Short. Needs ffmpeg, Python 3 with
Pillow, and Chrome or Chromium.

1. Film it: `./gradlew :fabric:runClientGameTest -PvideoTour`, with `-Pcottage=<file>` to film at a
   house built from a `.mcfunction` file: on its front path, and from the lawn to the east with the
   sun setting beside it (without the file the scenes play on open ground). On
   Linux without a screen, run it under `xvfb-run -a -s "-screen 0 1920x1920x24"` with
   `SDL_VIDEO_FORCE_EGL=1`. `VideoTour` saves a frame per tick, `actions.tsv` and `frames.tsv` in
   `fabric/build/run/clientGameTest/video_tour`. The next game-test run clears that folder, so copy
   it somewhere first.
2. `python3 -I tools/video/get_sounds.py <sounds dir>`: the sounds, from the game's asset cache.
3. `python3 -I tools/video/assemble_short.py <video_tour folder> <sounds dir> <out dir>`

Keep the frames, sounds and videos out of the repo. If a scene changes, change its caption in
`assemble_short.py` too.
