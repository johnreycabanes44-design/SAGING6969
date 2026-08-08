package com.zenya.utils.renderer;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderPass;
import com.zenya.ZenyaClient;
import com.zenya.utils.renderer.arc.ArcOutlinePipeline;
import com.zenya.utils.renderer.arc.ArcPipeline;
import com.zenya.utils.renderer.blur.KawasePipeline;
import com.zenya.utils.renderer.glass.LiquidGlassPipeline;
import com.zenya.utils.renderer.outline.OutlinePipeline;
import com.zenya.utils.renderer.rect.RectPipeline;
import com.zenya.utils.renderer.texture.TexturePipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * Entry point for every custom shader pipeline (rect, outline, arc, blur, glass, texture).
 *
 * <p>Every draw call takes an {@code override} flag. When set, the call is queued into
 * {@link #OVERRIDE_TASKS} instead of running now, so it can be replayed by
 * {@link #renderOverrides(GuiGraphics)} after vanilla has finished the frame — that is the
 * only way to draw on top of screens that render after our HUD hooks.
 *
 * <p>Scissor state is mirrored in static fields because the render passes are created deep
 * inside the pipelines and have to re-apply it themselves via {@link #applyScissor(RenderPass)}.
 */
public class RenderUtil {
	public static List<Runnable> OVERRIDE_TASKS = new ArrayList<>();
	public static float Z_OVERRIDE = 0.0f;
	public static int FIXED_GUI_SCALE = 1;
	public static boolean scissorActive = false;
	public static int scissorX;
	public static int scissorY;
	public static int scissorWidth;
	public static int scissorHeight;

	public static int getFixedScaledWidth() {
		Window window = Minecraft.getInstance().getWindow();
		return window.getScreenWidth();
	}

	public static int getFixedScaledHeight() {
		Window window = Minecraft.getInstance().getWindow();
		return window.getScreenHeight();
	}

	public static float getScaleFactor() {
		Window window = Minecraft.getInstance().getWindow();
		return window.getGuiScale();
	}

	public static float convertX(float x) {
		return x * getScaleFactor();
	}

	public static float convertY(float y) {
		return y * getScaleFactor();
	}

	public static float convertSize(float size) {
		return size * getScaleFactor();
	}

	/** Orthographic projection in raw framebuffer pixels, ignoring the GUI scale. */
	public static Matrix4f createProjection() {
		return new Matrix4f().ortho(0.0f, getFixedScaledWidth(), getFixedScaledHeight(), 0.0f, -1000.0f, 1000.0f);
	}

	/**
	 * Orthographic projection in GUI space with the current {@link GuiGraphics} 2D pose folded
	 * in, so queued shader draws land where the caller's translated/scaled matrix put them.
	 */
	public static Matrix4f createProjection(GuiGraphics graphics) {
		Window window = Minecraft.getInstance().getWindow();
		Matrix3x2fStack pose = graphics.pose();
		return new Matrix4f()
				.ortho(0.0f, window.getGuiScaledWidth(), window.getGuiScaledHeight(), 0.0f, -1000.0f, 1000.0f)
				.mul(new Matrix4f(
						pose.m00, pose.m01, 0.0f, 0.0f,
						pose.m10, pose.m11, 0.0f, 0.0f,
						0.0f, 0.0f, 1.0f, 0.0f,
						pose.m20, pose.m21, 0.0f, 1.0f));
	}

	/** The chat screen does not count: the HUD stays interactive while it is open. */
	public static boolean hasScreenOpen() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.screen != null && !(minecraft.screen instanceof ChatScreen);
	}

	public static void drawRoundedRect(GuiGraphics graphics, float x, float y, float width, float height, float radius, int color, boolean override) {
		drawRoundedRect(createProjection(graphics), x, y, width, height, radius, color, override);
	}

	public static void drawRoundedRect(GuiGraphics graphics, float x, float y, float width, float height, float radius, boolean override, int... colors) {
		drawRoundedRect(createProjection(graphics), x, y, width, height, radius, radius, radius, radius, override, colors);
	}

	public static void drawRoundedRect(GuiGraphics graphics, float x, float y, float width, float height, float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft, boolean override, int... colors) {
		drawRoundedRect(createProjection(graphics), x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, override, colors);
	}

	public static void drawRoundedRect(Matrix4f matrix, float x, float y, float width, float height, float radius, int color, boolean override) {
		drawRoundedRect(matrix, x, y, width, height, radius, radius, radius, radius, override, new int[] { color });
	}

	public static void drawRoundedRect(Matrix4f matrix, float x, float y, float width, float height, float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft, boolean override, int... colors) {
		if (override) {
			OVERRIDE_TASKS.add(() -> RectPipeline.draw(matrix, x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, 0.0f, colors));
			return;
		}

		RectPipeline.draw(matrix, x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, 0.0f, colors);
	}

	public static void drawRoundedRect(Matrix4f matrix, float x, float y, float width, float height, float radius, boolean override, int... colors) {
		drawRoundedRect(matrix, x, y, width, height, radius, radius, radius, radius, override, colors);
	}

	public static void drawOutline(GuiGraphics graphics, float x, float y, float width, float height, float radius, float thickness, int color, boolean override) {
		drawOutline(createProjection(graphics), x, y, width, height, radius, radius, radius, radius, thickness, color, override);
	}

	public static void drawOutline(GuiGraphics graphics, float x, float y, float width, float height, float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft, float thickness, int color, boolean override) {
		drawOutline(createProjection(graphics), x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, thickness, color, override);
	}

	public static void drawOutline(GuiGraphics graphics, float x, float y, float width, float height, float radius, float thickness, boolean override, int... colors) {
		drawOutline(createProjection(graphics), x, y, width, height, radius, radius, radius, radius, thickness, override, colors);
	}

	public static void drawOutline(GuiGraphics graphics, float x, float y, float width, float height, float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft, float thickness, boolean override, int... colors) {
		drawOutline(createProjection(graphics), x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, thickness, override, colors);
	}

	public static void drawOutline(Matrix4f matrix, float x, float y, float width, float height, float radius, float thickness, int color, boolean override) {
		drawOutline(matrix, x, y, width, height, radius, radius, radius, radius, thickness, color, override);
	}

	public static void drawOutline(Matrix4f matrix, float x, float y, float width, float height, float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft, float thickness, int color, boolean override) {
		if (override) {
			OVERRIDE_TASKS.add(() -> OutlinePipeline.draw(matrix, x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, thickness, 0.0f, new int[] { color }));
			return;
		}

		OutlinePipeline.draw(matrix, x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, thickness, 0.0f, new int[] { color });
	}

	public static void drawOutline(Matrix4f matrix, float x, float y, float width, float height, float radius, float thickness, boolean override, int... colors) {
		drawOutline(matrix, x, y, width, height, radius, radius, radius, radius, thickness, override, colors);
	}

	public static void drawOutline(Matrix4f matrix, float x, float y, float width, float height, float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft, float thickness, boolean override, int... colors) {
		if (override) {
			OVERRIDE_TASKS.add(() -> OutlinePipeline.draw(matrix, x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, thickness, 0.0f, colors));
			return;
		}

		OutlinePipeline.draw(matrix, x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, thickness, 0.0f, colors);
	}

	/**
	 * Kawase blur of whatever is already in the main render target, masked to a rounded rect.
	 * The projection is captured up front so a queued task blurs the region the caller meant,
	 * not the pose that happens to be active at replay time.
	 */
	public static void drawBlur(GuiGraphics graphics, float x, float y, float width, float height, float radius, float strength, boolean override) {
		Matrix4f matrix = createProjection(graphics);

		if (override) {
			OVERRIDE_TASKS.add(() -> KawasePipeline.draw(matrix, x, y, width, height, radius, strength, 0.0f));
			return;
		}

		KawasePipeline.draw(matrix, x, y, width, height, radius, strength, 0.0f);
	}

	public static void drawArc(GuiGraphics graphics, float x, float y, float size, float thickness, float degrees, float rotation, int color, boolean override) {
		drawArc(createProjection(graphics), x, y, size, thickness, degrees, rotation, color, override);
	}

	public static void drawArc(GuiGraphics graphics, float x, float y, float size, float thickness, float degrees, float rotation, boolean override, int... colors) {
		drawArc(createProjection(graphics), x, y, size, thickness, degrees, rotation, override, colors);
	}

	public static void drawArc(Matrix4f matrix, float x, float y, float size, float thickness, float degrees, float rotation, int color, boolean override) {
		if (override) {
			OVERRIDE_TASKS.add(() -> ArcPipeline.draw(matrix, x, y, size, thickness, degrees, rotation, 0.0f, new int[] { color }));
			return;
		}

		ArcPipeline.draw(matrix, x, y, size, thickness, degrees, rotation, 0.0f, new int[] { color });
	}

	public static void drawArc(Matrix4f matrix, float x, float y, float size, float thickness, float degrees, float rotation, boolean override, int... colors) {
		if (override) {
			OVERRIDE_TASKS.add(() -> ArcPipeline.draw(matrix, x, y, size, thickness, degrees, rotation, 0.0f, colors));
			return;
		}

		ArcPipeline.draw(matrix, x, y, size, thickness, degrees, rotation, 0.0f, colors);
	}

	public static void arcOutline(GuiGraphics graphics, float x, float y, float size, float thickness, float degrees, float rotation, float outlineThickness, int fillColor, int outlineColor, boolean override) {
		arcOutline(createProjection(graphics), x, y, size, thickness, degrees, rotation, outlineThickness, fillColor, outlineColor, override);
	}

	public static void arcOutline(Matrix4f matrix, float x, float y, float size, float thickness, float degrees, float rotation, float outlineThickness, int fillColor, int outlineColor, boolean override) {
		if (override) {
			OVERRIDE_TASKS.add(() -> ArcOutlinePipeline.draw(matrix, x, y, size, thickness, degrees, rotation, outlineThickness, fillColor, outlineColor, 0.0f));
			return;
		}

		ArcOutlinePipeline.draw(matrix, x, y, size, thickness, degrees, rotation, outlineThickness, fillColor, outlineColor, 0.0f);
	}

	public static void drawTexture(GuiGraphics graphics, float x, float y, float size, Identifier texture, int color, float radius, boolean override) {
		drawTexture(createProjection(graphics), x, y, size, texture, color, radius, override);
	}

	public static void drawTexture(Matrix4f matrix, float x, float y, float size, Identifier texture, int color, float radius, boolean override) {
		Minecraft minecraft = Minecraft.getInstance();
		AbstractTexture bound = minecraft.getTextureManager().getTexture(texture);

		if (bound == null) {
			return;
		}

		if (override) {
			OVERRIDE_TASKS.add(() -> TexturePipeline.draw(matrix, x, y, size, bound.getTextureView(), color, radius, 0.0f));
			return;
		}

		TexturePipeline.draw(matrix, x, y, size, bound.getTextureView(), color, radius, 0.0f);
	}

	/**
	 * Frosted-glass panel: refracts the framebuffer and tints the edge with a fresnel term.
	 * The fresnel power is stepped rather than continuous because only two panel heights are
	 * ever drawn and they need visibly different edge falloff.
	 */
	public static void drawLiquidGlass(GuiGraphics graphics, float x, float y, float width, float height, float smoothness, float distortStrength, float radius, int color, boolean override) {
		Matrix4f matrix = createProjection(graphics);
		float[] cornerRadii = new float[] {
				radius * smoothness / 2.0f,
				radius * smoothness / 2.0f,
				radius * smoothness / 2.0f,
				radius * smoothness / 2.0f
		};
		float globalAlpha = (color >> 24 & 255) / 255.0f;
		float fresnelPower = height == 240.0f ? 100.0f : 50.0f;
		int fresnelColor = color | 0xFF000000;
		boolean fresnelInvert = true;

		if (override) {
			OVERRIDE_TASKS.add(() -> LiquidGlassPipeline.draw(matrix, x, y, width, height, cornerRadii, color, globalAlpha, fresnelPower, fresnelColor, 1.0f, fresnelInvert, 0.0f, distortStrength, smoothness, 0.0f));
			return;
		}

		LiquidGlassPipeline.draw(matrix, x, y, width, height, cornerRadii, color, globalAlpha, fresnelPower, fresnelColor, 1.0f, fresnelInvert, 0.0f, distortStrength, smoothness, 0.0f);
	}

	public static void addOverrideTask(Runnable task) {
		OVERRIDE_TASKS.add(task);
	}

	/**
	 * Replays every queued task with depth testing off, then restores the previous depth state
	 * and drops the scissor. The queue is drained even if a task throws, otherwise a single bad
	 * draw would repeat forever.
	 */
	public static void renderOverrides(GuiGraphics graphics) {
		if (OVERRIDE_TASKS.isEmpty()) {
			return;
		}

		boolean depthWasEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
		GL11.glDisable(GL11.GL_DEPTH_TEST);

		try {
			for (Runnable task : new ArrayList<>(OVERRIDE_TASKS)) {
				try {
					task.run();
				} catch (Throwable error) {
					// One broken pipeline must not abort the rest of the overlay.
					ZenyaClient.LOGGER.warn("Skipping failed override render task", error);
				}
			}
		} finally {
			OVERRIDE_TASKS.clear();

			if (depthWasEnabled) {
				GL11.glEnable(GL11.GL_DEPTH_TEST);
			} else {
				GL11.glDisable(GL11.GL_DEPTH_TEST);
			}

			scissorActive = false;
			GlStateManager._disableScissorTest();
		}
	}

	public static void clearOverrideTasks() {
		OVERRIDE_TASKS.clear();
	}

	public static boolean hasOverrideTasks() {
		return !OVERRIDE_TASKS.isEmpty();
	}

	public static void setScissor(float x, float y, float width, float height, boolean override) {
		if (override) {
			OVERRIDE_TASKS.add(() -> setScissorTasks(x, y, width, height));
			return;
		}

		setScissorTasks(x, y, width, height);
	}

	/**
	 * Converts a GUI-space rect into a framebuffer scissor box. GL measures Y from the bottom,
	 * so the rect is flipped before being clamped to the framebuffer; an empty result disables
	 * scissoring entirely rather than leaving a zero-area box that would cull everything.
	 */
	public static void setScissorTasks(float x, float y, float width, float height) {
		Window window = Minecraft.getInstance().getWindow();
		double guiScale = window.getGuiScale();
		int framebufferWidth = window.getWidth();
		int framebufferHeight = window.getHeight();

		scissorActive = true;
		scissorX = (int) x;
		scissorY = (int) y;
		scissorWidth = (int) width;
		scissorHeight = (int) height;

		int scaledX = (int) Math.round(scissorX * guiScale);
		int scaledY = (int) Math.round(scissorY * guiScale);
		int scaledWidth = (int) Math.round(scissorWidth * guiScale);
		int scaledHeight = (int) Math.round(scissorHeight * guiScale);
		int flippedY = framebufferHeight - (scaledY + scaledHeight);

		int left = Math.max(0, scaledX);
		int bottom = Math.max(0, flippedY);
		int right = Math.min(framebufferWidth, scaledX + scaledWidth);
		int top = Math.min(framebufferHeight, flippedY + scaledHeight);
		int boxWidth = Math.max(0, right - left);
		int boxHeight = Math.max(0, top - bottom);

		if (boxWidth == 0 || boxHeight == 0) {
			scissorActive = false;
			GlStateManager._disableScissorTest();
			return;
		}

		GlStateManager._enableScissorTest();
		GlStateManager._scissorBox(left, bottom, boxWidth, boxHeight);
	}

	/** Re-applies the mirrored scissor rect inside a pipeline's own render pass. */
	public static void applyScissor(RenderPass pass) {
		if (scissorActive) {
			pass.enableScissor(scissorX, scissorY, scissorX + scissorWidth, scissorY + scissorHeight);
		}
	}

	public static void clearScissor(boolean override) {
		if (override) {
			OVERRIDE_TASKS.add(() -> {
				scissorActive = false;
				GlStateManager._disableScissorTest();
			});
			return;
		}

		scissorActive = false;
		GlStateManager._disableScissorTest();
	}

	/** True when nothing but chat is on screen, i.e. HUD draws have to go through the override queue. */
	public static boolean isOverrideActive() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.screen == null || minecraft.screen instanceof ChatScreen;
	}

	// ponytail: unscaledProjection and scaledProjection have identical bodies, so one of them
	// scales the wrong way; behaviour left as-is.
	public static void unscaledProjection(GuiGraphics graphics) {
		Window window = Minecraft.getInstance().getWindow();
		double guiScale = window.getGuiScale();
		graphics.pose().scale((float) guiScale, (float) guiScale);
	}

	public static void scaledProjection(GuiGraphics graphics) {
		Window window = Minecraft.getInstance().getWindow();
		double guiScale = window.getGuiScale();
		graphics.pose().scale((float) guiScale, (float) guiScale);
	}

	/** Scales RGB by {@code factor}, clamping each channel at full and leaving alpha alone. */
	public static int multiplyColor(int color, float factor) {
		int alpha = color & 0xFF000000;
		float red = (color >> 16 & 255) / 255.0f;
		float green = (color >> 8 & 255) / 255.0f;
		float blue = (color & 255) / 255.0f;

		red = Math.min(red * factor, 1.0f);
		green = Math.min(green * factor, 1.0f);
		blue = Math.min(blue * factor, 1.0f);

		return alpha | (int) (red * 255.0f) << 16 | (int) (green * 255.0f) << 8 | (int) (blue * 255.0f);
	}

	/** Per-channel linear blend from {@code from} to {@code to}, alpha included. */
	public static int interpolateColor(int from, int to, float progress) {
		int fromAlpha = from >> 24 & 255;
		int fromRed = from >> 16 & 255;
		int fromGreen = from >> 8 & 255;
		int fromBlue = from & 255;
		int toAlpha = to >> 24 & 255;
		int toRed = to >> 16 & 255;
		int toGreen = to >> 8 & 255;
		int toBlue = to & 255;

		int alpha = (int) (fromAlpha + (toAlpha - fromAlpha) * progress);
		int red = (int) (fromRed + (toRed - fromRed) * progress);
		int green = (int) (fromGreen + (toGreen - fromGreen) * progress);
		int blue = (int) (fromBlue + (toBlue - fromBlue) * progress);

		return alpha << 24 | red << 16 | green << 8 | blue;
	}
}
