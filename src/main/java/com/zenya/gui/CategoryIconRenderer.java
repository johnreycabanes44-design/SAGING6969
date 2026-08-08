package com.zenya.gui;

import com.zenya.module.Category;
import com.zenya.utils.renderer.RenderUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

/**
 * Resolves a category's icon shape name to a texture and draws it.
 *
 * <p>Several shape names share one texture on purpose ("sword" and "swords" are both
 * the combat icon), and an unknown shape falls back to the misc icon so a category
 * with a typo'd shape still draws something instead of a hole in the sidebar.
 */
public final class CategoryIconRenderer {
	private static final Identifier COMBAT = Identifier.fromNamespaceAndPath("zenya", "textures/gui/icons/combat.png");
	private static final Identifier RENDER = Identifier.fromNamespaceAndPath("zenya", "textures/gui/icons/render.png");
	private static final Identifier DONUT = Identifier.fromNamespaceAndPath("zenya", "textures/gui/icons/donut.png");
	private static final Identifier SMPS = Identifier.fromNamespaceAndPath("zenya", "textures/gui/icons/smps.png");
	private static final Identifier MISC = Identifier.fromNamespaceAndPath("zenya", "textures/gui/icons/misc.png");
	private static final Identifier LAYERS = Identifier.fromNamespaceAndPath("zenya", "textures/gui/icons/layers.png");
	private static final Identifier CLIENT = Identifier.fromNamespaceAndPath("zenya", "textures/gui/icons/client.png");

	private CategoryIconRenderer() {
	}

	public static void draw(GuiGraphics graphics, int x, int y, int size, Category category, int color) {
		draw(graphics, x, y, size, category.getIconShape(), color);
	}

	public static void draw(GuiGraphics graphics, int x, int y, int size, String shape, int color) {
		// A fully transparent tint would upload the texture for nothing.
		if ((color >>> 24 & 0xFF) == 0) {
			return;
		}
		Identifier texture = resolve(shape);
		// ponytail: resolve() falls back to MISC, so this null branch is unreachable
		if (texture == null) {
			return;
		}
		RenderUtil.drawTexture(graphics, x, y, size, texture, color, 0.0f, false);
	}

	private static Identifier resolve(String shape) {
		return switch (shape) {
			case "combat", "sword", "swords" -> COMBAT;
			case "render", "eye" -> RENDER;
			case "donut", "circle" -> DONUT;
			case "smps", "smp", "list" -> SMPS;
			case "layers" -> LAYERS;
			case "misc", "star" -> MISC;
			case "client", "settings" -> CLIENT;
			default -> MISC;
		};
	}
}
