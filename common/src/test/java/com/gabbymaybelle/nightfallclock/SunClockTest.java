package com.gabbymaybelle.nightfallclock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Reading the clock time from the sun's angle (0 at noon, rising to 360 through the day). */
class SunClockTest {
	private static int minute(float angle) {
		return NightfallClock.DayTime.minuteFromSun(angle);
	}

	@Test
	void noonAndMidnight() {
		assertEquals(12 * 60, minute(0F), "the sun overhead is noon");
		// Vanilla's sun easing is symmetric, so half way round is midnight.
		assertEquals(0, minute(180F), "the sun underfoot is midnight");
	}

	@Test
	void anglesOutsideZeroTo360() {
		assertEquals(minute(10F), minute(370F));
		assertEquals(minute(350F), minute(-10F));
		assertEquals(minute(0F), minute(360F));
	}

	@Test
	void timeNeverGoesBackwardsThroughTheDay() {
		// From noon round to the next noon, the clock only moves forward (minutes after noon).
		int last = -1;
		for (int tenth = 0; tenth < 3600; tenth++) {
			int sinceNoon = Math.floorMod(minute(tenth / 10F) - 12 * 60, 24 * 60);
			assertTrue(sinceNoon >= last, "at " + tenth / 10F + " degrees the clock went back from " + last + " to " + sinceNoon);
			last = sinceNoon;
		}
	}
}
