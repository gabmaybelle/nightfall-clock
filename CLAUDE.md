# Nightfall Clock

A client-side mod for Minecraft 1.21.1 on Fabric and NeoForge (this branch; `main` is 26.2 and 26.3 on Fabric, `1.20.1` is Fabric and Forge): a day counter and clock above the hotbar, and "The sun's getting real low" at 6:00 PM. It reads the time from the sun's angle, not from tick counts, so it stays right on packs with longer or stretched days. See README.md for what players see.

`PROGRESS.md` says what's done, what's broken and what's next, with a row per session: read
it first, and add this session's row before ending.

## Layout

Three Gradle projects: `common` holds everything that doesn't care about the loader, and `fabric` and `neoforge` compile those sources straight into their own jars.

- `common/src/main/java/com/gabbymaybelle/nightfallclock/`
  - `NightfallClock.java`: HUD drawing (`extract`), the tick handler (`tick`: dusk detection, fades), and the `DayTime` record (sun angle to clock time). Each loader calls the two from its own events.
  - `Config.java`: `config/nightfallclock.properties`, the `Position` and `TimeFormat` enums; `Config.load(Path)` is given the loader's config folder.
  - `SettingsScreen.java`: the settings screen, built from vanilla option widgets.
  - `DuskWatch.java`: the dusk rule, free of Minecraft classes.
  - `mixin/HudAccessor.java` and `mixin/ChatComponentAccessor.java`: read-only accessors for vanilla fields.
- `common/src/main/resources/`: the lang file, icon and mixin config. `common/src/test/`: the unit tests.
- `fabric/`: `NightfallClockFabric` (Fabric API HUD and tick events), `ModMenuIntegration` (optional, compile-only), `fabric.mod.json`.
- `neoforge/`: `NightfallClockNeoForge` (client-only `@Mod`: NeoForge's GUI layer and tick events, and the config screen on the Mods screen), `META-INF/neoforge.mods.toml`.
- `tools/generate-icon.js`: draws `icon.png` (`node tools/generate-icon.js`).

In-game tests: `fabric/src/gametest` (Fabric has no client game test API for 1.21.1, so `harness/` is a small one of our own, copied from Pocket List) and `neoforge/src/clienttest` (a step-by-step test, part of the mod only in the test run). Both check the clock's time at noon, 6:00 PM and midnight, the dusk warning, and the clock stepping aside for the held item's name and the action bar; the Fabric one also checks the clock text and the Nether. They reach the clock through `NightfallClock.duskShowing()` and `clockHidden()`, package-private hooks for the tests.

## Build and test

Needs JDK 21 or newer to run Gradle. The code is compiled for Java 21 whichever JDK compiles it.

- `./gradlew build`: `fabric/build/libs/nightfallclock-fabric-<version>.jar` and `neoforge/build/libs/nightfallclock-neoforge-<version>.jar`. The first NeoForge build takes a few minutes (Minecraft is decompiled once).
- `./gradlew :common:test` and `./gradlew :neoforge:test` run the same unit tests (the dusk rule and reading the time from the sun); `build` runs both. `:neoforge:test` proves them on NeoForge's classes too.
- `./gradlew :fabric:runClient` and `:neoforge:runClient`: a dev game with the mod.
- `./gradlew :fabric:runClientGameTest` and `:neoforge:runClientTest`: the in-game tests (each opens a game window, closes it when done, and fails the task on a failed check).

CI (`.github/workflows/build.yml`) builds both jars and runs the unit tests and both in-game tests (under `xvfb-run`), with Java 25. A live check of the settings screen in a 1.21.1 game is done by hand before a release.

## Gotchas

- **The 26.3 game test on a virtual display (the `main` branch).** 26.3's renderer (`com.mojang.renderpearl`, on SDL3) needs an sRGB-capable window. Xvfb's GLX has no sRGB visuals, so the game fails to make a window and then hangs instead of exiting. Fix: set `SDL_VIDEO_FORCE_EGL=1` and install `libegl1 libegl-mesa0`. CI does both, and the test step has a 10-minute timeout. 26.2 opens its window through GLFW and isn't affected.
- **Fabric API versions are parallel per Minecraft version**; this branch builds against `0.116.17+1.21.1` and `fabric.mod.json` requires `>=0.116.0`.
- **No refmap on either side.** NeoForge 1.21.1 runs on Mojang's names, so the accessors need none; on Fabric, Loom 1.18 writes the intermediary names (`field_2041` and so on) straight into the accessor classes instead of adding a refmap (check with `javap -v` on the built jar).
- **Waiting for the time in game tests.** On 1.21.1 and 1.20.1 the server sends the time to the client only once a second, so after `/time set` the tests wait until the client has it, not a fixed few ticks. Commands also post feedback in chat, and the clock steps aside for recent chat: the tests switch `sendCommandFeedback` off and clear chat before the step-aside checks.
- **Mixin accessors fail hard.** If vanilla renames a field they read, the game crashes on launch. Compiling won't catch it, but the game test will. Before widening the supported versions, run the game test on the new version.
- **Copied vanilla values.** The sun easing (here `DimensionType.timeOfDay`, the same in 1.21.1 as in 1.20.1: angle 0 at tick 6000, noon; `main` copies the sun_angle timeline instead) and the text positions (item name 59 above the bottom, action bar 72, chat 40) are copied from vanilla. Check them when adding a Minecraft version.
- **Restricted networks.** Builds need `maven.terraformersmc.com` (Mod Menu), `maven.fabricmc.net`, Maven Central and Mojang's servers. Running the game also needs `resources.download.minecraft.net` for assets. On a network that blocks the Mod Menu server, a local stub of its two API interfaces (`ConfigScreenFactory`, `ModMenuApi`), added through a Gradle init script, is enough to compile. Don't commit it.

## Conventions

- Tabs for indentation. Imports are in alphabetical order (`com`, `java`, `net`, `org`).
- Comments explain why, in plain sentences, and name the vanilla source of any copied value.
- Every string players see goes in `assets/nightfallclock/lang/en_us.json`.
- Format numbers with `Locale.ROOT` so digits match the game's.
- Log messages start with "Nightfall Clock:".
- The README and CHANGELOG are written for players: plain and friendly, no jargon. Add player-facing changes under `## Unreleased` at the top of `CHANGELOG.md` (start that heading again after a release is numbered).
- The mod is credited as "made by Gabby with help from Claude, an AI assistant" (README). Keep that line.

## Review plan

A full review in October 2026 produced this plan. Done so far (merged to `main`):

1. **Quick fixes:** `gradlew` is executable. Auto 12-hour detection looks for an `h` or `K` hour field (it used to look for `a`, which missed regions such as Traditional Chinese, `Bh:mm`). Digits use `Locale.ROOT`. Fabric API needs `>=0.161.0`. README wording about `/time set`.
2. **Text Background in the corners:** the corner clock now draws with `textWithBackdrop`, like the line above the hotbar.
3. **26.3 support proven:** CI builds against both versions and runs the in-game test on each.

4. **Pure logic unit-tested** (`./gradlew test`, also part of `build`): the dusk rule now lives in
   `DuskWatch` (no Minecraft classes) with tests for crossing 6:00 PM, short and long jumps, joining
   after 6:00 PM, the midnight wrap, a world with no day and a different world (the proxy case);
   `minuteFromSun` has tests for noon, midnight, angles past 360 or below 0, and never going
   backwards. Config parsing and the clock text are checked by the game test instead (they need
   Fabric Loader and the game's translations).
5. **Fixes:** per-world timeline lookup and per-minute clock text are cached; the settings file is
   only written when a setting is missing, keeps unknown settings, is written to a temporary file
   and moved into place, and a value that isn't true or false keeps its default with a warning;
   the clock fades in alongside a permanent action bar; a different world resets the dusk tracking
   (no false warning behind a proxy); `chatCovers` caps at the lines unfocused chat shows. CI runs
   once per push (forks' pull requests too) with Gradle caching. The Java version is set once. The
   mixin config no longer sets `injectors.defaultRequire`.

Still to do, if wanted:

- **Draw order:** register the HUD element before chat instead of `addLast`, then simplify
  `chatCovers`. This changes how the clock looks under chat, so try it in game first.
- **Mod Menu in dev runs:** needs a different Mod Menu version for 26.2 and 26.3.
- **Lang keys** mix camelCase and snake_case. Renaming them would break any translation made since.
- No release workflow: Gabby publishes on Modrinth herself.

## Gabby's mods: who does what (the same in every repo)

One job per mod, decided 2026-10-15, so nothing is done twice and each repo tells the same story:

- **Cozy Core** (cozy-core): the Hearth Lantern, which makes the ground around it home: no
  monsters or phantoms spawn there, and creeper and ghast blasts, fire and endermen leave its
  blocks alone (each an ordinary game rule, with world-wide switches too); keep your hotbar on
  death, cheap wood in the stonecutter, re-dyeing and builder trades. Fabric and NeoForge jars, plus a plain
  data pack of the recipes.
- **Espresso Flavoured Core** (espresso-flavoured): the café: coffee you grow and brew, mugs,
  baristas, Bean Coins, the café charms, and Drowsy (coffee's reason to exist). Longer days stay a
  Core setting for now. The survival rules Cozy Core now covers left Core in 0.4.0 (its PLAN-0.4.0.md).
- **Spawn Gazebo** (spawn-gazebo): the gazebo at the world spawn and its starter chest.
- **Nightfall Clock** (nightfall-clock): the clock above the hotbar and the dusk warning.
- **Pocket List** (pocket-list): the gathering checklist.
- **Not Now, Shield** (not-now-shield): the shield drawn lower in first person; on 26.x also
  tools that change blocks while a shield is in the off-hand.

A new feature goes in the mod whose job it is. If none fits, ask Gabby before adding it anywhere.

## Working on Gabby's mods (the same in every repo)

Gabby makes these mods with help from Claude. Several Claude sessions work on them: one on her
computer and others in the cloud. These rules keep their work fitting together.

**One session per repo at a time.**
- A cloud session works on a `claude/...` branch and never pushes to `main`. Gabby's local
  session reviews the branch, runs its tests and merges it as one commit.
- Start from the latest `main` (`git pull`), and say in the branch's last commit or a short note
  what is finished and what isn't.

**Commits are made one at a time, by hand.** Never commit or push from a chained script. Run
the test, read its result (and the screenshot, for anything visual), then commit that one
branch, then push it. A cherry-pick that conflicts stops the work until it is resolved by hand.
A worldgen or balance change is tested on every terrain in the branch's matrix before it goes
to any other branch. (Learned on another mod, 2026-10-14, when scripted chains committed
a conflicted cherry-pick, put one branch's files on another, and sent an untested ore change to
all three branches.) For a Gradle game test use a game watcher that reports a verdict (in the world, error screen, crashed or hung),
not a wait loop.

**Commits.**
- Author and committer are always `gabmaybelle <211247938+gabmaybelle@users.noreply.github.com>`:
  `git -c user.name=gabmaybelle -c user.email=211247938+gabmaybelle@users.noreply.github.com commit ...`.
  Never a personal email, and never `Claude <noreply@anthropic.com>` as the author. Claude goes in
  a `Co-Authored-By:` line at the end of the message.
- Ask Gabby before committing or pushing when she's around. Finish the build and the tests first.
- Commit messages say what changed for players, in plain sentences.

**Privacy.** These repos are public. Don't add personal email addresses, real names other than
"Gabby", or anything about her other private projects and modpacks: not in code, docs, commits
or test names. Check the diff for these before every push.

**Tests.**
- `./gradlew build` and `./gradlew runClientGameTest` (where the repo has one) must pass before
  a commit. The game test opens a real game window for a minute or two.
- A new or changed check should fail with the feature switched off: run it that way once, then
  put the code back. A check that compares against the setting it tests proves nothing.
- A data change (worldgen, loot, a template, a pack's mod list) gets the same proof: run the part
  of the test that sees it, and quote the numbers in the progress row.
- On Linux without a screen, run game tests under `xvfb-run -a`. 26.3 also needs
  `SDL_VIDEO_FORCE_EGL=1` with `libegl1 libegl-mesa0` installed, or the game hangs at startup.

**Writing.**
- README and CHANGELOG are for players: plain, friendly, no jargon. New player-facing changes
  go in the CHANGELOG's newest section.
- Keep the credit line: "made by Gabby with help from Claude, an AI assistant".
- Gabby publishes on Modrinth herself. Don't upload anything there.

**Minecraft.** Fabric on Minecraft 26.3 (Mojang names; Loom 1.18.4, Java 25). Vanilla names
changed a lot in 26.x: check the merged jar rather than older docs (for example `iron_chain`,
`pink_petals[flower_amount=N]`, timelines instead of `time query daytime`).
