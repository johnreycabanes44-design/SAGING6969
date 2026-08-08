package com.zenya.mixin;

import com.mojang.authlib.GameProfile;
import com.zenya.module.modules.misc.SkinChanger;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies the SkinChanger override to the tab list and anywhere else that reads the
 * skin off the player list entry rather than off the entity.
 *
 * <p>Entries for players who have not resolved a profile yet carry a null id, hence the
 * guard before the lookup.
 */
@Mixin(PlayerInfo.class)
public abstract class PlayerListEntryMixin {
	@Shadow
	public abstract GameProfile getProfile();

	@Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
	private void zenya$overrideListEntrySkin(CallbackInfoReturnable<PlayerSkin> info) {
		GameProfile profile = this.getProfile();

		if (profile == null || profile.id() == null) {
			return;
		}

		// getOverrideSkin returns null for anyone but the local player, so other players
		// keep their own skins.
		PlayerSkin override = SkinChanger.getOverrideSkin(profile.id());

		if (override != null) {
			info.setReturnValue(override);
		}
	}
}
