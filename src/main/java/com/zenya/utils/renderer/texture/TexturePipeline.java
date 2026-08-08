package com.zenya.utils.renderer.texture;

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
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.zenya.utils.renderer.RenderUtil;

import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Tinted sprite quad clipped against a signed-distance rounded rectangle, so icons and
 * avatars can share the exact corner radius of the panels they sit on without a mask
 * texture.
 *
 * <p>Everything travels in a single 128-byte std140 uniform block. The absolute buffer
 * positions in {@link #draw} mirror the block layout in {@code texture_fragment.fsh} —
 * change one and the other must follow.
 */
public class TexturePipeline {
	public static RenderPipeline pipeline;
	public static GpuBuffer uniformBuffer;
	public static int UNIFORM_SIZE = 128;

	public static void init() {
		if (pipeline != null) {
			return;
		}

		try {
			pipeline = RenderPipeline.builder()
					.withLocation(Identifier.fromNamespaceAndPath("zenya", "texture"))
					.withVertexShader(Identifier.fromNamespaceAndPath("zenya", "texture_vertex"))
					.withFragmentShader(Identifier.fromNamespaceAndPath("zenya", "texture_fragment"))
					.withVertexFormat(VertexFormat.builder().build(), VertexFormat.Mode.TRIANGLES)
					.withUniform("Uniforms", UniformType.UNIFORM_BUFFER)
					.withSampler("Sampler0")
					.withBlend(BlendFunction.TRANSLUCENT)
					.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
					.withCull(false)
					.build();

			// 136: the usage flags a uniform block written through the command encoder needs.
			uniformBuffer = RenderSystem.getDevice().createBuffer(() -> "Texture2D Uniforms", 136, 128L);
		} catch (Exception failure) {
			// Swallowed on purpose: a missing or broken shader must not take the
			// whole client down, it just leaves textures undrawn.
			System.err.println("[Texture2D] Failed to init: " + failure.getMessage());
			failure.printStackTrace();
		}
	}

	/**
	 * Uploads the uniform block and draws {@code texture} into the square at
	 * ({@code x}, {@code y}) of side {@code size}. {@code color} is an ARGB tint
	 * multiplied over each texel, so opaque white leaves the sprite untouched, and
	 * {@code cornerRadius} is clamped by the shader to half the side.
	 */
	public static void draw(Matrix4f projection, float x, float y, float size, GpuTextureView texture,
			int color, float cornerRadius, float z) {
		if (pipeline == null) {
			init();
		}

		if (pipeline == null || uniformBuffer == null || texture == null) {
			return;
		}

		float red = (color >> 16 & 255) / 255.0f;
		float green = (color >> 8 & 255) / 255.0f;
		float blue = (color & 255) / 255.0f;
		float alpha = (color >> 24 & 255) / 255.0f;

		ByteBuffer uniforms = MemoryUtil.memAlloc(128);

		// uProjection
		uniforms.putFloat(projection.m00()).putFloat(projection.m01()).putFloat(projection.m02()).putFloat(projection.m03());
		uniforms.putFloat(projection.m10()).putFloat(projection.m11()).putFloat(projection.m12()).putFloat(projection.m13());
		uniforms.putFloat(projection.m20()).putFloat(projection.m21()).putFloat(projection.m22()).putFloat(projection.m23());
		uniforms.putFloat(projection.m30()).putFloat(projection.m31()).putFloat(projection.m32()).putFloat(projection.m33());

		uniforms.position(64);
		// uRect, then uColor.
		uniforms.putFloat(x).putFloat(y).putFloat(size).putFloat(size);
		uniforms.putFloat(red).putFloat(green).putFloat(blue).putFloat(alpha);

		// uRadius and uZ, each followed by three floats of padding.
		uniforms.putFloat(cornerRadius).putFloat(0.0f).putFloat(0.0f).putFloat(0.0f);
		uniforms.putFloat(z).putFloat(0.0f).putFloat(0.0f).putFloat(0.0f);

		uniforms.flip();

		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		encoder.writeToBuffer(uniformBuffer.slice(), uniforms);
		MemoryUtil.memFree(uniforms);

		GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		RenderTarget target = Minecraft.getInstance().getMainRenderTarget();

		try (RenderPass pass = encoder.createRenderPass(() -> "Texture2D", target.getColorTextureView(),
				OptionalInt.empty(), target.getDepthTextureView(), OptionalDouble.of(1.0))) {
			RenderUtil.applyScissor(pass);
			pass.setPipeline(pipeline);
			pass.setUniform("Uniforms", uniformBuffer);
			pass.bindTexture("Sampler0", texture, sampler);
			pass.draw(0, 6);
		}
	}

	public static void shutdown() {
		if (uniformBuffer != null) {
			uniformBuffer.close();
			uniformBuffer = null;
		}

		pipeline = null;
	}
}
