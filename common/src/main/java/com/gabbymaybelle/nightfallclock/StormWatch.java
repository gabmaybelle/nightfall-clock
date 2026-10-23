package com.gabbymaybelle.nightfallclock;

/**
 * Decides the moment a thunderstorm arrives as you play: monsters can spawn in daylight while it
 * thunders. Joining a world, or moving to a different one, where it's already thundering isn't a
 * storm arriving, and neither is the weather reaching the client a moment after the world does,
 * so it has to have been clear for {@link #CLEAR_TICKS} first. Kept free of Minecraft classes so
 * it can be unit-tested; the world is any object that stands for it.
 */
final class StormWatch {
	/** Ticks of clear weather (a second) before thunder counts as arriving. */
	static final int CLEAR_TICKS = 20;

	private Object world;
	private int clearTicks = 0;

	/** Called once a tick with the current world (null when there's none). True at the moment thunder starts. */
	boolean update(Object world, boolean thundering) {
		if (world != this.world) {
			this.world = world;
			clearTicks = 0;
		}
		if (world == null) {
			return false;
		}
		if (!thundering) {
			clearTicks++;
			return false;
		}
		boolean arrived = clearTicks >= CLEAR_TICKS;
		clearTicks = 0;
		return arrived;
	}
}
