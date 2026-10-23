package com.gabbymaybelle.nightfallclock.filmmixin;

import com.gabbymaybelle.nightfallclock.NightfallClock;
import com.gabbymaybelle.nightfallclock.VideoClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Filming only (the game-test mod, never the mod's jar): the countdown to dark reads the video's
 * clock instead of the computer's while a take of the third Short runs. See VideoClock.
 */
@Mixin(value = NightfallClock.class, remap = false)
abstract class FilmClockMixin {
	@Redirect(method = "countdown", at = @At(value = "INVOKE", target = "Ljava/lang/System;nanoTime()J"), remap = false)
	private static long nightfallclockgametest$videoClock() {
		return VideoClock.on ? VideoClock.nanos() : System.nanoTime();
	}
}
