package com.gabbymaybelle.nightfallclock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** When "The sun's getting real low" shows: only when 6:00 PM arrives as you play. */
class DuskWatchTest {
	private static final int FIVE_FIFTY_NINE_PM = 17 * 60 + 59;
	private static final int SIX_PM = 18 * 60;
	private final Object world = new Object();

	@Test
	void playingThroughSixPm() {
		DuskWatch dusk = new DuskWatch();
		assertFalse(dusk.update(world, FIVE_FIFTY_NINE_PM));
		assertTrue(dusk.update(world, SIX_PM));
		assertFalse(dusk.update(world, SIX_PM + 1), "only once");
	}

	@Test
	void aShortJumpStillCounts() {
		DuskWatch dusk = new DuskWatch();
		dusk.update(world, SIX_PM - 5);
		assertTrue(dusk.update(world, SIX_PM + 5), "a ten-minute step is still playing through it");
	}

	@Test
	void aLongJumpDoesNot() {
		DuskWatch dusk = new DuskWatch();
		dusk.update(world, 17 * 60);
		assertFalse(dusk.update(world, 19 * 60), "/time set or sleeping past 6:00 PM");
	}

	@Test
	void joiningAfterSixPm() {
		DuskWatch dusk = new DuskWatch();
		assertFalse(dusk.update(world, SIX_PM + 2), "the first tick in a world is never dusk arriving");
		assertFalse(dusk.update(world, SIX_PM + 3));
	}

	@Test
	void midnightWrap() {
		DuskWatch dusk = new DuskWatch();
		dusk.update(world, 23 * 60 + 59);
		assertFalse(dusk.update(world, 0), "midnight isn't dusk");
		assertFalse(dusk.update(world, 1));
	}

	@Test
	void anotherWorldPastSixPm() {
		// Moving between servers behind a proxy: the world changes without ever being null.
		DuskWatch dusk = new DuskWatch();
		dusk.update(world, FIVE_FIFTY_NINE_PM);
		assertFalse(dusk.update(new Object(), SIX_PM + 1), "a different world's time isn't dusk arriving here");
	}

	@Test
	void aWorldWithNoDay() {
		DuskWatch dusk = new DuskWatch();
		dusk.update(world, FIVE_FIFTY_NINE_PM);
		assertFalse(dusk.update(world, -1), "the Nether");
		assertFalse(dusk.update(world, SIX_PM + 1), "coming back after 6:00 PM");
	}

	@Test
	void leavingAndComingBack() {
		DuskWatch dusk = new DuskWatch();
		dusk.update(world, FIVE_FIFTY_NINE_PM);
		dusk.update(null, -1);
		assertFalse(dusk.update(world, SIX_PM), "rejoining isn't playing through it");
	}
}
