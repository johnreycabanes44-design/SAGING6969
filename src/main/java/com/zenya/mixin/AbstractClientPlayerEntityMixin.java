package com.zenya.mixin;

import com.zenya.module.modules.misc.SkinChanger;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies the SkinChanger override before vanilla resolves the skin. */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerEntityMixin {
	@Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
	private void zenya$overrideOwnSkin(CallbackInfoReturnable<PlayerSkin> info) {
		// getOverrideSkin returns null for anyone but the local player, so other players
		// keep their own skins.
		PlayerSkin override = SkinChanger.getOverrideSkin(((AbstractClientPlayer) (Object) this).getUUID());

		if (override != null) {
			info.setReturnValue(override);
		}
	}
}
