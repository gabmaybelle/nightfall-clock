package com.gabbymaybelle.nightfallclock.fabric;

import com.gabbymaybelle.nightfallclock.Config;
import com.gabbymaybelle.nightfallclock.NightfallClock;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

/** Fabric's way in: the shared clock, registered through Fabric API's HUD and tick events. */
public class NightfallClockFabric implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		Config.load(FabricLoader.getInstance().getConfigDir());
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(NightfallClock.MOD_ID, "clock"), NightfallClock::extract);
		ClientTickEvents.END_CLIENT_TICK.register(NightfallClock::tick);
	}
}
