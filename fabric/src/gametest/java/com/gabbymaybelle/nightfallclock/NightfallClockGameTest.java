package com.gabbymaybelle.nightfallclock;

import com.gabbymaybelle.nightfallclock.mixin.ChatComponentAccessor;
import com.gabbymaybelle.nightfallclock.mixin.HudAccessor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.Level;

/**
 * Starts the real game, opens a world and checks the parts that only break at runtime: the
 * mixin accessors (a renamed vanilla field crashes the game), reading the time from the sun,
 * the dusk warning, the countdown to dark, the storm warning, the clock text in both formats, and
 * the Overworld's time in the Nether. Run with
 * ./gradlew runClientGameTest; CI runs it on each supported Minecraft version. Screenshots
 * land in build/run/clientGameTest/screenshots.
 */
public class NightfallClockGameTest implements FabricClientGameTest {
	/** Vanilla ticks: 0 is 6:00 AM, 1000 ticks to the hour. */
	private static final int NOON = 6000;

	@Override
	public void runTest(ClientGameTestContext context) {
		// A filming run (-PvideoTour) films only the video.
		if (Film.on("nightfallclock")) {
			return;
		}
		settingsFile();
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();

			context.runOnClient(mc -> {
				HudAccessor hud = (HudAccessor) mc.gui.hud;
				hud.nightfallclock$overlayMessageTime();
				hud.nightfallclock$toolHighlightTimer();
				hud.nightfallclock$lastToolHighlight();
				((ChatComponentAccessor) mc.gui.hud.getChat()).nightfallclock$trimmedMessages();
			});

			// The test world starts with the day cycle stopped, so /time set holds still.
			expectTime(context, singleplayer, NOON, 1, 12 * 60);
			context.takeScreenshot("nightfallclock-noon");
			expectTime(context, singleplayer, 1000, 1, 7 * 60);
			expectTime(context, singleplayer, 24000 + NOON, 2, 12 * 60);

			// Jumping over 6:00 PM with /time set isn't dusk arriving.
			expectTime(context, singleplayer, 11000, 1, 17 * 60);
			expectTime(context, singleplayer, 13000, 1, 19 * 60);
			if (context.computeOnClient(mc -> NightfallClock.duskShowing())) {
				throw new AssertionError("Dusk warning showed after /time set jumped over 6:00 PM");
			}

			// Playing through 6:00 PM is.
			expectTime(context, singleplayer, 11950, 1, 17 * 60 + 57);
			singleplayer.getServer().runCommand("gamerule advance_time true");
			context.waitFor(mc -> NightfallClock.duskShowing(), 200);
			context.takeScreenshot("nightfallclock-dusk");

			// After 6:00 PM the clock counts down to dark. From 12500 (about 6:30 PM) vanilla's sky
			// light reaches 7 at about tick 13178, some 34 s away at 20 ticks a second; a slow test
			// machine runs the day slower, so the count only has to be sensible. The light's fall is
			// timed over 2 s, so wait past the /time set jump first.
			singleplayer.getServer().runCommand("time set 12500");
			context.waitTicks(60);
			int seconds = context.computeOnClient(mc -> NightfallClock.darkSeconds());
			if (seconds < 20 || seconds > 120) {
				throw new AssertionError("Expected a countdown to dark of about 34 s at 6:30 PM, got " + seconds);
			}
			String line = context.computeOnClient(mc -> NightfallClock.describe(NightfallClock.DayTime.of(mc.level), NightfallClock.darkSeconds()).getString());
			if (!line.contains(" · dark in ")) {
				throw new AssertionError("The clock isn't counting down to dark: \"" + line + "\"");
			}
			// The dusk message has the line for 8 s; the countdown shows on the clock once it's gone.
			context.waitFor(mc -> !NightfallClock.duskShowing(), 200);
			// And the clock fades back in over a second.
			context.waitTicks(25);
			context.takeScreenshot("nightfallclock-countdown");
			singleplayer.getServer().runCommand("time set 13300");
			context.waitTicks(60);
			if (context.computeOnClient(mc -> NightfallClock.darkSeconds()) >= 0) {
				throw new AssertionError("Still counting down after dark (13300)");
			}
			singleplayer.getServer().runCommand("gamerule advance_time false");

			// Thunder starting as you play brings "Storm's rolling in".
			singleplayer.getServer().runCommand("weather clear");
			context.waitTicks(StormWatch.CLEAR_TICKS + 20);
			if (context.computeOnClient(mc -> NightfallClock.stormShowing())) {
				throw new AssertionError("Storm warning showed in clear weather");
			}
			singleplayer.getServer().runCommand("weather thunder");
			context.waitFor(mc -> NightfallClock.stormShowing(), 400);
			context.takeScreenshot("nightfallclock-storm");
			singleplayer.getServer().runCommand("weather clear");
			// Still up as the player leaves for the Nether, below, so that check proves it ends there.

			// The words on screen, in both formats: midnight and noon are 12, minutes are padded.
			expectText(context, Config.TimeFormat.TWELVE_HOUR, 4, 14 * 60 + 35, "Day 4 · 2:35 PM");
			expectText(context, Config.TimeFormat.TWELVE_HOUR, 1, 7 * 60 + 5, "Day 1 · 7:05 AM");
			expectText(context, Config.TimeFormat.TWELVE_HOUR, 2, 0, "Day 2 · 12:00 AM");
			expectText(context, Config.TimeFormat.TWELVE_HOUR, 2, 12 * 60, "Day 2 · 12:00 PM");
			expectText(context, Config.TimeFormat.TWENTY_FOUR_HOUR, 4, 14 * 60 + 35, "Day 4 · 14:35");
			expectText(context, Config.TimeFormat.TWENTY_FOUR_HOUR, 1, 7 * 60 + 5, "Day 1 · 07:05");
			expectLine(context, new NightfallClock.DayTime(1, 18 * 60 + 42), 24, "Day 1 · 6:42 PM · dark in 24s");
			expectLine(context, new NightfallClock.DayTime(1, 18 * 60 + 5), 75, "Day 1 · 6:05 PM · dark in 1:15");
			expectLine(context, new NightfallClock.DayTime(4, 21 * 60 + 12, true), -1, "Overworld: Day 4 · 9:12 PM");

			// The Nether has no day of its own, so the clock shows the Overworld's: day 4, 9:00 PM.
			singleplayer.getServer().runCommand("time set " + (3 * 24000 + 15000));
			singleplayer.getServer().runCommand("execute in minecraft:the_nether run tp @a 0 100 0");
			context.waitFor(mc -> mc.level != null && mc.level.dimension() == Level.NETHER, 400);
			context.waitTicks(5);
			NightfallClock.DayTime nether = context.computeOnClient(mc -> NightfallClock.DayTime.of(mc.level));
			if (nether == null || !nether.elsewhere() || nether.day() != 4 || Math.abs(nether.minute() - 21 * 60) > 1) {
				throw new AssertionError("Expected the Overworld's day 4, 9:00 PM in the Nether, got " + nether);
			}
			if (context.computeOnClient(mc -> NightfallClock.stormShowing())) {
				throw new AssertionError("The Overworld's storm warning followed the player into the Nether");
			}
			// The clock fades back in over a second once the line is free.
			context.waitTicks(25);
			context.takeScreenshot("nightfallclock-nether");
			boolean hidden = context.computeOnClient(mc -> {
				Config.overworldTime = false;
				boolean none = NightfallClock.DayTime.of(mc.level) == null;
				Config.overworldTime = true;
				return none;
			});
			if (!hidden) {
				throw new AssertionError("With Overworld Time off, the clock still shows in the Nether");
			}
		}
	}

	/**
	 * The settings file: a typo keeps the default instead of switching the clock off, case and
	 * spaces don't matter, a setting from a newer version survives, no temporary file is left
	 * behind, and a complete file isn't rewritten just by loading it (so notes added by hand stay).
	 */
	private static void settingsFile() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve(NightfallClock.MOD_ID + ".properties");
		try {
			Files.writeString(file, "clock=ture\nduskWarning= FALSE \nfutureSetting=hello\n");
			Config.load(FabricLoader.getInstance().getConfigDir());
			if (!Config.clock) {
				throw new AssertionError("A typo (\"ture\") switched the clock off instead of keeping its default");
			}
			if (Config.duskWarning) {
				throw new AssertionError("\" FALSE \" didn't read as false");
			}
			String written = Files.readString(file);
			if (!written.contains("futureSetting=hello") || !written.contains("position=above_hotbar")) {
				throw new AssertionError("The file lost an unknown setting or didn't get the missing ones:\n" + written);
			}
			if (Files.exists(file.resolveSibling(file.getFileName() + ".tmp"))) {
				throw new AssertionError("A temporary settings file was left behind");
			}
			String complete = "# a note added by hand\n" + written;
			Files.writeString(file, complete);
			Config.load(FabricLoader.getInstance().getConfigDir());
			if (!Files.readString(file).equals(complete)) {
				throw new AssertionError("Loading a complete settings file rewrote it");
			}
		} catch (IOException e) {
			throw new AssertionError("Couldn't use the settings file", e);
		} finally {
			Config.clock = true;
			Config.duskWarning = true;
			Config.save();
		}
	}

	/** Checks the clock's text for a day and time (minutes after midnight) in one time format. */
	private static void expectText(ClientGameTestContext context, Config.TimeFormat format, int day, int minute, String expected) {
		String shown = context.computeOnClient(mc -> {
			Config.TimeFormat before = Config.timeFormat;
			Config.timeFormat = format;
			String text = new NightfallClock.DayTime(day, minute).describe().getString();
			Config.timeFormat = before;
			return text;
		});
		if (!shown.equals(expected)) {
			throw new AssertionError("Expected \"" + expected + "\" (" + format + "), got \"" + shown + "\"");
		}
	}

	/** Checks the whole clock line (12-hour), with a countdown or in another world. */
	private static void expectLine(ClientGameTestContext context, NightfallClock.DayTime time, int seconds, String expected) {
		String shown = context.computeOnClient(mc -> {
			Config.TimeFormat before = Config.timeFormat;
			Config.timeFormat = Config.TimeFormat.TWELVE_HOUR;
			String text = NightfallClock.describe(time, seconds).getString();
			Config.timeFormat = before;
			return text;
		});
		if (!shown.equals(expected)) {
			throw new AssertionError("Expected \"" + expected + "\", got \"" + shown + "\"");
		}
	}

	/** Sets the time and checks the clock reads the expected day and time (minutes after midnight). */
	private static void expectTime(ClientGameTestContext context, TestSingleplayerContext singleplayer, int ticks, int day, int minute) {
		singleplayer.getServer().runCommand("time set " + ticks);
		singleplayer.getConnection().waitForClientboundPackets();
		context.waitTicks(2);
		NightfallClock.DayTime time = context.computeOnClient(mc -> NightfallClock.DayTime.of(mc.level));
		if (time == null) {
			throw new AssertionError("No day cycle in the overworld at time " + ticks);
		}
		// One minute either way allows for the client's own ticking between sync packets.
		if (time.day() != day || Math.abs(time.minute() - minute) > 1) {
			throw new AssertionError("At time " + ticks + " expected day " + day + " minute " + minute + ", got " + time);
		}
	}
}
