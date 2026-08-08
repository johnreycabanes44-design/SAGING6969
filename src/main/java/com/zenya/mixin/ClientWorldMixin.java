package com.zenya.mixin;

import com.zenya.module.modules.render.NoRender;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Reports zero remaining lightning ticks for NoRender, which kills the full-screen
 * white flash at its source instead of trying to unpaint it later in the frame.
 */
@Mixin(ClientLevel.class)
public class ClientWorldMixin {
	@Inject(method = "getSkyFlashTime", at = @At("HEAD"), cancellable = true)
	private void zenya$hideLightningFlash(CallbackInfoReturnable<Integer> cir) {
		if (NoRender.hideLightningFlash()) {
			cir.setReturnValue(0);
		}
	}
}
