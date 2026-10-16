package com.gabbymaybelle.nightfallclock.fabric;

import com.gabbymaybelle.nightfallclock.Config;
import com.gabbymaybelle.nightfallclock.NightfallClock;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;

/** Fabric's way in: the shared clock, registered through Fabric API's HUD and tick events. */
public class NightfallClockFabric implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		Config.load(FabricLoader.getInstance().getConfigDir());
		HudRenderCallback.EVENT.register((graphics, deltaTracker) -> NightfallClock.extract(graphics));
		ClientTickEvents.END_CLIENT_TICK.register(NightfallClock::tick);
	}
}
