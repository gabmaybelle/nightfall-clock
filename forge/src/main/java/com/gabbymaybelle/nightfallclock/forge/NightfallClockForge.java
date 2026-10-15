package com.gabbymaybelle.nightfallclock.forge;

import com.gabbymaybelle.nightfallclock.Config;
import com.gabbymaybelle.nightfallclock.NightfallClock;
import com.gabbymaybelle.nightfallclock.SettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

/** Forge's way in: the shared clock, registered through Forge's overlay and tick events. */
@Mod(NightfallClock.MOD_ID)
public class NightfallClockForge {
	public NightfallClockForge() {
		// The clock only draws on a client; a dedicated server loads the file and does nothing.
		if (FMLEnvironment.dist != Dist.CLIENT) {
			return;
		}
		Config.load(FMLPaths.CONFIGDIR.get());
		FMLJavaModLoadingContext.get().getModEventBus().addListener(NightfallClockForge::overlays);
		MinecraftForge.EVENT_BUS.addListener(NightfallClockForge::tick);
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
