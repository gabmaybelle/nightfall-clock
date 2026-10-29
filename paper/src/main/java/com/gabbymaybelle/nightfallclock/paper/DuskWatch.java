package com.gabbymaybelle.nightfallclock.paper;

/**
 * Decides the moment dusk arrives in a world: the clock passing 6:00 PM as the day runs. A jump of
 * more than {@link #MAX_STEP_MINUTES} (a /time set, or everyone sleeping) isn't dusk arriving, and
 * neither is the first look at a world that's already past 6:00 PM. The same rule as the mod's,
 * one per world. No Bukkit classes, so it's unit-tested.
 */
final class DuskWatch {
	/** Larger forward steps than this (in game minutes) are /time set or sleeping, not dusk arriving. */
	static final int MAX_STEP_MINUTES = 10;

	private int lastMinute = -1;

	/** Called every tick with the clock time in minutes after midnight. True at the moment dusk arrives. */
	boolean update(int minute) {
		boolean arrived = lastMinute >= 0 && lastMinute < DayClock.SUNSET_MINUTE && minute >= DayClock.SUNSET_MINUTE
				&& minute - lastMinute <= MAX_STEP_MINUTES;
		lastMinute = minute;
		return arrived;
	}
}
