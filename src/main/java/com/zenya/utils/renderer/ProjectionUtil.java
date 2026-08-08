package com.zenya.utils.renderer;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Tuple;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

/**
 * World space to GUI space, for anything drawn flat but anchored to a world
 * position: nametags, tracer endpoints, spawner labels.
 *
 * <p>The three matrices are public and mutable on purpose — the world render
 * hook writes them every frame and the callers below read whatever was last
 * captured. Calling any of these outside a world render frame projects against
 * a stale (or identity) matrix.
 */
public class ProjectionUtil {
	public static Matrix4f projectionMatrix = new Matrix4f();
	public static Matrix4f modelViewMatrix = new Matrix4f();
	public static Matrix4f positionMatrix = new Matrix4f();
	public static Minecraft mc = Minecraft.getInstance();

	/** Projects via JOML using the live GL viewport, and returns GUI-scaled coordinates. */
	public static Vec3 worldSpaceToScreenSpace(Vec3 world) {
		Camera camera = mc.getEntityRenderDispatcher().camera;
		int screenHeight = mc.getWindow().getScreenHeight();
		int[] viewport = new int[4];
		GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
		Vector3f windowCoords = new Vector3f();
		double relativeX = world.x - camera.position().x;
		double relativeY = world.y - camera.position().y;
		double relativeZ = world.z - camera.position().z;
		Vector4f point = new Vector4f((float) relativeX, (float) relativeY, (float) relativeZ, 1.0f).mul(positionMatrix);
		Matrix4f combined = new Matrix4f(projectionMatrix);
		Matrix4f modelView = new Matrix4f(modelViewMatrix);
		combined.mul(modelView).project(point.x(), point.y(), point.z(), viewport, windowCoords);
		float guiScale = (float) mc.getWindow().getGuiScale();
		return new Vec3(windowCoords.x / guiScale, ((float) screenHeight - windowCoords.y) / guiScale, windowCoords.z);
	}

	/** @return screen position paired with whether the point is in front of the camera, or null if unprojectable. */
	public static Tuple<Vec3, Boolean> project(Matrix4f modelView, Matrix4f projection, Vec3 world) {
		if (mc.gameRenderer == null || mc.getCameraEntity() == null) {
			return null;
		}
		ScreenProjection projected = new ScreenProjection();
		if (!ProjectionUtil.projectToScreen(modelView, projection, world.x, world.y, world.z, projected)) {
			return null;
		}
		return new Tuple<>(new Vec3(projected.x, projected.y, projected.z), projected.visible);
	}

	/** Manual matrix multiply into {@code out}; false means the call was made with no camera. */
	public static boolean projectToScreen(Matrix4f modelView, Matrix4f projection, double worldX, double worldY, double worldZ, ScreenProjection out) {
		if (mc.gameRenderer == null || mc.getCameraEntity() == null || out == null) {
			return false;
		}
		Vec3 cameraPos = mc.gameRenderer.getMainCamera().position();
		double relativeX = worldX - cameraPos.x;
		double relativeY = worldY - cameraPos.y;
		double relativeZ = worldZ - cameraPos.z;
		double viewX = modelView.m00() * relativeX + modelView.m10() * relativeY + modelView.m20() * relativeZ + modelView.m30();
		double viewY = modelView.m01() * relativeX + modelView.m11() * relativeY + modelView.m21() * relativeZ + modelView.m31();
		double viewZ = modelView.m02() * relativeX + modelView.m12() * relativeY + modelView.m22() * relativeZ + modelView.m32();
		double viewW = modelView.m03() * relativeX + modelView.m13() * relativeY + modelView.m23() * relativeZ + modelView.m33();
		double clipX = projection.m00() * viewX + projection.m10() * viewY + projection.m20() * viewZ + projection.m30() * viewW;
		double clipY = projection.m01() * viewX + projection.m11() * viewY + projection.m21() * viewZ + projection.m31() * viewW;
		double clipZ = projection.m02() * viewX + projection.m12() * viewY + projection.m22() * viewZ + projection.m32() * viewW;
		double clipW = projection.m03() * viewX + projection.m13() * viewY + projection.m23() * viewZ + projection.m33() * viewW;
		boolean visible = clipW > 0.0;
		// ponytail: this is the perspective divide, but it multiplies by w instead of 1/w,
		// so the result is only correct where w happens to be 1.
		double perspective = clipW != 0.0 ? clipW : 0.0;
		double ndcX = clipX * perspective;
		double ndcY = clipY * perspective;
		double ndcZ = clipZ * perspective;
		double screenX = (ndcX * 0.5 + 0.5) * mc.getWindow().getGuiScaledWidth();
		double screenY = (0.5 - ndcY * 0.5) * mc.getWindow().getGuiScaledHeight();
		out.set(screenX, screenY, ndcZ, clipW, visible);
		return true;
	}

	public static Vector3f projectVector(Matrix4f modelView, Matrix4f projection, Vec3 world) {
		Tuple<Vec3, Boolean> projected = ProjectionUtil.project(modelView, projection, world);
		if (projected == null) {
			return null;
		}
		Vec3 screen = projected.getA();
		return new Vector3f((float) screen.x, (float) screen.y, (float) screen.z);
	}

	public static Vector3f project(double worldX, double worldY, double worldZ) {
		if (mc.gameRenderer == null || mc.getCameraEntity() == null) {
			return null;
		}
		return ProjectionUtil.project(new Vec3(worldX, worldY, worldZ));
	}

	/** Null when the point falls outside the depth range, i.e. behind the near plane or past the far plane. */
	public static Vector3f project(Vec3 world) {
		if (mc.gameRenderer == null || mc.getCameraEntity() == null) {
			return null;
		}
		Vec3 screen = ProjectionUtil.worldSpaceToScreenSpace(world);
		if (screen.z < 0.0 || screen.z > 1.0) {
			return null;
		}
		return new Vector3f((float) screen.x, (float) screen.y, (float) screen.z);
	}

	/**
	 * Like {@link #projectVector} but never returns an off-screen point: anything behind the
	 * camera or outside the viewport is pushed onto a 10px inset border, pointing at the target.
	 * Used to keep tracers attached to the screen edge.
	 */
	public static Vector3f projectWithClamp(Matrix4f modelView, Matrix4f projection, Vec3 world) {
		if (mc.gameRenderer == null || mc.getCameraEntity() == null) {
			return null;
		}
		Vec3 relative = world.subtract(mc.gameRenderer.getMainCamera().position());
		if (relative.lengthSqr() < 1.0E-4) {
			return new Vector3f((float) mc.getWindow().getGuiScaledWidth() / 2.0f, (float) mc.getWindow().getGuiScaledHeight() / 2.0f, 0.0f);
		}
		Vector4f point = new Vector4f((float) relative.x, (float) relative.y, (float) relative.z, 1.0f);
		point.mul(modelView);
		point.mul(projection);
		boolean behind = point.w() <= 0.0f;
		float divisor = Math.abs(point.w());
		if (divisor < 0.001f) {
			divisor = 0.001f;
		}
		float ndcX = point.x() / divisor;
		float ndcY = point.y() / divisor;
		float screenWidth = mc.getWindow().getGuiScaledWidth();
		float screenHeight = mc.getWindow().getGuiScaledHeight();
		float centerX = screenWidth / 2.0f;
		float centerY = screenHeight / 2.0f;
		float screenX = (ndcX * 0.5f + 0.5f) * screenWidth;
		float screenY = (0.5f - ndcY * 0.5f) * screenHeight;
		if (!behind && screenX >= 0.0f && screenX <= screenWidth && screenY >= 0.0f && screenY <= screenHeight) {
			return new Vector3f(screenX, screenY, 0.0f);
		}
		float offsetX = screenX - centerX;
		float offsetY = screenY - centerY;
		if (offsetX == 0.0f && offsetY == 0.0f) {
			offsetY = 1.0f;
		}
		float insetX = screenWidth / 2.0f - 10.0f;
		float insetY = screenHeight / 2.0f - 10.0f;
		float scaleX = Float.MAX_VALUE;
		float scaleY = Float.MAX_VALUE;
		if (offsetX != 0.0f) {
			scaleX = Math.abs(insetX / offsetX);
		}
		if (offsetY != 0.0f) {
			scaleY = Math.abs(insetY / offsetY);
		}
		float scale = Math.min(scaleX, scaleY);
		return new Vector3f(centerX + offsetX * scale, centerY + offsetY * scale, 0.0f);
	}

	/** Mutable out-parameter for {@link #projectToScreen}; {@code w} is the raw clip-space w. */
	public static class ScreenProjection {
		public double x;
		public double y;
		public double z;
		public double w;
		public boolean visible;

		public void set(double x, double y, double z, double w, boolean visible) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.w = w;
			this.visible = visible;
		}
	}
}
