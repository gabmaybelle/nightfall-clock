package com.gabbymaybelle.nightfallclock;

import com.gabbymaybelle.nightfallclock.mixin.ChatComponentAccessor;
import com.gabbymaybelle.nightfallclock.mixin.HudAccessor;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.level.Level;

/**
 * Starts the real game, opens a world and checks the parts that only break at runtime: the
 * mixin accessors (a renamed vanilla field crashes the game), reading the time from the sun,
 * the dusk warning, the clock text in both formats, and no clock in the Nether. Run with
 * ./gradlew runClientGameTest; CI runs it on each supported Minecraft version. Screenshots
 * land in build/run/clientGameTest/screenshots.
 */
public class NightfallClockGameTest implements FabricClientGameTest {
	/** Vanilla ticks: 0 is 6:00 AM, 1000 ticks to the hour. */
	private static final int NOON = 6000;

	@Override
	public void runTest(ClientGameTestContext context) {
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
			singleplayer.getServer().runCommand("gamerule advance_time false");

			// The words on screen, in both formats: midnight and noon are 12, minutes are padded.
			expectText(context, Config.TimeFormat.TWELVE_HOUR, 4, 14 * 60 + 35, "Day 4 · 2:35 PM");
			expectText(context, Config.TimeFormat.TWELVE_HOUR, 1, 7 * 60 + 5, "Day 1 · 7:05 AM");
			expectText(context, Config.TimeFormat.TWELVE_HOUR, 2, 0, "Day 2 · 12:00 AM");
			expectText(context, Config.TimeFormat.TWELVE_HOUR, 2, 12 * 60, "Day 2 · 12:00 PM");
			expectText(context, Config.TimeFormat.TWENTY_FOUR_HOUR, 4, 14 * 60 + 35, "Day 4 · 14:35");
			expectText(context, Config.TimeFormat.TWENTY_FOUR_HOUR, 1, 7 * 60 + 5, "Day 1 · 07:05");

			// The Nether has no day, so no clock.
			singleplayer.getServer().runCommand("execute in minecraft:the_nether run tp @a 0 100 0");
			context.waitFor(mc -> mc.level != null && mc.level.dimension() == Level.NETHER, 400);
			context.waitTicks(5);
			if (context.computeOnClient(mc -> NightfallClock.DayTime.of(mc.level)) != null) {
				throw new AssertionError("The clock has a time in the Nether, which has no day");
			}
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
