# Progress

What's done, what's broken, what's next. Newest entry first. Each session adds a row before it
ends, saying what changed, what the game test counted, what the proof run was (the feature off,
which checks fail, code put back), what was learned, and what was deliberately not done.

## Where things stand

- **Done:** Nightfall Clock 0.2.1: the clock above the hotbar and the dusk warning, text backgrounds in the corners, 12-hour time where the region writes it that way, no false warning on server networks; unit tests and an in-game test on 26.2 and 26.3, run by CI on every push.
- **Broken:** Nothing known as of this writing (not re-verified).
- **Next:** The October 2026 review plan in CLAUDE.md lists what is still open.

## Log

| Date | Session | What happened |
| --- | --- | --- |
| 2026-10-16 | NeoForge | `main` restructured into `common` (shared code, unit tests), `fabric` and `neoforge`, on branch `multiloader-26.x`, the same layout as the 1.21.1 branch. Both jars build against 26.2. 11 unit tests pass (8 + 3, in `common` and again on NeoForge). The Fabric game test passes on 26.2 and 26.3; the NeoForge jar was loaded in a world on 26.2 and 26.3 and the clock showed above the hotbar. No automated NeoForge game test yet. Not done: release, version bump. |
| 2026-10-15 | Map | The one-job-per-mod map (Cozy Core: builder rules; Espresso Flavoured Core: the café; Spawn Gazebo, Nightfall Clock, Pocket List: one thing each; the pack: the bundle) written into CLAUDE.md, the same in every repo. No code change. |
| 2026-10-15 | Notes | `PROGRESS.md` added, with the rows below reconstructed from the commit history (one row per day, the commits' own words); the two shared rules (commits one at a time by hand, proof runs for data changes) added to `CLAUDE.md`. No code change. |
| 2026-10-14 | (from history) | Drop the template's modid comment from settings.gradle; 0.2.1: the clock no longer hides when Espresso Flavoured Core is installed. |
| 2026-10-13 | (from history) | Nightfall Clock 0.2.0; No false dusk warning on server networks, safer settings, and unit tests; Game test checks the clock's text and the Nether; shared rules in CLAUDE.md; Review fixes, corner backdrop, and in-game tests on 26.2 and 26.3. |
| 2026-10-12 | (from history) | README: top corners may clash with minimaps; Build on every push, and a changelog; Link the GitHub page, source and issues from the mod info; Nightfall Clock 0.1.0. |
