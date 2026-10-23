package com.gabbymaybelle.nightfallclock;

import static com.gabbymaybelle.nightfallclock.ClientTest.check;
import static com.gabbymaybelle.nightfallclock.ClientTest.command;
import static com.gabbymaybelle.nightfallclock.ClientTest.createWorld;
import static com.gabbymaybelle.nightfallclock.ClientTest.run;
import static com.gabbymaybelle.nightfallclock.ClientTest.screenshot;
import static com.gabbymaybelle.nightfallclock.ClientTest.waitTicks;
import static com.gabbymaybelle.nightfallclock.ClientTest.waitUntil;

import com.gabbymaybelle.nightfallclock.mixin.ChatComponentAccessor;
import com.gabbymaybelle.nightfallclock.mixin.HudAccessor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.gui.modlist.DefaultModDisplayInfo;
import net.neoforged.neoforge.client.gui.modlist.ImageResource;

/**
 * The NeoForge jar's in-game test: the same checks as the Fabric game test (fabric/src/gametest),
 * so both loaders are held to the same behaviour. The settings file, the mixin accessors, the clock
 * time at several /time set points, the dusk warning (not after a jump, yes when played through),
 * the countdown to dark, the storm warning, the clock text in both formats, the Overworld's time
 * in the Nether, and the icon on the Mods screen.
 */
final class NightfallClockClientTest {
	/** Vanilla ticks: 0 is 6:00 AM, 1000 ticks to the hour. */
	private static final int NOON = 6000;

	private NightfallClockClientTest() {
	}

	static void steps() {
		run(mc -> settingsFile());
		// The Mods screen's icon: NeoForge looks it up the way its Mods screen does, and the picture opens.
		run(mc -> {
			DefaultModDisplayInfo info = new DefaultModDisplayInfo(ModList.get().getModContainerById(NightfallClock.MOD_ID).orElseThrow());
			ImageResource icon = info.icon();
			check(icon != null && icon.get(mc.getResourceManager()) != null, "The Mods screen finds no icon for Nightfall Clock: " + icon);
		});
		createWorld("nightfallclock-clienttest");
		// Hold the time still, so /time set stays put between checks.
		command("gamerule advance_time false");

		run(mc -> {
			HudAccessor hud = (HudAccessor) mc.gui.hud;
			hud.nightfallclock$overlayMessageTime();
			hud.nightfallclock$toolHighlightTimer();
			hud.nightfallclock$lastToolHighlight();
			((ChatComponentAccessor) mc.gui.hud.getChat()).nightfallclock$trimmedMessages();
		});

		expectTime(NOON, 1, 12 * 60);
		screenshot();
		expectTime(1000, 1, 7 * 60);
		expectTime(24000 + NOON, 2, 12 * 60);

		// Jumping over 6:00 PM with /time set isn't dusk arriving.
		expectTime(11000, 1, 17 * 60);
		expectTime(13000, 1, 19 * 60);
		run(mc -> check(!NightfallClock.duskShowing(), "Dusk warning showed after /time set jumped over 6:00 PM"));

		// Playing through 6:00 PM is.
		expectTime(11950, 1, 17 * 60 + 57);
		command("gamerule advance_time true");
		waitUntil("the dusk warning while playing through 6:00 PM", 200, mc -> NightfallClock.duskShowing());
		screenshot();

		// After 6:00 PM the clock counts down to dark. From 12500 (about 6:30 PM) vanilla's sky
		// light reaches 7 at about tick 13178, some 34 s away at 20 ticks a second; a slow test
		// machine runs the day slower, so the count only has to be sensible. The light's fall is
		// timed over 2 s, so wait past the /time set jump first.
		command("time set 12500");
		waitTicks(60);
		run(mc -> {
			int seconds = NightfallClock.darkSeconds();
			check(seconds >= 20 && seconds <= 120, "Expected a countdown to dark of about 34 s at 6:30 PM, got " + seconds);
			String line = NightfallClock.describe(NightfallClock.DayTime.of(mc.level), seconds).getString();
			check(line.contains(" · dark in "), "The clock isn't counting down to dark: \"" + line + "\"");
		});
		// The dusk message has the line for 8 s; the countdown shows on the clock once it's gone.
		waitUntil("the dusk warning to finish", 200, mc -> !NightfallClock.duskShowing());
		// And the clock fades back in over a second (unless recent chat has the line).
		waitTicks(25);
		screenshot();
		command("time set 13300");
		waitTicks(60);
		run(mc -> check(NightfallClock.darkSeconds() < 0, "Still counting down after dark (13300)"));
		command("gamerule advance_time false");

		// Thunder starting as you play brings "Storm's rolling in".
		command("weather clear");
		waitTicks(StormWatch.CLEAR_TICKS + 20);
		run(mc -> check(!NightfallClock.stormShowing(), "Storm warning showed in clear weather"));
		command("weather thunder");
		waitUntil("the storm warning when thunder starts", 400, mc -> NightfallClock.stormShowing());
		screenshot();
		command("weather clear");

		// The words on screen, in both formats: midnight and noon are 12, minutes are padded.
		expectText(Config.TimeFormat.TWELVE_HOUR, 4, 14 * 60 + 35, "Day 4 · 2:35 PM");
		expectText(Config.TimeFormat.TWELVE_HOUR, 1, 7 * 60 + 5, "Day 1 · 7:05 AM");
		expectText(Config.TimeFormat.TWELVE_HOUR, 2, 0, "Day 2 · 12:00 AM");
		expectText(Config.TimeFormat.TWELVE_HOUR, 2, 12 * 60, "Day 2 · 12:00 PM");
		expectText(Config.TimeFormat.TWENTY_FOUR_HOUR, 4, 14 * 60 + 35, "Day 4 · 14:35");
		expectText(Config.TimeFormat.TWENTY_FOUR_HOUR, 1, 7 * 60 + 5, "Day 1 · 07:05");
		expectLine(new NightfallClock.DayTime(1, 18 * 60 + 42), 24, "Day 1 · 6:42 PM · dark in 24s");
		expectLine(new NightfallClock.DayTime(1, 18 * 60 + 5), 75, "Day 1 · 6:05 PM · dark in 1:15");
		expectLine(new NightfallClock.DayTime(4, 21 * 60 + 12, true), -1, "Overworld: Day 4 · 9:12 PM");

		// The Nether has no day of its own, so the clock shows the Overworld's: day 4, 9:00 PM.
		command("time set " + (3 * 24000 + 15000));
		command("execute in minecraft:the_nether run tp @a 0 128 0");
		waitUntil("the player in the Nether", 400, mc -> mc.level != null && mc.level.dimension() == Level.NETHER);
		waitTicks(5);
		run(mc -> {
			NightfallClock.DayTime nether = NightfallClock.DayTime.of(mc.level);
			check(nether != null && nether.elsewhere() && nether.day() == 4 && Math.abs(nether.minute() - 21 * 60) <= 1,
					"Expected the Overworld's day 4, 9:00 PM in the Nether, got " + nether);
			Config.overworldTime = false;
			boolean hidden = NightfallClock.DayTime.of(mc.level) == null;
			Config.overworldTime = true;
			check(hidden, "With Overworld Time off, the clock still shows in the Nether");
			// The storm warning was still up when the player left; it ends with the Overworld.
			check(!NightfallClock.stormShowing(), "The Overworld's storm warning followed the player into the Nether");
		});
		// The clock fades back in over a second once the line is free.
		waitTicks(25);
		screenshot();
	}

	/** Checks the whole clock line (12-hour), with a countdown or in another world. */
	private static void expectLine(NightfallClock.DayTime time, int seconds, String expected) {
		run(mc -> {
			Config.TimeFormat before = Config.timeFormat;
			Config.timeFormat = Config.TimeFormat.TWELVE_HOUR;
			String shown = NightfallClock.describe(time, seconds).getString();
			Config.timeFormat = before;
			check(shown.equals(expected), "Expected \"" + expected + "\", got \"" + shown + "\"");
		});
	}

	/**
	 * The settings file: a typo keeps the default instead of switching the clock off, case and
	 * spaces don't matter, a setting from a newer version survives, no temporary file is left
	 * behind, and a complete file isn't rewritten just by loading it (so notes added by hand stay).
	 */
	private static void settingsFile() {
		Path dir = FMLPaths.CONFIGDIR.get();
		Path file = dir.resolve(NightfallClock.MOD_ID + ".properties");
		try {
			Files.writeString(file, "clock=ture\nduskWarning= FALSE \nfutureSetting=hello\n");
			Config.load(dir);
			check(Config.clock, "A typo (\"ture\") switched the clock off instead of keeping its default");
			check(!Config.duskWarning, "\" FALSE \" didn't read as false");
			String written = Files.readString(file);
			check(written.contains("futureSetting=hello") && written.contains("position=above_hotbar"),
					"The file lost an unknown setting or didn't get the missing ones:\n" + written);
			check(!Files.exists(file.resolveSibling(file.getFileName() + ".tmp")), "A temporary settings file was left behind");
			String complete = "# a note added by hand\n" + written;
			Files.writeString(file, complete);
			Config.load(dir);
			check(Files.readString(file).equals(complete), "Loading a complete settings file rewrote it");
		} catch (IOException e) {
			throw new AssertionError("Couldn't use the settings file", e);
		} finally {
			Config.clock = true;
			Config.duskWarning = true;
			Config.save();
		}
	}

	/** Checks the clock's text for a day and time (minutes after midnight) in one time format. */
	private static void expectText(Config.TimeFormat format, int day, int minute, String expected) {
		run(mc -> {
			Config.TimeFormat before = Config.timeFormat;
			Config.timeFormat = format;
			String shown = new NightfallClock.DayTime(day, minute).describe().getString();
			Config.timeFormat = before;
			check(shown.equals(expected), "Expected \"" + expected + "\" (" + format + "), got \"" + shown + "\"");
		});
	}

	/** Sets the time and checks the clock reads the expected day and time (minutes after midnight). */
	private static void expectTime(int ticks, int day, int minute) {
		command("time set " + ticks);
		run(mc -> {
			NightfallClock.DayTime time = NightfallClock.DayTime.of(mc.level);
			check(time != null, "No day cycle in the overworld at time " + ticks);
			// One minute either way allows for the client's own ticking between sync packets.
			check(time.day() == day && Math.abs(time.minute() - minute) <= 1,
					"At time " + ticks + " expected day " + day + " minute " + minute + ", got " + time);
		});
	}
}
