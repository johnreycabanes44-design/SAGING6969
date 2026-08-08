package com.zenya.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reserved hook at the head of vanilla input handling, ahead of the attack key
 * being consumed.
 *
 * <p>require = 0 because the target is optional: a remap miss must not abort
 * mixin application for the rest of the client.
 */
@Mixin(Minecraft.class)
public class MinecraftClientAttackMixin {
	// ponytail: shipped build reads no state here — the hook body is a no-op past the guards.
	@Inject(method = "handleKeybinds", at = @At("HEAD"), require = 0)
	private void zenya$preInput(CallbackInfo info) {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.player == null || mc.options == null) {
			return;
		}
		if (mc.screen != null) {
			return;
		}
	}
}
