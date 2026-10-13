# Nightfall Clock

A small clock just above your hotbar, and a heads-up when the sun starts to set.

I kept getting caught out by the dark, halfway through a build or a long way from home. Nightfall Clock shows the day and time where you'd glance anyway, and an hour before night it tells you: "The sun's getting real low".

## What you see

A line of text just above your hearts and hunger: "Day 4 · 2:35 PM". It turns gold at 6:00 PM and stays gold until 7:00 PM, when night falls.

At 6:00 PM that line reads "The sun's getting real low" for a few seconds, then goes back to the time. It only happens when dusk arrives as you play, not when you sleep or jump ahead with `/time set` (a jump of just a few minutes still counts as playing through it).

The clock shares its spot politely. When something else needs that space (the name of the item you just switched to, a message in the action bar, or recent chat), the clock steps aside and fades back in once it's gone. If a server keeps its own text in the action bar all the time, the clock shows alongside it instead of staying hidden.

It hides with the rest of the HUD when you press F1, and in the Nether and the End, which have no day.

## Right on longer days

Most clocks divide the day's ticks into 24 equal hours. That works for vanilla, but packs and datapacks that make days longer, or stretch the daylight more than the night, throw them off: "6:00 PM" can arrive while it's still bright, or after dark.

Nightfall Clock reads the time from where the sun actually is, the same way Minecraft's own clock item does. However long the day is, 6:00 PM is when the sun is where it sits at 6:00 PM in vanilla.

## Settings

With [Mod Menu](https://modrinth.com/mod/modmenu) installed, open Mods, pick Nightfall Clock and click the settings button. Changes show straight away.

- **Clock:** on or off.
- **Dusk Warning:** on or off.
- **Position:** above the hotbar, or in any corner of the screen. (Minimaps and some HUD mods use the top corners; pick one they don't.)
- **Time Format:** 12-hour (6:00 PM), 24-hour (18:00), or Auto, which follows your computer's region settings.

Without Mod Menu, the same settings are in `config/nightfallclock.properties`.

If you play with Text Background set to Everywhere (Options, Accessibility), the clock gets the same dark box behind it as the rest of the game's text.

## Good to know

- It's client-side only. It works on any server, and the server doesn't need it.
- The day number changes at 6:00 AM, the way Minecraft counts days, so 2:00 AM still belongs to the day before.
- [Espresso Flavoured Core](https://github.com/gabmaybelle/espresso-flavoured) has its own clock and dusk message, so Nightfall Clock stays hidden when it's installed.

## Requirements

- Minecraft 26.2 or 26.3
- Fabric Loader 0.19.5 or newer
- [Fabric API](https://modrinth.com/mod/fabric-api)

Optional: [Mod Menu](https://modrinth.com/mod/modmenu), for the settings screen.

## Building

Requires JDK 25.

```
./gradlew build
```

The mod jar is written to `build/libs/nightfallclock-<version>.jar`. The icon is drawn by `node tools/generate-icon.js`.

`./gradlew runClientGameTest` starts the game, checks the settings file (typos, unknown settings, safe saving), then opens a test world and checks the clock and dusk warning (it needs a display; on a Linux virtual display such as Xvfb, set `SDL_VIDEO_FORCE_EGL=1` for 26.3). Add `-Pminecraft_version=26.3 -Pfabric_api_version=0.162.0+26.3` to build or test against 26.3.

## Licence

[MIT](LICENSE).

Nightfall Clock is made by Gabby with help from Claude, an AI assistant.
