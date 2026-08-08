package com.zenya.gui.hud;

import com.zenya.gui.ClickGUI;
import com.zenya.module.Module;
import com.zenya.module.ModuleManager;
import com.zenya.module.modules.client.Themes;
import com.zenya.module.modules.client.ZenyaPlus;
import com.zenya.utils.ZenyaFont;
import com.zenya.utils.renderer.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The enabled-module list drawn as rounded cards down the right-hand edge.
 *
 * <p>Rows are sorted widest-first so the stack reads as a clean diagonal against the
 * screen edge. ENABLED_BUFFER is a single reused list: this runs every frame and a
 * fresh ArrayList per frame would be pure garbage.
 */
public final class ModuleListHud {
	public static final ModuleListHud INSTANCE = new ModuleListHud();
	private static final int CARD_COLOR = -16777216;
	private static final int OUTLINE_COLOR = -14013910;
	private static final int SHADOW_COLOR = 0;
	private static final float CARD_RADIUS = 12.0f;
	private static final int PAD_X = 12;
	private static final int PAD_Y = 5;
	private static final int GAP = 4;
	private static final int TOP_OFFSET = 10;
	private static final int RIGHT_OFFSET = 10;
	private static final int BAR_W = 4;
	private static final int BAR_PAD = 0;
	private static final List<Module> ENABLED_BUFFER = new ArrayList<Module>(64);

	private ModuleListHud() {
	}

	/**
	 * Reorders the sorted list for the requested anchor. "Middle" grows outwards from
	 * the centre slot, alternating below then above, so the widest row sits in the
	 * middle. Any other value keeps the incoming order.
	 */
	private static List<Module> applyLayout(List<Module> modules, String anchor) {
		if (modules.size() <= 1) {
			return modules;
		}
		if ("Bottom".equalsIgnoreCase(anchor)) {
			ArrayList<Module> reversed = new ArrayList<Module>(modules);
			Collections.reverse(reversed);
			return reversed;
		}
		if ("Middle".equalsIgnoreCase(anchor)) {
			int size = modules.size();
			Module[] arranged = new Module[size];
			int centre = size / 2;
			int upper = centre;
			int lower = centre - 1;
			for (int i = 0; i < size; ++i) {
				if (i == 0) {
					arranged[centre] = modules.get(0);
					continue;
				}
				if (i % 2 == 1) {
					if (upper + 1 < size) {
						arranged[++upper] = modules.get(i);
						continue;
					}
					arranged[lower--] = modules.get(i);
					continue;
				}
				if (lower >= 0) {
					arranged[lower--] = modules.get(i);
					continue;
				}
				arranged[++upper] = modules.get(i);
			}
			return Arrays.asList(arranged);
		}
		return modules;
	}

	public void render(GuiGraphics graphics) {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.options == null || mc.getDebugOverlay().showDebugScreen()) {
			return;
		}
		if (mc.screen instanceof ClickGUI) {
			return;
		}
		Font font = mc.font;
		ENABLED_BUFFER.clear();
		for (Module module : ModuleManager.INSTANCE.getModules()) {
			if (!module.isEnabled()) continue;
			ENABLED_BUFFER.add(module);
		}
		if (ENABLED_BUFFER.isEmpty()) {
			return;
		}
		ENABLED_BUFFER.sort(Comparator.comparingInt((Module module) -> ZenyaFont.width(font, module.getName())).reversed());
		List<Module> ordered = applyLayout(ENABLED_BUFFER, "Top");
		int accent = ZenyaPlus.getAccentARGB();
		boolean rainbow = Themes.isRainbow();
		int screenWidth = mc.getWindow().getGuiScaledWidth();
		// Leftover from the original source; kept so a null font still fails here.
		Objects.requireNonNull(font);
		// One text line plus PAD_Y above and below.
		int cardHeight = 19;
		int y = TOP_OFFSET;
		for (int index = 0; index < ordered.size(); ++index) {
			Module module = ordered.get(index);
			String name = module.getName();
			int textWidth = ZenyaFont.width(font, name);
			int cardWidth = textWidth + PAD_X * 2;
			int x = screenWidth - RIGHT_OFFSET - cardWidth;
			int color = rainbow ? Themes.rainbowAt(index, 0.1f) : accent;
			RenderUtil.drawRoundedRect(graphics, x + 2, y + 2, cardWidth, cardHeight, CARD_RADIUS, SHADOW_COLOR, false);
			RenderUtil.drawRoundedRect(graphics, x, y, cardWidth, cardHeight, CARD_RADIUS, CARD_COLOR, false);
			// Accent bar down the left edge, slightly translucent, with a gloss on its top half.
			int barColor = color & 0xFFFFFF | 0xDD000000;
			RenderUtil.drawRoundedRect(graphics, x, y, BAR_W, cardHeight, CARD_RADIUS, 0.0f, CARD_RADIUS, 0.0f, false, barColor);
			RenderUtil.drawRoundedRect(graphics, x, y, BAR_W, 9.0f, CARD_RADIUS, 0.0f, 0.0f, 0.0f, false, 0x33FFFFFF);
			RenderUtil.drawOutline(graphics, x, y, cardWidth, cardHeight, CARD_RADIUS, 1.0f, OUTLINE_COLOR, false);
			ZenyaFont.draw(graphics, font, name, x + PAD_X + 2, y + PAD_Y + 1, color, false);
			y += cardHeight + GAP;
		}
	}
}
