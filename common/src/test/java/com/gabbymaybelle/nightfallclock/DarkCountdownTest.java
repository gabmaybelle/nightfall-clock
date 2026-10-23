package com.gabbymaybelle.nightfallclock;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** How long until monsters can spawn outside, timed from how fast the sky light is falling. */
class DarkCountdownTest {
	private static final long TICK = 50_000_000L;
	/** Vanilla's sky light falls from 15 to 4 over 1803 ticks (timeline/day.json, sky_light_level). */
	private static final float VANILLA_FALL = 11.0F / 1803;
	private final Object world = new Object();

	/** Plays ticks of falling light; returns the last answer. */
	private static int play(DarkCountdown countdown, Object world, long startTick, int ticks, float startLight, float perTick) {
		int seconds = -1;
		for (int i = 0; i < ticks; i++) {
			seconds = countdown.update(world, (startTick + i) * TICK, startLight - perTick * i, 7);
		}
		return seconds;
	}

	@Test
	void vanillaDusk() {
		// From light 11, 4 levels to go at vanilla's pace: 4 / (11/1803) = 656 ticks, about 33 s.
		DarkCountdown countdown = new DarkCountdown();
		int seconds = play(countdown, world, 0, 41, 11.0F + 40 * VANILLA_FALL, VANILLA_FALL);
		assertEquals(33, seconds);
	}

	@Test
	void aLongerDayCountsLonger() {
		// The same sky, falling half as fast: twice the wait.
		DarkCountdown countdown = new DarkCountdown();
		int seconds = play(countdown, world, 0, 41, 11.0F + 20 * VANILLA_FALL, VANILLA_FALL / 2);
		assertEquals(66, seconds);
	}

	@Test
	void notEnoughTimingYet() {
		DarkCountdown countdown = new DarkCountdown();
		assertEquals(-1, play(countdown, world, 0, 5, 11.0F, VANILLA_FALL), "a quarter of a second isn't enough to tell");
	}

	@Test
	void steadyLightIsNoCountdown() {
		DarkCountdown countdown = new DarkCountdown();
		assertEquals(-1, play(countdown, world, 0, 60, 15.0F, 0), "midday");
	}

	@Test
	void brighteningIsNoCountdown() {
		DarkCountdown countdown = new DarkCountdown();
		assertEquals(-1, play(countdown, world, 0, 60, 5.0F, -VANILLA_FALL), "dawn");
	}

	@Test
	void alreadyDark() {
		DarkCountdown countdown = new DarkCountdown();
		assertEquals(-1, play(countdown, world, 0, 60, 7.2F, VANILLA_FALL), "the light reached 7 partway through");
	}

	@Test
	void anotherWorldStartsAgain() {
		DarkCountdown countdown = new DarkCountdown();
		play(countdown, world, 0, 40, 12.0F, VANILLA_FALL);
		assertEquals(-1, countdown.update(new Object(), 40 * TICK, 11.0F, 7), "nothing timed in the new world yet");
	}

	@Test
	void aPauseIsForgotten() {
		// Without the reset, ten paused seconds would look like the light barely falling.
		DarkCountdown countdown = new DarkCountdown();
		play(countdown, world, 0, 40, 12.0F, VANILLA_FALL);
		countdown.reset();
		int seconds = play(countdown, world, 240, 41, 11.0F + 40 * VANILLA_FALL, VANILLA_FALL);
		assertEquals(33, seconds);
	}

	@Test
	void onlyRecentTimingCounts() {
		// Light that fell fast a while ago (rain coming in) and slowly now: the slow pace counts.
		DarkCountdown countdown = new DarkCountdown();
		play(countdown, world, 0, 60, 14.0F, 0.05F);
		int seconds = play(countdown, world, 60, 61, 11.0F + 60 * VANILLA_FALL, VANILLA_FALL);
		assertEquals(33, seconds);
	}
}
