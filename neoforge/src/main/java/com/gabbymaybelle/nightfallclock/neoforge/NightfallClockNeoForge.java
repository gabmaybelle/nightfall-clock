package com.gabbymaybelle.nightfallclock.neoforge;

import com.gabbymaybelle.nightfallclock.Config;
import com.gabbymaybelle.nightfallclock.NightfallClock;
import com.gabbymaybelle.nightfallclock.SettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/**
 * NeoForge's way in: the shared clock, registered through NeoForge's HUD layer and tick events, and
 * the settings button on the Mods screen. Client only (dist = CLIENT), so a dedicated server never
 * loads this class.
 */
@Mod(value = NightfallClock.MOD_ID, dist = Dist.CLIENT)
public class NightfallClockNeoForge {
	public NightfallClockNeoForge(IEventBus modBus, ModContainer container) {
		Config.load(FMLPaths.CONFIGDIR.get());
		modBus.addListener(NightfallClockNeoForge::layers);
		NeoForge.EVENT_BUS.addListener(NightfallClockNeoForge::tick);
		container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new SettingsScreen(parent));
	}

	private static void layers(RegisterGuiLayersEvent event) {
		event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(NightfallClock.MOD_ID, "clock"),
				(graphics, deltaTracker) -> NightfallClock.extract(graphics));
	}

	private static void tick(ClientTickEvent.Post event) {
		NightfallClock.tick(Minecraft.getInstance());
	}
}
