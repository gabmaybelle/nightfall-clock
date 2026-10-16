package com.gabbymaybelle.nightfallclock;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

/**
 * Nightfall Clock's settings, opened from Mod Menu. Built from vanilla option widgets so
 * it looks like the game's own options. Changes show straight away and are saved when
 * the screen closes.
 */
public class SettingsScreen extends OptionsSubScreen {
	public SettingsScreen(Screen parent) {
		super(parent, Minecraft.getInstance().options, Component.translatable("nightfallclock.options.title"));
	}

	@Override
	protected void addOptions() {
		list.addSmall(
				toggle("clock", Config.clock, v -> Config.clock = v),
				toggle("duskWarning", Config.duskWarning, v -> Config.duskWarning = v),
				choice("position", Config.Position.values(), Config.position, v -> Config.position = v),
				choice("timeFormat", Config.TimeFormat.values(), Config.timeFormat, v -> Config.timeFormat = v));
	}

	@Override
	public void removed() {
		super.removed();
		Config.save();
	}

	private static OptionInstance<Boolean> toggle(String key, boolean value, Consumer<Boolean> set) {
		return OptionInstance.createBoolean("nightfallclock.options." + key,
				OptionInstance.cachedConstantTooltip(Component.translatable("nightfallclock.options." + key + ".tooltip")),
				value, set::accept);
	}

	/** A button that cycles through an enum's values, each named "nightfallclock.options.<key>.<value>". */
	private static <E extends Enum<E>> OptionInstance<E> choice(String key, E[] values, E value, Consumer<E> set) {
		String prefix = "nightfallclock.options." + key;
		return new OptionInstance<>(prefix,
				OptionInstance.cachedConstantTooltip(Component.translatable(prefix + ".tooltip")),
				// The button adds "Position: " itself, so this is just the value.
				(caption, v) -> Component.translatable(prefix + "." + v.name().toLowerCase(Locale.ROOT)),
				new OptionInstance.Enum<>(List.of(values), Codec.INT.xmap(i -> values[i], Enum::ordinal)),
				value, set::accept);
	}
}
