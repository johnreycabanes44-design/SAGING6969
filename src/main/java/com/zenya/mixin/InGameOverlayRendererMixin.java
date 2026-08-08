package com.zenya.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zenya.module.modules.render.NoRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses the first-person screen effects NoRender hides.
 *
 * <p>The three overlay passes are static in vanilla and the totem animation is not, which
 * is why the handlers differ in modifier - the injected method has to match the target's
 * staticness or mixin refuses to apply.
 */
@Mixin(ScreenEffectRenderer.class)
public class InGameOverlayRendererMixin {
	@Inject(method = "renderTex", at = @At("HEAD"), cancellable = true)
	private static void zenya$cancelInWallOverlay(TextureAtlasSprite sprite, PoseStack matrices, MultiBufferSource vertexConsumers, CallbackInfo info) {
		if (NoRender.hideInWallOverlay()) {
			info.cancel();
		}
	}

	@Inject(method = "renderWater", at = @At("HEAD"), cancellable = true)
	private static void zenya$cancelLiquidOverlay(Minecraft client, PoseStack matrices, MultiBufferSource vertexConsumers, CallbackInfo info) {
		if (NoRender.hideLiquidOverlay()) {
			info.cancel();
		}
	}

	@Inject(method = "renderFire", at = @At("HEAD"), cancellable = true)
	private static void zenya$cancelFireOverlay(PoseStack matrices, MultiBufferSource vertexConsumers, TextureAtlasSprite sprite, CallbackInfo info) {
		if (NoRender.hideFireOverlay()) {
			info.cancel();
		}
	}

	@Inject(method = "renderItemActivationAnimation", at = @At("HEAD"), cancellable = true)
	private void zenya$cancelTotemAnimation(PoseStack matrices, float tickProgress, SubmitNodeCollector queue, CallbackInfo info) {
		if (NoRender.hideTotemAnimation()) {
			info.cancel();
		}
	}
}
