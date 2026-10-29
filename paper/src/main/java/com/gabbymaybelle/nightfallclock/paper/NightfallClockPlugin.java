package com.gabbymaybelle.nightfallclock.paper;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Nightfall Clock as a Paper plugin: the day and time in every player's action bar ("Day 4 ·
 * 2:35 PM", gold from 6:00 PM until dark, counting down the seconds to dark after 6:00 PM), a
 * title "The sun's getting real low" when dusk arrives and "Storm's rolling in" when thunder
 * starts, and the Overworld's time in the Nether and the End. Players turn their clock off, or
 * pick 12- or 24-hour time, with {@code /clock} ({@link ClockCommand}); the server's settings are
 * in {@code plugins/NightfallClock/config.yml}. The rules are {@link DayClock}, {@link DuskWatch}
 * and {@link StormWatch}.
 */
public final class NightfallClockPlugin extends JavaPlugin {
	/** How often the action bar is sent, in ticks: well inside vanilla's 60-tick action bar, so it never fades. */
	private static final int ACTION_BAR_TICKS = 10;
	/** How long a title stays: half a second in, five seconds up, a second out. */
	private static final Title.Times TITLE_TIMES = Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(5), Duration.ofSeconds(1));

	/** The dusk and storm watches of a world that has the day, and when each last arrived (game time, -1 for never). */
	static final class WorldWatch {
		final DuskWatch dusk = new DuskWatch();
		final StormWatch storm = new StormWatch();
		long duskAt = -1;
		long stormAt = -1;
	}

	/** A player's own choices, remembered on the player: the clock off (absent: on) and the time format (absent: the server's). */
	private NamespacedKey offKey;
	private NamespacedKey formatKey;

	private boolean clock = true;
	private boolean duskWarning = true;
	private boolean countdown = true;
	private boolean stormWarning = true;
	private boolean overworldTime = true;
	private boolean twelveHour = true;
	private final Map<UUID, WorldWatch> watches = new HashMap<>();
	private int ticks = 0;

	@Override
	public void onEnable() {
		saveDefaultConfig();
		clock = getConfig().getBoolean("clock", true);
		duskWarning = getConfig().getBoolean("duskWarning", true);
		countdown = getConfig().getBoolean("countdown", true);
		stormWarning = getConfig().getBoolean("stormWarning", true);
		overworldTime = getConfig().getBoolean("overworldTime", true);
		int format = getConfig().getInt("timeFormat", 12);
		if (format != 12 && format != 24) {
			getLogger().warning("Nightfall Clock: timeFormat should be 12 or 24, not " + getConfig().get("timeFormat") + "; using 12");
			format = 12;
		}
		twelveHour = format == 12;
		offKey = new NamespacedKey(this, "off");
		formatKey = new NamespacedKey(this, "format");
		getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
				event.registrar().register("clock", "Your Nightfall Clock: /clock turns it on or off, /clock 12 or /clock 24 picks the time format", new ClockCommand(this)));
		Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
	}

	/** Every tick: watch each world's day for dusk and thunder, and keep the players' action bars fresh. */
	private void tick() {
		ticks++;
		for (World world : Bukkit.getWorlds()) {
			if (!hasDay(world)) {
				continue;
			}
			WorldWatch watch = watches.computeIfAbsent(world.getUID(), id -> new WorldWatch());
			long dayTime = world.getTime();
			boolean dusk = watch.dusk.update(DayClock.minuteOfDay(dayTime));
			boolean storm = watch.storm.update(world.isThundering());
			if (dusk) {
				watch.duskAt = world.getGameTime();
				if (duskWarning) {
					int seconds = countdown ? DayClock.secondsToDark(dayTime, world.isThundering()) : -1;
					Component subtitle = seconds >= 0 ? Component.text(DayClock.darkIn(seconds), NamedTextColor.GOLD) : Component.empty();
					show(world, Title.title(Component.text(DayClock.DUSK, NamedTextColor.GOLD), subtitle, TITLE_TIMES));
				}
			}
			if (storm) {
				watch.stormAt = world.getGameTime();
				if (stormWarning) {
					show(world, Title.title(Component.text(DayClock.STORM, NamedTextColor.AQUA), Component.empty(), TITLE_TIMES));
				}
			}
		}
		if (clock && ticks % ACTION_BAR_TICKS == 0) {
			for (Player player : Bukkit.getOnlinePlayers()) {
				if (clockOn(player)) {
					Component line = line(player.getWorld(), twelveHour(player));
					if (line != null) {
						player.sendActionBar(line);
					}
				}
			}
		}
	}

	/** A title to everyone in the world whose clock is on. */
	private void show(World world, Title title) {
		for (Player player : world.getPlayers()) {
			if (clockOn(player)) {
				player.showTitle(title);
			}
		}
	}

	/**
	 * The clock line for a player in this world: the world's own day, the Overworld's in the Nether
	 * and the End (when that setting is on), or null where there's no day to show. Gold from 6:00 PM
	 * until dark.
	 */
	Component line(World world, boolean twelveHour) {
		Reading reading = read(world);
		if (reading == null) {
			return null;
		}
		return Component.text(reading.line(twelveHour), reading.gold() ? NamedTextColor.GOLD : NamedTextColor.WHITE);
	}

	/** What the clock reads in a world just now: the numbers behind the line, for the line itself and for the selftest. */
	record Reading(World world, long dayTime, int day, int minute, int secondsToDark, boolean elsewhere, boolean thundering) {
		String line(boolean twelveHour) {
			return DayClock.line(day, minute, twelveHour, secondsToDark, elsewhere);
		}

		boolean gold() {
			return DayClock.gold(minute, secondsToDark);
		}
	}

	/** The reading for a player standing in this world, or null where there's nothing to show. */
	Reading read(World world) {
		boolean elsewhere = false;
		if (!hasDay(world)) {
			if (!overworldTime || world.getEnvironment() == World.Environment.NORMAL || world.getEnvironment() == World.Environment.CUSTOM) {
				return null;
			}
			// The Nether and the End have no day of their own, so they show the main world's.
			world = Bukkit.getWorlds().get(0);
			if (!hasDay(world)) {
				return null;
			}
			elsewhere = true;
		}
		long dayTime = world.getTime();
		int minute = DayClock.minuteOfDay(dayTime);
		// No countdown for a day shown from elsewhere: there's no sky of its own to go dark.
		int seconds = countdown && !elsewhere ? DayClock.secondsToDark(dayTime, world.isThundering()) : -1;
		return new Reading(world, dayTime, DayClock.dayNumber(world.getFullTime()), minute, seconds, elsewhere, world.isThundering());
	}

	/** The watch of a world that has the day, for the selftest; null for any other world. */
	WorldWatch watch(World world) {
		return watches.get(world.getUID());
	}

	/** Whether a world has a day of its own to tell the time by: the Overworld, or a custom world whose time runs. */
	static boolean hasDay(World world) {
		World.Environment environment = world.getEnvironment();
		return (environment == World.Environment.NORMAL || environment == World.Environment.CUSTOM) && !world.isFixedTime();
	}

	boolean clockOn(Player player) {
		return !player.getPersistentDataContainer().has(offKey, PersistentDataType.BYTE);
	}

	void setClockOn(Player player, boolean on) {
		PersistentDataContainer data = player.getPersistentDataContainer();
		if (on) {
			data.remove(offKey);
		} else {
			data.set(offKey, PersistentDataType.BYTE, (byte) 1);
		}
	}

	/** The player's own format, or the server's for a player who hasn't picked one. */
	boolean twelveHour(Player player) {
		String format = player.getPersistentDataContainer().get(formatKey, PersistentDataType.STRING);
		return format == null ? twelveHour : format.equals("12");
	}

	void setTwelveHour(Player player, boolean twelveHour) {
		player.getPersistentDataContainer().set(formatKey, PersistentDataType.STRING, twelveHour ? "12" : "24");
	}

	boolean serverTwelveHour() {
		return twelveHour;
	}
}
