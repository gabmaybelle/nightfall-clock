# Progress

What's done, what's broken, what's next. Newest entry first. Each session adds a row before it
ends, saying what changed, what the game test counted, what the proof run was (the feature off,
which checks fail, code put back), what was learned, and what was deliberately not done.

## Where things stand

- **1.20.1 branch (`1.20.1`, not pushed):** Fabric and Forge for Minecraft 1.20.1 from one shared code part (`common`), committed locally. The unit tests pass on both; the Fabric jar was run in a 1.20.1 world; the Forge jar has been built but not run in a game. See the row for 2026-10-15 (1.20.1 port).
- **Done (`main`):** Nightfall Clock 0.2.1: the clock above the hotbar and the dusk warning, text backgrounds in the corners, 12-hour time where the region writes it that way, no false warning on server networks; unit tests and an in-game test on 26.2 and 26.3, run by CI on every push.
- **Broken:** Nothing known as of this writing (not re-verified).
- **Next:** The October 2026 review plan in CLAUDE.md lists what is still open.

## Log

| Date | Session | What happened |
| --- | --- | --- |
| 2026-10-15 | 1.20.1 port | New branch `1.20.1`: the clock and dusk warning for Minecraft 1.20.1 on Fabric, then on Forge too. The 26.x code needed rewriting for 1.20.1: the HUD (`Hud`, `GuiGraphicsExtractor` and `HudElementRegistry` became `Gui`, `GuiGraphics` and `HudRenderCallback`), the sun reading (1.20.1 has no timelines, so the easing is vanilla's `DimensionType.timeOfDay` curve and the day number is world time over 24,000), the text backdrop (no helper in `GuiGraphics`, so a box tinted by the colour is filled by hand), the settings screen (no `addOptions` hook) and the accessor targets (`Gui`, `net.minecraft.client.GuiMessage`). Then the code was split into `common`, `fabric` and `forge`. **Counted:** the 11 unit tests (8 dusk, 3 sun) pass in `common` and again on Forge's classes. A real 1.20.1 game with the Fabric jar and Fabric API 0.92.12 showed "Day 1 · 6:24 AM" above the hotbar and, after `/time set 11900`, the gold "The sun's getting real low". **Not done:** the Forge jar has not been run in a game; NeoForge 1.20.1 (47.1) is untested; the new CI workflow has not run; no proof run with the dusk feature off; the in-game test mod stays on `main` since 1.20.1 has no client game test; no stretched-day pack was tried. **Learned:** Forge's build tools need a Java 17 install, and the Forge unit tests need Minecraft added to the test classpath by hand (the legacy plugin has no unit-test block). |
| 2026-10-15 | Map | The one-job-per-mod map (Cozy Core: builder rules; Espresso Flavoured Core: the café; Spawn Gazebo, Nightfall Clock, Pocket List: one thing each; the pack: the bundle) written into CLAUDE.md, the same in every repo. No code change. |
| 2026-10-15 | Notes | `PROGRESS.md` added, with the rows below reconstructed from the commit history (one row per day, the commits' own words); the two shared rules (commits one at a time by hand, proof runs for data changes) added to `CLAUDE.md`. No code change. |
| 2026-10-14 | (from history) | Drop the template's modid comment from settings.gradle; 0.2.1: the clock no longer hides when Espresso Flavoured Core is installed. |
| 2026-10-13 | (from history) | Nightfall Clock 0.2.0; No false dusk warning on server networks, safer settings, and unit tests; Game test checks the clock's text and the Nether; shared rules in CLAUDE.md; Review fixes, corner backdrop, and in-game tests on 26.2 and 26.3. |
| 2026-10-12 | (from history) | README: top corners may clash with minimaps; Build on every push, and a changelog; Link the GitHub page, source and issues from the mod info; Nightfall Clock 0.1.0. |
