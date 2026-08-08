package com.zenya.utils;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.Tuple;
import net.minecraft.world.phys.Vec3;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zenya.mixin.GameRendererAccessor;
import com.zenya.utils.renderer.ProjectionUtil;

import java.awt.Color;
import java.lang.reflect.Method;

import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/**
 * In-world drawing helpers for the ESP modules.
 *
 * <p>Everything draws through the two no-depth pipelines registered below, so geometry
 * shows through terrain. Fog is force-disabled at flush time ({@link #drawWithoutFog})
 * because world geometry drawn here would otherwise fade out with distance.
 *
 * <p>{@link #REUSABLE_BATCH} is thread-local: the buffer inside it is not safe to share,
 * and the world render event can fire off more than one thread.
 */
public class RenderUtils {
	/** Golden ratio, the icosahedron vertex coordinate. */
	private static final double PHI = 1.618033988749895;
	/** 1 / |(1, PHI)| — scales the icosahedron onto the unit sphere. */
	private static final double ICOSPHERE_SCALE = 0.5257311121191336;
	private static final int BATCH_BUFFER_BYTES = 524288;

	public static GpuBufferSlice lastFogBuffer;
	public static Matrix4f POSITION_PROJECTION_MATRIX;
	public static FrustumIntersection FRUSTUM;
	public static boolean frustumReady;
	public static double frustumX;
	public static double frustumY;
	public static double frustumZ;
	public static RenderPipeline NO_DEPTH_FILLED_BOX_PIPELINE;
	public static RenderPipeline NO_DEPTH_LINES_PIPELINE;
	public static float[][] ICOSPHERE_VERTS;
	public static int[][] ICOSPHERE_FACES;
	public static RenderType NO_DEPTH_FILLED_BOX;
	public static RenderType NO_DEPTH_LINES;
	public static ThreadLocal<ReusableWorldBatch> REUSABLE_BATCH;
	public static Method cameraPosMethod;

	static {
		POSITION_PROJECTION_MATRIX = new Matrix4f();
		FRUSTUM = new FrustumIntersection();

		NO_DEPTH_FILLED_BOX_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
				.withLocation("pipeline/zenya_no_depth_filled_box")
				.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
				.withDepthWrite(false)
				.withCull(false)
				.build());
		NO_DEPTH_LINES_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
				.withLocation("pipeline/zenya_no_depth_lines")
				.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
				.withDepthWrite(false)
				.build());

		ICOSPHERE_FACES = new int[][] {
				{ 0, 8, 4 }, { 0, 2, 8 }, { 0, 10, 2 }, { 0, 6, 10 }, { 0, 4, 6 },
				{ 3, 5, 9 }, { 3, 7, 5 }, { 3, 11, 7 }, { 3, 1, 11 }, { 3, 9, 1 },
				{ 4, 1, 6 }, { 4, 9, 1 }, { 4, 8, 9 }, { 8, 5, 9 }, { 8, 2, 5 },
				{ 2, 7, 5 }, { 2, 10, 7 }, { 10, 11, 7 }, { 10, 6, 11 }, { 6, 1, 11 }
		};

		double[][] icosahedron = new double[][] {
				{ 0.0, 1.0, PHI }, { 0.0, 1.0, -PHI }, { 0.0, -1.0, PHI }, { 0.0, -1.0, -PHI },
				{ 1.0, PHI, 0.0 }, { 1.0, -PHI, 0.0 }, { -1.0, PHI, 0.0 }, { -1.0, -PHI, 0.0 },
				{ PHI, 0.0, 1.0 }, { PHI, 0.0, -1.0 }, { -PHI, 0.0, 1.0 }, { -PHI, 0.0, -1.0 }
		};
		ICOSPHERE_VERTS = new float[icosahedron.length][3];
		for (int vertex = 0; vertex < icosahedron.length; ++vertex) {
			ICOSPHERE_VERTS[vertex][0] = (float)(icosahedron[vertex][0] * ICOSPHERE_SCALE);
			ICOSPHERE_VERTS[vertex][1] = (float)(icosahedron[vertex][1] * ICOSPHERE_SCALE);
			ICOSPHERE_VERTS[vertex][2] = (float)(icosahedron[vertex][2] * ICOSPHERE_SCALE);
		}

		NO_DEPTH_FILLED_BOX = RenderType.create("zenya_no_depth_filled_box",
				RenderSetup.builder(NO_DEPTH_FILLED_BOX_PIPELINE).sortOnUpload().createRenderSetup());
		NO_DEPTH_LINES = RenderType.create("zenya_no_depth_lines",
				RenderSetup.builder(NO_DEPTH_LINES_PIPELINE).createRenderSetup());
		REUSABLE_BATCH = ThreadLocal.withInitial(ReusableWorldBatch::new);
	}

	public static RenderType noDepthFilledBoxLayer() {
		return NO_DEPTH_FILLED_BOX;
	}

	/**
	 * Ends the batch with the fog uniform swapped for the renderer's "no fog" buffer,
	 * then puts the previous buffer back. Without this, boxes far from the camera get
	 * washed out by world fog.
	 */
	public static void drawWithoutFog(MultiBufferSource.BufferSource bufferSource) {
		GpuBufferSlice previousFog = RenderSystem.getShaderFog();
		try {
			RenderSystem.setShaderFog(null);
			try {
				GameRenderer gameRenderer = Minecraft.getInstance().gameRenderer;
				if (gameRenderer instanceof GameRendererAccessor accessor) {
					FogRenderer fogRenderer = accessor.zenya$getFogRenderer();
					if (fogRenderer != null) {
						GpuBufferSlice noFog = fogRenderer.getBuffer(FogRenderer.FogMode.NONE);
						if (noFog != null) {
							RenderSystem.setShaderFog(noFog);
						}
					}
				}
			} catch (Throwable ignored) {
				// Best effort only: if the fog renderer is missing or mixed into by
				// something else we still draw, just with the null fog set above.
			}
			bufferSource.endBatch();
		} finally {
			if (previousFog != null) {
				RenderSystem.setShaderFog(previousFog);
			}
		}
	}

	public static WorldBatch beginWorldBatch(PoseStack matrices) {
		return new WorldBatch(matrices);
	}

	/** Caches the view-projection matrix and camera position used by {@link #isWorldBoxVisible}. */
	public static void updateFrustum(Matrix4f modelViewMatrix, Matrix4f projectionMatrix, Vec3 cameraPos) {
		if (modelViewMatrix == null || projectionMatrix == null || cameraPos == null) {
			frustumReady = false;
			return;
		}
		projectionMatrix.mul(modelViewMatrix, POSITION_PROJECTION_MATRIX);
		FRUSTUM.set(POSITION_PROJECTION_MATRIX);
		frustumX = cameraPos.x;
		frustumY = cameraPos.y;
		frustumZ = cameraPos.z;
		frustumReady = true;
	}

	/** World-space box test. Returns true when the frustum has not been updated yet, so nothing is culled by mistake. */
	public static boolean isWorldBoxVisible(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		if (!frustumReady) {
			return true;
		}
		float relMinX = (float)(minX - frustumX);
		float relMinY = (float)(minY - frustumY);
		float relMinZ = (float)(minZ - frustumZ);
		float relMaxX = (float)(maxX - frustumX);
		float relMaxY = (float)(maxY - frustumY);
		float relMaxZ = (float)(maxZ - frustumZ);
		int result = FRUSTUM.intersectAab(relMinX, relMinY, relMinZ, relMaxX, relMaxY, relMaxZ);
		// -1 = INTERSECT, -2 = INSIDE.
		return result == -1 || result == -2;
	}

	public static Camera getCamera() {
		return Minecraft.getInstance().gameRenderer.getMainCamera();
	}

	/**
	 * Camera position by reflection: the accessor is remapped differently across
	 * loader/mapping combinations, so the first no-arg method returning a Vec3 is used.
	 */
	public static Vec3 getCameraPos(Camera camera) {
		if (cameraPosMethod == null) {
			Method[] methods = Camera.class.getMethods();
			for (int i = 0; i < methods.length; ++i) {
				Method method = methods[i];
				if (method.getReturnType() != Vec3.class || method.getParameterCount() != 0) continue;
				cameraPosMethod = method;
				break;
			}
		}
		try {
			return (Vec3)cameraPosMethod.invoke(camera, new Object[0]);
		} catch (Exception failed) {
			// No usable accessor (or it threw): the player's eye is close enough to keep rendering.
			return Minecraft.getInstance().player.getEyePosition(1.0f);
		}
	}

	public static void renderFilledBox(PoseStack matrices, double minX, double minY, double minZ, double maxX, double maxY, double maxZ, Color color) {
		ReusableWorldBatch batch = REUSABLE_BATCH.get();
		batch.begin(matrices);
		batch.renderFilledBox(minX, minY, minZ, maxX, maxY, maxZ, color);
		batch.flush();
	}

	public static void renderOutlineBox(PoseStack matrices, double minX, double minY, double minZ, double maxX, double maxY, double maxZ, Color color) {
		ReusableWorldBatch batch = REUSABLE_BATCH.get();
		batch.begin(matrices);
		batch.renderOutlineBox(minX, minY, minZ, maxX, maxY, maxZ, color);
		batch.flush();
	}

	public static void renderLine(PoseStack matrices, Color color, Vec3 from, Vec3 to) {
		RenderUtils.renderLine(matrices, color, from, to, 2.0f);
	}

	public static void renderLine(PoseStack matrices, Color color, Vec3 from, Vec3 to, float width) {
		ReusableWorldBatch batch = REUSABLE_BATCH.get();
		batch.begin(matrices);
		batch.renderLine(color, from, to, width);
		batch.flush();
	}

	public static Vec3 getCameraForward(Camera camera) {
		return new Vec3(0.0, 0.0, 1.0).xRot(-((float)Math.toRadians(camera.xRot()))).yRot(-((float)Math.toRadians(camera.yRot()))).normalize();
	}

	public static Vec3 getCameraRight(Camera camera) {
		return new Vec3(1.0, 0.0, 0.0).yRot(-((float)Math.toRadians(camera.yRot()))).normalize();
	}

	public static Vec3 getCameraUp(Vec3 forward, Vec3 right) {
		return forward.cross(right).normalize();
	}

	/**
	 * Tracer end for a target behind or beside the camera: the direction is expressed in
	 * camera axes and pushed out to at least {@code minSpread} so tracers to off-screen
	 * targets do not all collapse onto the crosshair.
	 */
	public static Vec3 getSpreadTracerEnd(double dirX, double dirY, double dirZ, Vec3 forward, Vec3 right, Vec3 up, double length, double minSpread) {
		double rightAmount = dirX * right.x + dirY * right.y + dirZ * right.z;
		double upAmount = dirX * up.x + dirY * up.y + dirZ * up.z;
		double forwardAmount = dirX * forward.x + dirY * forward.y + dirZ * forward.z;
		double depth = Math.max(Math.abs(forwardAmount), 0.25);
		double offsetX = rightAmount / depth;
		double offsetY = upAmount / depth;
		double spread;
		if (forwardAmount <= 0.0 && (spread = Math.hypot(offsetX, offsetY)) < minSpread) {
			if (spread < 1.0E-4) {
				// Straight behind the camera: no direction to push along, pick right.
				offsetX = minSpread;
				offsetY = 0.0;
			} else {
				double scale = minSpread / spread;
				offsetX *= scale;
				offsetY *= scale;
			}
		}
		return forward.add(right.scale(offsetX)).add(up.scale(offsetY)).normalize().scale(length);
	}

	public static Vec3 getSpreadTracerEnd(Vec3 direction, Vec3 forward, Vec3 right, Vec3 up, double length, double minSpread) {
		return RenderUtils.getSpreadTracerEnd(direction.x, direction.y, direction.z, forward, right, up, length, minSpread);
	}

	/**
	 * Tracer end clamped to the screen edge. Targets already on screen keep
	 * {@code defaultEnd}; anything else is re-aimed at the clamped screen point.
	 */
	public static Vec3 getClampedTracerEnd(Vec3 defaultEnd, Vec3 targetPos, Vec3 forward, Vec3 right, Vec3 up, double length) {
		Minecraft mc = Minecraft.getInstance();
		int screenWidth = mc.getWindow().getGuiScaledWidth();
		int screenHeight = mc.getWindow().getGuiScaledHeight();
		Tuple<?, ?> projected = ProjectionUtil.project(ProjectionUtil.modelViewMatrix, ProjectionUtil.projectionMatrix, targetPos);
		if (projected != null && ((Boolean)projected.getB()).booleanValue()) {
			Vec3 screenPos = (Vec3)projected.getA();
			if (screenPos.x >= 0.0 && screenPos.x <= (double)screenWidth && screenPos.y >= 0.0 && screenPos.y <= (double)screenHeight) {
				return defaultEnd;
			}
		}
		Vector3f clamped = ProjectionUtil.projectWithClamp(ProjectionUtil.modelViewMatrix, ProjectionUtil.projectionMatrix, targetPos);
		if (clamped == null) {
			return defaultEnd;
		}
		return RenderUtils.getRayToScreenPoint(clamped.x, clamped.y, screenWidth, screenHeight, forward, right, up).scale(length);
	}

	/** Unprojects a screen point back into a world-space direction using the camera axes. */
	public static Vec3 getRayToScreenPoint(float screenX, float screenY, int screenWidth, int screenHeight, Vec3 forward, Vec3 right, Vec3 up) {
		double ndcX = (double)(screenX / (float)screenWidth) * 2.0 - 1.0;
		double ndcY = 1.0 - (double)(screenY / (float)screenHeight) * 2.0;
		double aspect = ProjectionUtil.projectionMatrix.m11() / ProjectionUtil.projectionMatrix.m00();
		double tanHalfFov = 1.0 / (double)ProjectionUtil.projectionMatrix.m11();
		return forward.add(right.scale(ndcX * aspect * tanHalfFov)).add(up.scale(ndcY * tanHalfFov)).normalize();
	}

	/**
	 * Per-thread batch that keeps its buffer between frames. {@link #begin} rebinds it to
	 * the current pose stack instead of allocating, which the one-shot static helpers rely on.
	 */
	public static class ReusableWorldBatch {
		public ByteBufferBuilder allocator = new ByteBufferBuilder(BATCH_BUFFER_BYTES);
		public MultiBufferSource.BufferSource immediate = MultiBufferSource.immediate(this.allocator);
		public PoseStack matrices;
		public boolean dirty;

		public void begin(PoseStack matrices) {
			this.matrices = matrices;
			this.dirty = false;
		}

		public void renderBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, Color color) {
			this.renderFilledBox(minX, minY, minZ, maxX, maxY, maxZ, color);
		}

		public void renderFilledBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, Color color) {
			VertexConsumer buffer = this.immediate.getBuffer(RenderUtils.NO_DEPTH_FILLED_BOX);
			int argb = color.getAlpha() << 24 | color.getRed() << 16 | color.getGreen() << 8 | color.getBlue();
			PoseStack.Pose pose = this.matrices.last();
			float x0 = (float)minX;
			float y0 = (float)minY;
			float z0 = (float)minZ;
			float x1 = (float)maxX;
			float y1 = (float)maxY;
			float z1 = (float)maxZ;
			// Bottom.
			buffer.addVertex(pose, x0, y0, z0).setColor(argb);
			buffer.addVertex(pose, x1, y0, z0).setColor(argb);
			buffer.addVertex(pose, x1, y0, z1).setColor(argb);
			buffer.addVertex(pose, x0, y0, z1).setColor(argb);
			// Top.
			buffer.addVertex(pose, x0, y1, z0).setColor(argb);
			buffer.addVertex(pose, x0, y1, z1).setColor(argb);
			buffer.addVertex(pose, x1, y1, z1).setColor(argb);
			buffer.addVertex(pose, x1, y1, z0).setColor(argb);
			// North.
			buffer.addVertex(pose, x0, y0, z0).setColor(argb);
			buffer.addVertex(pose, x0, y1, z0).setColor(argb);
			buffer.addVertex(pose, x1, y1, z0).setColor(argb);
			buffer.addVertex(pose, x1, y0, z0).setColor(argb);
			// South.
			buffer.addVertex(pose, x1, y0, z1).setColor(argb);
			buffer.addVertex(pose, x1, y1, z1).setColor(argb);
			buffer.addVertex(pose, x0, y1, z1).setColor(argb);
			buffer.addVertex(pose, x0, y0, z1).setColor(argb);
			// West.
			buffer.addVertex(pose, x0, y0, z1).setColor(argb);
			buffer.addVertex(pose, x0, y1, z1).setColor(argb);
			buffer.addVertex(pose, x0, y1, z0).setColor(argb);
			buffer.addVertex(pose, x0, y0, z0).setColor(argb);
			// East.
			buffer.addVertex(pose, x1, y0, z0).setColor(argb);
			buffer.addVertex(pose, x1, y1, z0).setColor(argb);
			buffer.addVertex(pose, x1, y1, z1).setColor(argb);
			buffer.addVertex(pose, x1, y0, z1).setColor(argb);
			this.dirty = true;
		}

		/** Box drawn as twelve thin filled edges, so it keeps its thickness at any distance. */
		public void renderOutlineBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, Color color) {
			double sizeX = maxX - minX;
			double sizeY = maxY - minY;
			double sizeZ = maxZ - minZ;
			double thickness = Math.min(0.02, Math.min(sizeX / 8.0, Math.min(sizeY / 8.0, sizeZ / 8.0)));
			if (thickness <= 0.0) {
				return;
			}
			this.renderFilledBox(minX, minY, minZ, maxX, minY + thickness, minZ + thickness, color);
			this.renderFilledBox(minX, minY, maxZ - thickness, maxX, minY + thickness, maxZ, color);
			this.renderFilledBox(minX, minY, minZ + thickness, minX + thickness, minY + thickness, maxZ - thickness, color);
			this.renderFilledBox(maxX - thickness, minY, minZ + thickness, maxX, minY + thickness, maxZ - thickness, color);
			this.renderFilledBox(minX, maxY - thickness, minZ, maxX, maxY, minZ + thickness, color);
			this.renderFilledBox(minX, maxY - thickness, maxZ - thickness, maxX, maxY, maxZ, color);
			this.renderFilledBox(minX, maxY - thickness, minZ + thickness, minX + thickness, maxY, maxZ - thickness, color);
			this.renderFilledBox(maxX - thickness, maxY - thickness, minZ + thickness, maxX, maxY, maxZ - thickness, color);
			this.renderFilledBox(minX, minY + thickness, minZ, minX + thickness, maxY - thickness, minZ + thickness, color);
			this.renderFilledBox(maxX - thickness, minY + thickness, minZ, maxX, maxY - thickness, minZ + thickness, color);
			this.renderFilledBox(minX, minY + thickness, maxZ - thickness, minX + thickness, maxY - thickness, maxZ - thickness, color);
			this.renderFilledBox(maxX - thickness, minY + thickness, maxZ - thickness, maxX, maxY - thickness, maxZ - thickness, color);
		}

		public void renderSphere(double centerX, double centerY, double centerZ, double radius, Color color) {
			VertexConsumer buffer = this.immediate.getBuffer(RenderUtils.NO_DEPTH_FILLED_BOX);
			int argb = color.getAlpha() << 24 | color.getRed() << 16 | color.getGreen() << 8 | color.getBlue();
			PoseStack.Pose pose = this.matrices.last();
			float x = (float)centerX;
			float y = (float)centerY;
			float z = (float)centerZ;
			float scale = (float)radius;
			int[][] faces = RenderUtils.ICOSPHERE_FACES;
			for (int i = 0; i < faces.length; ++i) {
				int[] face = faces[i];
				float[] a = RenderUtils.ICOSPHERE_VERTS[face[0]];
				float[] b = RenderUtils.ICOSPHERE_VERTS[face[1]];
				float[] c = RenderUtils.ICOSPHERE_VERTS[face[2]];
				// The filled-box pipeline draws quads, so each triangle repeats its last vertex.
				buffer.addVertex(pose, x + a[0] * scale, y + a[1] * scale, z + a[2] * scale).setColor(argb);
				buffer.addVertex(pose, x + b[0] * scale, y + b[1] * scale, z + b[2] * scale).setColor(argb);
				buffer.addVertex(pose, x + c[0] * scale, y + c[1] * scale, z + c[2] * scale).setColor(argb);
				buffer.addVertex(pose, x + c[0] * scale, y + c[1] * scale, z + c[2] * scale).setColor(argb);
			}
			this.dirty = true;
		}

		public void renderLine(Color color, Vec3 from, Vec3 to, float width) {
			VertexConsumer buffer = this.immediate.getBuffer(RenderUtils.NO_DEPTH_LINES);
			int argb = color.getAlpha() << 24 | color.getRed() << 16 | color.getGreen() << 8 | color.getBlue();
			PoseStack.Pose pose = this.matrices.last();
			Vector3f normal = new Vector3f((float)(to.x - from.x), (float)(to.y - from.y), (float)(to.z - from.z)).normalize();
			float lineWidth = Math.max(1.0f, width);
			buffer.addVertex(pose, (float)from.x, (float)from.y, (float)from.z).setColor(argb).setNormal(pose, normal).setLineWidth(lineWidth);
			buffer.addVertex(pose, (float)to.x, (float)to.y, (float)to.z).setColor(argb).setNormal(pose, normal).setLineWidth(lineWidth);
			this.dirty = true;
		}

		/** Draws whatever was queued with the depth test off, then restores it. */
		public void flush() {
			if (!this.dirty) {
				return;
			}
			boolean depthTestWasEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
			GL11.glDisable(GL11.GL_DEPTH_TEST);
			GL11.glDepthMask(false);
			RenderUtils.drawWithoutFog(this.immediate);
			// ponytail: the depth mask is forced back on rather than restored to its previous value.
			GL11.glDepthMask(true);
			if (depthTestWasEnabled) {
				GL11.glEnable(GL11.GL_DEPTH_TEST);
			}
			this.dirty = false;
		}
	}

	/**
	 * One-shot batch owning its own buffer. {@link #flush} closes the allocator, so an
	 * instance is good for a single frame and refuses to draw afterwards.
	 */
	public static class WorldBatch {
		public PoseStack matrices;
		public ByteBufferBuilder allocator;
		public MultiBufferSource.BufferSource immediate;
		public boolean dirty;
		public boolean closed;

		public WorldBatch(PoseStack matrices) {
			this.matrices = matrices;
			this.allocator = new ByteBufferBuilder(BATCH_BUFFER_BYTES);
			this.immediate = MultiBufferSource.immediate(this.allocator);
		}

		public void renderBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, Color color) {
			this.renderFilledBox(minX, minY, minZ, maxX, maxY, maxZ, color);
		}

		public void renderFilledBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, Color color) {
			VertexConsumer buffer = this.immediate.getBuffer(RenderUtils.NO_DEPTH_FILLED_BOX);
			int argb = color.getAlpha() << 24 | color.getRed() << 16 | color.getGreen() << 8 | color.getBlue();
			PoseStack.Pose pose = this.matrices.last();
			float x0 = (float)minX;
			float y0 = (float)minY;
			float z0 = (float)minZ;
			float x1 = (float)maxX;
			float y1 = (float)maxY;
			float z1 = (float)maxZ;
			// Bottom.
			buffer.addVertex(pose, x0, y0, z0).setColor(argb);
			buffer.addVertex(pose, x1, y0, z0).setColor(argb);
			buffer.addVertex(pose, x1, y0, z1).setColor(argb);
			buffer.addVertex(pose, x0, y0, z1).setColor(argb);
			// Top.
			buffer.addVertex(pose, x0, y1, z0).setColor(argb);
			buffer.addVertex(pose, x0, y1, z1).setColor(argb);
			buffer.addVertex(pose, x1, y1, z1).setColor(argb);
			buffer.addVertex(pose, x1, y1, z0).setColor(argb);
			// North.
			buffer.addVertex(pose, x0, y0, z0).setColor(argb);
			buffer.addVertex(pose, x0, y1, z0).setColor(argb);
			buffer.addVertex(pose, x1, y1, z0).setColor(argb);
			buffer.addVertex(pose, x1, y0, z0).setColor(argb);
			// South.
			buffer.addVertex(pose, x1, y0, z1).setColor(argb);
			buffer.addVertex(pose, x1, y1, z1).setColor(argb);
			buffer.addVertex(pose, x0, y1, z1).setColor(argb);
			buffer.addVertex(pose, x0, y0, z1).setColor(argb);
			// West.
			buffer.addVertex(pose, x0, y0, z1).setColor(argb);
			buffer.addVertex(pose, x0, y1, z1).setColor(argb);
			buffer.addVertex(pose, x0, y1, z0).setColor(argb);
			buffer.addVertex(pose, x0, y0, z0).setColor(argb);
			// East.
			buffer.addVertex(pose, x1, y0, z0).setColor(argb);
			buffer.addVertex(pose, x1, y1, z0).setColor(argb);
			buffer.addVertex(pose, x1, y1, z1).setColor(argb);
			buffer.addVertex(pose, x1, y0, z1).setColor(argb);
			this.dirty = true;
		}

		/** Box drawn as twelve thin filled edges, so it keeps its thickness at any distance. */
		public void renderOutlineBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, Color color) {
			double sizeX = maxX - minX;
			double sizeY = maxY - minY;
			double sizeZ = maxZ - minZ;
			double thickness = Math.min(0.02, Math.min(sizeX / 8.0, Math.min(sizeY / 8.0, sizeZ / 8.0)));
			if (thickness <= 0.0) {
				return;
			}
			this.renderFilledBox(minX, minY, minZ, maxX, minY + thickness, minZ + thickness, color);
			this.renderFilledBox(minX, minY, maxZ - thickness, maxX, minY + thickness, maxZ, color);
			this.renderFilledBox(minX, minY, minZ + thickness, minX + thickness, minY + thickness, maxZ - thickness, color);
			this.renderFilledBox(maxX - thickness, minY, minZ + thickness, maxX, minY + thickness, maxZ - thickness, color);
			this.renderFilledBox(minX, maxY - thickness, minZ, maxX, maxY, minZ + thickness, color);
			this.renderFilledBox(minX, maxY - thickness, maxZ - thickness, maxX, maxY, maxZ, color);
			this.renderFilledBox(minX, maxY - thickness, minZ + thickness, minX + thickness, maxY, maxZ - thickness, color);
			this.renderFilledBox(maxX - thickness, maxY - thickness, minZ + thickness, maxX, maxY, maxZ - thickness, color);
			this.renderFilledBox(minX, minY + thickness, minZ, minX + thickness, maxY - thickness, minZ + thickness, color);
			this.renderFilledBox(maxX - thickness, minY + thickness, minZ, maxX, maxY - thickness, minZ + thickness, color);
			this.renderFilledBox(minX, minY + thickness, maxZ - thickness, minX + thickness, maxY - thickness, maxZ - thickness, color);
			this.renderFilledBox(maxX - thickness, minY + thickness, maxZ - thickness, maxX, maxY - thickness, maxZ - thickness, color);
		}

		public void renderSphere(double centerX, double centerY, double centerZ, double radius, Color color) {
			VertexConsumer buffer = this.immediate.getBuffer(RenderUtils.NO_DEPTH_FILLED_BOX);
			int argb = color.getAlpha() << 24 | color.getRed() << 16 | color.getGreen() << 8 | color.getBlue();
			PoseStack.Pose pose = this.matrices.last();
			float x = (float)centerX;
			float y = (float)centerY;
			float z = (float)centerZ;
			float scale = (float)radius;
			int[][] faces = RenderUtils.ICOSPHERE_FACES;
			for (int i = 0; i < faces.length; ++i) {
				int[] face = faces[i];
				float[] a = RenderUtils.ICOSPHERE_VERTS[face[0]];
				float[] b = RenderUtils.ICOSPHERE_VERTS[face[1]];
				float[] c = RenderUtils.ICOSPHERE_VERTS[face[2]];
				// The filled-box pipeline draws quads, so each triangle repeats its last vertex.
				buffer.addVertex(pose, x + a[0] * scale, y + a[1] * scale, z + a[2] * scale).setColor(argb);
				buffer.addVertex(pose, x + b[0] * scale, y + b[1] * scale, z + b[2] * scale).setColor(argb);
				buffer.addVertex(pose, x + c[0] * scale, y + c[1] * scale, z + c[2] * scale).setColor(argb);
				buffer.addVertex(pose, x + c[0] * scale, y + c[1] * scale, z + c[2] * scale).setColor(argb);
			}
			this.dirty = true;
		}

		public void renderLine(Color color, Vec3 from, Vec3 to, float width) {
			VertexConsumer buffer = this.immediate.getBuffer(RenderUtils.NO_DEPTH_LINES);
			int argb = color.getAlpha() << 24 | color.getRed() << 16 | color.getGreen() << 8 | color.getBlue();
			PoseStack.Pose pose = this.matrices.last();
			Vector3f normal = new Vector3f((float)(to.x - from.x), (float)(to.y - from.y), (float)(to.z - from.z)).normalize();
			float lineWidth = Math.max(1.0f, width);
			buffer.addVertex(pose, (float)from.x, (float)from.y, (float)from.z).setColor(argb).setNormal(pose, normal).setLineWidth(lineWidth);
			buffer.addVertex(pose, (float)to.x, (float)to.y, (float)to.z).setColor(argb).setNormal(pose, normal).setLineWidth(lineWidth);
			this.dirty = true;
		}

		/** Draws with the depth test off and then closes the allocator: calling it twice is a no-op. */
		public void flush() {
			if (this.closed) {
				return;
			}
			try {
				if (this.dirty) {
					boolean depthTestWasEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
					GL11.glDisable(GL11.GL_DEPTH_TEST);
					GL11.glDepthMask(false);
					RenderUtils.drawWithoutFog(this.immediate);
					// ponytail: the depth mask is forced back on rather than restored to its previous value.
					GL11.glDepthMask(true);
					if (depthTestWasEnabled) {
						GL11.glEnable(GL11.GL_DEPTH_TEST);
					}
					this.dirty = false;
				}
			} finally {
				this.allocator.close();
				this.closed = true;
			}
		}
	}
}
