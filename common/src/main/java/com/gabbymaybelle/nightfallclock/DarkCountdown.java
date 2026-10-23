package com.gabbymaybelle.nightfallclock;

import java.util.ArrayDeque;

/**
 * How many real seconds until it's dark enough outside for monsters to spawn. Monsters can spawn
 * under open sky once the sky light level falls to the dimension's monster light limit (7 in the
 * overworld; Monster.isDarkEnoughToSpawn), so this watches the sky light fall and times how fast
 * it's falling. Timing it, rather than counting vanilla's ticks, keeps it right on longer days,
 * in rain and under /tick rate. Kept free of Minecraft classes so it can be unit-tested; the world
 * is any object that stands for it.
 */
final class DarkCountdown {
	/** How far back the fall is timed over, in nanoseconds. */
	static final long WINDOW_NANOS = 2_000_000_000L;
	/** Less than this much timing, in nanoseconds, isn't enough to tell how fast the light is falling. */
	static final long MIN_NANOS = 500_000_000L;

	private record Sample(long nanos, float light) {
	}

	private final ArrayDeque<Sample> samples = new ArrayDeque<>();
	private Object world;

	/** Forgets the timing so far, for when time stops (the game paused) and starts again. */
	void reset() {
		samples.clear();
	}

	/**
	 * Called once a tick with the current world, the time in nanoseconds, the sky light level
	 * (0-15, with fractions) and the level at or below which monsters can spawn. Returns the
	 * whole seconds left until dark, or -1 when it isn't getting darker or is dark already.
	 */
	int update(Object world, long nanos, float light, int darkAt) {
		if (world != this.world) {
			this.world = world;
			samples.clear();
		}
		samples.addLast(new Sample(nanos, light));
		while (samples.size() > 2 && nanos - samples.peekFirst().nanos() > WINDOW_NANOS) {
			samples.removeFirst();
		}
		if (light <= darkAt) {
			return -1;
		}
		Sample first = samples.peekFirst();
		long elapsed = nanos - first.nanos();
		float fallen = first.light() - light;
		if (elapsed < MIN_NANOS || fallen <= 0) {
			return -1;
		}
		double perSecond = fallen / (elapsed / 1e9);
		return (int) Math.ceil((light - darkAt) / perSecond);
	}
}
