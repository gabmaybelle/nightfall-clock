package com.gabbymaybelle.nightfallclock.forge;

import com.gabbymaybelle.nightfallclock.Config;
import com.gabbymaybelle.nightfallclock.NightfallClock;
import com.gabbymaybelle.nightfallclock.SettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

/** Everything the clock does on Forge, registered through Forge's overlay and tick events. Client only. */
final class NightfallClockForgeClient {
	private NightfallClockForgeClient() {
	}

	static void init() {
		Config.load(FMLPaths.CONFIGDIR.get());
		FMLJavaModLoadingContext.get().getModEventBus().addListener(NightfallClockForgeClient::overlays);
		MinecraftForge.EVENT_BUS.addListener(NightfallClockForgeClient::tick);
		// The settings button on Forge's Mods screen.
		ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
				() -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new SettingsScreen(parent)));
	}

	private static void overlays(RegisterGuiOverlaysEvent event) {
		event.registerAboveAll("clock", (gui, graphics, partialTick, width, height) -> NightfallClock.extract(graphics, partialTick));
	}

	private static void tick(TickEvent.ClientTickEvent event) {
		if (event.phase == TickEvent.Phase.END) {
			NightfallClock.tick(Minecraft.getInstance());
		}
	}
}
