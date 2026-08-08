package com.zenya.gui;

import com.zenya.module.modules.client.ZenyaPlus;
import com.zenya.module.modules.render.BlockESP;
import com.zenya.setting.BlocksSetting;
import com.zenya.utils.ZenyaFont;
import com.zenya.utils.renderer.RenderUtil;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.lwjgl.glfw.GLFW;

/**
 * Draggable block browser for a {@link BlocksSetting}, with an embedded HSV picker for the
 * per-block BlockESP colour.
 * Edits are staged in {@link #tempSelected} / {@link #tempColors} and only pushed to the setting
 * and the module on Save, so Cancel (or Escape) leaves both untouched.
 * The hover indices are produced by {@link #render} and consumed by the next click, so clicks are
 * always resolved against the rows that were last drawn.
 */
public class BlockPickerScreen extends Screen {
	private static final int PANEL_W = 560;
	private static final int PANEL_H = 400;
	private static final int PANEL_R = 16;
	private static final int HEADER_H = 30;
	private static final int SEARCH_H = 28;
	private static final int FOOTER_H = 36;
	private static final int LEFT_W = 150;
	private static final int RIGHT_W = 130;
	private static final int PAD = 12;
	private static final int ROW_H = 20;
	private static final int C_BG = -16119286;
	private static final int C_PANEL = -15592942;
	private static final int C_BORDER = -14342875;
	private static final int C_TEXT = -1;
	private static final int C_MUTED = -7696486;
	private static final int C_ROW_HOVER = -15066598;
	private static final int C_ROW_SEL = -14540254;

	private final BlocksSetting setting;
	private final Screen parent;
	private final BlockESP espModule;
	private final Set<Block> tempSelected;
	private final Map<Block, Color> tempColors;
	private String searchQuery = "";
	private boolean searchFocused = false;
	private int centerScroll = 0;
	private int rightScroll = 0;
	private Block activeBlock = null;
	private int panelX;
	private int panelY;
	private boolean draggingPanel = false;
	private int dragOffX;
	private int dragOffY;
	private float[] pickerHSV = new float[3];
	private int pickerAlpha = 255;
	private int pickerX;
	private int pickerY;
	private boolean draggingPicker = false;
	private int pickerDragOffX;
	private int pickerDragOffY;
	private DragMode dragMode = DragMode.NONE;
	private int pSvX;
	private int pSvY;
	private int pHueX;
	private int pHueY;
	private int pAlphaX;
	private int pAlphaY;
	private float openAnim = 0.0f;
	private long openNano = 0L;
	private List<Block> filteredBlocks = new ArrayList<>();
	private int hoverCenter = -1;
	private int hoverRight = -1;

	public BlockPickerScreen(Screen parent, BlocksSetting setting, BlockESP espModule) {
		super(Component.literal("Block ESP"));
		this.parent = parent;
		this.setting = setting;
		this.espModule = espModule;
		this.tempSelected = new LinkedHashSet<>(setting.getSelectedBlocks());
		this.tempColors = new HashMap<>(espModule != null ? espModule.getColorMap() : Map.of());
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** The panel draws its own dim, so the vanilla background is suppressed. */
	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		long now = System.nanoTime();
		float deltaSeconds = this.openNano == 0L ? 0.016f : Math.min(0.1f, (float) (now - this.openNano) / 1.0E9f);
		this.openNano = now;
		this.openAnim += (1.0f - this.openAnim) * (1.0f - (float) Math.exp(-16.0f * deltaSeconds));

		if (this.panelX == 0 && this.panelY == 0) {
			this.panelX = (this.width - PANEL_W) / 2;
			this.panelY = (this.height - PANEL_H) / 2;
		}

		int accent = ZenyaPlus.getAccentARGB();
		// Open animation: scale up towards 1 while sliding up from 18px below.
		float openScale = 0.88f + 0.12f * this.openAnim;
		float openRise = (1.0f - this.openAnim) * 18.0f;
		int pivotX = this.panelX + PANEL_W / 2;
		int pivotY = this.panelY + PANEL_H / 2;
		graphics.pose().pushMatrix();
		graphics.pose().translate(pivotX, pivotY + openRise);
		graphics.pose().scale(openScale, openScale);
		graphics.pose().translate(-pivotX, -pivotY);

		graphics.fill(0, 0, this.width, this.height, 0x55000000);
		RenderUtil.drawRoundedRect(graphics, this.panelX, this.panelY, PANEL_W, PANEL_H, PANEL_R, C_PANEL, false);
		RenderUtil.drawOutline(graphics, this.panelX, this.panelY, PANEL_W, PANEL_H, PANEL_R, 1.0f, C_BORDER, false);
		ZenyaFont.draw(graphics, this.font, "Block ESP", this.panelX + PAD, this.panelY + 10, C_TEXT, false);

		String countLabel = this.tempSelected.size() + " selected";
		ZenyaFont.draw(graphics, this.font, countLabel, this.panelX + PANEL_W - PAD - ZenyaFont.width(this.font, countLabel), this.panelY + 10, C_MUTED, false);
		graphics.fill(this.panelX + PAD, this.panelY + HEADER_H, this.panelX + PANEL_W - PAD, this.panelY + HEADER_H + 1, C_BORDER);

		int searchY = this.panelY + HEADER_H + 8;
		int searchX = this.panelX + PAD;
		RenderUtil.drawRoundedRect(graphics, searchX, searchY, 536.0f, SEARCH_H, 10.0f, -15790321, false);
		RenderUtil.drawOutline(graphics, searchX, searchY, 536.0f, SEARCH_H, 10.0f, 1.0f, this.searchFocused ? accent : C_BORDER, false);

		String searchText = this.searchQuery.isEmpty() ? "Search blocks..." : this.searchQuery;
		// Caret blinks at 1Hz while the field has focus.
		if (this.searchFocused && System.currentTimeMillis() / 500L % 2L == 0L) {
			searchText = searchText + "_";
		}
		ZenyaFont.draw(graphics, this.font, searchText, searchX + 10, searchY + 8, this.searchQuery.isEmpty() && !this.searchFocused ? C_MUTED : C_TEXT, false);

		int listY = searchY + SEARCH_H + 10;
		int footerY = this.panelY + PANEL_H - FOOTER_H;
		int listH = footerY - listY - 8;
		int centerColX = this.panelX + PAD + LEFT_W + 8;
		int rightColX = centerColX + 240 + 8;

		RenderUtil.drawRoundedRect(graphics, this.panelX + PAD, listY, LEFT_W, listH, 10.0f, -16250872, false);
		ZenyaFont.draw(graphics, this.font, "Color", this.panelX + PAD + 8, listY + 6, C_MUTED, false);

		if (this.activeBlock != null) {
			this.drawEmbeddedPicker(graphics, mouseX, mouseY, this.panelX + PAD + 6, listY + 22, 138, listH - 28);
		} else {
			ZenyaFont.draw(graphics, this.font, "Select a block", this.panelX + PAD + 8, listY + 40, C_MUTED, false);
		}

		RenderUtil.drawRoundedRect(graphics, centerColX, listY, 240.0f, listH, 10.0f, -16250872, false);
		ZenyaFont.draw(graphics, this.font, "All Blocks", centerColX + 8, listY + 6, C_MUTED, false);

		this.filteredBlocks = this.setting.filter(this.searchQuery);

		int rowsTop = listY + 22;
		int centerRows = Math.max(1, (listH - 26) / ROW_H);
		this.centerScroll = Math.max(0, Math.min(this.centerScroll, Math.max(0, this.filteredBlocks.size() - centerRows)));
		this.hoverCenter = -1;
		RenderUtil.setScissor(centerColX + 4, rowsTop, 232.0f, listH - 28, false);

		for (int row = 0; row < centerRows; ++row) {
			int index = this.centerScroll + row;

			if (index >= this.filteredBlocks.size()) {
				break;
			}

			Block block = this.filteredBlocks.get(index);
			int rowY = rowsTop + row * ROW_H;
			boolean hovered = hit(mouseX, mouseY, centerColX + 4, rowY, 232, 18);
			boolean selected = this.tempSelected.contains(block);
			boolean active = block.equals(this.activeBlock);

			if (hovered) {
				this.hoverCenter = index;
			}

			if (selected || active) {
				RenderUtil.drawRoundedRect(graphics, centerColX + 4, rowY, 232.0f, 18.0f, 6.0f, active ? blend(C_ROW_SEL, accent, 0.25f) : C_ROW_SEL, false);
			} else if (hovered) {
				RenderUtil.drawRoundedRect(graphics, centerColX + 4, rowY, 232.0f, 18.0f, 6.0f, C_ROW_HOVER, false);
			}

			String label = trimLabel(this.setting.getDisplayName(block), 206);
			RenderUtil.drawRoundedRect(graphics, centerColX + 10, rowY + 2, 16.0f, 16.0f, 4.0f, -15395563, false);
			graphics.renderItem(new ItemStack(block), centerColX + 10, rowY + 2);
			ZenyaFont.draw(graphics, this.font, label, centerColX + 30, rowY + 5, selected ? C_TEXT : C_MUTED, false);
		}

		RenderUtil.clearScissor(false);
		RenderUtil.drawRoundedRect(graphics, rightColX, listY, RIGHT_W, listH, 10.0f, -16250872, false);
		ZenyaFont.draw(graphics, this.font, "Picked", rightColX + 8, listY + 6, C_MUTED, false);

		List<Block> picked = new ArrayList<>(this.tempSelected);
		int pickedRows = Math.max(1, (listH - 26) / ROW_H);
		this.rightScroll = Math.max(0, Math.min(this.rightScroll, Math.max(0, picked.size() - pickedRows)));
		this.hoverRight = -1;
		RenderUtil.setScissor(rightColX + 4, rowsTop, 122.0f, listH - 28, false);

		for (int row = 0; row < pickedRows; ++row) {
			int index = this.rightScroll + row;

			if (index >= picked.size()) {
				break;
			}

			Block block = picked.get(index);
			int rowY = rowsTop + row * ROW_H;
			boolean hovered = hit(mouseX, mouseY, rightColX + 4, rowY, 122, 18);

			if (hovered) {
				this.hoverRight = index;
			}

			if (block.equals(this.activeBlock)) {
				RenderUtil.drawRoundedRect(graphics, rightColX + 4, rowY, 122.0f, 18.0f, 6.0f, blend(C_ROW_SEL, accent, 0.25f), false);
			} else if (hovered) {
				RenderUtil.drawRoundedRect(graphics, rightColX + 4, rowY, 122.0f, 18.0f, 6.0f, C_ROW_HOVER, false);
			}

			String label = trimLabel(this.setting.getDisplayName(block), 96);
			RenderUtil.drawRoundedRect(graphics, rightColX + 10, rowY + 2, 16.0f, 16.0f, 4.0f, -15395563, false);
			graphics.renderItem(new ItemStack(block), rightColX + 10, rowY + 2);
			ZenyaFont.draw(graphics, this.font, label, rightColX + 30, rowY + 5, C_TEXT, false);
		}

		RenderUtil.clearScissor(false);
		graphics.fill(this.panelX + PAD, footerY, this.panelX + PANEL_W - PAD, footerY + 1, C_BORDER);

		int buttonY = footerY + 5;
		int saveX = this.panelX + PAD;
		int cancelX = this.panelX + PANEL_W - PAD - 72;
		boolean saveHovered = hit(mouseX, mouseY, saveX, buttonY, 72, 26);
		boolean cancelHovered = hit(mouseX, mouseY, cancelX, buttonY, 72, 26);
		RenderUtil.drawRoundedRect(graphics, saveX, buttonY, 72.0f, 26.0f, 8.0f, saveHovered ? -15395563 : -16777216, false);
		RenderUtil.drawOutline(graphics, saveX, buttonY, 72.0f, 26.0f, 8.0f, 1.0f, accent, false);
		RenderUtil.drawRoundedRect(graphics, cancelX, buttonY, 72.0f, 26.0f, 8.0f, cancelHovered ? C_ROW_HOVER : -15658735, false);
		this.drawCenter(graphics, "Save", saveX, buttonY, 72, 26, C_TEXT);
		this.drawCenter(graphics, "Cancel", cancelX, buttonY, 72, 26, C_MUTED);

		graphics.pose().popMatrix();
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	/**
	 * Draws the floating HSV picker inside the given area, clamped so it cannot leave it.
	 * The SV/hue/alpha strip origins are cached in the pSv/pHue/pAlpha fields because the hit
	 * tests in {@link #mouseClicked} need the positions from the last frame.
	 */
	private void drawEmbeddedPicker(GuiGraphics graphics, int mouseX, int mouseY, int areaX, int areaY, int areaW, int areaH) {
		if (this.pickerX == 0 && this.pickerY == 0) {
			this.pickerX = areaX;
			this.pickerY = areaY;
		}

		this.pickerX = clamp(this.pickerX, areaX, areaX + areaW - 120);
		this.pickerY = clamp(this.pickerY, areaY, areaY + areaH - 130);
		RenderUtil.drawRoundedRect(graphics, this.pickerX, this.pickerY, 120.0f, 128.0f, 10.0f, -16777216, false);
		RenderUtil.drawOutline(graphics, this.pickerX, this.pickerY, 120.0f, 128.0f, 10.0f, 1.0f, C_BORDER, false);

		int contentX = this.pickerX + 6;
		int contentY = this.pickerY + 16;
		this.pSvX = contentX;
		this.pSvY = contentY;

		// Saturation/value field: one gradient column per pixel of saturation.
		for (int column = 0; column < 78; ++column) {
			int columnColor = 0xFF000000 | Color.HSBtoRGB(this.pickerHSV[0], (float) column / 78.0f, 1.0f);
			graphics.fillGradient(this.pSvX + column, this.pSvY, this.pSvX + column + 1, this.pSvY + 58, columnColor, -16777216);
		}

		int cursorX = clamp(this.pSvX + (int) (this.pickerHSV[1] * 78.0f), this.pSvX, this.pSvX + 78 - 1);
		int cursorY = clamp(this.pSvY + (int) ((1.0f - this.pickerHSV[2]) * 58.0f), this.pSvY, this.pSvY + 58 - 1);
		graphics.fill(cursorX - 1, cursorY - 1, cursorX + 2, cursorY + 2, C_TEXT);

		this.pHueX = contentX + 78 + 4;
		this.pHueY = contentY;

		for (int row = 0; row < 58; ++row) {
			int hueColor = 0xFF000000 | Color.HSBtoRGB((float) row / 58.0f, 1.0f, 1.0f);
			graphics.fill(this.pHueX, this.pHueY + row, this.pHueX + 10, this.pHueY + row + 1, hueColor);
		}

		this.pAlphaX = contentX;
		this.pAlphaY = contentY + 58 + 6;

		int rgb = Color.HSBtoRGB(this.pickerHSV[0], this.pickerHSV[1], this.pickerHSV[2]) & 0xFFFFFF;

		for (int column = 0; column < 92; ++column) {
			int alpha = (int) ((float) column / 92.0f * 255.0f);
			graphics.fill(this.pAlphaX + column, this.pAlphaY, this.pAlphaX + column + 1, this.pAlphaY + 8, alpha << 24 | rgb);
		}

		int swatchY = this.pAlphaY + 8 + 6;
		graphics.fill(contentX, swatchY, contentX + 14, swatchY + 10, 0xFF000000 | rgb);
		ZenyaFont.draw(graphics, this.font, trimLabel(this.setting.getDisplayName(this.activeBlock), 60), contentX + 18, swatchY + 1, C_MUTED, false);
		// Drag handle along the top of the picker.
		RenderUtil.drawRoundedRect(graphics, this.pickerX + 4, this.pickerY + 4, 112.0f, 8.0f, 4.0f, -15395563, false);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int mouseX = (int) event.x();
		int mouseY = (int) event.y();
		int button = event.button();

		if (button == 0) {
			if (hit(mouseX, mouseY, this.panelX, this.panelY, PANEL_W, HEADER_H)) {
				this.draggingPanel = true;
				this.dragOffX = mouseX - this.panelX;
				this.dragOffY = mouseY - this.panelY;
				return true;
			}

			if (this.activeBlock != null && hit(mouseX, mouseY, this.pickerX + 4, this.pickerY + 4, 112, 8)) {
				this.draggingPicker = true;
				this.pickerDragOffX = mouseX - this.pickerX;
				this.pickerDragOffY = mouseY - this.pickerY;
				return true;
			}

			if (this.activeBlock != null) {
				if (this.hitSv(mouseX, mouseY)) {
					this.dragMode = DragMode.SV;
					this.applySvDrag(mouseX, mouseY);
					this.commitColor();
					return true;
				}

				if (this.hitHue(mouseX, mouseY)) {
					this.dragMode = DragMode.HUE;
					this.applyHueDrag(mouseX, mouseY);
					this.commitColor();
					return true;
				}

				if (this.hitAlpha(mouseX, mouseY)) {
					this.dragMode = DragMode.ALPHA;
					this.applyAlphaDrag(mouseX, mouseY);
					this.commitColor();
					return true;
				}
			}

			this.searchFocused = hit(mouseX, mouseY, this.panelX + PAD, this.panelY + HEADER_H + 8, 536, SEARCH_H);

			int buttonY = this.panelY + PANEL_H - FOOTER_H + 5;

			if (hit(mouseX, mouseY, this.panelX + PAD, buttonY, 72, 26)) {
				this.save();
				return true;
			}

			if (hit(mouseX, mouseY, this.panelX + PANEL_W - PAD - 72, buttonY, 72, 26)) {
				this.dismiss();
				return true;
			}

			if (this.hoverCenter >= 0 && this.hoverCenter < this.filteredBlocks.size()) {
				Block block = this.filteredBlocks.get(this.hoverCenter);

				// ponytail: the button == 1 branches below are unreachable, the whole body is
				// already guarded by button == 0, so right click never removes a block.
				if (button == 0) {
					if (this.tempSelected.contains(block)) {
						this.setActiveBlock(block);
					} else {
						this.tempSelected.add(block);
						this.setActiveBlock(block);
					}
				} else if (button == 1) {
					this.tempSelected.remove(block);
				}

				return true;
			}

			if (this.hoverRight >= 0) {
				List<Block> picked = new ArrayList<>(this.tempSelected);

				if (this.hoverRight < picked.size()) {
					Block block = picked.get(this.hoverRight);

					if (button == 0) {
						this.setActiveBlock(block);
					} else if (button == 1) {
						this.tempSelected.remove(block);
					}
				}

				return true;
			}
		}

		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		int mouseX = (int) event.x();
		int mouseY = (int) event.y();

		if (this.draggingPanel) {
			this.panelX = mouseX - this.dragOffX;
			this.panelY = mouseY - this.dragOffY;
			return true;
		}

		if (this.draggingPicker) {
			this.pickerX = mouseX - this.pickerDragOffX;
			this.pickerY = mouseY - this.pickerDragOffY;
			return true;
		}

		if (this.dragMode != DragMode.NONE) {
			switch (this.dragMode) {
				case SV -> this.applySvDrag(mouseX, mouseY);
				case HUE -> this.applyHueDrag(mouseX, mouseY);
				case ALPHA -> this.applyAlphaDrag(mouseX, mouseY);
				default -> {
				}
			}

			this.commitColor();
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
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int listY = this.panelY + HEADER_H + 8 + SEARCH_H + 10;
		int centerColX = this.panelX + PAD + LEFT_W + 8;
		int rightColX = centerColX + 240 + 8;

		if (hit((int) mouseX, (int) mouseY, centerColX, listY, 240, this.panelY + PANEL_H - FOOTER_H - listY - 8)) {
			this.centerScroll -= (int) Math.signum(scrollY);
			return true;
		}

		if (hit((int) mouseX, (int) mouseY, rightColX, listY, RIGHT_W, this.panelY + PANEL_H - FOOTER_H - listY - 8)) {
			this.rightScroll -= (int) Math.signum(scrollY);
			return true;
		}

		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
			this.dismiss();
			return true;
		}

		if (event.key() == GLFW.GLFW_KEY_ENTER) {
			this.save();
			return true;
		}

		if (this.searchFocused && event.isPaste()) {
			this.searchQuery = this.searchQuery + this.getClipboardText();
			this.centerScroll = 0;
			return true;
		}

		if (this.searchFocused && event.key() == GLFW.GLFW_KEY_BACKSPACE && !this.searchQuery.isEmpty()) {
			this.searchQuery = this.searchQuery.substring(0, this.searchQuery.length() - 1);
			this.centerScroll = 0;
			return true;
		}

		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (this.searchFocused) {
			this.searchQuery = this.searchQuery + Character.toString(event.codepoint());
			this.centerScroll = 0;
			return true;
		}

		return super.charTyped(event);
	}

	/** Selects a block for editing and seeds the picker from its staged (or setting) colour. */
	private void setActiveBlock(Block block) {
		this.activeBlock = block;
		Color color = this.tempColors.getOrDefault(block, this.setting.getColor(block));
		this.tempColors.putIfAbsent(block, color);
		this.pickerHSV = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
		this.pickerAlpha = color.getAlpha();
	}

	/** Writes the current picker HSV plus alpha back onto the active block's staged colour. */
	private void commitColor() {
		if (this.activeBlock == null) {
			return;
		}

		int rgb = Color.HSBtoRGB(this.pickerHSV[0], this.pickerHSV[1], this.pickerHSV[2]) & 0xFFFFFF;
		this.tempColors.put(this.activeBlock, new Color(this.pickerAlpha << 24 | rgb, true));
	}

	/** Pushes the staged selection and colours to the setting and the module, then closes. */
	private void save() {
		this.commitColor();
		this.setting.setValue(new LinkedHashSet<>(this.tempSelected));

		if (this.espModule != null) {
			for (Map.Entry<Block, Color> entry : this.tempColors.entrySet()) {
				this.espModule.setCustomBlockColor(entry.getKey(), entry.getValue());
			}
		}

		this.dismiss();
	}

	private void dismiss() {
		this.minecraft.setScreen(this.parent);
	}

	/** Clipboard text with control characters stripped, so pasting cannot corrupt the query. */
	private String getClipboardText() {
		String clipboard = this.minecraft.keyboardHandler.getClipboard();

		if (clipboard == null) {
			return "";
		}

		StringBuilder cleaned = new StringBuilder();
		clipboard.codePoints().filter(codePoint -> !Character.isISOControl(codePoint)).forEach(cleaned::appendCodePoint);
		return cleaned.toString();
	}

	private boolean hitSv(int mouseX, int mouseY) {
		return hit(mouseX, mouseY, this.pSvX, this.pSvY, 78, 58);
	}

	private boolean hitHue(int mouseX, int mouseY) {
		return hit(mouseX, mouseY, this.pHueX, this.pHueY, 10, 58);
	}

	private boolean hitAlpha(int mouseX, int mouseY) {
		return hit(mouseX, mouseY, this.pAlphaX, this.pAlphaY, 92, 8);
	}

	private void applySvDrag(int mouseX, int mouseY) {
		this.pickerHSV[1] = clamp01((float) (mouseX - this.pSvX) / 78.0f);
		this.pickerHSV[2] = clamp01(1.0f - (float) (mouseY - this.pSvY) / 58.0f);
	}

	private void applyHueDrag(int mouseX, int mouseY) {
		this.pickerHSV[0] = clamp01((float) (mouseY - this.pHueY) / 58.0f);
	}

	private void applyAlphaDrag(int mouseX, int mouseY) {
		this.pickerAlpha = (int) (clamp01((float) (mouseX - this.pAlphaX) / 92.0f) * 255.0f);
	}

	// ponytail: maxWidth is ignored, every label is cut at a fixed 18 characters instead.
	private static String trimLabel(String label, int maxWidth) {
		if (label == null) {
			return "";
		}

		if (label.length() > 18) {
			label = label.substring(0, 17) + "\u2026";
		}

		return label;
	}

	/** Draws text centred inside the given box. */
	private void drawCenter(GuiGraphics graphics, String text, int x, int y, int width, int height, int color) {
		int textX = x + (width - ZenyaFont.width(this.font, text)) / 2;
		ZenyaFont.draw(graphics, this.font, text, textX, y + (height - this.font.lineHeight) / 2, color, false);
	}

	/** Inclusive on both edges, so a click on the far border still counts as a hit. */
	private static boolean hit(int mouseX, int mouseY, int x, int y, int width, int height) {
		return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static float clamp01(float value) {
		return value < 0.0f ? 0.0f : Math.min(1.0f, value);
	}

	/** Lerps the RGB channels of two ARGB colours, always returning full alpha. */
	private static int blend(int from, int to, float progress) {
		int fromR = from >> 16 & 0xFF;
		int fromG = from >> 8 & 0xFF;
		int fromB = from & 0xFF;
		int toR = to >> 16 & 0xFF;
		int toG = to >> 8 & 0xFF;
		int toB = to & 0xFF;
		return 0xFF000000
				| (int) ((float) fromR + (float) (toR - fromR) * progress) << 16
				| (int) ((float) fromG + (float) (toG - fromG) * progress) << 8
				| (int) ((float) fromB + (float) (toB - fromB) * progress);
	}

	/** Which strip of the embedded picker a held mouse button is scrubbing. */
	private enum DragMode {
		NONE,
		SV,
		HUE,
		ALPHA
	}
}
