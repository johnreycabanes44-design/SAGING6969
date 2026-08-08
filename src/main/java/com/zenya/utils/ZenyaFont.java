package com.zenya.utils;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

/**
 * The client's two typefaces, defined by assets/zenya/font/{zenya,zenya_heading}.json.
 *
 * <p>Vanilla resolves a font from the {@link Style} carried by a component, so every
 * helper here only re-styles the text before handing it to {@link GuiGraphics}; there is
 * no separate text renderer. Null input is tolerated everywhere: it renders as empty text
 * and measures as zero, which lets callers pass values that have not resolved yet.
 */
public class ZenyaFont {
	public static Identifier ID = Identifier.fromNamespaceAndPath("zenya", "zenya");
	public static Identifier HEADING_ID = Identifier.fromNamespaceAndPath("zenya", "zenya_heading");
	public static float MC_FONT_SCALE = 1.15f;
	public static FontDescription.Resource FONT_SOURCE = new FontDescription.Resource(ID);
	public static FontDescription.Resource HEADING_FONT_SOURCE = new FontDescription.Resource(HEADING_ID);
	public static Style STYLE = Style.EMPTY.withFont(FONT_SOURCE);
	public static Style HEADING_STYLE = Style.EMPTY.withFont(HEADING_FONT_SOURCE);

	/** Vanilla-font fallback switch, hard-wired off. */
	public static boolean mc() {
		return false;
	}

	public static Component text(String text) {
		return Component.literal(text == null ? "" : text).copy().setStyle(STYLE);
	}

	public static Component heading(String text) {
		return Component.literal(text == null ? "" : text).copy().setStyle(HEADING_STYLE);
	}

	public static void drawHeading(GuiGraphics graphics, Font font, String text, int x, int y, int color) {
		graphics.drawString(font, ZenyaFont.heading(text), x, y, color, false);
	}

	public static int headingWidth(Font font, String text) {
		if (text == null) {
			return 0;
		}
		return font.width(ZenyaFont.heading(text));
	}

	/** Re-styles an existing component onto the body font, keeping the rest of its style. */
	public static Component wrap(Component component) {
		if (component == null) {
			return null;
		}
		Style style = component.getStyle().withFont(FONT_SOURCE);
		return component.copy().setStyle(style);
	}

	public static void draw(GuiGraphics graphics, Font font, String text, int x, int y, int color, boolean shadow) {
		graphics.drawString(font, ZenyaFont.text(text), x, y, color, shadow);
	}

	public static void draw(GuiGraphics graphics, Font font, Component text, int x, int y, int color, boolean shadow) {
		graphics.drawString(font, ZenyaFont.wrap(text), x, y, color, shadow);
	}

	public static void draw(GuiGraphics graphics, Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
		graphics.drawString(font, text, x, y, color, shadow);
	}

	public static void drawShadow(GuiGraphics graphics, Font font, String text, int x, int y, int color) {
		graphics.drawString(font, ZenyaFont.text(text), x, y, color);
	}

	public static void drawShadow(GuiGraphics graphics, Font font, Component text, int x, int y, int color) {
		graphics.drawString(font, ZenyaFont.wrap(text), x, y, color);
	}

	public static void drawShadow(GuiGraphics graphics, Font font, FormattedCharSequence text, int x, int y, int color) {
		graphics.drawString(font, text, x, y, color);
	}

	public static void drawCentered(GuiGraphics graphics, Font font, String text, int centerX, int y, int color, boolean shadow) {
		int textWidth = ZenyaFont.width(font, text);
		Component component = ZenyaFont.text(text);
		graphics.drawString(font, component, centerX - textWidth / 2, y, color, shadow);
	}

	public static int width(Font font, String text) {
		if (text == null) {
			return 0;
		}
		return font.width(ZenyaFont.text(text));
	}

	public static int width(Font font, Component text) {
		if (text == null) {
			return 0;
		}
		return font.width(ZenyaFont.wrap(text));
	}

	public static int width(Font font, FormattedCharSequence text) {
		if (text == null) {
			return 0;
		}
		return font.width(text);
	}

	public static FormattedCharSequence ordered(String text) {
		return ZenyaFont.text(text).getVisualOrderText();
	}

	/** Draws at (x, y) scaled about that corner; the caller's coordinates stay unscaled. */
	public static void drawScaled(GuiGraphics graphics, Font font, Component text, int x, int y, int color, boolean shadow, float scale) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		graphics.drawString(font, text, 0, 0, color, shadow);
		graphics.pose().popMatrix();
	}

	public static void drawScaledOrdered(GuiGraphics graphics, Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow, float scale) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		if (shadow) {
			graphics.drawString(font, text, 0, 0, color);
		} else {
			graphics.drawString(font, text, 0, 0, color, false);
		}
		graphics.pose().popMatrix();
	}
}
