package com.gabbymaybelle.nightfallclock.filmmixin;

import com.gabbymaybelle.nightfallclock.Part3;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Filming only (the game-test mod, never the mod's jar): while Part 3 films from a free camera, the view
 * is drawn from {@link Part3#freeEye} towards {@link Part3#freeLook} instead of from behind her, so a
 * shot can face her. The camera stays hers (the game draws her own model, her hotbar and lets her walk only
 * then; an armor stand as the camera lost all three, first tries), so only where it sits changes.
 */
@Mixin(Camera.class)
abstract class FilmCameraMixin {
	@Shadow
	private boolean detached;

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Shadow
	protected abstract void setPosition(Vec3 position);

	/**
	 * Right after the camera is placed behind her, before the game works out what is in view: moved at the
	 * end of {@code update} instead, the world was still culled for her own view, so the portal and the
	 * trees behind her vanished from the shot (Gabby: "it wouldn't normally disappear like that").
	 */
	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void nightfallclockgametest$freeCamera(float partialTicks, CallbackInfo ci) {
		Vec3 eye = Part3.freeEye, look = Part3.freeLook;
		if (!Part3.freeCamera || eye == null || look == null) {
			return;
		}
		Vec3 d = look.subtract(eye);
		float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
		detached = true;
		setRotation(yaw, pitch);
		setPosition(eye);
	}
}
