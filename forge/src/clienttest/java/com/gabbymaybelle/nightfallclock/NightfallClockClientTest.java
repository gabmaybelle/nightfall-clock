package com.gabbymaybelle.nightfallclock;

import static com.gabbymaybelle.nightfallclock.ClientTest.check;
import static com.gabbymaybelle.nightfallclock.ClientTest.command;
import static com.gabbymaybelle.nightfallclock.ClientTest.createWorld;
import static com.gabbymaybelle.nightfallclock.ClientTest.run;
import static com.gabbymaybelle.nightfallclock.ClientTest.screenshot;
import static com.gabbymaybelle.nightfallclock.ClientTest.waitUntil;

import com.gabbymaybelle.nightfallclock.mixin.ChatComponentAccessor;
import com.gabbymaybelle.nightfallclock.mixin.HudAccessor;

/**
 * The Forge jar's in-game test: the same checks as the Fabric game test (fabric/src/gametest)
 * on Forge's overlay and tick event. The accessors work, the clock reads the right time at
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
		// Stop the day so /time set holds still (1.20.1's name for the rule).
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
		command("gamerule doDaylightCycle false");
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
