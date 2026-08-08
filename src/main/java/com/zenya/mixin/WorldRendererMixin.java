package com.zenya.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import com.zenya.module.ModuleManager;
import com.zenya.module.modules.render.NoRender;
import com.zenya.utils.RenderUtils;
import com.zenya.utils.renderer.ProjectionUtil;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drives the client's in-world render pass off the vanilla level render.
 *
 * <p>HEAD captures the camera state the rest of the client projects against; RETURN then
 * replays it, so modules draw after the world is complete and are not clipped by it. The
 * captured matrix is stripped of its translation because module rendering is done in
 * camera-relative space.
 */
@Mixin(LevelRenderer.class)
public class WorldRendererMixin {
	private static final Matrix4f capturedMatrix = new Matrix4f();
	private static boolean hasCapturedMatrix;
	private static float capturedTickDelta = 1.0f;

	@Inject(method = "renderLevel", at = @At("HEAD"))
	private void captureRenderState(GraphicsResourceAllocator allocator, DeltaTracker tickCounter, boolean renderBlockOutline,
			Camera camera, Matrix4f positionMatrix, Matrix4f projectionMatrix, Matrix4f frustumMatrix, GpuBufferSlice fog,
			Vector4f fogColor, boolean renderTicking, CallbackInfo info) {
		capturedMatrix.set(positionMatrix);

		// Both projection fields take the same matrix; the client reads them under two names.
		ProjectionUtil.modelViewMatrix = positionMatrix;
		ProjectionUtil.positionMatrix = positionMatrix;
		ProjectionUtil.projectionMatrix = projectionMatrix;
		RenderUtils.updateFrustum(positionMatrix, projectionMatrix, camera.position());
		RenderUtils.lastFogBuffer = fog;

		hasCapturedMatrix = true;
		capturedTickDelta = tickCounter.getGameTimeDeltaPartialTick(false);
	}

	@Inject(method = "renderLevel", at = @At("RETURN"))
	private void onRender(CallbackInfo info) {
		if (!hasCapturedMatrix) {
			return;
		}

		PoseStack poseStack = new PoseStack();
		Matrix4f rotationOnly = new Matrix4f(capturedMatrix);
		rotationOnly.setTranslation(0.0f, 0.0f, 0.0f);
		poseStack.mulPose(rotationOnly);
		ModuleManager.INSTANCE.onRender(poseStack, capturedTickDelta);
	}

	@Inject(method = "addWeatherPass", at = @At("HEAD"), cancellable = true)
	private void zenya$skipWeatherPass(FrameGraphBuilder frameGraphBuilder, GpuBufferSlice fog, CallbackInfo info) {
		if (NoRender.hideAllPrecipitation()) {
			info.cancel();
		}
	}
}
