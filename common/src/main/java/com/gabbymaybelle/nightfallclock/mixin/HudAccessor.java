package com.gabbymaybelle.nightfallclock.mixin;

import net.minecraft.client.gui.Hud;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only peek at vanilla's text above the hotbar, so the clock can step around it. */
@Mixin(Hud.class)
public interface HudAccessor {
	/** Ticks left on the action-bar message; above 0 while one is showing (or fading). */
	@Accessor("overlayMessageTime")
	int nightfallclock$overlayMessageTime();

	/** Ticks left on the held item's name, shown for a moment after switching hotbar slots. */
	@Accessor("toolHighlightTimer")
	int nightfallclock$toolHighlightTimer();

	@Accessor("lastToolHighlight")
	ItemStack nightfallclock$lastToolHighlight();
}
