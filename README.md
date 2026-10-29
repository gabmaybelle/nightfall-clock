# Nightfall Clock

A clock that tells you what tonight brings. The day and time sit just above your hotbar, and at 6 PM it tells you "The sun's getting real low", then counts down to dark in real seconds. It warns you when a storm lets monsters out by day, and in the Nether and the End it shows the Overworld's time, so night never catches you far from home.

Client-side, for Fabric and NeoForge on Minecraft 26.2, 26.3 and 1.21.1, and Fabric and Forge on 1.20.1, plus a plugin for Paper servers on 26.3. Download it from [CurseForge](https://www.curseforge.com/minecraft/mc-mods/nightfall-clock), or [watch it in 20 seconds](https://youtube.com/shorts/1PF-1B3eXoY).

I kept getting caught out by the dark, halfway through a build or a long way from home, so I made a clock that sits where I'd glance anyway.

## What you see

A line of text just above your hearts and hunger: "Day 4 · 2:35 PM". It turns gold at 6:00 PM and stays gold until night falls.

At 6:00 PM that line reads "The sun's getting real low" for a few seconds, then goes back to the time. It only happens when dusk arrives as you play, not when you sleep or jump ahead with `/time set` (a jump of just a few minutes still counts as playing through it).

Then the clock counts down to dark: "Day 4 · 6:42 PM · dark in 24s". It's counting to the moment it's dark enough for monsters to spawn outside, in real seconds, so you know whether there's time to make it home. It's timed from how fast the sky is really getting darker, so it stays right on longer days and in rain.

When thunder starts, the line reads "Storm's rolling in" for a few seconds. Monsters can spawn in daylight during a thunderstorm, so it's a second kind of nightfall.

The clock shares its spot politely. When something else needs that space (the name of the item you just switched to, a message in the action bar, or recent chat), the clock steps aside and fades back in once it's gone. If a server keeps its own text in the action bar all the time, the clock shows alongside it instead of staying hidden.

It hides with the rest of the HUD when you press F1.

In the Nether and the End, which have no day of their own, the clock shows the Overworld's: "Overworld: Day 4 · 9:12 PM". So you know whether it's night before you step back through the portal.

## Right on longer days

Most clocks divide the day's ticks into 24 equal hours. That works for vanilla, but packs and datapacks that make days longer, or stretch the daylight more than the night, throw them off: "6:00 PM" can arrive while it's still bright, or after dark.

Nightfall Clock reads the time from where the sun actually is, the same way Minecraft's own clock item does. However long the day is, 6:00 PM is when the sun is where it sits at 6:00 PM in vanilla.

## Settings

On Fabric, with [Mod Menu](https://modrinth.com/mod/modmenu) installed, open Mods, pick Nightfall Clock and click the settings button. On NeoForge, open Mods, pick Nightfall Clock and click Config. Changes show straight away.

- **Clock:** on or off.
- **Dusk Warning:** on or off.
- **Countdown to Dark:** on or off.
- **Storm Warning:** on or off.
- **Overworld Time:** the Overworld's time in the Nether and the End, on or off.
- **Position:** above the hotbar, or in any corner of the screen. (Minimaps and some HUD mods use the top corners; pick one they don't.)
- **Time Format:** 12-hour (6:00 PM), 24-hour (18:00), or Auto, which follows your computer's region settings.

Without Mod Menu, the same settings are in `config/nightfallclock.properties`.

If you play with Text Background set to Everywhere (Options, Accessibility), the clock gets the same dark box behind it as the rest of the game's text.

## Good to know

- It's client-side only. It works on any server, and the server doesn't need it.
- The day number changes at 6:00 AM, the way Minecraft counts days, so 2:00 AM still belongs to the day before.

## On a Paper server

The `paper` file is a plugin for [Paper](https://papermc.io/) 26.3 (Java 25). Drop it in the server's `plugins` folder and every player gets the clock, with nothing to install: the day and time in the action bar, gold from 6:00 PM and counting down to dark, "The sun's getting real low" as a title at 6:00 PM, "Storm's rolling in" when thunder starts, and the Overworld's time in the Nether and the End.

- `/clock` turns your clock off or back on. `/clock 12` or `/clock 24` picks your time format. The server remembers your choice.
- The server's own settings are in `plugins/NightfallClock/config.yml`: each part of the clock on or off for everyone, and the time format for players who haven't picked one.
- The action bar is shared with any other plugin that writes there, so the clock may take turns with their text. There's no position setting: the action bar is where a plugin can write.
- If you also run the mod on your own game, you'll see both clocks; turn one off with `/clock` or in the mod's settings. A later version may let the mod step aside when the server shows the clock.
- A Paper server runs the vanilla day, so the plugin reads the day's ticks (the mod reads the sun). The countdown counts to the moment monsters can spawn outside in clear weather; in rain the sky darkens a little sooner.

## Requirements

- Minecraft 26.2 or 26.3
- Fabric: Fabric Loader 0.19.5 or newer and [Fabric API](https://modrinth.com/mod/fabric-api). Optional: [Mod Menu](https://modrinth.com/mod/modmenu), for the settings screen.
- NeoForge: NeoForge 26.2 or newer. Nothing else; the settings are on the Mods screen's Config button.

Pick the `fabric` or `neoforge` file for your loader, or the `paper` file for a Paper server.

**Other versions:** Minecraft 1.21.1, on Fabric and on NeoForge, is on the [`1.21.1` branch](https://github.com/gabmaybelle/nightfall-clock/tree/1.21.1), with its download on the [1.21.1 release page](https://github.com/gabmaybelle/nightfall-clock/releases/tag/v0.2.1%2B1.21.1). Minecraft 1.20.1, on Fabric and on Forge, is on the [`1.20.1` branch](https://github.com/gabmaybelle/nightfall-clock/tree/1.20.1), with its download on the [1.20.1 release page](https://github.com/gabmaybelle/nightfall-clock/releases/tag/v0.2.1%2B1.20.1).

## Building

Requires JDK 25.

```
./gradlew build
```

The jars are written to `fabric/build/libs/nightfallclock-fabric-<version>.jar`, `neoforge/build/libs/nightfallclock-neoforge-<version>.jar` and `paper/build/libs/nightfallclock-paper-<version>.jar`. The icon is drawn by `node tools/generate-icon.js`.

`./gradlew :fabric:runClientGameTest` starts the game, checks the settings file (typos, unknown settings, safe saving), then opens a test world and checks the clock, the dusk and storm warnings, the countdown to dark and the Overworld's time in the Nether (it needs a display; on a Linux virtual display such as Xvfb, set `SDL_VIDEO_FORCE_EGL=1` for 26.3). `./gradlew :neoforge:runClientTest` does the same for the NeoForge jar. Add `-Pminecraft_version=26.3 -Pfabric_api_version=0.162.0+26.3 -Pneoforge_version=26.3.0.77` to build or test against 26.3.

`node tools/paper-server-test.js <folder> --plugin paper/build/libs/nightfallclock-paper-<version>.jar --eula` downloads a Paper server into the folder once and starts it with the plugin, with no window, to check what the clock reads: the time at noon, 7:00 AM, midnight and 6:00 PM, the countdown to dark, dusk and thunder arriving, and the Overworld's time in the Nether. `--eula` is your acceptance of Minecraft's server EULA.

## Licence

[MIT](LICENSE).

Nightfall Clock is made by Gabby.
