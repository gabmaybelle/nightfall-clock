package com.gabbymaybelle.nightfallclock.paper;

/**
 * Decides the moment a thunderstorm arrives in a world: monsters can spawn in daylight while it
 * thunders. A world that's already thundering when first looked at isn't a storm arriving, so it
 * has to have been clear for {@link #CLEAR_TICKS} first. The same rule as the mod's, one per
 * world. No Bukkit classes, so it's unit-tested.
 */
final class StormWatch {
	/** Ticks without thunder (a second) before thunder counts as arriving. */
	static final int CLEAR_TICKS = 20;

	private int clearTicks = 0;

	/** Called every tick. True at the moment thunder starts. */
	boolean update(boolean thundering) {
		if (!thundering) {
			clearTicks++;
			return false;
		}
		boolean arrived = clearTicks >= CLEAR_TICKS;
		clearTicks = 0;
		return arrived;
	}
}
