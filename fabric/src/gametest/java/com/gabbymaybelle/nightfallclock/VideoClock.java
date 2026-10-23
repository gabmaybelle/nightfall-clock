package com.gabbymaybelle.nightfallclock;

/**
 * The clock the countdown to dark reads while filming the third Short. The countdown times the sky
 * light's fall in real seconds, but a take runs several times slower than real time (each frame is
 * one game tick, saved as a screenshot), so the computer's clock would read "dark in 2:30" where the
 * finished video plays 30 seconds. This clock steps 50 ms for each frame, the pace the video plays
 * at, so the countdown shows what a player sees. FilmClockMixin hands it to the countdown, only
 * while {@link #on}; the mod's own code and jars are unchanged.
 */
public final class VideoClock {
	/** Whether the countdown reads this clock (the third Short's take) or the computer's. */
	public static volatile boolean on;
	private static volatile long nanos = 1_000_000_000_000L;

	private VideoClock() {
	}

	public static long nanos() {
		return nanos;
	}

	/** One frame of video: 50 ms, a game tick at 20 ticks a second. */
	public static void tick() {
		nanos += 50_000_000L;
	}

	/**
	 * A cut to another time: ten seconds on, past the countdown's two-second window, so the last
	 * scene's light readings drop out instead of being timed against this one's.
	 */
	public static void cut() {
		nanos += 10_000_000_000L;
	}
}
