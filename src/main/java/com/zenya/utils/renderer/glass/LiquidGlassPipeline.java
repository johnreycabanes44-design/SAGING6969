package com.zenya.utils.renderer.glass;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.zenya.utils.renderer.RenderUtil;
import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

/**
 * Full-screen "liquid glass" panel: a rounded rect that refracts whatever is
 * already on screen behind it.
 *
 * <p>The fragment shader samples the scene, so it cannot read the main render
 * target it is writing into. Every draw first blits the colour attachment into
 * {@link #copyTexture} (resized lazily) and samples that instead.
 *
 * <p>State is static and GPU-owned; {@link #shutdown()} must run before the
 * device goes away or the buffer and texture leak.
 */
public class LiquidGlassPipeline {
	/** Size of the std140 Uniforms block in liquidglass_fragment.fsh, in bytes. */
	public static final int UNIFORM_SIZE = 176;

	public static RenderPipeline pipeline;
	public static GpuBuffer uniformBuffer;
	public static GpuTexture copyTexture;
	public static GpuTextureView copyTextureView;
	public static int copyWidth;
	public static int copyHeight;

	public static void init() {
		if (pipeline != null) {
			return;
		}
		try {
			pipeline = RenderPipeline.builder()
					.withLocation(Identifier.fromNamespaceAndPath("zenya", "liquidglass"))
					.withVertexShader(Identifier.fromNamespaceAndPath("zenya", "liquidglass_vertex"))
					.withFragmentShader(Identifier.fromNamespaceAndPath("zenya", "liquidglass_fragment"))
					.withVertexFormat(VertexFormat.builder().build(), VertexFormat.Mode.TRIANGLES)
					.withUniform("Uniforms", UniformType.UNIFORM_BUFFER)
					.withSampler("Sampler0")
					.withBlend(BlendFunction.TRANSLUCENT)
					.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
					.withCull(false)
					.build();
			uniformBuffer = RenderSystem.getDevice().createBuffer(() -> "LiquidGlass Uniforms", 136, (long) UNIFORM_SIZE);
		}
		catch (Exception e) {
			// Swallowed on purpose: a broken shader must not take the game down.
			// pipeline stays null, so draw() simply renders nothing.
			System.err.println("[LiquidGlass] Failed to init: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Packs the uniform block, copies the framebuffer and issues the fullscreen triangle pair.
	 *
	 * @param radii per-corner radii; entries past the end of the array fall back to the first one
	 * @param color unused by the shader, kept because callers pass the panel colour here
	 * @param fresnelColor packed ARGB of the rim highlight
	 */
	public static void draw(Matrix4f projection, float x, float y, float width, float height, float[] radii, int color, float globalAlpha, float fresnelPower, int fresnelColor, float baseAlpha, boolean fresnelInvert, float fresnelMix, float distortStrength, float smoothness, float z) {
		if (pipeline == null) {
			LiquidGlassPipeline.init();
		}
		if (pipeline == null || uniformBuffer == null) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		RenderTarget target = mc.getMainRenderTarget();
		if (target == null || target.getColorTextureView() == null) {
			return;
		}
		float fresnelRed = (float)(fresnelColor >> 16 & 255) / 255.0f;
		float fresnelGreen = (float)(fresnelColor >> 8 & 255) / 255.0f;
		float fresnelBlue = (float)(fresnelColor & 255) / 255.0f;
		float fresnelAlpha = (float)(fresnelColor >> 24 & 255) / 255.0f;
		float radiusTopLeft = radii.length > 0 ? radii[0] : 0.0f;
		float radiusTopRight = radii.length > 1 ? radii[1] : radiusTopLeft;
		float radiusBottomLeft = radii.length > 2 ? radii[2] : radiusTopLeft;
		float radiusBottomRight = radii.length > 3 ? radii[3] : radiusTopLeft;
		int targetWidth = target.width;
		int targetHeight = target.height;
		if (targetWidth <= 0 || targetHeight <= 0) {
			return;
		}
		ByteBuffer uniforms = MemoryUtil.memAlloc(UNIFORM_SIZE);
		uniforms.putFloat(projection.m00()).putFloat(projection.m01()).putFloat(projection.m02()).putFloat(projection.m03());
		uniforms.putFloat(projection.m10()).putFloat(projection.m11()).putFloat(projection.m12()).putFloat(projection.m13());
		uniforms.putFloat(projection.m20()).putFloat(projection.m21()).putFloat(projection.m22()).putFloat(projection.m23());
		uniforms.putFloat(projection.m30()).putFloat(projection.m31()).putFloat(projection.m32()).putFloat(projection.m33());
		// uRect + uSize
		uniforms.position(64);
		uniforms.putFloat(x).putFloat(y).putFloat(width).putFloat(height);
		uniforms.putFloat(targetWidth).putFloat(targetHeight);
		// uRadius
		uniforms.position(96);
		uniforms.putFloat(radiusTopLeft).putFloat(radiusTopRight).putFloat(radiusBottomLeft).putFloat(radiusBottomRight);
		// uSmoothness, uCornerSmoothness, uGlobalAlpha, uFresnelPower
		uniforms.position(112);
		uniforms.putFloat(smoothness);
		uniforms.putFloat(2.0f);
		uniforms.putFloat(globalAlpha);
		uniforms.putFloat(fresnelPower);
		// uFresnelColor
		uniforms.position(128);
		uniforms.putFloat(fresnelRed).putFloat(fresnelGreen).putFloat(fresnelBlue).putFloat(fresnelAlpha);
		// uBaseAlpha, uFresnelInvert, uFresnelMix, uDistortStrength, uZ, _pad
		uniforms.position(144);
		uniforms.putFloat(baseAlpha);
		uniforms.putInt(fresnelInvert ? 1 : 0);
		uniforms.putFloat(fresnelMix);
		uniforms.putFloat(distortStrength);
		uniforms.putFloat(z);
		uniforms.putFloat(0.0f);
		uniforms.flip();
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		encoder.writeToBuffer(uniformBuffer.slice(), uniforms);
		MemoryUtil.memFree(uniforms);
		LiquidGlassPipeline.ensureCopyTexture(targetWidth, targetHeight);
		if (copyTexture == null || copyTextureView == null) {
			return;
		}
		encoder.copyTextureToTexture(target.getColorTexture(), copyTexture, 0, 0, 0, 0, 0, targetWidth, targetHeight);
		GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		try (RenderPass pass = encoder.createRenderPass(() -> "LiquidGlass", target.getColorTextureView(), OptionalInt.empty(), target.getDepthTextureView(), OptionalDouble.of(1.0))) {
			RenderUtil.applyScissor(pass);
			pass.setPipeline(pipeline);
			pass.setUniform("Uniforms", uniformBuffer);
			pass.bindTexture("Sampler0", copyTextureView, sampler);
			pass.draw(0, 6);
		}
	}

	/** Reallocates the scene copy only when the framebuffer size actually changed. */
	public static void ensureCopyTexture(int width, int height) {
		if (copyTexture != null && copyWidth == width && copyHeight == height) {
			return;
		}
		if (copyTextureView != null) {
			copyTextureView.close();
			copyTextureView = null;
		}
		if (copyTexture != null) {
			copyTexture.close();
			copyTexture = null;
		}
		copyTexture = RenderSystem.getDevice().createTexture(() -> "zenya:liquidglass_copy", 5, TextureFormat.RGBA8, width, height, 1, 1);
		copyTextureView = RenderSystem.getDevice().createTextureView(copyTexture);
		copyWidth = width;
		copyHeight = height;
	}

	public static void shutdown() {
		if (uniformBuffer != null) {
			uniformBuffer.close();
			uniformBuffer = null;
		}
		if (copyTextureView != null) {
			copyTextureView.close();
			copyTextureView = null;
		}
		if (copyTexture != null) {
			copyTexture.close();
			copyTexture = null;
		}
		copyWidth = 0;
		copyHeight = 0;
		pipeline = null;
	}
}
