package com.gabbymaybelle.nightfallclock.forge;

import com.gabbymaybelle.nightfallclock.NightfallClock;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge's way in. A dedicated server loads this class too, so it must not touch anything that only
 * exists on a client (the game's screens, GUI classes): everything that does is in
 * {@link NightfallClockForgeClient}, which is only loaded on a client.
 */
@Mod(NightfallClock.MOD_ID)
public class NightfallClockForge {
	public NightfallClockForge() {
		DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> NightfallClockForgeClient::init);
	}
}
