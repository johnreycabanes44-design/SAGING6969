package com.zenya.utils.renderer.arc;

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
 * Ring segment with a border: the same signed-distance arc as {@link ArcPipeline},
 * but the shader splits the band into a fill core and an outline of
 * {@code outlineThickness} pixels, so both edges stay a constant width at any sweep.
 *
 * <p>Everything travels in a single 160-byte std140 uniform block. The absolute
 * buffer positions in {@link #draw} mirror the block layout in
 * {@code arc_outline_fragment.fsh} — change one and the other must follow.
 */
public class ArcOutlinePipeline {
	public static RenderPipeline pipeline;
	public static GpuBuffer uniformBuffer;
	public static int UNIFORM_SIZE = 160;

	public static void init() {
		if (pipeline != null) {
			return;
		}

		try {
			pipeline = RenderPipeline.builder()
					.withLocation(Identifier.fromNamespaceAndPath("zenya", "arc_outline"))
					.withVertexShader(Identifier.fromNamespaceAndPath("zenya", "arc_outline_vertex"))
					.withFragmentShader(Identifier.fromNamespaceAndPath("zenya", "arc_outline_fragment"))
					.withVertexFormat(VertexFormat.builder().build(), VertexFormat.Mode.TRIANGLES)
					.withUniform("Uniforms", UniformType.UNIFORM_BUFFER)
					.withBlend(BlendFunction.TRANSLUCENT)
					.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
					.withCull(false)
					.build();

			// 136: the usage flags a uniform block written through the command encoder needs.
			uniformBuffer = RenderSystem.getDevice().createBuffer(() -> "ArcOutline2D Uniforms", 136, 160L);
		} catch (Exception failure) {
			// Swallowed on purpose: a missing or broken shader must not take the
			// whole client down, it just leaves outlined arcs undrawn. Unlike the other
			// pipelines this one does not dump a stack trace.
			System.err.println("[ArcOutline2D] Failed to init: " + failure.getMessage());
		}
	}

	/**
	 * Uploads the uniform block and draws the outlined arc as two triangles. The arc is
	 * inscribed in the square at ({@code x}, {@code y}) of side {@code size};
	 * {@code thickness} eats inwards from that side, {@code degrees} is the sweep and
	 * {@code rotation} turns it clockwise. {@code outlineThickness} is the border band
	 * taken out of {@code thickness}, painted in {@code outlineColor} over the fill.
	 */
	public static void draw(Matrix4f projection, float x, float y, float size, float thickness,
			float degrees, float rotation, float outlineThickness, int fillColor, int outlineColor, float z) {
		if (pipeline == null) {
			init();
		}

		if (pipeline == null || uniformBuffer == null) {
			return;
		}

		float fillRed = (fillColor >> 16 & 255) / 255.0f;
		float fillGreen = (fillColor >> 8 & 255) / 255.0f;
		float fillBlue = (fillColor & 255) / 255.0f;
		float fillAlpha = (fillColor >> 24 & 255) / 255.0f;
		float outlineRed = (outlineColor >> 16 & 255) / 255.0f;
		float outlineGreen = (outlineColor >> 8 & 255) / 255.0f;
		float outlineBlue = (outlineColor & 255) / 255.0f;
		float outlineAlpha = (outlineColor >> 24 & 255) / 255.0f;

		ByteBuffer uniforms = MemoryUtil.memAlloc(160);

		// uProjection
		uniforms.putFloat(projection.m00()).putFloat(projection.m01()).putFloat(projection.m02()).putFloat(projection.m03());
		uniforms.putFloat(projection.m10()).putFloat(projection.m11()).putFloat(projection.m12()).putFloat(projection.m13());
		uniforms.putFloat(projection.m20()).putFloat(projection.m21()).putFloat(projection.m22()).putFloat(projection.m23());
		uniforms.putFloat(projection.m30()).putFloat(projection.m31()).putFloat(projection.m32()).putFloat(projection.m33());

		uniforms.position(64);
		// uRect, then uParams: outer diameter, thickness, sweep, rotation.
		uniforms.putFloat(x).putFloat(y).putFloat(size).putFloat(size);
		uniforms.putFloat(size).putFloat(thickness).putFloat(degrees).putFloat(rotation);

		// uParams2: the vertex shader reads z from .x, the fragment shader the border
		// width from .y, then two floats of padding.
		uniforms.putFloat(z).putFloat(outlineThickness).putFloat(0.0f).putFloat(0.0f);

		// uFillColor, then uOutlineColor.
		uniforms.putFloat(fillRed).putFloat(fillGreen).putFloat(fillBlue).putFloat(fillAlpha);
		uniforms.putFloat(outlineRed).putFloat(outlineGreen).putFloat(outlineBlue).putFloat(outlineAlpha);

		uniforms.flip();

		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		encoder.writeToBuffer(uniformBuffer.slice(), uniforms);
		MemoryUtil.memFree(uniforms);

		RenderTarget target = Minecraft.getInstance().getMainRenderTarget();

		try (RenderPass pass = encoder.createRenderPass(() -> "ArcOutline2D", target.getColorTextureView(),
				OptionalInt.empty(), target.getDepthTextureView(), OptionalDouble.of(1.0))) {
			RenderUtil.applyScissor(pass);
			pass.setPipeline(pipeline);
			pass.setUniform("Uniforms", uniformBuffer);
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
