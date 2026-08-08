package com.zenya.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zenya.module.modules.render.NoRender;
import com.zenya.utils.NametagRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses the vanilla player label so the client can own it.
 *
 * <p>AvatarRenderer overrides {@code submitNameTag}, so the cancel in
 * {@link EntityRendererMixin} never reaches players and has to be repeated here. The
 * second case only fires for states the client already queued its own nametag for -
 * everyone else keeps the vanilla label.
 */
@Mixin(AvatarRenderer.class)
public class PlayerEntityRendererMixin {
	@Inject(method = "submitNameTag", at = @At("HEAD"), cancellable = true)
	private void zenya$renderPlayerNametag(AvatarRenderState state, PoseStack matrices, SubmitNodeCollector queue,
			CameraRenderState cameraRenderState, CallbackInfo info) {
		if (NoRender.hideNametags() || NametagRenderState.hasEntry(state)) {
			info.cancel();
		}
	}
}
