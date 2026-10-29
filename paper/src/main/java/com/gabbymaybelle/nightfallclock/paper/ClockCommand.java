package com.gabbymaybelle.nightfallclock.paper;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /clock}: your own Nightfall Clock. On its own it turns the clock on or off; {@code on}
 * and {@code off} say which; {@code 12} and {@code 24} pick the time format. The choices are
 * remembered on the player. {@code /clock selftest} (operators, permission
 * {@code nightfallclock.selftest}) prints what the clock reads in the world you're in, in both
 * formats, with the countdown, the weather and when dusk and thunder last arrived: it's what the
 * headless server test checks, since there's no player there to see the action bar.
 */
final class ClockCommand implements BasicCommand {
	/** Every selftest line starts with this, so a test can wait for it. */
	static final String SELFTEST = "Nightfall Clock selftest:";

	private final NightfallClockPlugin plugin;

	ClockCommand(NightfallClockPlugin plugin) {
		this.plugin = plugin;
	}

	@Override
	public String permission() {
		return "nightfallclock.clock";
	}

	@Override
	public Collection<String> suggest(CommandSourceStack source, String[] args) {
		if (args.length > 1) {
			return List.of();
		}
		return source.getSender().hasPermission("nightfallclock.selftest")
				? List.of("on", "off", "12", "24", "selftest")
				: List.of("on", "off", "12", "24");
	}

	@Override
	public void execute(CommandSourceStack source, String[] args) {
		CommandSender sender = source.getSender();
		String word = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
		if (word.equals("selftest")) {
			if (!sender.hasPermission("nightfallclock.selftest")) {
				fail(sender, "Only an operator can run /clock selftest.");
				return;
			}
			selftest(source);
			return;
		}
		if (!(source.getExecutor() instanceof Player player)) {
			fail(sender, "Only a player has a clock to turn on or off. The console can run /clock selftest.");
			return;
		}
		switch (word) {
			case "" -> setOn(player, !plugin.clockOn(player));
			case "on" -> setOn(player, true);
			case "off" -> setOn(player, false);
			case "12" -> setFormat(player, true);
			case "24" -> setFormat(player, false);
			default -> fail(sender, "Usage: /clock (on or off), /clock on, /clock off, /clock 12, /clock 24.");
		}
	}

	private void setOn(Player player, boolean on) {
		plugin.setClockOn(player, on);
		if (on) {
			say(player, "Nightfall Clock: on, " + (plugin.twelveHour(player) ? "12" : "24") + "-hour time. /clock turns it off; /clock 12 or /clock 24 picks the format.");
		} else {
			player.sendActionBar(Component.empty());
			say(player, "Nightfall Clock: off. /clock turns it back on.");
		}
	}

	private void setFormat(Player player, boolean twelveHour) {
		plugin.setTwelveHour(player, twelveHour);
		say(player, "Nightfall Clock: " + (twelveHour ? "12-hour time (6:00 PM)" : "24-hour time (18:00)") + "."
				+ (plugin.clockOn(player) ? "" : " Your clock is off; /clock turns it on."));
	}

	/**
	 * One line with everything the clock would show in this world right now, so a test can read it:
	 * the world and its time, the line in both formats, whether it's gold, the countdown, the weather,
	 * and the game time dusk and thunder last arrived (or "none").
	 */
	private void selftest(CommandSourceStack source) {
		CommandSender sender = source.getSender();
		World world = source.getLocation().getWorld();
		NightfallClockPlugin.Reading reading = plugin.read(world);
		if (reading == null) {
			say(sender, SELFTEST + " nothing to show in " + world.getName() + " (no day there, or no Overworld time to show)");
			return;
		}
		NightfallClockPlugin.WorldWatch watch = plugin.watch(reading.world());
		say(sender, SELFTEST + " world " + world.getName() + " · time " + reading.dayTime() + " · game time " + reading.world().getGameTime()
				+ " · 12h \"" + reading.line(true) + "\" · 24h \"" + reading.line(false) + "\""
				+ " · gold " + reading.gold() + " · seconds " + reading.secondsToDark() + " · thundering " + reading.thundering()
				+ " · dusk " + (watch == null || watch.duskAt < 0 ? "none" : "at " + watch.duskAt)
				+ " · storm " + (watch == null || watch.stormAt < 0 ? "none" : "at " + watch.stormAt)
				+ " · server format " + (plugin.serverTwelveHour() ? "12" : "24"));
	}

	private static void say(CommandSender to, String text) {
		to.sendMessage(Component.text(text));
	}

	private static void fail(CommandSender to, String text) {
		to.sendMessage(Component.text(text, NamedTextColor.RED));
	}
}
