package com.gabbymaybelle.nightfallclock;

/**
 * Decides the moment dusk arrives: the clock passing 6:00 PM as you play. A jump of more than
 * {@link #MAX_STEP_MINUTES} (a /time set, or sleeping) isn't dusk arriving, and neither is
 * joining a world, or moving to a different one, when it's already past 6:00 PM there. Kept free
 * of Minecraft classes so it can be unit-tested; the world is any object that stands for it.
 */
final class DuskWatch {
	static final int SUNSET_MINUTE = 18 * 60;
	/** Larger forward steps than this (in game minutes) are /time set or sleeping, not dusk arriving. */
	static final int MAX_STEP_MINUTES = 10;

	/** The world {@link #lastMinute} was read in. */
	private Object world;
	private int lastMinute = -1;

	/**
	 * Called once a tick with the current world (null when there's none) and the clock time in
	 * minutes after midnight (-1 when this world has no day). True at the moment dusk arrives.
	 */
	boolean update(Object world, int minute) {
		if (world != this.world) {
			// A different world: its time has nothing to do with the last one's. (Moving between
			// servers behind a proxy changes the world without ever leaving one.)
			this.world = world;
			lastMinute = -1;
		}
		if (world == null || minute < 0) {
			lastMinute = -1;
			return false;
		}
		boolean arrived = lastMinute >= 0 && lastMinute < SUNSET_MINUTE && minute >= SUNSET_MINUTE
				&& minute - lastMinute <= MAX_STEP_MINUTES;
		lastMinute = minute;
		return arrived;
	}
}
