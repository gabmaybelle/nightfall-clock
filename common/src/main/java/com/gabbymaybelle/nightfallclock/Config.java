package com.gabbymaybelle.nightfallclock;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.chrono.IsoChronology;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Settings in {@code config/nightfallclock.properties}, also editable in game through
 * Mod Menu. Missing keys get their defaults, and the file is only written when it's missing a
 * setting (so players can see what exists) or the settings screen changes one; a value that
 * can't be read keeps its default and says so in the log. Settings this version doesn't know are
 * kept. Changes apply straight away.
 */
public final class Config {
	/** config/nightfallclock.properties; set by {@link #load(Path)}, since each loader knows its config folder. */
	private static Path file;

	/** Day counter and clock, above the hotbar or in a corner. */
	public static boolean clock = true;
	/** "The sun's getting real low" above the hotbar at 6:00 PM. */
	public static boolean duskWarning = true;
	public static Position position = Position.ABOVE_HOTBAR;
	public static TimeFormat timeFormat = TimeFormat.AUTO;

	/** Where the clock sits: centred above the hotbar (where the dusk message shows), or a corner. */
	public enum Position {
		ABOVE_HOTBAR, TOP_RIGHT, TOP_LEFT, BOTTOM_RIGHT, BOTTOM_LEFT;

		public boolean right() {
			return this == TOP_RIGHT || this == BOTTOM_RIGHT;
		}

		public boolean top() {
			return this == TOP_RIGHT || this == TOP_LEFT;
		}
	}

	/** 12-hour (6:00 PM) or 24-hour (18:00) time. AUTO follows the computer's region settings. */
	public enum TimeFormat {
		AUTO, TWELVE_HOUR, TWENTY_FOUR_HOUR;

		private static final boolean LOCALE_USES_12_HOUR = usesTwelveHour(DateTimeFormatterBuilder
				.getLocalizedDateTimePattern(null, FormatStyle.SHORT, IsoChronology.INSTANCE, Locale.getDefault(Locale.Category.FORMAT)));

		public boolean twelveHour() {
			return this == AUTO ? LOCALE_USES_12_HOUR : this == TWELVE_HOUR;
		}

		/**
		 * Whether a time pattern counts hours 1-12 ({@code h} or {@code K}). Checking for the hour
		 * rather than the AM/PM marker also catches regions that use a day period instead
		 * ("Bh:mm" in Traditional Chinese). Text in quotes ("HH 'h' mm") is skipped.
		 */
		private static boolean usesTwelveHour(String pattern) {
			boolean quoted = false;
			for (char c : pattern.toCharArray()) {
				if (c == '\'') {
					quoted = !quoted;
				} else if (!quoted && (c == 'h' || c == 'K')) {
					return true;
				}
			}
			return false;
		}
	}

	private Config() {
	}

	/** Reads the settings from {@code configDir}/nightfallclock.properties (the loader's config folder). */
	public static void load(Path configDir) {
		file = configDir.resolve(NightfallClock.MOD_ID + ".properties");
		Properties props = new Properties();
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file)) {
				props.load(reader);
			} catch (IOException e) {
				NightfallClock.LOGGER.warn("Nightfall Clock: couldn't read {}, using defaults", file, e);
			}
		}
		clock = bool(props, "clock", clock);
		duskWarning = bool(props, "duskWarning", duskWarning);
		position = choice(props, "position", position);
		timeFormat = choice(props, "timeFormat", timeFormat);
		loaded = props;
		// Only write when there's something to add: a new file or a new setting.
		if (!KEYS.stream().allMatch(props::containsKey)) {
			save();
		}
	}

	/** Every setting the file should have; the file is written when one is missing. */
	private static final List<String> KEYS = List.of("clock", "duskWarning", "position", "timeFormat");
	/** The file as it was read, so settings from a newer version (or added by hand) survive a save. */
	private static Properties loaded = new Properties();

	public static void save() {
		Properties props = new Properties();
		props.putAll(loaded);
		props.setProperty("clock", Boolean.toString(clock));
		props.setProperty("duskWarning", Boolean.toString(duskWarning));
		props.setProperty("position", position.name().toLowerCase(Locale.ROOT));
		props.setProperty("timeFormat", timeFormat.name().toLowerCase(Locale.ROOT));
		// Written to a temporary file first and then swapped in, so a crash part-way through never
		// leaves a half-written or empty settings file.
		Path temp = file.resolveSibling(file.getFileName() + ".tmp");
		try (Writer writer = Files.newBufferedWriter(temp)) {
			props.store(writer, "Nightfall Clock (also in game: Mods > Nightfall Clock > settings, with Mod Menu)\n"
					+ "clock: day and time above the hotbar (or in a corner)\n"
					+ "duskWarning: message above the hotbar at sunset (6:00 PM)\n"
					+ "position: above_hotbar, top_right, top_left, bottom_right or bottom_left\n"
					+ "timeFormat: auto, twelve_hour or twenty_four_hour (auto = your computer's region)");
		} catch (IOException e) {
			NightfallClock.LOGGER.warn("Nightfall Clock: couldn't write {}", file, e);
			return;
		}
		try {
			try {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			NightfallClock.LOGGER.warn("Nightfall Clock: couldn't write {}", file, e);
		}
	}

	private static boolean bool(Properties props, String key, boolean fallback) {
		String value = props.getProperty(key);
		if (value == null) {
			return fallback;
		}
		String v = value.trim().toLowerCase(Locale.ROOT);
		if (v.equals("true") || v.equals("false")) {
			return v.equals("true");
		}
		// A typo shouldn't quietly switch a setting off: keep the default and say so.
		NightfallClock.LOGGER.warn("Nightfall Clock: {} should be true or false, not \"{}\"; using {}", key, value.trim(), fallback);
		return fallback;
	}

	private static <E extends Enum<E>> E choice(Properties props, String key, E fallback) {
		String value = props.getProperty(key);
		if (value == null) {
			return fallback;
		}
		try {
			return Enum.valueOf(fallback.getDeclaringClass(), value.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			NightfallClock.LOGGER.warn("Nightfall Clock: unknown {} \"{}\", using {}", key, value.trim(), fallback.name().toLowerCase(Locale.ROOT));
			return fallback;
		}
	}
}
