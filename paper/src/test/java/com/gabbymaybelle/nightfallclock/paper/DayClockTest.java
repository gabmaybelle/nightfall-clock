package com.gabbymaybelle.nightfallclock.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The clock read from the day's ticks, the countdown to dark and the words on the line. */
class DayClockTest {
	@Test
	void theVanillaClock() {
		assertEquals(6 * 60, DayClock.minuteOfDay(0), "tick 0 is 6:00 AM");
		assertEquals(12 * 60, DayClock.minuteOfDay(6000), "noon");
		assertEquals(18 * 60, DayClock.minuteOfDay(12000), "6:00 PM");
		assertEquals(0, DayClock.minuteOfDay(18000), "midnight");
		assertEquals(6 * 60, DayClock.minuteOfDay(24000), "the next day's 6:00 AM");
		assertEquals(18 * 60 + 42, DayClock.minuteOfDay(12700), "the README's 6:42 PM");
	}

	@Test
	void daysCountFromOneAndChangeAtSixAm() {
		assertEquals(1, DayClock.dayNumber(0));
		assertEquals(1, DayClock.dayNumber(23999));
		assertEquals(2, DayClock.dayNumber(24000));
		assertEquals(4, DayClock.dayNumber(3 * 24000 + 12700));
	}

	@Test
	void countdownToDark() {
		assertEquals(-1, DayClock.secondsToDark(6000, false), "nothing before 6:00 PM");
		assertEquals(-1, DayClock.secondsToDark(11999, false));
		assertEquals(59, DayClock.secondsToDark(12000, false), "6:00 PM: 1178 ticks to tick 13178");
		assertEquals(24, DayClock.secondsToDark(12700, false), "the README's dark in 24s at 6:42 PM");
		assertEquals(1, DayClock.secondsToDark(13178, false), "a fraction of a tick left still rounds up");
		assertEquals(-1, DayClock.secondsToDark(13179, false), "dark");
		assertEquals(-1, DayClock.secondsToDark(18000, false));
		assertEquals(-1, DayClock.secondsToDark(12000, true), "monsters can spawn already in a thunderstorm");
	}

	@Test
	void goldFromSunsetUntilDark() {
		assertFalse(DayClock.gold(17 * 60 + 59, -1));
		assertTrue(DayClock.gold(18 * 60, 59));
		assertTrue(DayClock.gold(18 * 60 + 59, 1));
		assertTrue(DayClock.gold(19 * 60 + 5, 3), "still gold past 7:00 PM while counting down");
		assertFalse(DayClock.gold(19 * 60 + 5, -1), "white once it's dark");
		assertTrue(DayClock.gold(18 * 60 + 30, -1), "and gold until 7:00 PM with the countdown off, or in a storm");
	}

	@Test
	void theLine() {
		assertEquals("Day 4 · 2:35 PM", DayClock.time(4, 14 * 60 + 35, true));
		assertEquals("Day 4 · 14:35", DayClock.time(4, 14 * 60 + 35, false));
		assertEquals("Day 1 · 12:00 AM", DayClock.time(1, 0, true));
		assertEquals("Day 1 · 00:00", DayClock.time(1, 0, false));
		assertEquals("Day 1 · 12:05 PM", DayClock.time(1, 12 * 60 + 5, true));
		assertEquals("Day 4 · 6:42 PM · dark in 24s", DayClock.line(4, 18 * 60 + 42, true, 24, false));
		assertEquals("Day 4 · 18:02 · dark in 1:05", DayClock.line(4, 18 * 60 + 2, false, 65, false));
		assertEquals("Overworld: Day 4 · 9:12 PM", DayClock.line(4, 21 * 60 + 12, true, -1, true));
	}
}
