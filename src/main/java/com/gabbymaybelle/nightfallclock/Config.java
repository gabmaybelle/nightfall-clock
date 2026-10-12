package com.gabbymaybelle.nightfallclock;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.chrono.IsoChronology;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Settings in {@code config/nightfallclock.properties}, also editable in game through
 * Mod Menu. Missing keys get their defaults and the file is rewritten with every key,
 * so players can see what exists. Changes apply straight away.
 */
public final class Config {
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve(NightfallClock.MOD_ID + ".properties");

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

		private static final boolean LOCALE_USES_12_HOUR = DateTimeFormatterBuilder
				.getLocalizedDateTimePattern(null, FormatStyle.SHORT, IsoChronology.INSTANCE, Locale.getDefault(Locale.Category.FORMAT))
				.contains("a");

		public boolean twelveHour() {
			return this == AUTO ? LOCALE_USES_12_HOUR : this == TWELVE_HOUR;
		}
	}

	private Config() {
	}

	static void load() {
		Properties props = new Properties();
		if (Files.exists(FILE)) {
			try (Reader reader = Files.newBufferedReader(FILE)) {
				props.load(reader);
			} catch (IOException e) {
				NightfallClock.LOGGER.warn("Nightfall Clock: couldn't read {}, using defaults", FILE, e);
			}
		}
		clock = bool(props, "clock", clock);
		duskWarning = bool(props, "duskWarning", duskWarning);
		position = choice(props, "position", position);
		timeFormat = choice(props, "timeFormat", timeFormat);
		save();
	}

	public static void save() {
		Properties props = new Properties();
		props.setProperty("clock", Boolean.toString(clock));
		props.setProperty("duskWarning", Boolean.toString(duskWarning));
		props.setProperty("position", position.name().toLowerCase(Locale.ROOT));
		props.setProperty("timeFormat", timeFormat.name().toLowerCase(Locale.ROOT));
		try (Writer writer = Files.newBufferedWriter(FILE)) {
			props.store(writer, "Nightfall Clock (also in game: Mods > Nightfall Clock > settings, with Mod Menu)\n"
					+ "clock: day and time above the hotbar (or in a corner)\n"
					+ "duskWarning: message above the hotbar at sunset (6:00 PM)\n"
					+ "position: above_hotbar, top_right, top_left, bottom_right or bottom_left\n"
					+ "timeFormat: auto, twelve_hour or twenty_four_hour (auto = your computer's region)");
		} catch (IOException e) {
			NightfallClock.LOGGER.warn("Nightfall Clock: couldn't write {}", FILE, e);
		}
	}

	private static boolean bool(Properties props, String key, boolean fallback) {
		String value = props.getProperty(key);
		return value == null ? fallback : Boolean.parseBoolean(value.trim());
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
