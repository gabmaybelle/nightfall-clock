package com.gabbymaybelle.nightfallclock;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.OptionsSubScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Nightfall Clock's settings, opened from Mod Menu. Built from vanilla option widgets so
 * it looks like the game's own options. Changes show straight away and are saved when
 * the screen closes.
 */
public class SettingsScreen extends OptionsSubScreen {
	private OptionsList list;

	public SettingsScreen(Screen parent) {
		super(parent, Minecraft.getInstance().options, Component.translatable("nightfallclock.options.title"));
	}

	@Override
	protected void init() {
		// 1.20.1 has no addOptions hook: the screen builds its own list and Done button.
		list = new OptionsList(minecraft, width, height, 32, height - 32, 25);
		list.addSmall(new OptionInstance<?>[] {
				toggle("clock", Config.clock, v -> Config.clock = v),
				toggle("duskWarning", Config.duskWarning, v -> Config.duskWarning = v),
				choice("position", Config.Position.values(), Config.position, v -> Config.position = v),
				choice("timeFormat", Config.TimeFormat.values(), Config.timeFormat, v -> Config.timeFormat = v) });
		addWidget(list);
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
				.bounds(width / 2 - 100, height - 27, 200, 20).build());
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics);
		list.render(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(font, title, width / 2, 5, 0xFFFFFF);
		super.render(graphics, mouseX, mouseY, partialTick);
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
