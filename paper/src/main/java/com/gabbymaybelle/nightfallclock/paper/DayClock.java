package com.gabbymaybelle.nightfallclock.paper;

import java.util.Locale;

/**
 * The clock's rules, from a world's day ticks. The mod reads the time from where the sun is, but
 * a plugin can't see the sun, only the day's ticks, so this is vanilla's own sun read back: the
 * sun stands at noon at tick 6000 and goes round once in 24000 ticks, so 6:00 PM is tick 12000.
 * The countdown to dark follows vanilla's sky light, which starts falling at tick 11867 and
 * reaches 4 of 15 at tick 13670 in a straight line ({@code timeline/day.json},
 * {@code minecraft:gameplay/sky_light_level}); monsters can spawn outside once it is at or below 7
 * ({@code monsterSpawnLightTest} in the overworld), which is tick 13178 or so. No Bukkit classes,
 * so it's unit-tested. The words match the mod's {@code en_us.json}.
 */
final class DayClock {
	static final int DAY_TICKS = 24000;
	static final int MINUTES_PER_DAY = 24 * 60;
	/** Tick 0 is 6:00 AM: the sun's noon (tick 6000) is 12:00. */
	static final int TICKS_BEFORE_MIDNIGHT = 6000;
	static final int SUNSET_MINUTE = 18 * 60;
	static final int NIGHT_MINUTE = 19 * 60;
	/** The sky light starts falling here (full, 15 of 15, before it). */
	static final int LIGHT_FALL_START = 11867;
	/** And has fallen to {@link #LIGHT_AT_FALL_END} of full by here. */
	static final int LIGHT_FALL_END = 13670;
	static final double LIGHT_AT_FALL_END = 0.26666668;
	/** Monsters can spawn under open sky once the sky light is at or below this (of 15). */
	static final int MONSTER_LIGHT = 7;
	/** The day tick at which the sky light reaches {@link #MONSTER_LIGHT}: about 13178.3. */
	static final double DARK_TICK = LIGHT_FALL_START
			+ (1.0 - MONSTER_LIGHT / 15.0) / (1.0 - LIGHT_AT_FALL_END) * (LIGHT_FALL_END - LIGHT_FALL_START);
	static final int TICKS_PER_SECOND = 20;

	static final String DUSK = "The sun's getting real low";
	static final String STORM = "Storm's rolling in";

	private DayClock() {
	}

	/** Minutes after midnight on the vanilla clock at this day tick (0-23999, or any tick: it wraps). */
	static int minuteOfDay(long dayTime) {
		return (int) (Math.floorMod(dayTime + TICKS_BEFORE_MIDNIGHT, DAY_TICKS) * MINUTES_PER_DAY / DAY_TICKS);
	}

	/** The day number, counted from 1 and changing at 6:00 AM (tick 0), the way Minecraft counts days. */
	static int dayNumber(long fullTime) {
		return (int) Math.floorDiv(fullTime, DAY_TICKS) + 1;
	}

	/**
	 * Whole real seconds until it's dark enough outside for monsters to spawn, from 6:00 PM until
	 * then at the usual 20 ticks a second; -1 at any other time, or in a thunderstorm, when they can
	 * spawn already.
	 */
	static int secondsToDark(long dayTime, boolean thundering) {
		if (thundering || minuteOfDay(dayTime) < SUNSET_MINUTE) {
			return -1;
		}
		double left = DARK_TICK - Math.floorMod(dayTime, DAY_TICKS);
		return left <= 0 ? -1 : (int) Math.ceil(left / TICKS_PER_SECOND);
	}

	/** Gold from 6:00 PM until dark: 7:00 PM, or later while the clock is still counting down. */
	static boolean gold(int minute, int secondsToDark) {
		return minute >= SUNSET_MINUTE && (minute < NIGHT_MINUTE || secondsToDark >= 0);
	}

	/**
	 * "Day 4 · 2:35 PM", or "Day 4 · 14:35" with 24-hour time. Locale.ROOT keeps the digits Latin,
	 * the way the game writes numbers.
	 */
	static String time(int day, int minute, boolean twelveHour) {
		int hour24 = minute / 60;
		String minutes = String.format(Locale.ROOT, "%02d", minute % 60);
		if (!twelveHour) {
			return String.format(Locale.ROOT, "Day %d · %02d:%s", day, hour24, minutes);
		}
		int hour12 = hour24 % 12 == 0 ? 12 : hour24 % 12;
		return String.format(Locale.ROOT, "Day %d · %d:%s %s", day, hour12, minutes, hour24 < 12 ? "AM" : "PM");
	}

	/** "dark in 24s", or "dark in 1:05" from a minute up. */
	static String darkIn(int seconds) {
		return seconds < 60
				? String.format(Locale.ROOT, "dark in %ds", seconds)
				: String.format(Locale.ROOT, "dark in %d:%02d", seconds / 60, seconds % 60);
	}

	/**
	 * The whole clock line: "Day 4 · 6:42 PM · dark in 24s" while counting down (seconds -1
	 * otherwise), and "Overworld: Day 4 · 9:12 PM" when shown in the Nether or the End.
	 */
	static String line(int day, int minute, boolean twelveHour, int secondsToDark, boolean elsewhere) {
		String text = time(day, minute, twelveHour);
		if (secondsToDark >= 0) {
			text = text + " · " + darkIn(secondsToDark);
		}
		return elsewhere ? "Overworld: " + text : text;
	}
}
