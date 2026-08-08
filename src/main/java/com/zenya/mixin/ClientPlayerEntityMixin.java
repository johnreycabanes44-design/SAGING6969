package com.zenya.mixin;

import com.zenya.module.modules.render.Freecam;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Freezes the real player's sneak and sprint state while Freecam is running, so the
 * body left behind does not keep crouching or sprinting from held keys the camera ate.
 */
@Mixin(LocalPlayer.class)
public class ClientPlayerEntityMixin {
	@Inject(method = "aiStep", at = @At("HEAD"))
	private void onTickMovement(CallbackInfo ci) {
		// instance is null until the module is constructed.
		if (Freecam.instance != null && Freecam.instance.isEnabled()) {
			LocalPlayer self = (LocalPlayer) (Object) this;
			self.setShiftKeyDown(false);
			self.setSprinting(false);
		}
	}
}
