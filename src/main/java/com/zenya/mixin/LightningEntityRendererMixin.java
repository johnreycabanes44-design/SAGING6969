package com.zenya.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zenya.module.modules.render.NoRender;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LightningBoltRenderer;
import net.minecraft.client.renderer.entity.state.LightningBoltRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drops the lightning bolt geometry for NoRender.
 *
 * <p>The target is spelled out with a full descriptor because {@code submit} is
 * overloaded on the renderer; this only kills the bolt itself, not the sky flash.
 */
@Mixin(LightningBoltRenderer.class)
public class LightningEntityRendererMixin {
	@Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LightningBoltRenderState;"
			+ "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;"
			+ "Lnet/minecraft/client/renderer/state/CameraRenderState;)V", at = @At("HEAD"), cancellable = true)
	private void zenya$hideLightningEntity(LightningBoltRenderState state, PoseStack matrices, SubmitNodeCollector queue,
			CameraRenderState cameraRenderState, CallbackInfo info) {
		if (NoRender.hideThunder()) {
			info.cancel();
		}
	}
}
