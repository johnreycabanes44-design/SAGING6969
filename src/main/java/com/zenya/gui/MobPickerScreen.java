package com.zenya.gui;

import com.zenya.module.modules.render.MobESP;
import com.zenya.setting.MobsSetting;
import com.zenya.utils.ZenyaFont;
import com.zenya.utils.renderer.RenderUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Grid picker for a {@link MobsSetting}, with a per-mob HSV colour popup for {@link MobESP}.
 *
 * <p>Edits go to {@code tempSelected} / {@code tempColors} and are only pushed to the setting
 * and the module on Save, so Cancel and Escape can walk away without touching live state.
 *
 * <p>The layout fields (panelX, gridX, btnSaveX, ...) are recomputed every frame in
 * {@link #render} and read back by the mouse handlers, so hit testing always matches
 * what was last drawn.
 */
public class MobPickerScreen extends Screen {
	private static final int PANEL_W = 460;
	private static final int PANEL_H = 400;
	private static final int HEADER_H = 44;
	private static final int TAB_H = 32;
	private static final int SEARCH_H = 30;
	private static final int FOOTER_H = 48;
	private static final int GRID_PAD = 18;
	private static final int CELL_SIZE = 32;
	private static final int CELL_GAP = 6;
	private static final int SCROLL_W = 3;
	private static final int PICK_PAD = 10;
	private static final int PICK_SV_W = 160;
	private static final int PICK_SV_H = 110;
	private static final int PICK_HUE_W = 12;
	private static final int PICK_ALPHA_H = 10;
	private static final int PICK_PREV_H = 14;
	private static final int PICK_GAP = 8;
	private static final int PICK_TOTAL_W = 200;
	private static final int PICK_TOTAL_H = 170;
	private static final int C_PANEL_BG = -15329251;
	private static final int C_DIVIDER = -14407889;
	private static final int C_TAB_ACTIVE = -1096636;
	private static final int C_TAB_HOVER = 0x20FFFFFF;
	private static final int C_SEARCH_BG = -14802648;
	private static final int C_SEARCH_FOCUS = -1096636;
	private static final int C_CELL_HOVER = -14013131;
	private static final int C_CELL_SELECTED = -12969441;
	private static final int C_CELL_SEL_BORDER = -1096636;
	private static final int C_BTN_NEUTRAL_HV = -14407889;
	private static final int C_BTN_SAVE = -1096636;
	private static final int C_BTN_SAVE_HV = -2349530;
	private static final int C_TEXT = -1;
	private static final int C_TEXT_MUTED = -7696486;
	private static final int C_SCROLL = 0;
	private static final int C_SCROLL_THUMB = -12960443;
	private static final int C_POPUP_BG = -14802648;
	private static final int C_POPUP_BORDER = -14013131;
	private static final int C_KNOB = -1;
	private static final int C_CHECKER_A = -9538944;
	private static final int C_CHECKER_B = -6051659;
	private static final int C_TOOLTIP_BG = -233959400;
	private static final int C_TOOLTIP_BORDER = -14013131;

	/** Width of the alpha bar: it spans the SV square plus the gap plus the hue strip. */
	private static final int PICK_ALPHA_W = PICK_SV_W + PICK_GAP + PICK_HUE_W;

	private final MobsSetting setting;
	private final Screen parent;
	private final MobESP espModule;
	private final Set<EntityType<?>> tempSelected;
	private final Map<EntityType<?>, Color> tempColors;
	private boolean showSelected = false;
	private String searchQuery = "";
	private boolean searchFocused = false;
	private int scrollRow = 0;
	private EntityType<?> pickerMob = null;
	private int pickerRootX;
	private int pickerRootY;
	private float[] pickerHSV = new float[3];
	private int pickerAlpha = 255;
	private DragMode dragMode = DragMode.NONE;
	private int pSvX;
	private int pSvY;
	private int pHueX;
	private int pHueY;
	private int pAlphaX;
	private int pAlphaY;
	private int panelX;
	private int panelY;
	private int tabAllX;
	private int tabAllW;
	private int tabSelX;
	private int tabSelW;
	private int tabRowY;
	private int searchBarX;
	private int searchBarY;
	private int searchBarW;
	private int gridX;
	private int gridY;
	private int gridW;
	private int gridH;
	private int cols;
	private int visibleRows;
	private int btnY;
	private int btnH;
	private int btnClearX;
	private int btnCancelX;
	private int btnSaveX;
	private int btnBW;
	private List<EntityType<?>> displayedMobs = new ArrayList<>();
	private int hoverCell = -1;

	public MobPickerScreen(Screen parent, MobsSetting setting, MobESP espModule) {
		super(Component.literal("Mob Picker"));
		this.parent = parent;
		this.setting = setting;
		this.espModule = espModule;
		this.tempSelected = new LinkedHashSet<>(setting.getSelectedMobs());
		this.tempColors = new HashMap<>(espModule != null
				? espModule.getColorMap()
				: Map.<EntityType<?>, Color>of());
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** The panel paints its own opaque background, so vanilla must not dim or blur behind it. */
	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
	}

	@Override
	protected void renderBlurredBackground(GuiGraphics graphics) {
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		this.panelX = (this.width - PANEL_W) / 2;
		this.panelY = (this.height - PANEL_H) / 2;
		this.drawRoundedRect(graphics, this.panelX, this.panelY, PANEL_W, PANEL_H, 8, C_PANEL_BG);

		// Header: title on the left, "<n> mobs" (or "<visible>/<total>") on the right.
		int visibleSelected = this.getVisibleSelectedCount();
		String countText = this.showSelected && !this.searchQuery.isBlank()
				? visibleSelected + "/" + this.tempSelected.size()
				: String.valueOf(this.tempSelected.size());
		String countLabel = countText + (this.tempSelected.size() == 1 ? " mob" : " mobs");
		int headerTextY = this.panelY + (HEADER_H - this.font.lineHeight) / 2 + 1;
		ZenyaFont.draw(graphics, this.font, "Mob Picker", this.panelX + GRID_PAD, headerTextY, C_TEXT, false);
		int countWidth = ZenyaFont.width(this.font, countLabel);
		ZenyaFont.draw(graphics, this.font, countLabel, this.panelX + PANEL_W - GRID_PAD - countWidth,
				headerTextY, C_TEXT_MUTED, false);
		graphics.fill(this.panelX + GRID_PAD, this.panelY + HEADER_H, this.panelX + PANEL_W - GRID_PAD,
				this.panelY + HEADER_H + 1, C_DIVIDER);

		// Tab row.
		int rowY = this.panelY + HEADER_H + 8;
		this.tabRowY = rowY;
		String selectedTabLabel = "Selected (" + countText + ")";
		this.tabAllW = ZenyaFont.width(this.font, "All Mobs") + 16;
		this.tabSelW = ZenyaFont.width(this.font, selectedTabLabel) + 16;
		this.tabAllX = this.panelX + GRID_PAD;
		this.tabSelX = this.tabAllX + this.tabAllW + 8;
		boolean allTabHovered = this.hit(mouseX, mouseY, this.tabAllX, this.tabRowY, this.tabAllW, TAB_H);
		boolean selTabHovered = this.hit(mouseX, mouseY, this.tabSelX, this.tabRowY, this.tabSelW, TAB_H);
		if (allTabHovered && this.showSelected) {
			graphics.fill(this.tabAllX, this.tabRowY, this.tabAllX + this.tabAllW, this.tabRowY + TAB_H, C_TAB_HOVER);
		}
		if (selTabHovered && !this.showSelected) {
			graphics.fill(this.tabSelX, this.tabRowY, this.tabSelX + this.tabSelW, this.tabRowY + TAB_H, C_TAB_HOVER);
		}
		this.drawCenter(graphics, "All Mobs", this.tabAllX, this.tabRowY, this.tabAllW, TAB_H,
				!this.showSelected ? C_TEXT : C_TEXT_MUTED);
		this.drawCenter(graphics, selectedTabLabel, this.tabSelX, this.tabRowY, this.tabSelW, TAB_H,
				this.showSelected ? C_TEXT : C_TEXT_MUTED);
		int underlineY = this.tabRowY + TAB_H - 2;
		if (!this.showSelected) {
			graphics.fill(this.tabAllX + 4, underlineY, this.tabAllX + this.tabAllW - 4, underlineY + 2, C_TAB_ACTIVE);
		} else {
			graphics.fill(this.tabSelX + 4, underlineY, this.tabSelX + this.tabSelW - 4, underlineY + 2, C_TAB_ACTIVE);
		}

		// Search bar.
		this.searchBarX = this.panelX + GRID_PAD;
		rowY += 36;
		this.searchBarY = rowY;
		this.searchBarW = 424;
		this.drawRoundedRect(graphics, this.searchBarX, this.searchBarY, this.searchBarW, SEARCH_H, 4, C_SEARCH_BG);
		if (this.searchFocused) {
			graphics.fill(this.searchBarX, this.searchBarY + SEARCH_H - 2, this.searchBarX + this.searchBarW,
					this.searchBarY + SEARCH_H, C_SEARCH_FOCUS);
		}
		String searchText = this.searchQuery.isEmpty() ? "Search mobs..." : this.searchQuery;
		if (this.searchFocused && System.currentTimeMillis() / 500L % 2L == 0L) {
			searchText = searchText + "_";
		}
		ZenyaFont.draw(graphics, this.font, searchText, this.searchBarX + 10,
				this.searchBarY + (SEARCH_H - this.font.lineHeight) / 2 + 1,
				this.searchQuery.isEmpty() && !this.searchFocused ? C_TEXT_MUTED : C_TEXT, false);

		// Grid geometry.
		this.gridX = this.panelX + GRID_PAD;
		rowY += 38;
		this.gridY = rowY;
		int footerY = this.panelY + PANEL_H - FOOTER_H;
		this.gridW = 415;
		this.gridH = footerY - this.gridY - 8;
		this.cols = Math.max(1, (this.gridW + CELL_GAP) / (CELL_SIZE + CELL_GAP));
		this.visibleRows = Math.max(1, this.gridH / (CELL_SIZE + CELL_GAP));

		List<EntityType<?>> mobs;
		if (this.showSelected) {
			mobs = new ArrayList<>(this.tempSelected);
			if (!this.searchQuery.isEmpty()) {
				mobs.removeIf(type -> !this.matchesSearch(type, this.searchQuery));
			}
		} else {
			mobs = this.setting.filter(this.searchQuery);
		}
		this.displayedMobs = mobs;

		int totalRows = (this.displayedMobs.size() + this.cols - 1) / Math.max(1, this.cols);
		this.scrollRow = Math.max(0, Math.min(this.scrollRow, Math.max(0, totalRows - this.visibleRows)));
		this.hoverCell = -1;

		RenderUtil.setScissor(this.gridX, this.gridY, this.gridW + SCROLL_W + SCROLL_W, this.gridH, false);
		for (int row = 0; row < this.visibleRows; ++row) {
			for (int col = 0; col < this.cols; ++col) {
				int index = (this.scrollRow + row) * this.cols + col;
				if (index >= this.displayedMobs.size()) {
					break;
				}
				EntityType<?> type = this.displayedMobs.get(index);
				int cellX = this.gridX + col * (CELL_SIZE + CELL_GAP);
				int cellY = this.gridY + row * (CELL_SIZE + CELL_GAP);
				boolean selected = this.tempSelected.contains(type);
				boolean hovered = this.hit(mouseX, mouseY, cellX, cellY, CELL_SIZE, CELL_SIZE);
				if (hovered) {
					this.hoverCell = index;
				}
				if (selected) {
					int borderColor;
					int fillColor;
					Color custom = this.tempColors.get(type);
					if (custom != null) {
						// Custom colour: full brightness border over a quarter-brightness fill.
						int rgb = custom.getRGB() & 0xFFFFFF;
						borderColor = 0xFF000000 | rgb;
						int dimR = (rgb >> 16 & 0xFF) / 4;
						int dimG = (rgb >> 8 & 0xFF) / 4;
						int dimB = (rgb & 0xFF) / 4;
						fillColor = 0xFF000000 | dimR << 16 | dimG << 8 | dimB;
					} else {
						borderColor = C_CELL_SEL_BORDER;
						fillColor = C_CELL_SELECTED;
					}
					this.drawRoundedRect(graphics, cellX, cellY, CELL_SIZE, CELL_SIZE, 5, borderColor);
					this.drawRoundedRect(graphics, cellX + 2, cellY + 2, 28, 28, 3, fillColor);
				} else if (hovered) {
					this.drawRoundedRect(graphics, cellX, cellY, CELL_SIZE, CELL_SIZE, 5, C_CELL_HOVER);
				}
				ItemStack icon = this.getIconStack(type);
				if (!icon.isEmpty()) {
					graphics.renderItem(icon, cellX + 8, cellY + 8);
				}
				Color swatch = this.tempColors.get(type);
				if (swatch == null) {
					continue;
				}
				// Corner swatch showing the assigned ESP colour.
				int swatchX = cellX + CELL_SIZE - 5 - 1;
				int swatchY = cellY + CELL_SIZE - 5 - 1;
				graphics.fill(swatchX - 1, swatchY - 1, swatchX + 5 + 1, swatchY + 5 + 1, 0xFF000000);
				graphics.fill(swatchX, swatchY, swatchX + 5, swatchY + 5, 0xFF000000 | swatch.getRGB() & 0xFFFFFF);
			}
		}
		RenderUtil.clearScissor(false);

		if (totalRows > this.visibleRows) {
			float scrollBarX = this.gridX + this.gridW + SCROLL_W;
			float thumbH = Math.max(10.0f, (float) (this.gridH * this.visibleRows) / (float) totalRows);
			float thumbOffset = ((float) this.gridH - thumbH) * (float) this.scrollRow
					/ (float) Math.max(1, totalRows - this.visibleRows);
			RenderUtil.drawRoundedRect(graphics, scrollBarX, (float) this.gridY, SCROLL_W, (float) this.gridH,
					1.5f, C_SCROLL, false);
			RenderUtil.drawRoundedRect(graphics, scrollBarX, (float) this.gridY + thumbOffset, SCROLL_W, thumbH,
					1.5f, C_SCROLL_THUMB, false);
		}

		// Footer buttons.
		graphics.fill(this.panelX + GRID_PAD, footerY, this.panelX + PANEL_W - GRID_PAD, footerY + 1, C_DIVIDER);
		this.btnH = 28;
		this.btnBW = 72;
		this.btnY = footerY + (FOOTER_H - this.btnH) / 2;
		this.btnSaveX = this.panelX + PANEL_W - GRID_PAD - this.btnBW;
		this.btnCancelX = this.btnSaveX - 6 - this.btnBW;
		this.btnClearX = this.btnCancelX - 6 - this.btnBW;
		boolean clearHovered = this.hit(mouseX, mouseY, this.btnClearX, this.btnY, this.btnBW, this.btnH);
		boolean cancelHovered = this.hit(mouseX, mouseY, this.btnCancelX, this.btnY, this.btnBW, this.btnH);
		boolean saveHovered = this.hit(mouseX, mouseY, this.btnSaveX, this.btnY, this.btnBW, this.btnH);
		if (clearHovered) {
			this.drawRoundedRect(graphics, this.btnClearX, this.btnY, this.btnBW, this.btnH, 4, C_BTN_NEUTRAL_HV);
		}
		if (cancelHovered) {
			this.drawRoundedRect(graphics, this.btnCancelX, this.btnY, this.btnBW, this.btnH, 4, C_BTN_NEUTRAL_HV);
		}
		this.drawRoundedRect(graphics, this.btnSaveX, this.btnY, this.btnBW, this.btnH, 4,
				saveHovered ? C_BTN_SAVE_HV : C_BTN_SAVE);
		this.drawCenter(graphics, "Clear", this.btnClearX, this.btnY, this.btnBW, this.btnH, C_TEXT_MUTED);
		this.drawCenter(graphics, "Cancel", this.btnCancelX, this.btnY, this.btnBW, this.btnH, C_TEXT_MUTED);
		this.drawCenter(graphics, "Save", this.btnSaveX, this.btnY, this.btnBW, this.btnH, C_TEXT);

		super.render(graphics, mouseX, mouseY, partialTick);

		// The popup owns the cursor while it is open, so the cell tooltip stands down.
		if (this.pickerMob == null && this.hoverCell >= 0 && this.hoverCell < this.displayedMobs.size()) {
			this.drawTooltip(graphics, this.setting.getDisplayName(this.displayedMobs.get(this.hoverCell)),
					mouseX, mouseY);
		}
		if (this.pickerMob != null) {
			this.drawHsvPicker(graphics);
		}
	}

	private void drawTooltip(GuiGraphics graphics, String text, int mouseX, int mouseY) {
		int boxW = ZenyaFont.width(this.font, text) + 12;
		int boxH = this.font.lineHeight + 8;
		int boxX = Math.min(mouseX + 10, this.width - boxW - 2);
		int boxY = Math.min(mouseY + 10, this.height - boxH - 2);
		graphics.fill(boxX - 1, boxY - 1, boxX + boxW + 1, boxY + boxH + 1, C_TOOLTIP_BORDER);
		graphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, C_TOOLTIP_BG);
		ZenyaFont.draw(graphics, this.font, text, boxX + 6, boxY + 4, C_TEXT, false);
	}

	/** Spawn egg for the mob, falling back to a plain egg for types that have none. */
	private ItemStack getIconStack(EntityType<?> type) {
		SpawnEggItem egg = SpawnEggItem.byId(type);
		if (egg != null) {
			return new ItemStack(egg);
		}
		return new ItemStack(Items.EGG);
	}

	/**
	 * Draws the colour popup and, as a side effect, records the SV / hue / alpha widget
	 * origins that the drag handlers hit test against.
	 */
	private void drawHsvPicker(GuiGraphics graphics) {
		int popupX = Math.max(2, Math.min(this.pickerRootX, this.width - PICK_TOTAL_W - 2));
		int popupY = Math.max(2, Math.min(this.pickerRootY, this.height - PICK_TOTAL_H - 2));
		RenderUtil.drawRoundedRect(graphics, (float) popupX, (float) popupY, PICK_TOTAL_W, PICK_TOTAL_H,
				6.0f, C_POPUP_BG, false);
		RenderUtil.drawOutline(graphics, (float) popupX, (float) popupY, PICK_TOTAL_W, PICK_TOTAL_H,
				6.0f, 1.0f, C_POPUP_BORDER, false);

		int contentX = popupX + PICK_PAD;
		int contentY = popupY + PICK_PAD;
		this.pSvX = contentX;
		this.pSvY = contentY;

		// Saturation across, value down: one vertical gradient column per pixel.
		for (int x = 0; x < PICK_SV_W; ++x) {
			float saturation = (float) x / PICK_SV_W;
			int columnTop = 0xFF000000 | Color.HSBtoRGB(this.pickerHSV[0], saturation, 1.0f);
			graphics.fillGradient(this.pSvX + x, this.pSvY, this.pSvX + x + 1, this.pSvY + PICK_SV_H,
					columnTop, 0xFF000000);
		}
		int svCursorX = clamp(this.pSvX + (int) (this.pickerHSV[1] * PICK_SV_W), this.pSvX, this.pSvX + PICK_SV_W - 1);
		int svCursorY = clamp(this.pSvY + (int) ((1.0f - this.pickerHSV[2]) * PICK_SV_H), this.pSvY,
				this.pSvY + PICK_SV_H - 1);
		graphics.fill(svCursorX - 4, svCursorY - 1, svCursorX - 1, svCursorY + 2, 0xFF000000);
		graphics.fill(svCursorX + 2, svCursorY - 1, svCursorX + 5, svCursorY + 2, 0xFF000000);
		graphics.fill(svCursorX - 1, svCursorY - 4, svCursorX + 2, svCursorY - 1, 0xFF000000);
		graphics.fill(svCursorX - 1, svCursorY + 2, svCursorX + 2, svCursorY + 5, 0xFF000000);
		graphics.fill(svCursorX - 1, svCursorY - 1, svCursorX + 2, svCursorY + 2, C_KNOB);

		this.pHueX = contentX + PICK_SV_W + PICK_GAP;
		this.pHueY = contentY;
		for (int y = 0; y < PICK_SV_H; ++y) {
			float hue = (float) y / PICK_SV_H;
			graphics.fill(this.pHueX, this.pHueY + y, this.pHueX + PICK_HUE_W, this.pHueY + y + 1,
					0xFF000000 | Color.HSBtoRGB(hue, 1.0f, 1.0f));
		}
		int hueKnobY = clamp(this.pHueY + (int) (this.pickerHSV[0] * PICK_SV_H), this.pHueY, this.pHueY + PICK_SV_H - 1);
		graphics.fill(this.pHueX - 2, hueKnobY - 1, this.pHueX + PICK_HUE_W + 2, hueKnobY + 1, C_KNOB);

		this.pAlphaX = contentX;
		this.pAlphaY = contentY + PICK_SV_H + PICK_GAP;
		// Checkerboard so the alpha ramp reads against the popup background.
		for (int x = 0; x < PICK_ALPHA_W; x += 4) {
			for (int y = 0; y < PICK_ALPHA_H; y += 4) {
				int checker = (x / 4 + y / 4) % 2 == 0 ? C_CHECKER_A : C_CHECKER_B;
				graphics.fill(this.pAlphaX + x, this.pAlphaY + y,
						Math.min(this.pAlphaX + x + 4, this.pAlphaX + PICK_ALPHA_W),
						Math.min(this.pAlphaY + y + 4, this.pAlphaY + PICK_ALPHA_H), checker);
			}
		}
		int currentRgb = Color.HSBtoRGB(this.pickerHSV[0], this.pickerHSV[1], this.pickerHSV[2]) & 0xFFFFFF;
		for (int x = 0; x < PICK_ALPHA_W; ++x) {
			int alpha = (int) ((float) x / PICK_ALPHA_W * 255.0f);
			graphics.fill(this.pAlphaX + x, this.pAlphaY, this.pAlphaX + x + 1, this.pAlphaY + PICK_ALPHA_H,
					alpha << 24 | currentRgb);
		}
		int alphaKnobX = clamp(this.pAlphaX + (int) ((float) this.pickerAlpha / 255.0f * PICK_ALPHA_W),
				this.pAlphaX, this.pAlphaX + PICK_ALPHA_W - 1);
		graphics.fill(alphaKnobX - 1, this.pAlphaY - 2, alphaKnobX + 1, this.pAlphaY + PICK_ALPHA_H + 2, C_KNOB);

		int previewY = this.pAlphaY + PICK_ALPHA_H + PICK_GAP;
		graphics.fill(contentX, previewY, contentX + PICK_PREV_H, previewY + PICK_PREV_H, 0xFF000000 | currentRgb);
		String hex = String.format("#%02X%02X%02X%02X", this.pickerAlpha, currentRgb >> 16 & 0xFF,
				currentRgb >> 8 & 0xFF, currentRgb & 0xFF);
		ZenyaFont.draw(graphics, this.font, hex, contentX + PICK_PREV_H + 6,
				previewY + (PICK_PREV_H - this.font.lineHeight) / 2 + 1, C_TEXT, false);
	}

	private boolean isInsidePicker(int mouseX, int mouseY) {
		if (this.pickerMob == null) {
			return false;
		}
		int popupX = Math.min(this.pickerRootX, this.width - PICK_TOTAL_W - 2);
		int popupY = Math.min(this.pickerRootY, this.height - PICK_TOTAL_H - 2);
		return this.hit(mouseX, mouseY, Math.max(2, popupX), Math.max(2, popupY), PICK_TOTAL_W, PICK_TOTAL_H);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int mouseX = (int) event.x();
		int mouseY = (int) event.y();
		int button = event.button();

		// While the popup is open it swallows every click, inside or out.
		if (this.pickerMob != null) {
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
					this.commitPickerColor();
					this.pickerMob = null;
				}
				return true;
			}
			if (button == 1) {
				this.commitPickerColor();
				this.pickerMob = null;
				return true;
			}
			return true;
		}

		if (button == 0) {
			if (this.hit(mouseX, mouseY, this.tabAllX, this.tabRowY, this.tabAllW, TAB_H)) {
				this.showSelected = false;
				this.scrollRow = 0;
				return true;
			}
			if (this.hit(mouseX, mouseY, this.tabSelX, this.tabRowY, this.tabSelW, TAB_H)) {
				this.showSelected = true;
				this.scrollRow = 0;
				return true;
			}
			this.searchFocused = this.hit(mouseX, mouseY, this.searchBarX, this.searchBarY, this.searchBarW, SEARCH_H);
			if (this.hoverCell >= 0 && this.hoverCell < this.displayedMobs.size()) {
				EntityType<?> type = this.displayedMobs.get(this.hoverCell);
				if (this.tempSelected.contains(type)) {
					this.tempSelected.remove(type);
				} else {
					this.tempSelected.add(type);
				}
				return true;
			}
			if (this.hit(mouseX, mouseY, this.btnClearX, this.btnY, this.btnBW, this.btnH)) {
				this.tempSelected.clear();
				this.tempColors.clear();
				return true;
			}
			if (this.hit(mouseX, mouseY, this.btnCancelX, this.btnY, this.btnBW, this.btnH)) {
				this.dismiss();
				return true;
			}
			if (this.hit(mouseX, mouseY, this.btnSaveX, this.btnY, this.btnBW, this.btnH)) {
				this.save();
				return true;
			}
		} else if (button == 1 && this.hoverCell >= 0 && this.hoverCell < this.displayedMobs.size()) {
			this.openPickerForMob(this.displayedMobs.get(this.hoverCell), mouseX, mouseY);
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (this.pickerMob != null && event.button() == 0) {
			int mouseX = (int) event.x();
			int mouseY = (int) event.y();
			switch (this.dragMode) {
				case SV -> {
					this.applySvDrag(mouseX, mouseY);
					return true;
				}
				case HUE -> {
					this.applyHueDrag(mouseX, mouseY);
					return true;
				}
				case ALPHA -> {
					this.applyAlphaDrag(mouseX, mouseY);
					return true;
				}
				default -> {
				}
			}
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (this.dragMode != DragMode.NONE) {
			this.dragMode = DragMode.NONE;
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (this.pickerMob != null) {
			return true;
		}
		// render() clamps scrollRow against the row count, so no bounds check here.
		this.scrollRow -= (int) Math.signum(scrollY);
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (key == 256) { // Escape: close the popup first, then the screen.
			if (this.pickerMob != null) {
				this.pickerMob = null;
				return true;
			}
			this.dismiss();
			return true;
		}
		if (key == 257 || key == 335) { // Enter / numpad Enter.
			this.save();
			return true;
		}
		if (this.searchFocused && event.isPaste()) {
			this.searchQuery = this.searchQuery + this.getClipboardText();
			this.scrollRow = 0;
			return true;
		}
		if (this.searchFocused && key == 259 && !this.searchQuery.isEmpty()) { // Backspace.
			this.searchQuery = this.searchQuery.substring(0, this.searchQuery.length() - 1);
			this.scrollRow = 0;
			return true;
		}
		return super.keyPressed(event);
	}

	/** Clipboard contents with control characters stripped, so pasting cannot corrupt the query. */
	private String getClipboardText() {
		String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
		if (clipboard == null || clipboard.isEmpty()) {
			return "";
		}
		StringBuilder cleaned = new StringBuilder(clipboard.length());
		clipboard.codePoints().filter(codePoint -> !Character.isISOControl(codePoint)).forEach(cleaned::appendCodePoint);
		return cleaned.toString();
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (this.searchFocused) {
			this.searchQuery = this.searchQuery + Character.toString(event.codepoint());
			this.scrollRow = 0;
			return true;
		}
		return super.charTyped(event);
	}

	/** Opens the colour popup at the cursor, seeded with the mob's current colour. */
	private void openPickerForMob(EntityType<?> type, int mouseX, int mouseY) {
		this.pickerMob = type;
		this.pickerRootX = mouseX + 6;
		this.pickerRootY = mouseY + 6;
		this.dragMode = DragMode.NONE;
		Color current = this.tempColors.get(type);
		if (current != null) {
			Color.RGBtoHSB(current.getRed(), current.getGreen(), current.getBlue(), this.pickerHSV);
			this.pickerAlpha = current.getAlpha();
		} else {
			this.pickerHSV[0] = 0.0f;
			this.pickerHSV[1] = 1.0f;
			this.pickerHSV[2] = 1.0f;
			this.pickerAlpha = 255;
		}
	}

	private void commitPickerColor() {
		if (this.pickerMob == null) {
			return;
		}
		int rgb = Color.HSBtoRGB(this.pickerHSV[0], this.pickerHSV[1], this.pickerHSV[2]);
		this.tempColors.put(this.pickerMob, new Color(this.pickerAlpha << 24 | rgb & 0xFFFFFF, true));
	}

	private boolean hitSv(int mouseX, int mouseY) {
		return this.pickerMob != null && this.hit(mouseX, mouseY, this.pSvX, this.pSvY, PICK_SV_W, PICK_SV_H);
	}

	private boolean hitHue(int mouseX, int mouseY) {
		return this.pickerMob != null && this.hit(mouseX, mouseY, this.pHueX, this.pHueY, PICK_HUE_W, PICK_SV_H);
	}

	private boolean hitAlpha(int mouseX, int mouseY) {
		return this.pickerMob != null && this.hit(mouseX, mouseY, this.pAlphaX, this.pAlphaY, PICK_ALPHA_W, PICK_ALPHA_H);
	}

	private void applySvDrag(int mouseX, int mouseY) {
		this.pickerHSV[1] = clamp01((float) (mouseX - this.pSvX) / PICK_SV_W);
		this.pickerHSV[2] = clamp01(1.0f - (float) (mouseY - this.pSvY) / PICK_SV_H);
	}

	private void applyHueDrag(int mouseX, int mouseY) {
		this.pickerHSV[0] = clamp01((float) (mouseY - this.pHueY) / PICK_SV_H);
	}

	private void applyAlphaDrag(int mouseX, int mouseY) {
		this.pickerAlpha = (int) (clamp01((float) (mouseX - this.pAlphaX) / PICK_ALPHA_W) * 255.0f);
	}

	/** Writes the working copies back to the setting and the ESP module, then closes. */
	private void save() {
		if (this.pickerMob != null) {
			this.commitPickerColor();
		}
		this.setting.setValue(new LinkedHashSet<>(this.tempSelected));
		if (this.espModule != null) {
			// Push every available mob, so colours removed by Clear are cleared on the module too.
			for (EntityType<?> type : this.setting.getAvailableMobs()) {
				this.espModule.setCustomMobColor(type, this.tempColors.getOrDefault(type, null));
			}
		}
		this.dismiss();
	}

	private void dismiss() {
		this.minecraft.setScreen(this.parent);
	}

	/** Selected count as shown in the header: filtered by the search only on the Selected tab. */
	private int getVisibleSelectedCount() {
		if (!this.showSelected || this.searchQuery.isBlank()) {
			return this.tempSelected.size();
		}
		int count = 0;
		for (EntityType<?> type : this.tempSelected) {
			if (!this.matchesSearch(type, this.searchQuery)) {
				continue;
			}
			++count;
		}
		return count;
	}

	private boolean matchesSearch(EntityType<?> type, String query) {
		String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
		if (needle.isEmpty()) {
			return true;
		}
		return this.setting.getDisplayName(type).toLowerCase(Locale.ROOT).contains(needle);
	}

	private void drawCenter(GuiGraphics graphics, String text, int x, int y, int width, int height, int color) {
		int textX = x + (width - ZenyaFont.width(this.font, text)) / 2;
		ZenyaFont.draw(graphics, this.font, text, textX, y + (height - this.font.lineHeight) / 2 + 1, color, false);
	}

	private boolean hit(int mouseX, int mouseY, int x, int y, int width, int height) {
		return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
	}

	/** Filled rectangle with circular corners, drawn as a body plus one scanline per corner row. */
	private void drawRoundedRect(GuiGraphics graphics, int x, int y, int width, int height, int radius, int color) {
		int r = Math.min(radius, Math.min(width, height) / 2);
		graphics.fill(x, y + r, x + width, y + height - r, color);
		for (int i = 0; i < r; ++i) {
			int dy = r - i;
			int inset = (int) Math.ceil((double) r - Math.sqrt((double) r * (double) r - (double) (dy * dy)));
			int topY = y + i;
			int bottomY = y + height - i - 1;
			graphics.fill(x + inset, topY, x + width - inset, topY + 1, color);
			graphics.fill(x + inset, bottomY, x + width - inset, bottomY + 1, color);
		}
	}

	private static float clamp01(float value) {
		return value < 0.0f ? 0.0f : (value > 1.0f ? 1.0f : value);
	}

	private static int clamp(int value, int min, int max) {
		return value < min ? min : (value > max ? max : value);
	}

	/** Which popup widget the held mouse button is scrubbing. */
	private enum DragMode {
		NONE,
		SV,
		HUE,
		ALPHA
	}
}
