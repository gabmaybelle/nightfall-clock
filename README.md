# Nightfall Clock

A small clock just above your hotbar, and a heads-up when the sun starts to set.

This is the Minecraft 1.20.1 version, for Fabric and Forge. The 26.2 and 26.3 version (Fabric) is on the `main` branch.

I kept getting caught out by the dark, halfway through a build or a long way from home. Nightfall Clock shows the day and time where you'd glance anyway, and an hour before night it tells you: "The sun's getting real low".

## What you see

A line of text just above your hearts and hunger: "Day 4 · 2:35 PM". It turns gold at 6:00 PM and stays gold until 7:00 PM, when night falls.

At 6:00 PM that line reads "The sun's getting real low" for a few seconds, then goes back to the time. It only happens when dusk arrives as you play, not when you sleep or jump ahead with `/time set` (a jump of just a few minutes still counts as playing through it).

The clock shares its spot politely. When something else needs that space (the name of the item you just switched to, a message in the action bar, or recent chat), the clock steps aside and fades back in once it's gone. If a server keeps its own text in the action bar all the time, the clock shows alongside it instead of staying hidden.

It hides with the rest of the HUD when you press F1, and in the Nether and the End, which have no day.

## Right on longer days

Most clocks divide the day's ticks into 24 equal hours. That works for vanilla, but packs and datapacks that make days longer, or stretch the daylight more than the night, throw them off: "6:00 PM" can arrive while it's still bright, or after dark.

Nightfall Clock reads the time from where the sun actually is, the same way Minecraft's own clock item does. However long the day is, 6:00 PM is when the sun is where it sits at 6:00 PM in vanilla. (The day number counts whole days of world time, 24,000 ticks each, the way vanilla does.)

## Settings

On Fabric, with [Mod Menu](https://modrinth.com/mod/modmenu) installed, open Mods, pick Nightfall Clock and click the settings button. On Forge, open Mods, pick Nightfall Clock and click Config. Changes show straight away.

- **Clock:** on or off.
- **Dusk Warning:** on or off.
- **Position:** above the hotbar, or in any corner of the screen. (Minimaps and some HUD mods use the top corners; pick one they don't.)
- **Time Format:** 12-hour (6:00 PM), 24-hour (18:00), or Auto, which follows your computer's region settings.

Without Mod Menu on Fabric, the same settings are in `config/nightfallclock.properties`.

If you play with Text Background set to Everywhere (Options, Accessibility), the clock gets the same dark box behind it as the rest of the game's text.

## Good to know

- It's client-side only. It works on any server, and the server doesn't need it.
- The day number changes at 6:00 AM, the way Minecraft counts days, so 2:00 AM still belongs to the day before.

## Requirements

- Minecraft 1.20.1, and Java 17
- On Fabric: Fabric Loader 0.15 or newer and [Fabric API](https://modrinth.com/mod/fabric-api) 0.92 or newer
- On Forge: Forge 47 or newer

Optional on Fabric: [Mod Menu](https://modrinth.com/mod/modmenu), for the settings screen.

## Building

Requires JDK 25 to run Gradle, and a Java 17 install that Gradle can find (Forge's build tools run on it):

```
./gradlew build -Dorg.gradle.java.installations.fromEnv=JAVA_HOME_17_X64
```

(Or `-Dorg.gradle.java.installations.paths=<path to Java 17>`.) The two jars are written to `fabric/build/libs/` and `forge/build/libs/`, named `nightfallclock-fabric-<version>.jar` and `nightfallclock-forge-<version>.jar`. The first Forge build takes a few minutes. The icon is drawn by `node tools/generate-icon.js`.

The code is in three parts: `common` (the clock, the dusk warning and the settings screen), and a small `fabric` and `forge` part that start it. `./gradlew build` runs the unit tests in `common` and again on Forge's game classes. 1.20.1 has no Fabric in-game test, so the finished jar is checked in a real game by hand.

## Licence

[MIT](LICENSE).

Nightfall Clock is made by Gabby with help from Claude, an AI assistant.
