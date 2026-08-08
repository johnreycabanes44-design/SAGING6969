package com.zenya.gui;

import com.zenya.module.modules.client.ZenyaPlus;
import com.zenya.setting.StorageBlocksSetting;
import com.zenya.utils.ZenyaFont;
import com.zenya.utils.renderer.RenderUtil;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.lwjgl.glfw.GLFW;

/**
 * Draggable grid picker for a {@link StorageBlocksSetting}: one cell per block,
 * click to toggle, click the corner swatch to open an inline HSV picker.
 *
 * <p>Selection and colours are edited on local copies, but both are pushed back
 * into the setting as they change, so "Cancel" only closes the screen — it does
 * not roll anything back. {@code removed()} flushes them once more in case the
 * screen is closed by some other route.
 */
public class StoragePickerScreen extends Screen {
	private static final int PANEL_W = 420;
	private static final int PANEL_H = 360;
	private static final int HEADER_H = 32;
	private static final int FOOTER_H = 36;
	private static final int PAD = 12;
	private static final int CELL = 48;
	private static final int GAP = 8;
	private static final int COLS = 4;
	private static final int C_BG = -871099372;
	private static final int C_PANEL = -15066598;
	private static final int C_BORDER = -13750738;
	private static final int C_DIVIDER = -14277082;
	private static final int C_TEXT = -1118482;
	private static final int C_MUTED = -7829368;
	private static final int C_SEL_BG = -14474461;
	private static final int C_HOV_BG = -14803426;
	private static final int C_CELL_BG = -15263977;

	private final StorageBlocksSetting setting;
	private final Screen parent;
	private final Set<String> tempSelected;
	private final Map<String, Color> tempColors;
	private float openAnim = 0.0f;
	private long lastNano = 0L;
	private int panelX;
	private int panelY;
	private boolean draggingPanel = false;
	private int dragOffX;
	private int dragOffY;
	/** Registry id whose colour picker is open, or null when the picker is closed. */
	private String colorEntry = null;
	private int pickerX;
	private int pickerY;
	private boolean draggingPicker = false;
	private int pickerDragOffX;
	private int pickerDragOffY;
	private float[] pickerHSV = new float[3];
	private int pickerAlpha = 255;
	private DragMode dragMode = DragMode.NONE;
	private int pSvX;
	private int pSvY;
	private int pHueX;
	private int pHueY;
	private int pAlphaX;
	private int pAlphaY;
	private int gridX;
	private int gridY;
	private int btnY;
	private int btnH;
	private int btnSaveX;
	private int btnCancelX;
	private int btnBW;
	/** Cell under the cursor, recomputed every frame by {@link #render}; -1 when none. */
	private int hoverIndex = -1;

	public StoragePickerScreen(Screen parent, StorageBlocksSetting setting) {
		super(Component.literal("Blocks"));
		this.parent = parent;
		this.setting = setting;
		this.tempSelected = new LinkedHashSet<>(setting.getSelected());
		this.tempColors = new LinkedHashMap<>();
		for (StorageBlocksSetting.Entry entry : setting.getOptions()) {
			this.tempColors.put(entry.value(), setting.getColor(entry.value()));
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** Deliberately empty: the panel draws over the live world, no vanilla dim. */
	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		long now = System.nanoTime();
		float delta = this.lastNano == 0L ? 0.016f : Math.min(0.1f, (float) (now - this.lastNano) / 1.0E9f);
		this.lastNano = now;
		this.openAnim += (1.0f - this.openAnim) * (1.0f - (float) Math.exp(-16.0f * delta));

		// First frame only: centre the panel, after which dragging owns the position.
		if (this.panelX == 0 && this.panelY == 0) {
			this.panelX = (this.width - PANEL_W) / 2;
			this.panelY = (this.height - PANEL_H) / 2;
		}

		int accent = ZenyaPlus.getAccentARGB();
		float scale = 0.88f + 0.12f * this.openAnim;
		float riseY = (1.0f - this.openAnim) * 16.0f;
		int centerX = this.panelX + PANEL_W / 2;
		int centerY = this.panelY + PANEL_H / 2;
		graphics.pose().pushMatrix();
		graphics.pose().translate((float) centerX, (float) centerY + riseY);
		graphics.pose().scale(scale, scale);
		graphics.pose().translate((float) -centerX, (float) -centerY);

		RenderUtil.drawRoundedRect(graphics, this.panelX, this.panelY, PANEL_W, PANEL_H, 14.0f, -15987438, false);
		RenderUtil.drawOutline(graphics, this.panelX, this.panelY, PANEL_W, PANEL_H, 14.0f, 1.5f, C_BORDER, false);
		ZenyaFont.draw(graphics, this.font, "Blocks", this.panelX + PAD, this.panelY + 10, C_TEXT, false);

		String counter = this.tempSelected.size() + " / " + this.setting.getOptions().size();
		ZenyaFont.draw(graphics, this.font, counter, this.panelX + PANEL_W - PAD - ZenyaFont.width(this.font, counter), this.panelY + 10, C_MUTED, false);
		graphics.fill(this.panelX + PAD, this.panelY + HEADER_H, this.panelX + PANEL_W - PAD, this.panelY + HEADER_H + 1, C_DIVIDER);

		this.gridX = this.panelX + PAD;
		this.gridY = this.panelY + HEADER_H + 10;
		int footerY = this.panelY + PANEL_H - FOOTER_H;
		List<StorageBlocksSetting.Entry> options = this.setting.getOptions();
		this.hoverIndex = -1;

		for (int i = 0; i < options.size(); ++i) {
			int cellX = this.gridX + i % COLS * (CELL + GAP);
			int cellY = this.gridY + i / COLS * (CELL + GAP);
			// Rows that would collide with the footer are simply not drawn.
			if (cellY + CELL > footerY - 4) {
				break;
			}

			StorageBlocksSetting.Entry entry = options.get(i);
			boolean selected = this.tempSelected.contains(entry.value());
			boolean hovered = hit(mouseX, mouseY, cellX, cellY, CELL, CELL);
			if (hovered) {
				this.hoverIndex = i;
			}

			int cellBg = selected ? C_SEL_BG : (hovered ? C_HOV_BG : C_CELL_BG);
			RenderUtil.drawRoundedRect(graphics, cellX, cellY, CELL, CELL, 10.0f, cellBg, false);
			if (selected) {
				RenderUtil.drawOutline(graphics, cellX, cellY, CELL, CELL, 10.0f, 1.5f, accent, false);
				graphics.fill(cellX + 6, cellY + CELL - 5, cellX + CELL - 6, cellY + CELL - 3, accent & 0xFFFFFF | 0x50000000);
			} else if (hovered) {
				RenderUtil.drawOutline(graphics, cellX, cellY, CELL, CELL, 10.0f, 1.0f, C_BORDER, false);
			}

			try {
				Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(entry.value()));
				if (block != null && block != Blocks.AIR) {
					graphics.renderItem(new ItemStack(block), cellX + 16, cellY + 16);
				} else {
					this.drawCellLabel(graphics, entry, cellX, cellY, selected);
				}
			} catch (Exception unparsableId) {
				// An id that is not a valid Identifier still gets a text cell.
				this.drawCellLabel(graphics, entry, cellX, cellY, selected);
			}

			Color color = this.tempColors.get(entry.value());
			if (color == null) {
				continue;
			}
			int swatchX = cellX + CELL - 8 - 5;
			int swatchY = cellY + 5;
			graphics.fill(swatchX - 1, swatchY - 1, swatchX + 8 + 1, swatchY + 8 + 1, 0xFF000000);
			graphics.fill(swatchX, swatchY, swatchX + 8, swatchY + 8, 0xFF000000 | color.getRGB() & 0xFFFFFF);
		}

		graphics.fill(this.panelX + PAD, footerY, this.panelX + PANEL_W - PAD, footerY + 1, C_DIVIDER);
		this.btnH = 26;
		this.btnBW = 72;
		this.btnY = footerY + (FOOTER_H - this.btnH) / 2;
		this.btnSaveX = this.panelX + PAD;
		this.btnCancelX = this.panelX + PANEL_W - PAD - this.btnBW;

		boolean saveHovered = hit(mouseX, mouseY, this.btnSaveX, this.btnY, this.btnBW, this.btnH);
		boolean cancelHovered = hit(mouseX, mouseY, this.btnCancelX, this.btnY, this.btnBW, this.btnH);
		RenderUtil.drawRoundedRect(graphics, this.btnSaveX, this.btnY, this.btnBW, this.btnH, 8.0f, saveHovered ? C_SEL_BG : C_CELL_BG, false);
		RenderUtil.drawOutline(graphics, this.btnSaveX, this.btnY, this.btnBW, this.btnH, 8.0f, 1.5f, accent, false);
		RenderUtil.drawRoundedRect(graphics, this.btnCancelX, this.btnY, this.btnBW, this.btnH, 8.0f, cancelHovered ? C_HOV_BG : C_CELL_BG, false);
		RenderUtil.drawOutline(graphics, this.btnCancelX, this.btnY, this.btnBW, this.btnH, 8.0f, 1.0f, C_BORDER, false);
		this.drawCenter(graphics, "Save", this.btnSaveX, this.btnY, this.btnBW, this.btnH, C_TEXT);
		this.drawCenter(graphics, "Cancel", this.btnCancelX, this.btnY, this.btnBW, this.btnH, C_MUTED);

		graphics.pose().popMatrix();
		super.render(graphics, mouseX, mouseY, partialTick);

		// Drawn outside the open animation so the picker never scales with the panel.
		if (this.colorEntry != null) {
			this.drawHsvPicker(graphics);
		}
	}

	/** Fallback cell content for ids with no renderable block: the label, elided to 10 chars. */
	private void drawCellLabel(GuiGraphics graphics, StorageBlocksSetting.Entry entry, int cellX, int cellY, boolean selected) {
		String label = entry.label();
		if (label.length() > 10) {
			label = label.substring(0, 9) + "\u2026";
		}
		int textWidth = ZenyaFont.width(this.font, label);
		int textX = cellX + (CELL - textWidth) / 2;
		ZenyaFont.draw(graphics, this.font, label, textX, cellY + (CELL - this.font.lineHeight) / 2 + 1, selected ? C_TEXT : C_MUTED, false);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int mouseX = (int) event.x();
		int mouseY = (int) event.y();
		int button = event.button();

		// While the picker is open it swallows every click, wherever it lands.
		if (this.colorEntry != null) {
			if (button == 0) {
				if (this.hitSv(mouseX, mouseY)) {
					this.dragMode = DragMode.SV;
					this.applySvDrag(mouseX, mouseY);
					return true;
				}
				if (this.hitHue(mouseX, mouseY)) {
					this.dragMode = DragMode.HUE;
					this.applyHueDrag(mouseX, mouseY);
					return true;
				}
				if (this.hitAlpha(mouseX, mouseY)) {
					this.dragMode = DragMode.ALPHA;
					this.applyAlphaDrag(mouseX, mouseY);
					return true;
				}
				if (!this.isInsidePicker(mouseX, mouseY)) {
					this.commitColor();
					this.colorEntry = null;
				}
				return true;
			}
			if (button == 1) {
				this.commitColor();
				this.colorEntry = null;
			}
			return true;
		}

		if (button == 0) {
			if (hit(mouseX, mouseY, this.panelX, this.panelY, PANEL_W, HEADER_H)) {
				this.draggingPanel = true;
				this.dragOffX = mouseX - this.panelX;
				this.dragOffY = mouseY - this.panelY;
				return true;
			}

			List<StorageBlocksSetting.Entry> options = this.setting.getOptions();
			if (this.hoverIndex >= 0 && this.hoverIndex < options.size()) {
				StorageBlocksSetting.Entry entry = options.get(this.hoverIndex);
				int cellX = this.gridX + this.hoverIndex % COLS * (CELL + GAP);
				int cellY = this.gridY + this.hoverIndex / COLS * (CELL + GAP);
				// Top-right corner of the cell is the colour swatch, not the toggle.
				if (mouseX >= cellX + CELL - 16 && mouseX <= cellX + CELL - 2 && mouseY >= cellY + 2 && mouseY <= cellY + 14) {
					this.openColorPicker(entry.value(), mouseX, mouseY);
					return true;
				}
				if (this.tempSelected.contains(entry.value())) {
					this.tempSelected.remove(entry.value());
				} else {
					this.tempSelected.add(entry.value());
				}
				this.setting.setValue(new LinkedHashSet<>(this.tempSelected));
				return true;
			}

			if (hit(mouseX, mouseY, this.btnSaveX, this.btnY, this.btnBW, this.btnH)) {
				this.save();
				return true;
			}
			if (hit(mouseX, mouseY, this.btnCancelX, this.btnY, this.btnBW, this.btnH)) {
				this.dismiss();
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (this.draggingPanel) {
			this.panelX = (int) event.x() - this.dragOffX;
			this.panelY = (int) event.y() - this.dragOffY;
			return true;
		}
		if (this.colorEntry != null && this.dragMode != DragMode.NONE) {
			int mouseX = (int) event.x();
			int mouseY = (int) event.y();
			switch (this.dragMode) {
				case SV -> this.applySvDrag(mouseX, mouseY);
				case HUE -> this.applyHueDrag(mouseX, mouseY);
				case ALPHA -> this.applyAlphaDrag(mouseX, mouseY);
				case NONE -> { }
			}
			return true;
		}
		if (this.draggingPicker) {
			this.pickerX = (int) event.x() - this.pickerDragOffX;
			this.pickerY = (int) event.y() - this.pickerDragOffY;
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		this.draggingPanel = false;
		this.draggingPicker = false;
		this.dragMode = DragMode.NONE;
		return super.mouseReleased(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
			// Escape closes the colour picker first, the screen second.
			if (this.colorEntry != null) {
				this.colorEntry = null;
				return true;
			}
			this.dismiss();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void removed() {
		this.setting.setValue(new LinkedHashSet<>(this.tempSelected));
		for (Map.Entry<String, Color> color : this.tempColors.entrySet()) {
			this.setting.setColor(color.getKey(), color.getValue());
		}
		super.removed();
	}

	private void save() {
		this.dismiss();
	}

	private void dismiss() {
		if (this.minecraft != null) {
			this.minecraft.setScreen(this.parent);
		}
	}

	private void openColorPicker(String value, int x, int y) {
		this.colorEntry = value;
		this.pickerX = x;
		this.pickerY = y;
		Color color = this.tempColors.getOrDefault(value, Color.WHITE);
		this.pickerHSV = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
		this.pickerAlpha = color.getAlpha();
	}

	/** Writes the current HSV+alpha back to both the local copy and the setting. */
	private void commitColor() {
		if (this.colorEntry == null) {
			return;
		}
		int rgb = Color.HSBtoRGB(this.pickerHSV[0], this.pickerHSV[1], this.pickerHSV[2]) & 0xFFFFFF;
		Color color = new Color(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, this.pickerAlpha);
		this.tempColors.put(this.colorEntry, color);
		this.setting.setColor(this.colorEntry, color);
	}

	/** Also caches the SV/hue/alpha bar positions the hit tests read. */
	private void drawHsvPicker(GuiGraphics graphics) {
		int left = Math.max(2, Math.min(this.pickerX, this.width - 150 - 2));
		int top = Math.max(2, Math.min(this.pickerY, this.height - 145 - 2));
		RenderUtil.drawRoundedRect(graphics, left, top, 150.0f, 145.0f, 12.0f, -15987438, false);
		RenderUtil.drawOutline(graphics, left, top, 150.0f, 145.0f, 12.0f, 1.5f, C_BORDER, false);
		RenderUtil.drawRoundedRect(graphics, left + 8, top + 8, 134.0f, 20.0f, 6.0f, C_CELL_BG, false);
		this.drawCenter(graphics, "Edit Color", left + 8, top + 8, 134, 20, C_TEXT);

		int contentX = left + 12;
		int contentY = top + 38;

		// Saturation/value square: one gradient column per pixel of width.
		this.pSvX = contentX;
		this.pSvY = contentY;
		for (int x = 0; x < 100; ++x) {
			int columnColor = 0xFF000000 | Color.HSBtoRGB(this.pickerHSV[0], x / 100.0f, 1.0f);
			graphics.fillGradient(this.pSvX + x, this.pSvY, this.pSvX + x + 1, this.pSvY + 70, columnColor, 0xFF000000);
		}
		RenderUtil.drawOutline(graphics, this.pSvX - 1, this.pSvY - 1, 102.0f, 72.0f, 4.0f, 1.0f, C_BORDER, false);

		this.pHueX = contentX + 100 + 10;
		this.pHueY = contentY;
		for (int y = 0; y < 70; ++y) {
			int rowColor = 0xFF000000 | Color.HSBtoRGB(y / 70.0f, 1.0f, 1.0f);
			graphics.fill(this.pHueX, this.pHueY + y, this.pHueX + 14, this.pHueY + y + 1, rowColor);
		}
		RenderUtil.drawOutline(graphics, this.pHueX - 1, this.pHueY - 1, 16.0f, 72.0f, 4.0f, 1.0f, C_BORDER, false);

		this.pAlphaX = contentX;
		this.pAlphaY = contentY + 70 + 12;
		int baseRgb = Color.HSBtoRGB(this.pickerHSV[0], this.pickerHSV[1], this.pickerHSV[2]) & 0xFFFFFF;
		for (int x = 0; x < 124; ++x) {
			int alpha = (int) (x / 124.0f * 255.0f);
			graphics.fill(this.pAlphaX + x, this.pAlphaY, this.pAlphaX + x + 1, this.pAlphaY + 10, alpha << 24 | baseRgb);
		}
		RenderUtil.drawOutline(graphics, this.pAlphaX - 1, this.pAlphaY - 1, 126.0f, 12.0f, 4.0f, 1.0f, C_BORDER, false);
	}

	private boolean isInsidePicker(int mouseX, int mouseY) {
		// ponytail: uses height 140 while drawHsvPicker draws 145, so the bottom
		// 5px of the panel counts as "outside" and a click there closes the picker.
		int left = Math.max(2, Math.min(this.pickerX, this.width - 150 - 2));
		int top = Math.max(2, Math.min(this.pickerY, this.height - 140 - 2));
		return hit(mouseX, mouseY, left, top, 150, 140);
	}

	private boolean hitSv(int mouseX, int mouseY) {
		return hit(mouseX, mouseY, this.pSvX, this.pSvY, 100, 70);
	}

	private boolean hitHue(int mouseX, int mouseY) {
		// ponytail: hit width 12 but the hue bar is drawn 14 wide.
		return hit(mouseX, mouseY, this.pHueX, this.pHueY, 12, 70);
	}

	private boolean hitAlpha(int mouseX, int mouseY) {
		// ponytail: hit box 118x8 but the alpha bar is drawn 124x10.
		return hit(mouseX, mouseY, this.pAlphaX, this.pAlphaY, 118, 8);
	}

	private void applySvDrag(int mouseX, int mouseY) {
		this.pickerHSV[1] = clamp((mouseX - this.pSvX) / 100.0f, 0.0f, 1.0f);
		this.pickerHSV[2] = clamp(1.0f - (mouseY - this.pSvY) / 70.0f, 0.0f, 1.0f);
		this.commitColor();
	}

	private void applyHueDrag(int mouseX, int mouseY) {
		this.pickerHSV[0] = clamp((mouseY - this.pHueY) / 70.0f, 0.0f, 1.0f);
		this.commitColor();
	}

	private void applyAlphaDrag(int mouseX, int mouseY) {
		this.pickerAlpha = (int) clamp((mouseX - this.pAlphaX) / 118.0f * 255.0f, 0.0f, 255.0f);
		this.commitColor();
	}

	/** Inclusive on both edges, so neighbouring boxes overlap by a pixel. */
	private static boolean hit(int mouseX, int mouseY, int x, int y, int boxWidth, int boxHeight) {
		return mouseX >= x && mouseX <= x + boxWidth && mouseY >= y && mouseY <= y + boxHeight;
	}

	private static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}

	private static int blend(int from, int to, float progress) {
		int fromR = from >> 16 & 0xFF;
		int fromG = from >> 8 & 0xFF;
		int fromB = from & 0xFF;
		int toR = to >> 16 & 0xFF;
		int toG = to >> 8 & 0xFF;
		int toB = to & 0xFF;
		return 0xFF000000
				| (int) (fromR + (float) (toR - fromR) * progress) << 16
				| (int) (fromG + (float) (toG - fromG) * progress) << 8
				| (int) (fromB + (float) (toB - fromB) * progress);
	}

	private void drawCenter(GuiGraphics graphics, String text, int x, int y, int boxWidth, int boxHeight, int color) {
		int textX = x + (boxWidth - ZenyaFont.width(this.font, text)) / 2;
		ZenyaFont.draw(graphics, this.font, text, textX, y + (boxHeight - this.font.lineHeight) / 2, color, false);
	}

	/** Which bar of the colour picker the held mouse button is scrubbing. */
	private enum DragMode {
		NONE,
		SV,
		HUE,
		ALPHA
	}
}
