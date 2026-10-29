package com.gabbymaybelle.nightfallclock.paper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** When dusk and a storm count as arriving: the mod's rules, one watch per world. */
class WatchTest {
	private static final int SIX_PM = 18 * 60;

	@Test
	void playingThroughSixPm() {
		DuskWatch dusk = new DuskWatch();
		assertFalse(dusk.update(SIX_PM - 1));
		assertTrue(dusk.update(SIX_PM));
		assertFalse(dusk.update(SIX_PM + 1), "only once");
	}

	@Test
	void aShortJumpStillCounts() {
		DuskWatch dusk = new DuskWatch();
		dusk.update(SIX_PM - 5);
		assertTrue(dusk.update(SIX_PM + 5), "a ten-minute step is still playing through it");
	}

	@Test
	void aLongJumpDoesNot() {
		DuskWatch dusk = new DuskWatch();
		dusk.update(17 * 60);
		assertFalse(dusk.update(19 * 60), "/time set or sleeping past 6:00 PM");
	}

	@Test
	void aWorldAlreadyPastSixPm() {
		DuskWatch dusk = new DuskWatch();
		assertFalse(dusk.update(SIX_PM + 2), "the first look at a world is never dusk arriving");
		assertFalse(dusk.update(SIX_PM + 3));
	}

	@Test
	void theNextDayCountsAgain() {
		DuskWatch dusk = new DuskWatch();
		dusk.update(SIX_PM - 1);
		assertTrue(dusk.update(SIX_PM));
		dusk.update(23 * 60);
		dusk.update(0);
		dusk.update(SIX_PM - 1);
		assertTrue(dusk.update(SIX_PM));
	}

	@Test
	void thunderAfterClearWeather() {
		StormWatch storm = new StormWatch();
		for (int i = 0; i < StormWatch.CLEAR_TICKS; i++) {
			assertFalse(storm.update(false));
		}
		assertTrue(storm.update(true));
		assertFalse(storm.update(true), "only once while it lasts");
	}

	@Test
	void aWorldAlreadyThundering() {
		StormWatch storm = new StormWatch();
		assertFalse(storm.update(true), "the first look at a world is never a storm arriving");
		storm.update(false);
		assertFalse(storm.update(true), "nor is a flicker a moment later");
	}

	@Test
	void aSecondStormLater() {
		StormWatch storm = new StormWatch();
		for (int i = 0; i < StormWatch.CLEAR_TICKS; i++) {
			storm.update(false);
		}
		assertTrue(storm.update(true));
		for (int i = 0; i < StormWatch.CLEAR_TICKS; i++) {
			storm.update(false);
		}
		assertTrue(storm.update(true));
	}
}
