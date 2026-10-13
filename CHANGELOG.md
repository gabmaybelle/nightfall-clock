# Changelog

## Unreleased

- With Text Background set to Everywhere, the clock gets its dark box in the corners too, not just above the hotbar.
- Auto time format now picks 12-hour time in regions that write it with a day period instead of AM/PM, such as Traditional Chinese.
- The clock's minutes always use the same digits as the day and hour, in every region.
- Every build is now tested in game on both Minecraft 26.2 and 26.3.
- Fabric API 0.161.0 or newer is now required, the version Nightfall Clock is built against, so an older one gets a clear message instead of a crash.

## 0.1.0

The first release.

- A day counter and clock just above the hotbar ("Day 4 · 2:35 PM"), gold in the hour before night. It reads the time from where the sun is, so it stays right on longer or stretched days.
- "The sun's getting real low" above the hotbar at 6:00 PM, when dusk arrives as you play (not after sleeping or `/time set`).
- The clock steps aside for the held item's name, action-bar messages and recent chat, then fades back in. If a server keeps its own text in the action bar, the clock shows alongside it.
- Settings through Mod Menu, or `config/nightfallclock.properties`: clock on or off, dusk warning on or off, position (above the hotbar or any corner) and 12- or 24-hour time (Auto follows your computer's region).
- Hidden with F1, in the Nether and the End, and when Espresso Flavoured Core (which has its own clock) is installed.
- Client-side only; one jar for Minecraft 26.2 and 26.3.
