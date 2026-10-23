# Changelog

## Unreleased

- After 6:00 PM, the clock counts down to dark: "Day 4 · 6:42 PM · dark in 24s". It counts real seconds to the moment it's dark enough for monsters to spawn outside, timed from how fast the sky is really getting darker, so it stays right on longer days and in rain. The clock stays gold until then.
- "Storm's rolling in" shows above the hotbar when thunder starts, since monsters can spawn in daylight during a thunderstorm.
- In the Nether and the End, the clock now shows the Overworld's day and time ("Overworld: Day 4 · 9:12 PM"), so you know whether it's night before you head back.
- Each new part has its own setting: Countdown to Dark, Storm Warning and Overworld Time.
- Nightfall Clock now comes for Minecraft 1.21.1 too, on Fabric and on NeoForge: the same clock, dusk warning and settings. On NeoForge the settings are on the Mods screen's Config button.
- Nightfall Clock now comes for Minecraft 1.20.1, on Fabric and on Forge. It's the same clock as 0.2.1: the day and time above your hotbar, gold from 6:00 PM, "The sun's getting real low" when dusk arrives as you play, and the same settings (position, 12- or 24-hour time, Text Background).
- On 1.20.1 the settings are on Mod Menu's button for Fabric and on the Mods screen's Config button for Forge.

## 0.2.1

- Nightfall Clock no longer hides itself when Espresso Flavoured Core is installed: Espresso's own clock is gone, and Nightfall Clock is the clock in its pack.

## 0.2.0

- With Text Background set to Everywhere, the clock gets its dark box in the corners too, not just above the hotbar.
- Auto time format now picks 12-hour time in regions that write it with a day period instead of AM/PM, such as Traditional Chinese.
- The clock's minutes always use the same digits as the day and hour, in every region.
- Every build is now tested in game on both Minecraft 26.2 and 26.3.
- No more false "The sun's getting real low" when a server network moves you to another world that's already past 6:00 PM.
- On a server that keeps its own text in the action bar, the clock now fades in beside it instead of popping in.
- The clock no longer steps aside for old chat messages that chat isn't showing any more.
- Fabric API 0.161.0 or newer is now required, the version Nightfall Clock is built against, so an older one gets a clear message instead of a crash.
- Safer settings: the settings file can't be left half-written or empty if the game crashes while saving it; a typo in a setting ("ture") keeps its default instead of quietly switching it off, with a note in the log saying which setting; and settings the mod doesn't know about, or notes you've added, are no longer wiped each time the game starts.

## 0.1.0

The first release.

- A day counter and clock just above the hotbar ("Day 4 · 2:35 PM"), gold in the hour before night. It reads the time from where the sun is, so it stays right on longer or stretched days.
- "The sun's getting real low" above the hotbar at 6:00 PM, when dusk arrives as you play (not after sleeping or `/time set`).
- The clock steps aside for the held item's name, action-bar messages and recent chat, then fades back in. If a server keeps its own text in the action bar, the clock shows alongside it.
- Settings through Mod Menu, or `config/nightfallclock.properties`: clock on or off, dusk warning on or off, position (above the hotbar or any corner) and 12- or 24-hour time (Auto follows your computer's region).
- Hidden with F1, and in the Nether and the End.
- Client-side only; one jar for Minecraft 26.2 and 26.3.
