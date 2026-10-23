package com.gabbymaybelle.nightfallclock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** When "Storm's rolling in" shows: only when thunder starts as you play. */
class StormWatchTest {
	private final Object world = new Object();

	private static void clear(StormWatch storm, Object world, int ticks) {
		for (int i = 0; i < ticks; i++) {
			storm.update(world, false);
		}
	}

	@Test
	void thunderStarts() {
		StormWatch storm = new StormWatch();
		clear(storm, world, 100);
		assertTrue(storm.update(world, true));
		assertFalse(storm.update(world, true), "only once");
	}

	@Test
	void joiningInAStorm() {
		StormWatch storm = new StormWatch();
		assertFalse(storm.update(world, true), "the first tick in a world");
		assertFalse(storm.update(world, true));
	}

	@Test
	void weatherArrivingJustAfterTheWorld() {
		// The world can show clear for a tick before the weather packet lands.
		StormWatch storm = new StormWatch();
		clear(storm, world, 2);
		assertFalse(storm.update(world, true));
	}

	@Test
	void anotherWorldInAStorm() {
		StormWatch storm = new StormWatch();
		clear(storm, world, 100);
		assertFalse(storm.update(new Object(), true), "a different world's storm isn't arriving here");
	}

	@Test
	void aSecondStorm() {
		StormWatch storm = new StormWatch();
		clear(storm, world, 100);
		storm.update(world, true);
		clear(storm, world, StormWatch.CLEAR_TICKS);
		assertTrue(storm.update(world, true), "clear for a second, then thunder again");
	}

	@Test
	void flickerIsNotASecondStorm() {
		StormWatch storm = new StormWatch();
		clear(storm, world, 100);
		storm.update(world, true);
		clear(storm, world, 3);
		assertFalse(storm.update(world, true), "a few clear ticks inside one storm");
	}

	@Test
	void leavingAndComingBack() {
		StormWatch storm = new StormWatch();
		clear(storm, world, 100);
		storm.update(null, false);
		assertFalse(storm.update(world, true), "rejoining in a storm isn't it arriving");
	}
}
