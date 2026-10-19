# Progress

What's done, what's broken, what's next. Newest entry first. Each session adds a row before it
ends, saying what changed, what the game test counted, what the proof run was (the feature off,
which checks fail, code put back), what was learned, and what was deliberately not done.

## Where things stand

- **Done:** Nightfall Clock 0.3.1 (Fabric and NeoForge, 26.2 and 26.3; 0.3.0 released, 0.3.1 being released): the clock above the hotbar and the dusk warning, text backgrounds in the corners, 12-hour time where the region writes it that way, no false warning on server networks; unit tests and an in-game test on 26.2 and 26.3, run by CI on every push, for both the Fabric and the NeoForge jar. 0.3.1: the clock reads the sun the way the sky does.
- **Broken:** Nothing known as of this writing (not re-verified).
- **Next:** The October 2026 review plan in CLAUDE.md lists what is still open.

## Log

| Date | Session | What happened |
| --- | --- | --- |
| 2026-10-19 | Video Short (not finished) | Branch `claude/video-tour`: `VideoTour` (only with `-PvideoTour`, the cottage passed in with `-Pcottage`) films a vertical Short on the cottage porch: a dusk take racing to 6:00 PM, morning with the clock racing, switching hotbar items, and the settings screen (bottom-left corner, 24-hour). `tools/video/` sets the frames into 1080x1920 with captions in the Shorts safe zone and adds the game's sounds. Not filmed and not game-tested: the session's network blocked `resources.download.minecraft.net`, and without the game's assets the resource reload fails, chunks never draw and the game crashes, so the normal game test failed there too (before any change). `./gradlew :common:test :fabric:build` passed against 26.3 (Mod Menu stubbed, not committed). Next: allow the host, run the normal game test, film, check frames, assemble, deliver to mods-media. |
| 2026-10-16 | Release prep | Version 0.3.1: the changelog's Unreleased line (the clock reads the sun through the camera, as the sky does) becomes 0.3.1. No code change in this step; the fix and the NeoForge in-game test were merged before it, with CI green on 26.2 and 26.3 for both jars. |
| 2026-10-16 | NeoForge test proof | The NeoForge in-game test now names its world with the time, so a run never reopens a world an earlier run left in the same game folder (a 26.3 run once reopened a 26.2 world and failed with no day cycle). Proof run: with the dusk warning switched off the NeoForge test failed ("timed out waiting for the dusk warning while playing through 6:00 PM"); code put back, the test passes again on 26.2 and 26.3. Not merged. |
| 2026-10-16 | Release prep | Version 0.3.0 and its changelog; README requirements, other-versions links and build commands brought up to date for the Fabric and NeoForge jars. No code change. Not released. |
| 2026-10-16 | NeoForge | `main` restructured into `common` (shared code, unit tests), `fabric` and `neoforge`, on branch `multiloader-26.x`, the same layout as the 1.21.1 branch. Both jars build against 26.2. 11 unit tests pass (8 + 3, in `common` and again on NeoForge). The Fabric game test passes on 26.2 and 26.3; the NeoForge jar was loaded in a world on 26.2 and 26.3 and the clock showed above the hotbar. No automated NeoForge game test yet. Not done: release, version bump. |
| 2026-10-15 | Map | The one-job-per-mod map (Cozy Core: builder rules; Espresso Flavoured Core: the café; Spawn Gazebo, Nightfall Clock, Pocket List: one thing each; the pack: the bundle) written into CLAUDE.md, the same in every repo. No code change. |
| 2026-10-15 | Notes | `PROGRESS.md` added, with the rows below reconstructed from the commit history (one row per day, the commits' own words); the two shared rules (commits one at a time by hand, proof runs for data changes) added to `CLAUDE.md`. No code change. |
| 2026-10-14 | (from history) | Drop the template's modid comment from settings.gradle; 0.2.1: the clock no longer hides when Espresso Flavoured Core is installed. |
| 2026-10-13 | (from history) | Nightfall Clock 0.2.0; No false dusk warning on server networks, safer settings, and unit tests; Game test checks the clock's text and the Nether; shared rules in CLAUDE.md; Review fixes, corner backdrop, and in-game tests on 26.2 and 26.3. |
| 2026-10-12 | (from history) | README: top corners may clash with minimaps; Build on every push, and a changelog; Link the GitHub page, source and issues from the mod info; Nightfall Clock 0.1.0. |
