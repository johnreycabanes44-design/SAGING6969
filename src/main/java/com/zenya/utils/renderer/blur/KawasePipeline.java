package com.zenya.utils.renderer.blur;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
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
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.zenya.utils.renderer.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Dual-filter Kawase blur of the main framebuffer, used as the backdrop for GUI panels.
 *
 * <p>The whole chain (copy -> downsample -> ping-pong passes) is rebuilt only when the
 * frame index or the requested strength changes, so drawing several blurred panels in one
 * frame costs one composite pass each instead of a full blur each. {@code cachedBlurSrc}
 * therefore has to keep pointing at whichever ping-pong slot the last pass wrote to.
 *
 * <p>{@code dataBuffer} is off-heap: {@link #shutdown()} must run or it leaks.
 */
public class KawasePipeline {
	public static int BLUR_ITERATIONS = 5;
	public static int DOWNSAMPLE_SCALE = 2;
	public static int BUFFER_SIZE = 256;

	public static RenderPipeline PIPELINE_BLUR = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath("zenya", "pipeline/blur_pass"))
			.withVertexShader(Identifier.fromNamespaceAndPath("zenya", "blur_pass_vertex"))
			.withFragmentShader(Identifier.fromNamespaceAndPath("zenya", "blur_pass_fragment"))
			.withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
			.withUniform("BlurData", UniformType.UNIFORM_BUFFER)
			.withSampler("Sampler0")
			.withBlend(BlendFunction.TRANSLUCENT)
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.withDepthWrite(false)
			.withCull(false)
			.build());

	public static RenderPipeline PIPELINE_FINAL = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath("zenya", "pipeline/blur_final"))
			.withVertexShader(Identifier.fromNamespaceAndPath("zenya", "blur_final_vertex"))
			.withFragmentShader(Identifier.fromNamespaceAndPath("zenya", "blur_final_fragment"))
			.withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
			.withUniform("BlurData", UniformType.UNIFORM_BUFFER)
			.withSampler("Sampler0")
			.withBlend(BlendFunction.TRANSLUCENT)
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.withDepthWrite(false)
			.withCull(false)
			.build());

	public static Vector4f COLOR_MODULATOR = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);
	public static Vector3f MODEL_OFFSET = new Vector3f(0.0f, 0.0f, 0.0f);
	public static Matrix4f TEXTURE_MATRIX = new Matrix4f();

	public static GpuBuffer uniformBuffer;
	public static GpuBuffer dummyVertexBuffer;
	public static ByteBuffer dataBuffer;
	public static GpuTexture copyTexture;
	public static GpuTextureView copyTextureView;
	public static GpuTexture[] pingPongTextures = new GpuTexture[2];
	public static GpuTextureView[] pingPongViews = new GpuTextureView[2];
	public static int lastWidth;
	public static int lastHeight;
	public static boolean initialized;
	public static long lastFrameTime = -1L;
	public static int cachedBlurSrc;
	public static float cachedStrength;

	public static void init() {
		if (initialized) {
			return;
		}
		dataBuffer = MemoryUtil.memAlloc(256);

		// The shaders build their own fullscreen triangle from gl_VertexID, so the vertex
		// buffer only has to exist; its contents are never read.
		ByteBuffer dummyVertexData = MemoryUtil.memAlloc(4);
		dummyVertexData.putInt(0);
		dummyVertexData.flip();
		dummyVertexBuffer = RenderSystem.getDevice().createBuffer(() -> "zenya:blur_dummy_vertex", 32, dummyVertexData);
		MemoryUtil.memFree(dummyVertexData);
		initialized = true;
	}

	/** Snapshots the framebuffer and invalidates the cache so the next draw re-blurs it. */
	public static void captureFramebuffer() {
		Minecraft mc = Minecraft.getInstance();
		int width = mc.getMainRenderTarget().width;
		int height = mc.getMainRenderTarget().height;
		KawasePipeline.ensureTextures(width, height);
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		encoder.copyTextureToTexture(mc.getMainRenderTarget().getColorTexture(), copyTexture, 0, 0, 0, 0, 0, width, height);
		lastFrameTime = -1L;
	}

	/** Reallocates the copy target and the two half-res ping-pong targets on resize. */
	public static void ensureTextures(int width, int height) {
		int halfWidth = width / 2;
		int halfHeight = height / 2;
		if (copyTexture != null && width == lastWidth && height == lastHeight) {
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
		copyTexture = RenderSystem.getDevice().createTexture(() -> "zenya:blur_copy", 5, TextureFormat.RGBA8, width, height, 1, 1);
		copyTextureView = RenderSystem.getDevice().createTextureView(copyTexture);
		for (int i = 0; i < 2; ++i) {
			if (pingPongViews[i] != null) {
				pingPongViews[i].close();
				pingPongViews[i] = null;
			}
			if (pingPongTextures[i] != null) {
				pingPongTextures[i].close();
				pingPongTextures[i] = null;
			}
			int slot = i;
			pingPongTextures[i] = RenderSystem.getDevice().createTexture(() -> "zenya:blur_pp_" + slot, 13, TextureFormat.RGBA8, halfWidth, halfHeight, 1, 1);
			pingPongViews[i] = RenderSystem.getDevice().createTextureView(pingPongTextures[i]);
		}
		lastWidth = width;
		lastHeight = height;
		lastFrameTime = -1L;
	}

	/**
	 * Composites the blurred backdrop into the given rect. Re-runs the blur chain only if
	 * the frame or the strength moved since last time.
	 */
	public static void draw(Matrix4f matrix, float x, float y, float width, float height, float radius, float strength, float opacity) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getMainRenderTarget() == null) {
			return;
		}
		if (mc.getMainRenderTarget().getColorTexture() == null) {
			return;
		}
		KawasePipeline.init();
		int screenWidth = mc.getMainRenderTarget().width;
		int screenHeight = mc.getMainRenderTarget().height;
		int halfWidth = screenWidth / 2;
		int halfHeight = screenHeight / 2;
		KawasePipeline.ensureTextures(screenWidth, screenHeight);

		// 16666666ns == one 60Hz frame; the blur is refreshed at most once per such tick.
		long frame = System.nanoTime() / 16666666L;
		boolean needsRebuild = frame != lastFrameTime || Math.abs(strength - cachedStrength) > 0.01f;
		GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		if (needsRebuild) {
			encoder.copyTextureToTexture(mc.getMainRenderTarget().getColorTexture(), copyTexture, 0, 0, 0, 0, 0, screenWidth, screenHeight);
			KawasePipeline.prepareBlurData(screenWidth, screenHeight, halfWidth, halfHeight, 1.0f, strength);
			encoder.writeToBuffer(uniformBuffer.slice(), dataBuffer);
			try (RenderPass downsamplePass = encoder.createRenderPass(() -> "zenya:blur_downsample", pingPongViews[0], OptionalInt.empty(), null, OptionalDouble.empty())) {
				downsamplePass.setPipeline(PIPELINE_BLUR);
				downsamplePass.setVertexBuffer(0, dummyVertexBuffer);
				downsamplePass.bindTexture("Sampler0", copyTextureView, sampler);
				RenderSystem.bindDefaultUniforms(downsamplePass);
				downsamplePass.setUniform("DynamicTransforms", transforms);
				downsamplePass.setUniform("BlurData", uniformBuffer);
				downsamplePass.draw(0, 6);
			}

			int passes = Math.max(2, (int) (5.0f * strength));
			// Widening kernel offsets; passes beyond the table all sample at 3 texels.
			float[] offsets = { 1.0f, 2.0f, 2.0f, 3.0f };
			for (int pass = 0; pass < passes; ++pass) {
				int src = pass % 2;
				int dst = (pass + 1) % 2;
				float offset = pass < offsets.length ? offsets[pass] : 3.0f;
				int passIndex = pass;
				KawasePipeline.prepareBlurData(halfWidth, halfHeight, halfWidth, halfHeight, offset, 1.0f);
				encoder.writeToBuffer(uniformBuffer.slice(), dataBuffer);
				try (RenderPass blurPass = encoder.createRenderPass(() -> "zenya:blur_" + passIndex, pingPongViews[dst], OptionalInt.empty(), null, OptionalDouble.empty())) {
					blurPass.setPipeline(PIPELINE_BLUR);
					blurPass.setVertexBuffer(0, dummyVertexBuffer);
					blurPass.bindTexture("Sampler0", pingPongViews[src], sampler);
					RenderSystem.bindDefaultUniforms(blurPass);
					blurPass.setUniform("DynamicTransforms", transforms);
					blurPass.setUniform("BlurData", uniformBuffer);
					blurPass.draw(0, 6);
				}
			}
			cachedBlurSrc = passes % 2;
			lastFrameTime = frame;
			cachedStrength = strength;
		}

		float[] radii = { radius, radius, radius, radius };
		int scaledWidth = RenderUtil.getFixedScaledWidth();
		int scaledHeight = RenderUtil.getFixedScaledHeight();
		KawasePipeline.prepareFinalData(matrix, x, y, width, height, scaledWidth, scaledHeight, radii, opacity);
		encoder.writeToBuffer(uniformBuffer.slice(), dataBuffer);
		try (RenderPass finalPass = encoder.createRenderPass(() -> "zenya:blur_final", mc.getMainRenderTarget().getColorTextureView(), OptionalInt.empty(), mc.getMainRenderTarget().getDepthTextureView(), OptionalDouble.of(1.0))) {
			RenderUtil.applyScissor(finalPass);
			finalPass.setPipeline(PIPELINE_FINAL);
			finalPass.setVertexBuffer(0, dummyVertexBuffer);
			finalPass.bindTexture("Sampler0", pingPongViews[cachedBlurSrc], sampler);
			RenderSystem.bindDefaultUniforms(finalPass);
			finalPass.setUniform("DynamicTransforms", transforms);
			finalPass.setUniform("BlurData", uniformBuffer);
			finalPass.draw(0, 6);
		}
	}

	/** Fills the BlurData UBO for a blur pass: the leading 16 floats are the unused matrix slot. */
	public static void prepareBlurData(int srcWidth, int srcHeight, int dstWidth, int dstHeight, float offset, float strength) {
		dataBuffer.clear();
		for (int i = 0; i < 16; ++i) {
			dataBuffer.putFloat(0.0f);
		}
		dataBuffer.putFloat(0.0f).putFloat(0.0f).putFloat(srcWidth).putFloat(srcHeight);
		dataBuffer.putFloat(srcWidth).putFloat(srcHeight).putFloat(1.0f).putFloat(offset);
		dataBuffer.putFloat(dstWidth).putFloat(dstHeight).putFloat(1.0f).putFloat(strength);
		dataBuffer.putFloat(0.0f).putFloat(0.0f).putFloat(0.0f).putFloat(0.0f);
		dataBuffer.putFloat(0.0f).putFloat(0.0f).putFloat(0.0f).putFloat(0.0f);
		dataBuffer.flip();
		KawasePipeline.ensureBuffer();
	}

	/** Same UBO layout as {@link #prepareBlurData}, but the matrix slot carries the pose. */
	public static void prepareFinalData(Matrix4f matrix, float x, float y, float width, float height, int scaledWidth, int scaledHeight, float[] radii, float opacity) {
		dataBuffer.clear();
		dataBuffer.putFloat(matrix.m00()).putFloat(matrix.m01()).putFloat(matrix.m02()).putFloat(matrix.m03());
		dataBuffer.putFloat(matrix.m10()).putFloat(matrix.m11()).putFloat(matrix.m12()).putFloat(matrix.m13());
		dataBuffer.putFloat(matrix.m20()).putFloat(matrix.m21()).putFloat(matrix.m22()).putFloat(matrix.m23());
		dataBuffer.putFloat(matrix.m30()).putFloat(matrix.m31()).putFloat(matrix.m32()).putFloat(matrix.m33());
		dataBuffer.putFloat(x).putFloat(y).putFloat(width).putFloat(height);
		dataBuffer.putFloat(scaledWidth).putFloat(scaledHeight).putFloat(0.0f).putFloat(0.0f);
		dataBuffer.putFloat(scaledWidth).putFloat(scaledHeight).putFloat(1.0f).putFloat(0.0f);
		dataBuffer.putFloat(radii[0]).putFloat(radii[1]).putFloat(radii[2]).putFloat(radii[3]);
		dataBuffer.putFloat(opacity).putFloat(0.0f).putFloat(0.0f).putFloat(0.0f);
		dataBuffer.flip();
		KawasePipeline.ensureBuffer();
	}

	/** Grows the uniform buffer to fit whatever was just staged in {@code dataBuffer}. */
	public static void ensureBuffer() {
		int size = dataBuffer.remaining();
		if (uniformBuffer == null || uniformBuffer.size() < size) {
			if (uniformBuffer != null) {
				uniformBuffer.close();
			}
			uniformBuffer = RenderSystem.getDevice().createBuffer(() -> "zenya:blur_uniform", 136, (long) size);
		}
	}

	public static GpuTextureView getBlurTextureView() {
		if (!initialized || pingPongViews[cachedBlurSrc] == null) {
			return null;
		}
		return pingPongViews[cachedBlurSrc];
	}

	public static void shutdown() {
		if (uniformBuffer != null) {
			uniformBuffer.close();
			uniformBuffer = null;
		}
		if (dummyVertexBuffer != null) {
			dummyVertexBuffer.close();
			dummyVertexBuffer = null;
		}
		if (dataBuffer != null) {
			MemoryUtil.memFree(dataBuffer);
			dataBuffer = null;
		}
		if (copyTextureView != null) {
			copyTextureView.close();
			copyTextureView = null;
		}
		if (copyTexture != null) {
			copyTexture.close();
			copyTexture = null;
		}
		for (int i = 0; i < 2; ++i) {
			if (pingPongViews[i] != null) {
				pingPongViews[i].close();
				pingPongViews[i] = null;
			}
			if (pingPongTextures[i] == null) {
				continue;
			}
			pingPongTextures[i].close();
			pingPongTextures[i] = null;
		}
		lastWidth = 0;
		lastHeight = 0;
		initialized = false;
		lastFrameTime = -1L;
	}
}
