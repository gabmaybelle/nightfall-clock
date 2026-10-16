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
import net.neoforged.fml.loading.FMLPaths;

/**
 * The NeoForge jar's in-game test: the same checks as the Fabric game test (fabric/src/gametest),
 * so both loaders are held to the same behaviour. The settings file, the mixin accessors, the clock
 * time at several /time set points, the dusk warning (not after a jump, yes when played through),
 * the clock text in both formats, and no clock in the Nether.
 */
final class NightfallClockClientTest {
	/** Vanilla ticks: 0 is 6:00 AM, 1000 ticks to the hour. */
	private static final int NOON = 6000;

	private NightfallClockClientTest() {
	}

	static void steps() {
		run(mc -> settingsFile());
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
		command("gamerule advance_time false");

		// The words on screen, in both formats: midnight and noon are 12, minutes are padded.
		expectText(Config.TimeFormat.TWELVE_HOUR, 4, 14 * 60 + 35, "Day 4 · 2:35 PM");
		expectText(Config.TimeFormat.TWELVE_HOUR, 1, 7 * 60 + 5, "Day 1 · 7:05 AM");
		expectText(Config.TimeFormat.TWELVE_HOUR, 2, 0, "Day 2 · 12:00 AM");
		expectText(Config.TimeFormat.TWELVE_HOUR, 2, 12 * 60, "Day 2 · 12:00 PM");
		expectText(Config.TimeFormat.TWENTY_FOUR_HOUR, 4, 14 * 60 + 35, "Day 4 · 14:35");
		expectText(Config.TimeFormat.TWENTY_FOUR_HOUR, 1, 7 * 60 + 5, "Day 1 · 07:05");

		// The Nether has no day, so no clock.
		command("execute in minecraft:the_nether run tp @a 0 128 0");
		waitUntil("the player in the Nether", 400, mc -> mc.level != null && mc.level.dimension() == Level.NETHER);
		waitTicks(5);
		run(mc -> check(NightfallClock.DayTime.of(mc.level) == null, "The clock has a time in the Nether, which has no day"));
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
