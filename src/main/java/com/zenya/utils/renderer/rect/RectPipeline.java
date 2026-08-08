package com.zenya.utils.renderer.rect;

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
import com.mojang.blaze3d.vertex.VertexFormat;
import com.zenya.utils.renderer.RenderUtil;

import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Filled rounded rectangle drawn by a signed-distance shader, so one rect is six
 * vertices regardless of corner radius.
 *
 * <p>Everything travels in a single 256-byte std140 uniform block. The absolute
 * buffer positions in {@link #draw} mirror the block layout in
 * {@code rectangle_fragment.fsh} — change one and the other must follow.
 */
public class RectPipeline {
	public static RenderPipeline pipeline;
	public static GpuBuffer uniformBuffer;
	public static int UNIFORM_SIZE = 256;

	public static void init() {
		if (pipeline != null) {
			return;
		}

		try {
			pipeline = RenderPipeline.builder()
					.withLocation(Identifier.fromNamespaceAndPath("zenya", "rectangle"))
					.withVertexShader(Identifier.fromNamespaceAndPath("zenya", "rectangle_vertex"))
					.withFragmentShader(Identifier.fromNamespaceAndPath("zenya", "rectangle_fragment"))
					.withVertexFormat(VertexFormat.builder().build(), VertexFormat.Mode.TRIANGLES)
					.withUniform("Uniforms", UniformType.UNIFORM_BUFFER)
					.withBlend(BlendFunction.TRANSLUCENT)
					.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
					.withCull(false)
					.build();

			// 136: the usage flags a uniform block written through the command encoder needs.
			uniformBuffer = RenderSystem.getDevice().createBuffer(() -> "Rect2D Uniforms", 136, 256L);
		} catch (Exception failure) {
			// Swallowed on purpose: a missing or broken shader must not take the
			// whole client down, it just leaves rectangles undrawn.
			System.err.println("[Rect2D] Failed to init: " + failure.getMessage());
			failure.printStackTrace();
		}
	}

	/**
	 * Uploads the uniform block and draws the rectangle as two triangles. Corner
	 * radii run clockwise from the top left; {@code colors} is the 3x3 gradient grid
	 * the shader samples (see {@link #normalizeColors}).
	 */
	public static void draw(Matrix4f projection, float x, float y, float width, float height,
			float topLeftRadius, float topRightRadius, float bottomRightRadius, float bottomLeftRadius,
			float z, int... colors) {
		if (pipeline == null) {
			init();
		}

		if (pipeline == null || uniformBuffer == null) {
			return;
		}

		int[] grid = normalizeColors(colors);
		ByteBuffer uniforms = MemoryUtil.memAlloc(256);

		// uProjection
		uniforms.putFloat(projection.m00()).putFloat(projection.m01()).putFloat(projection.m02()).putFloat(projection.m03());
		uniforms.putFloat(projection.m10()).putFloat(projection.m11()).putFloat(projection.m12()).putFloat(projection.m13());
		uniforms.putFloat(projection.m20()).putFloat(projection.m21()).putFloat(projection.m22()).putFloat(projection.m23());
		uniforms.putFloat(projection.m30()).putFloat(projection.m31()).putFloat(projection.m32()).putFloat(projection.m33());

		uniforms.position(64);
		// uRect, then uRadii in the shader's order: top-right, bottom-right, top-left, bottom-left.
		uniforms.putFloat(x).putFloat(y).putFloat(width).putFloat(height);
		uniforms.putFloat(topRightRadius).putFloat(bottomRightRadius).putFloat(topLeftRadius).putFloat(bottomLeftRadius);

		uniforms.position(96);
		// uZ, then three floats of padding.
		uniforms.putFloat(z).putFloat(0.0f).putFloat(0.0f).putFloat(0.0f);

		uniforms.position(112);

		for (int i = 0; i < 9; i++) {
			int argb = grid[i];

			uniforms.putFloat((argb >> 16 & 255) / 255.0f);
			uniforms.putFloat((argb >> 8 & 255) / 255.0f);
			uniforms.putFloat((argb & 255) / 255.0f);
			uniforms.putFloat((argb >> 24 & 255) / 255.0f);
		}

		uniforms.flip();

		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		encoder.writeToBuffer(uniformBuffer.slice(), uniforms);
		MemoryUtil.memFree(uniforms);

		RenderTarget target = Minecraft.getInstance().getMainRenderTarget();

		try (RenderPass pass = encoder.createRenderPass(() -> "Rect2D", target.getColorTextureView(),
				OptionalInt.empty(), target.getDepthTextureView(), OptionalDouble.of(1.0))) {
			RenderUtil.applyScissor(pass);
			pass.setPipeline(pipeline);
			pass.setUniform("Uniforms", uniformBuffer);
			pass.draw(0, 6);
		}
	}

	/**
	 * Pads the caller's colours out to the nine the shader expects: a single colour
	 * becomes a flat fill, a short array repeats its last entry into the rest of the
	 * grid, and nine or more are passed straight through.
	 */
	public static int[] normalizeColors(int[] colors) {
		if (colors.length == 1) {
			int flat = colors[0];

			return new int[] { flat, flat, flat, flat, flat, flat, flat, flat, flat };
		}

		if (colors.length >= 9) {
			return colors;
		}

		// ponytail: an empty array falls through to here and indexes colors[-1].
		int[] padded = new int[9];

		for (int i = 0; i < 9; i++) {
			padded[i] = i < colors.length ? colors[i] : colors[colors.length - 1];
		}

		return padded;
	}

	public static void shutdown() {
		if (uniformBuffer != null) {
			uniformBuffer.close();
			uniformBuffer = null;
		}

		pipeline = null;
	}
}
