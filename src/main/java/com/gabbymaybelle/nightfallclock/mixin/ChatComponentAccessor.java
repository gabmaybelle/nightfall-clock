package com.gabbymaybelle.nightfallclock.mixin;

import java.util.List;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only peek at the chat's lines, so the clock can step aside while recent chat covers it. */
@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {
	/** The wrapped chat lines, newest first. */
	@Accessor("trimmedMessages")
	List<GuiMessage.Line> nightfallclock$trimmedMessages();
}
