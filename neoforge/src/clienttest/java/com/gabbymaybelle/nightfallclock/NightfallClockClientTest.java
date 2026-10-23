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
import net.minecraft.world.level.Level;

/**
 * The NeoForge jar's in-game test: the same checks as the Fabric game test (fabric/src/gametest)
 * on NeoForge's HUD layer and tick event. The accessors work, the clock reads the right time at
 * noon, 6:00 PM and midnight, the dusk warning fires when the day plays through 6:00 PM, and the
 * clock steps aside for the held item's name and an action-bar message.
 */
final class NightfallClockClientTest {
	/** Vanilla ticks: 0 is 6:00 AM, 1000 ticks to the hour. */
	private static final int NOON = 6000;

	private NightfallClockClientTest() {
	}

	static void steps() {
		createWorld("nightfallclock-clienttest");
		command("gamemode survival @a");
		// Stop the day so /time set holds still (1.21.1's name for the rule).
		command("gamerule doDaylightCycle false");
		// Commands would fill chat, and the clock steps aside for recent chat.
		command("gamerule sendCommandFeedback false");

		run(mc -> {
			HudAccessor hud = (HudAccessor) mc.gui;
			hud.nightfallclock$overlayMessageTime();
			hud.nightfallclock$toolHighlightTimer();
			hud.nightfallclock$lastToolHighlight();
			((ChatComponentAccessor) mc.gui.getChat()).nightfallclock$trimmedMessages();
		});

		expectTime(NOON, 1, 12 * 60);
		screenshot();
		expectTime(24000 + NOON, 2, 12 * 60);
		expectTime(12000, 1, 18 * 60);
		expectTime(18000, 1, 0);

		// The clock steps aside for the held item's name, then comes back.
		expectTime(NOON, 1, 12 * 60);
		run(mc -> mc.gui.getChat().clearMessages(false));
		waitUntil("the clock showing", 100, mc -> !NightfallClock.clockHidden());
		command("give @a minecraft:iron_pickaxe");
		waitUntil("the clock stepping aside for the item name", 40, mc -> NightfallClock.clockHidden());
		waitUntil("the clock back after the item name", 200, mc -> !NightfallClock.clockHidden());
		// And for an action-bar message.
		command("title @a actionbar \"Hello\"");
		waitUntil("the clock stepping aside for the action bar", 40, mc -> NightfallClock.clockHidden());
		waitUntil("the clock back after the action bar", 200, mc -> !NightfallClock.clockHidden());

		// Jumping over 6:00 PM with /time set isn't dusk arriving.
		expectTime(11000, 1, 17 * 60);
		expectTime(13000, 1, 19 * 60);
		run(mc -> check(!NightfallClock.duskShowing(), "Dusk warning showed after /time set jumped over 6:00 PM"));

		// Playing through 6:00 PM is.
		expectTime(11950, 1, 17 * 60 + 57);
		command("gamerule doDaylightCycle true");
		waitUntil("the dusk warning", 200, mc -> NightfallClock.duskShowing());
		screenshot();

		// After 6:00 PM the clock counts down to dark. From 12500 (about 6:30 PM) vanilla's sky
		// darkening reaches spawning level at about tick 13188, some 34 s away at 20 ticks a second;
		// a slow test machine runs the day slower, so the count only has to be sensible. The time
		// reaches the client within a second and the light's fall is timed over 2 s, so wait past
		// the /time set jump first.
		command("time set 12500");
		waitTicks(80);
		run(mc -> {
			int seconds = NightfallClock.darkSeconds();
			check(seconds >= 20 && seconds <= 120, "Expected a countdown to dark of about 34 s at 6:30 PM, got " + seconds);
			String line = NightfallClock.describe(NightfallClock.DayTime.of(mc.level), seconds).getString();
			check(line.contains(" · dark in "), "The clock isn't counting down to dark: \"" + line + "\"");
		});
		// The dusk message has the line for 8 s; the countdown shows on the clock once it's gone,
		// fading back in over a second.
		waitUntil("the dusk warning to finish", 200, mc -> !NightfallClock.duskShowing());
		waitTicks(25);
		screenshot();
		command("time set 13400");
		waitTicks(80);
		run(mc -> check(NightfallClock.darkSeconds() < 0, "Still counting down after dark (13400)"));
		command("gamerule doDaylightCycle false");

		// Thunder starting as you play brings "Storm's rolling in".
		command("weather clear");
		waitTicks(StormWatch.CLEAR_TICKS + 20);
		run(mc -> check(!NightfallClock.stormShowing(), "Storm warning showed in clear weather"));
		command("weather thunder");
		waitUntil("the storm warning when thunder starts", 400, mc -> NightfallClock.stormShowing());
		screenshot();
		command("weather clear");

		// The Nether has no day of its own, so the clock shows the Overworld's: day 4, 9:00 PM.
		command("time set " + (3 * 24000 + 15000));
		command("execute in minecraft:the_nether run tp @a 0 128 0");
		waitUntil("the player in the Nether", 400, mc -> mc.level != null && mc.level.dimension() == Level.NETHER);
		waitUntil("the Overworld's time in the Nether", 100, mc -> Math.abs(mc.level.getDayTime() - (3 * 24000 + 15000)) < 20);
		run(mc -> {
			NightfallClock.DayTime nether = NightfallClock.DayTime.of(mc.level);
			check(nether != null && nether.elsewhere() && nether.day() == 4 && Math.abs(nether.minute() - 21 * 60) <= 1,
					"Expected the Overworld's day 4, 9:00 PM in the Nether, got " + nether);
			// The storm warning was still up when the player left; it ends with the Overworld.
			check(!NightfallClock.stormShowing(), "The Overworld's storm warning followed the player into the Nether");
			Config.overworldTime = false;
			boolean hidden = NightfallClock.DayTime.of(mc.level) == null;
			Config.overworldTime = true;
			check(hidden, "With Overworld Time off, the clock still shows in the Nether");
		});
		// The clock fades back in over a second once the line is free, after the loading screen.
		waitUntil("the Nether loaded", 400, mc -> mc.screen == null);
		waitTicks(25);
		screenshot();
	}

	/** Sets the time and checks the clock reads the expected day and time (minutes after midnight). */
	private static void expectTime(int ticks, int day, int minute) {
		command("time set " + ticks);
		// This version sends the time to the client only once a second (every 20 ticks), so wait for it.
		waitUntil("the new time on the client", 100, mc -> Math.abs(mc.level.getDayTime() - ticks) < 20);
		run(mc -> {
			NightfallClock.DayTime time = NightfallClock.DayTime.of(mc.level);
			check(time != null, "No day cycle in the overworld at time " + ticks);
			// One minute either way allows for the client's own ticking between sync packets.
			check(time.day() == day && Math.abs(time.minute() - minute) <= 1,
					"At time " + ticks + " expected day " + day + " minute " + minute + ", got " + time);
		});
	}
}
