package com.zenya.mixin;

import com.zenya.module.modules.misc.Freelook;
import com.zenya.module.modules.render.CameraTweaks;
import com.zenya.module.modules.render.Freecam;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Drives the camera for CameraTweaks, Freecam and Freelook.
 *
 * <p>Freecam wins over Freelook: it replaces both position and rotation outright, while
 * Freelook only re-aims from the player's own eye position. Both run at TAIL of setup so
 * they overwrite whatever vanilla just computed.
 *
 * <p>getMaxZoom is hooked twice on purpose - the ModifyVariable feeds the configured
 * distance into vanilla's wall clipping, and the cancellable inject skips clipping
 * entirely when the user turned it off.
 *
 * <p>The module lookups can be null before the module list is built, so each is checked.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	protected abstract void setPosition(double x, double y, double z);

	@Shadow
	protected abstract void setRotation(float yaw, float pitch);

	@Shadow
	protected abstract float getMaxZoom(float desiredCameraDistance);

	@Shadow
	protected abstract void move(float x, float y, float z);

	@Inject(method = "getMaxZoom", at = @At("HEAD"), cancellable = true)
	private void onClipToSpace(float desiredCameraDistance, CallbackInfoReturnable<Float> info) {
		CameraTweaks tweaks = CameraTweaks.get();

		if (tweaks != null && tweaks.isEnabled() && tweaks.shouldClip()) {
			info.setReturnValue((float) tweaks.getDistance());
		}
	}

	@ModifyVariable(method = "getMaxZoom", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float modifyCamDistance(float desiredCameraDistance) {
		CameraTweaks tweaks = CameraTweaks.get();

		if (tweaks != null && tweaks.isEnabled()) {
			return (float) tweaks.getDistance();
		}

		return desiredCameraDistance;
	}

	@Inject(method = "setup", at = @At("TAIL"))
	private void onUpdate(Level area, Entity focusedEntity, boolean thirdPerson, boolean inverseView,
			float tickDelta, CallbackInfo info) {
		Freecam freecam = Freecam.instance;

		if (freecam != null && freecam.isEnabled()) {
			freecam.updateCameraMovement();
			this.setPosition(freecam.getInterpolatedX(tickDelta), freecam.getInterpolatedY(tickDelta),
					freecam.getInterpolatedZ(tickDelta));
			this.setRotation(freecam.getInterpolatedYaw(tickDelta), freecam.getInterpolatedPitch(tickDelta));
			return;
		}

		Freelook freelook = Freelook.instance;

		if (freelook != null && freelook.isCameraActive()) {
			Vec3 eyePos = focusedEntity.getEyePosition(tickDelta);
			this.setPosition(eyePos.x, eyePos.y, eyePos.z);
			this.setRotation(freelook.getCameraYaw(), freelook.getCameraPitch());

			// ponytail: reads backwards - shouldWallClip() true takes the raw distance and
			// skips getMaxZoom, which is what actually clips. It is hardcoded true, so the
			// getMaxZoom branch never runs. Kept as shipped.
			float distance = freelook.shouldWallClip()
					? freelook.getDistance()
					: this.getMaxZoom(freelook.getDistance());
			this.move(-distance, 0.0f, 0.0f);
		}
	}

	@Inject(method = "isDetached", at = @At("HEAD"), cancellable = true)
	private void onIsThirdPerson(CallbackInfoReturnable<Boolean> info) {
		Freecam freecam = Freecam.instance;

		if (freecam != null && freecam.isEnabled()) {
			info.setReturnValue(true);
			return;
		}

		Freelook freelook = Freelook.instance;

		if (freelook != null && freelook.isCameraActive()) {
			info.setReturnValue(true);
		}
	}
}
