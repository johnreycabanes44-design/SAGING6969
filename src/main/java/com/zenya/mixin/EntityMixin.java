package com.zenya.mixin;

import com.zenya.module.modules.misc.Freelook;
import com.zenya.module.modules.render.Freecam;
import com.zenya.module.modules.render.NoRender;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Entity-level hooks for the detached camera and for NoRender's visibility overrides.
 *
 * <p>Freecam takes mouse look before Freelook does, so with both active only the free
 * camera moves. Freecam also forces distant entities to keep rendering, since the camera
 * can sit far outside the player's normal render distance.
 *
 * <p>The static module instances are null until the modules are constructed, so every
 * lookup is null-checked before it is used.
 */
@Mixin(Entity.class)
public class EntityMixin {
	@Inject(method = "turn", at = @At("HEAD"), cancellable = true)
	private void onChangeLookDirection(double cursorDeltaX, double cursorDeltaY, CallbackInfo info) {
		Entity self = (Entity) (Object) this;

		if (self != Minecraft.getInstance().player) {
			return;
		}

		Freecam freecam = Freecam.instance;

		if (freecam != null && freecam.isEnabled()) {
			// 0.15 is vanilla's mouse-delta-to-degrees factor, kept so the free camera
			// turns at the same rate as the player would.
			double sensitivity = freecam.getLookSensitivity();
			freecam.updateRotation(cursorDeltaX * 0.15 * sensitivity, cursorDeltaY * 0.15 * sensitivity);
			info.cancel();
			return;
		}

		Freelook freelook = Freelook.instance;

		if (freelook != null && freelook.isCameraActive()) {
			freelook.consumeMouseDelta(cursorDeltaX, cursorDeltaY);
			info.cancel();
		}
	}

	@Inject(method = "isShiftKeyDown", at = @At("HEAD"), cancellable = true)
	private void onIsSneaking(CallbackInfoReturnable<Boolean> info) {
		Freecam freecam = Freecam.instance;

		if (freecam != null && freecam.isEnabled() && (Object) this == Minecraft.getInstance().player) {
			info.setReturnValue(false);
		}
	}

	@Inject(method = "Lnet/minecraft/world/entity/Entity;shouldRenderAtSqrDistance(D)Z", at = @At("HEAD"), cancellable = true)
	private void onShouldRender(double distance, CallbackInfoReturnable<Boolean> info) {
		Entity self = (Entity) (Object) this;

		if (NoRender.hideNoRenderEntity(self)) {
			info.setReturnValue(false);
			return;
		}

		Freecam freecam = Freecam.instance;

		// 25600 is 160 blocks squared - the distance is already squared here.
		if (freecam != null && freecam.isEnabled() && distance < 25600.0) {
			info.setReturnValue(true);
		}
	}

	@Inject(method = "isInvisible", at = @At("HEAD"), cancellable = true)
	private void zenya$showInvisible(CallbackInfoReturnable<Boolean> info) {
		if (NoRender.showInvisibleEntities()) {
			info.setReturnValue(false);
		}
	}

	@Inject(method = "isInvisibleTo", at = @At("HEAD"), cancellable = true)
	private void zenya$showInvisibleTo(Player player, CallbackInfoReturnable<Boolean> info) {
		if (NoRender.showInvisibleEntities()) {
			info.setReturnValue(false);
		}
	}

	@Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
	private void zenya$hideGlowing(CallbackInfoReturnable<Boolean> info) {
		if (NoRender.hideGlowing()) {
			info.setReturnValue(false);
		}
	}
}
