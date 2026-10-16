# Changelog

## 0.3.1

- The clock reads the sun where you are, the same way the sky does. In plain Minecraft it reads just the same; in packs where the sun depends on where you are, it now matches the sky you see.

## 0.3.0

- Nightfall Clock now comes as a NeoForge mod as well as a Fabric one, for Minecraft 26.2 and 26.3. It looks and works the same. On NeoForge, the settings button is on the Mods screen, no extra mod needed. The Fabric file is now named `nightfallclock-fabric-0.3.0.jar`.

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
