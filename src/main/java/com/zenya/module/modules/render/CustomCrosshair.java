package com.zenya.module.modules.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.ModeSetting;
import com.zenya.setting.Setting;
import com.zenya.utils.renderer.RenderUtil;

import java.awt.Color;

/**
 * Replaces the vanilla crosshair with one of four hand-drawn shapes.
 *
 * <p>The mixin that suppresses the vanilla crosshair reaches this class through the
 * static {@link #INSTANCE}, which the constructor claims — so the last instance built
 * is the one that renders. Drawing is skipped whenever a screen is open, because the
 * crosshair would otherwise sit on top of the GUI.
 */
public class CustomCrosshair
		extends Module {
	public static CustomCrosshair INSTANCE;
	public ModeSetting mode;
	public Setting<Color> color;
	public Setting<Integer> size;
	public Setting<Integer> thickness;
	public static Minecraft mc = Minecraft.getInstance();

	public CustomCrosshair() {
		super("Custom Crosshair", Category.RENDER);
		this.mode = new ModeSetting("Look", "FS", "Cross", "Circle", "Dot", "FS");
		this.color = new Setting<>("Color", new Color(255, 255, 255, 255));
		this.size = new Setting<>("Size", 4, 1, 10);
		this.thickness = new Setting<>("Thickness", 2, 1, 5);
		this.setDescription("Changes your crosshair look.");
		this.addSetting(this.mode);
		this.addSetting(this.color);
		this.addSetting(this.size);
		this.addSetting(this.thickness);
		INSTANCE = this;
	}

	public static CustomCrosshair getInstance() {
		return INSTANCE;
	}

	/** True while the mixin should hide the vanilla crosshair and let this class draw instead. */
	public static boolean customCrosshairActive() {
		return INSTANCE != null && INSTANCE.isEnabled();
	}

	public void renderCrosshair(GuiGraphics graphics) {
		if (mc.screen != null) {
			return;
		}
		float centerX = (float) mc.getWindow().getGuiScaledWidth() / 2.0f;
		float centerY = (float) mc.getWindow().getGuiScaledHeight() / 2.0f;
		String style = this.mode.getValue();
		int argb = this.color.getValue().getRGB();
		int length = this.size.getValue();
		int weight = this.thickness.getValue();

		// Coordinates are truncated to whole pixels first so the arms stay crisp at any GUI scale.
		if ("Cross".equals(style)) {
			RenderUtil.drawRoundedRect(graphics, (int) (centerX - weight / 2.0f), (int) (centerY - length), weight, length * 2, 0.0f, argb, false);
			RenderUtil.drawRoundedRect(graphics, (int) (centerX - length), (int) (centerY - weight / 2.0f), length * 2, weight, 0.0f, argb, false);
		} else if ("Circle".equals(style)) {
			RenderUtil.drawOutline(graphics, (int) (centerX - length), (int) (centerY - length), length * 2, length * 2, length, 1.5f, argb, false);
		} else if ("Dot".equals(style)) {
			RenderUtil.drawRoundedRect(graphics, (int) (centerX - weight), (int) (centerY - weight), weight * 2, weight * 2, weight, argb, false);
		} else if ("FS".equals(style)) {
			Font font = mc.font;
			// 4.5f is half the font's 9px line height, which centres the text on the crosshair.
			graphics.drawString(font, "FS", (int) (centerX - font.width("FS") / 2.0f), (int) (centerY - 4.5f), argb, false);
		}
	}
}
