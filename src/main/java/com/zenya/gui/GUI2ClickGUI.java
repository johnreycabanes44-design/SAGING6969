package com.zenya.gui;

import com.zenya.ZenyaClient;
import com.zenya.module.ActivatableModule;
import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.module.ModuleManager;
import com.zenya.module.modules.client.Friends;
import com.zenya.module.modules.client.Themes;
import com.zenya.module.modules.client.ZenyaPlus;
import com.zenya.module.modules.render.BlockESP;
import com.zenya.module.modules.render.MobESP;
import com.zenya.setting.ActionSetting;
import com.zenya.setting.BlocksSetting;
import com.zenya.setting.ConfirmBooleanSetting;
import com.zenya.setting.MobsSetting;
import com.zenya.setting.ModeSetting;
import com.zenya.setting.OptionEntry;
import com.zenya.setting.OptionMultiSelectSetting;
import com.zenya.setting.OptionSelectSetting;
import com.zenya.setting.SectionSetting;
import com.zenya.setting.Setting;
import com.zenya.setting.StorageBlocksSetting;
import com.zenya.setting.ThresholdSetting;
import com.zenya.utils.ZenyaFont;
import com.zenya.utils.renderer.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The floating-panel layout: one draggable panel per category, each listing its
 * modules and, when a module is expanded, that module's settings.
 *
 * <p>Panel origins, GUI scale and the search/scale bar geometry live in statics
 * so they survive the screen closing; everything else is rebuilt every frame.
 * Clicks are resolved against the hit rectangles collected during the last
 * render, walked back-to-front, so a click only ever hits what was drawn.
 */
public class GUI2ClickGUI extends Screen {
	private static final int PANEL_W = 200;
	private static final int HEADER_H = 36;
	private static final int MOD_H = 26;
	private static final int SET_H = 22;
	private static final int SLIDER_H = 30;
	private static final int STORAGE_GRID_COLS = 6;
	private static final int STORAGE_CELL_H = 28;
	private static final int BLOCKS_GRID_COLS = 5;
	private static final int BLOCKS_GRID_CELL_H = 24;
	private static final int BLOCKS_GRID_MAX = 200;
	private static final int LIST_MAX = 120;
	/** So large that panel bodies are never actually clipped by height. */
	private static final int MAX_VIS_H = 0x1FFFFFFF;
	private static final int RADIUS = 18;
	private static final int SEARCH_W = 230;
	private static final int SEARCH_H = 30;
	private static final int TGL_W = 32;
	private static final int TGL_H = 16;
	private static final int C_DIV = -14013910;
	private static final int C_TEXT = -1118482;
	private static final int C_MUTED = -7829368;
	private static final int C_DIM = -11513776;
	private static final int C_SUB = -15329770;
	private static final int BASE_PANEL = -14802128;
	private static final int BASE_HEADER = -14538953;
	private static final Category[] ORDER = new Category[]{Category.COMBAT, Category.DONUT, Category.SMPS, Category.MISC, Category.RENDER};
	/** Panel index of the catch-all panel that holds {@link Category#CLIENT}. */
	private static final int OTHER_IDX = ORDER.length;
	private static final int N = ORDER.length + 1;
	private static final int SCALE_W = 160;
	private static final int SCALE_H = 28;
	private static final int CP_PAD = 6;
	private static final int CP_SV_H = 68;
	private static final int CP_BAR_H = 10;
	private static final int CP_GAP = 6;
	private static final int CP_PREV_H = 18;
	private static final int CP_TOTAL = 136;

	private static int curPanel = BASE_PANEL;
	private static int curHeader = BASE_HEADER;
	/** Accent colour eased per frame, kept as RGB so it can be interpolated. */
	private static final float[] curAccRgb = new float[]{239.0f, 68.0f, 68.0f};
	private static final float[] curTintRgb = new float[]{0.0f, 0.0f, 0.0f};
	private static int curAcc = -1096636;
	private static int curAccDim = -6743269;
	private static int curAccFaint = 871318596;
	private static int curOn = -15063752;
	private static int curSubSel = -14799552;
	/** Panel origins survive the screen closing, so they are static. */
	private static final int[] SPX = new int[N];
	private static final int[] SPY = new int[N];
	private static boolean posInit = false;
	private static float guiScale = 0.65f;
	private static int scaleBarOX = 0;
	private static int scaleBarOY = 0;
	private static boolean scaleCollapsed = false;
	private static int searchOX = 0;
	private static int searchOY = 0;
	private static int searchDynW = SEARCH_W;
	private static int searchDynH = SEARCH_H;
	private static boolean themesOpen = false;
	private static int themesScroll = 0;

	private int dynSrchBg = -300279270;
	private int dynSrchBd = C_DIV;
	private int dynTrack = -13224394;
	private int dynHover = -14342875;
	private final int[] px = new int[N];
	private final int[] py = new int[N];
	private final int[] scroll = new int[N];
	private final int[] maxSc = new int[N];
	private final boolean[] collapsed = new boolean[N];
	private boolean scaleDragging = false;
	private boolean scaleBarDragging = false;
	private int scaleBarDragStartX;
	private int scaleBarDragStartY;
	private final int[] resizeBtnBounds = new int[4];
	private int dragPanel = -1;
	private int dragOx;
	private int dragOy;
	private final Map<Module, Boolean> expanded = new HashMap<>();
	private Setting<?> openListSetting = null;
	private String listSearchBuf = "";
	private boolean listSearchFocus = false;
	private Setting<Color> openColor = null;
	private final float[] cHSV = new float[3];
	private int cAlpha = 255;
	private CDrag cDrag = CDrag.NONE;
	private int cSvX;
	private int cSvY;
	private int cSvW;
	private int cSvH;
	private int cHueX;
	private int cHueY;
	private int cHueW;
	private int cHueH;
	private int cAlX;
	private int cAlY;
	private int cAlW;
	private int cAlH;
	private Setting<?> strFocus = null;
	private String strBuf = "";
	private Module bindListening = null;
	private Setting<Integer> bindListeningSetting = null;
	private Setting<?> sliderDrag = null;
	private int slTrkX;
	private int slTrkW;
	private String search = "";
	private boolean searchFocus = false;
	private final int[] searchBounds = new int[4];
	private final int[] searchGrip = new int[4];
	private final int[] searchResizer = new int[4];
	private boolean searchDragging = false;
	private boolean searchResizing = false;
	private int searchDragStartX;
	private int searchDragStartY;
	private int searchResizeStartX;
	private int searchResizeStartW;
	private int searchResizeStartY;
	private int searchResizeStartH;
	private final int[] themeBtnBounds = new int[4];
	private final int[] themesOverlayBounds = new int[4];
	private final List<int[]> themesHitR = new ArrayList<>();
	private final List<Runnable> themesHitA = new ArrayList<>();
	private final Map<Object, float[]> anims = new HashMap<>();
	private final List<int[]> hitR = new ArrayList<>();
	private final List<HitTarget> hitH = new ArrayList<>();
	private final List<Object> hitExtra = new ArrayList<>();
	private float openT = 0.0f;
	private long lastNs = 0L;
	private final int[] scaleBarBounds = new int[4];
	private Module hoveredModule = null;

	public GUI2ClickGUI() {
		super(Component.literal("Frost Client"));
	}

	/** Eases the cached theme colours towards the active theme by one frame. */
	private void refreshTheme(float dt) {
		Themes themes = Themes.getInstance();
		int accentArgb = -1096636;
		int tintArgb = 0;
		boolean rainbow = false;
		if (themes != null) {
			Themes.Theme theme = Themes.currentTheme();
			if (!theme.name().equalsIgnoreCase("Dark")) {
				accentArgb = theme.accentArgb();
				tintArgb = theme.palette()[0];
			}
			rainbow = "Rainbow".equalsIgnoreCase(themes.selectedSetting().getValue());
		}

		float[] targetAcc = new float[]{accentArgb >> 16 & 0xFF, accentArgb >> 8 & 0xFF, accentArgb & 0xFF};
		if (rainbow) {
			// Rainbow already moves every frame, so easing towards it would only smear the hue.
			curAccRgb[0] = targetAcc[0];
			curAccRgb[1] = targetAcc[1];
			curAccRgb[2] = targetAcc[2];
		} else {
			for (int i = 0; i < 3; ++i) {
				curAccRgb[i] = exp(curAccRgb[i], targetAcc[i], dt, 9.0f);
			}
		}
		int accR = (int) curAccRgb[0];
		int accG = (int) curAccRgb[1];
		int accB = (int) curAccRgb[2];
		curAcc = 0xFF000000 | accR << 16 | accG << 8 | accB;
		curAccDim = darken(curAcc, 0.45f);
		curAccFaint = 0x33000000 | curAcc & 0xFFFFFF;
		curOn = blend(curPanel, curAcc, 0.18f);
		curSubSel = blend(curPanel, curAcc, 0.14f);

		// ponytail: curTintRgb is eased towards the theme tint but never read - the
		// panel colour below is hard-coded, so the tint has no effect.
		float[] targetTint = new float[]{tintArgb >> 16 & 0xFF, tintArgb >> 8 & 0xFF, tintArgb & 0xFF};
		for (int i = 0; i < 3; ++i) {
			curTintRgb[i] = exp(curTintRgb[i], targetTint[i], dt, 9.0f);
		}

		curPanel = 0xFF111111;
		curHeader = 0xFF111111;
		this.dynSrchBg = 0xEE000000 | darken(curPanel, 0.08f) & 0xFFFFFF;
		this.dynSrchBd = curHeader;
		this.dynTrack = blend(curPanel, -16777216, 0.25f);
		this.dynHover = blend(curPanel, -1, 0.08f);
	}

	private static int acc() {
		return curAcc;
	}

	private static int accDim() {
		return curAccDim;
	}

	/** Scales every channel towards black, keeping the colour fully opaque. */
	private static int darken(int argb, float amount) {
		int r = argb >> 16 & 0xFF;
		int g = argb >> 8 & 0xFF;
		int b = argb & 0xFF;
		return 0xFF000000 | (int) ((float) r * (1.0f - amount)) << 16 | (int) ((float) g * (1.0f - amount)) << 8 | (int) ((float) b * (1.0f - amount));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
	}

	@Override
	protected void renderBlurredBackground(GuiGraphics graphics) {
	}

	@Override
	public void init() {
		if (!posInit) {
			posInit = true;
			for (int i = 0; i < N; ++i) {
				SPX[i] = 30 + i * 210;
				SPY[i] = 55;
			}
		}
		System.arraycopy(SPX, 0, this.px, 0, N);
		System.arraycopy(SPY, 0, this.py, 0, N);
		this.openT = 0.0f;
		this.lastNs = 0L;
		// Drop the entry animations so the panels slide in again on every open.
		for (int i = 0; i < N; ++i) {
			this.anims.remove("pentr" + i);
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		long now = System.nanoTime();
		float dt = this.lastNs == 0L ? 0.016666668f : Math.min(0.1f, (float) (now - this.lastNs) / 1.0E9f);
		this.lastNs = now;
		this.openT = exp(this.openT, 1.0f, dt, 10.0f);
		this.refreshTheme(dt);

		int dim = (int) (89.0f * ease(this.openT));
		graphics.fill(0, 0, this.width, this.height, dim << 24);
		if (!themesOpen) {
			// The bars sit outside the scaled matrix, so they draw at raw mouse coords.
			this.renderScaleBar(graphics, mouseX, mouseY);
			this.renderSearchBar(graphics, mouseX, mouseY);
			graphics.renderDeferredElements();
		}

		this.hoveredModule = null;
		float scale = guiScale;
		graphics.pose().pushMatrix();
		graphics.pose().scale(scale, scale);
		int scaledMouseX = Math.round((float) mouseX / scale);
		int scaledMouseY = Math.round((float) mouseY / scale);
		this.hitR.clear();
		this.hitH.clear();
		this.hitExtra.clear();
		for (int panel = 0; panel < N; ++panel) {
			this.renderPanel(graphics, panel, scaledMouseX, scaledMouseY, dt);
		}
		if (themesOpen) {
			this.renderThemesOverlay(graphics, scaledMouseX, scaledMouseY, dt);
		}
		graphics.pose().popMatrix();

		if (!themesOpen) {
			this.renderHoverDescription(graphics);
		}
	}

	private void renderPanel(GuiGraphics graphics, int panel, int mouseX, int mouseY, float dt) {
		List<Module> modules = this.modsFor(panel);
		int x = this.px[panel];
		int y = this.py[panel];
		float entry = this.anim("pentr" + panel, 1.0f, dt, 14.0f);
		float entryEase = ease(entry);
		float dropIn = (1.0f - ease(entry)) * 10.0f;

		int contentH = 0;
		if (!this.collapsed[panel]) {
			for (Module module : modules) {
				contentH += MOD_H;
				if (!this.isExpanded(module)) {
					continue;
				}
				contentH += this.settingsHeight(module);
			}
		}
		int visibleH = Math.min(contentH, MAX_VIS_H);
		float collapse = this.anim("col" + panel, this.collapsed[panel] ? 1.0f : 0.0f, dt, 22.0f);
		int bodyH = (int) ((float) visibleH * (1.0f - ease(collapse)));
		int panelH = HEADER_H + bodyH;

		graphics.pose().pushMatrix();
		graphics.pose().translate(0.0f, dropIn);

		// Stacked translucent outlines fake a drop shadow.
		for (int ring = 8; ring >= 1; --ring) {
			int shadow = (int) (14.0f * entryEase * ease(this.openT) * (1.0f - (float) ring / 9.0f));
			RenderUtil.drawRoundedRect(graphics, x - ring, y - ring, PANEL_W + ring * 2, panelH + ring * 2, RADIUS + ring, shadow << 24, false);
		}

		boolean panelHovered = this.isH(mouseX, mouseY, x, y, PANEL_W, panelH);
		float panelHover = this.anim("phov" + panel, panelHovered ? 1.0f : 0.0f, dt, 16.0f);
		if (panelHover > 0.01f) {
			int glow = (int) (30.0f * panelHover * entryEase);
			RenderUtil.drawRoundedRect(graphics, x - 1, y - 1, PANEL_W + 2, panelH + 2, 19.0f, glow << 24 | acc() & 0xFFFFFF, false);
		}

		int headerColor = wA(curHeader, (int) (255.0f * entryEase));
		int bodyColor = wA(curPanel, (int) (255.0f * entryEase));
		if (bodyH == 0) {
			RenderUtil.drawRoundedRect(graphics, x, y, PANEL_W, HEADER_H, 4.0f, 4.0f, 18.0f, 18.0f, false, headerColor);
		} else {
			RenderUtil.drawRoundedRect(graphics, x, y, PANEL_W, HEADER_H, 4.0f, 4.0f, 0.0f, 0.0f, false, headerColor);
			RenderUtil.drawRoundedRect(graphics, x, y + HEADER_H, PANEL_W, bodyH, 0.0f, 0.0f, 18.0f, 18.0f, false, bodyColor);
		}

		int fullAlpha = (int) (255.0f * entryEase);
		RenderUtil.drawRoundedRect(graphics, x, y + 7, 3.0f, 22.0f, 2.0f, wA(acc(), fullAlpha), false);
		String title = (panel == OTHER_IDX ? "Other" : ORDER[panel].getName()).toUpperCase(Locale.ROOT);
		ZenyaFont.draw(graphics, this.font, title, x + 14, y + (HEADER_H - this.font.lineHeight) / 2 + 1, wA(C_TEXT, (int) (255.0f * entryEase)), false);

		boolean chevronHovered = this.isH(mouseX, mouseY, x + PANEL_W - 32, y + 4, 28, 28);
		float chevronHover = this.anim("chev" + panel, chevronHovered ? 1.0f : 0.0f, dt, 16.0f);
		if (chevronHover > 0.01f) {
			int glow = (int) (30.0f * chevronHover * entryEase);
			RenderUtil.drawRoundedRect(graphics, x + PANEL_W - 32, y + 6, 28.0f, 24.0f, 4.0f, glow << 24 | acc() & 0xFFFFFF, false);
		}
		int chevronColor = blend(wA(C_MUTED, (int) (200.0f * entryEase)), wA(acc(), (int) (255.0f * entryEase)), chevronHover);
		this.chevron(graphics, x + PANEL_W - 18, y + 18, collapse, chevronColor);

		if (bodyH > 0 && !modules.isEmpty()) {
			this.maxSc[panel] = Math.max(0, contentH - visibleH);
			this.scroll[panel] = clamp(this.scroll[panel], 0, this.maxSc[panel]);
			int clipTop = y + HEADER_H;
			int clipBottom = clipTop + bodyH;
			graphics.enableScissor(x, clipTop, x + PANEL_W, clipBottom);
			int rowY = clipTop - this.scroll[panel];
			for (Module module : modules) {
				rowY = this.renderModule(graphics, panel, module, x, rowY, mouseX, mouseY, dt, clipTop, clipBottom);
			}
			graphics.disableScissor();

			if (contentH > visibleH && this.maxSc[panel] > 0) {
				float shown = (float) visibleH / (float) contentH;
				int thumbH = Math.max(16, (int) ((float) visibleH * shown));
				int thumbY = clipTop + (int) ((float) (visibleH - thumbH) * (float) this.scroll[panel] / (float) this.maxSc[panel]);
				RenderUtil.drawRoundedRect(graphics, x + PANEL_W - 5, thumbY, 3.0f, thumbH, 1.5f, wA(0xFFFFFF, (int) (100.0f * entryEase)), false);
			}
		}
		graphics.pose().popMatrix();
	}

	/** Draws one module row plus its settings body; returns the next row's y. */
	private int renderModule(GuiGraphics graphics, int panel, Module module, int x, int y, int mouseX, int mouseY, float dt, int clipTop, int clipBottom) {
		boolean visible = y + MOD_H > clipTop && y < clipBottom;
		boolean enabled = module.isEnabled();
		boolean hovered = visible && this.isH(mouseX, mouseY, x, y, PANEL_W, MOD_H) && this.sliderDrag == null && this.openColor == null;
		if (hovered) {
			this.hoveredModule = module;
		}
		float hover = this.anim("mh" + module.getName(), hovered ? 1.0f : 0.0f, dt, 26.0f);
		float on = this.anim("me" + module.getName(), enabled ? 1.0f : 0.0f, dt, 20.0f);

		if (visible) {
			int rowColor = blend(blend(curPanel, this.dynHover, hover), curOn, on);
			graphics.fill(x, y, x + PANEL_W, y + MOD_H, rowColor);
			if (hover > 0.01f) {
				int glow = (int) (18.0f * ease(hover));
				graphics.fill(x, y, x + PANEL_W, y + MOD_H, glow << 24 | acc() & 0xFFFFFF);
			}
			int dotColor = blend(C_DIM, acc(), on);
			RenderUtil.drawRoundedRect(graphics, x + 10, y + 13 - 3, 6.0f, 6.0f, 3.0f, dotColor, false);
			int textColor = blend(C_MUTED, C_TEXT, Math.max(hover, on));
			ZenyaFont.draw(graphics, this.font, module.getName(), x + 22, y + (MOD_H - this.font.lineHeight) / 2 + 1, textColor, false);
			// Kept for its side effect: the animation has to keep ticking even unused.
			this.anim("mt" + module.getName(), enabled ? 1.0f : 0.0f, dt, 22.0f);
			// ponytail: the row's y is passed where the 0..1 toggle progress belongs,
			// so this switch draws with an extrapolated colour and an off-screen knob.
			this.toggle(graphics, x + PANEL_W - TGL_W - 8, y + 5, TGL_W, TGL_H, y);
			this.hitR.add(new int[]{x, y, PANEL_W, MOD_H});
			this.hitH.add(new HitTarget(module, null, 1));
			this.hitExtra.add(null);
		}
		y += MOD_H;

		float expand = this.anim("mx" + module.getName(), this.isExpanded(module) ? 1.0f : 0.0f, dt, 24.0f);
		if (expand > 0.02f) {
			int fullH = this.settingsHeight(module);
			int shownH = Math.round((float) fullH * expand);
			if (shownH > 0) {
				int bodyTop = Math.max(y, clipTop);
				int bodyBottom = Math.min(y + shownH, clipBottom);
				if (bodyTop < bodyBottom) {
					int bodyColor = blend(curPanel, -16777216, 0.12f);
					int alpha = (int) (255.0f * expand);
					graphics.enableScissor(x, bodyTop, x + PANEL_W, bodyBottom);
					RenderUtil.drawRoundedRect(graphics, x, y, PANEL_W, fullH, 0.0f, 0.0f, 4.0f, 4.0f, false, wA(bodyColor, alpha));
					this.renderSettings(graphics, module, x, y, mouseX, mouseY, dt, expand, y, bodyBottom);
					graphics.disableScissor();
				}
			}
			y += shownH;
		}
		return y;
	}

	/** Dispatches each visible setting to the row renderer that matches its type. */
	private int renderSettings(GuiGraphics graphics, Module module, int x, int y, int mouseX, int mouseY, float dt, float expand, int clipTop, int clipBottom) {
		int alpha = (int) (255.0f * ease(expand));
		int rowX = x + 4;
		int rowW = 192;
		y = this.renderBindRow(graphics, module, rowX, y, rowW, mouseX, mouseY, dt, alpha, clipTop, clipBottom);

		for (Setting<?> setting : module.getSettings()) {
			if (!setting.isVisible()) {
				continue;
			}
			if (setting instanceof SectionSetting) {
				if (y + SET_H > clipTop && y < clipBottom) {
					graphics.fill(rowX + 4, y + 11, rowX + rowW - 4, y + 11 + 1, wA(C_DIV, alpha));
					ZenyaFont.draw(graphics, this.font, setting.getName(), rowX + 8, y + (SET_H - this.font.lineHeight) / 2, wA(C_DIM, alpha), false);
				}
				y += SET_H;
			} else if (setting instanceof ActionSetting action) {
				y = this.renderActionRow(graphics, module, action, rowX, y, rowW, mouseX, mouseY, dt, alpha, clipTop, clipBottom);
			} else if (setting instanceof ConfirmBooleanSetting confirm) {
				y = this.renderBoolRow(graphics, module, confirm, rowX, y, rowW, mouseX, mouseY, dt, alpha, clipTop, clipBottom, 10);
			} else if (setting.getValue() instanceof Boolean) {
				@SuppressWarnings("unchecked")
				Setting<Boolean> flag = (Setting<Boolean>) setting;
				y = this.renderBoolRow(graphics, module, flag, rowX, y, rowW, mouseX, mouseY, dt, alpha, clipTop, clipBottom, 3);
			} else if (setting instanceof ThresholdSetting threshold) {
				y = this.renderThresholdRow(graphics, module, threshold, rowX, y, rowW, mouseX, mouseY, dt, alpha, clipTop, clipBottom);
			} else if (setting.getValue() instanceof Double || setting.getValue() instanceof Float) {
				y = this.renderSliderRow(graphics, module, setting, rowX, y, rowW, mouseX, mouseY, dt, alpha, clipTop, clipBottom);
			} else if (setting.getValue() instanceof Integer && !(setting instanceof ThresholdSetting)) {
				y = this.renderIntSliderRow(graphics, module, setting, rowX, y, rowW, mouseX, mouseY, dt, alpha, clipTop, clipBottom);
			} else if (setting.getValue() instanceof Color) {
				@SuppressWarnings("unchecked")
				Setting<Color> color = (Setting<Color>) setting;
				y = this.renderColorRow(graphics, module, color, rowX, y, rowW, mouseX, mouseY, dt, alpha, clipTop, clipBottom);
			} else if (setting instanceof ModeSetting mode) {
				y = this.renderModeRow(graphics, module, mode, rowX, y, rowW, mouseX, mouseY, dt, alpha, clipTop, clipBottom);
			} else if (setting instanceof OptionSelectSetting select) {
				y = this.renderOptionSelectRow(graphics, module, select, rowX, y, rowW, mouseX, mouseY, dt, alpha, clipTop, clipBottom);
			} else if (setting instanceof OptionMultiSelectSetting multi) {
				y = this.renderOptionMultiRow(graphics, module, multi, rowX, y, rowW, mouseX, mouseY, dt, alpha, clipTop, clipBottom);
			} else if (setting instanceof StorageBlocksSetting storage) {
				y = this.renderStorageBlocksRow(graphics, module, storage, rowX, y, rowW, mouseX, mouseY, dt, alpha, clipTop, clipBottom);
			} else if (setting instanceof BlocksSetting blocks) {
				y = this.renderBlocksRow(graphics, module, blocks, rowX, y, rowW, mouseX, mouseY, dt, alpha, clipTop, clipBottom);
			} else if (setting instanceof MobsSetting mobs) {
				y = this.renderMobsRow(graphics, module, mobs, rowX, y, rowW, mouseX, mouseY, dt, alpha, clipTop, clipBottom);
			} else if (setting.getValue() instanceof String) {
				y = this.renderStringRow(graphics, module, setting, rowX, y, rowW, mouseX, mouseY, dt, alpha, clipTop, clipBottom);
			} else {
				y += SET_H;
			}
		}
		return y + 1;
	}

	/** The keybind row every module gets, above its own settings. */
	private int renderBindRow(GuiGraphics graphics, Module module, int x, int y, int width, int mouseX, int mouseY, float dt, int alpha, int clipTop, int clipBottom) {
		if (y + SET_H > clipTop && y < clipBottom) {
			boolean listening = this.bindListening == module;
			boolean hovered = this.isH(mouseX, mouseY, x, y, width, SET_H);
			float hover = this.anim("bindh" + module.getName(), hovered ? 1.0f : 0.0f, dt, 14.0f);
			int rowColor = blend(curPanel, this.dynHover, hover);
			if (listening) {
				rowColor = blend(rowColor, acc(), 0.35f);
			}
			RenderUtil.drawRoundedRect(graphics, x, y, width, 20.0f, 5.0f, wA(rowColor, alpha), false);
			ZenyaFont.draw(graphics, this.font, "Bind", x + 6, y + (SET_H - this.font.lineHeight) / 2, wA(C_TEXT, alpha), false);

			String label = listening ? "..." : (module.getBind() == 0 ? "None" : ClickGUI.getKeyDisplayNameStatic(module.getBind()));
			int pillW = ZenyaFont.width(this.font, label) + 12;
			int pillX = x + width - pillW - 6;
			int pillY = y + 4;
			int pillColor = listening ? wA(acc(), alpha) : wA(blend(curPanel, -16777216, 0.35f), alpha);
			RenderUtil.drawRoundedRect(graphics, pillX, pillY, pillW, 14.0f, 7.0f, pillColor, false);
			int labelColor = listening ? wA(-1, alpha) : wA(acc(), alpha);
			ZenyaFont.draw(graphics, this.font, label, pillX + (pillW - ZenyaFont.width(this.font, label)) / 2, pillY + (14 - this.font.lineHeight) / 2 + 1, labelColor, false);

			this.hitR.add(new int[]{x, y, width, SET_H});
			this.hitH.add(new HitTarget(module, null, 200));
			this.hitExtra.add(null);
		}
		return y + SET_H;
	}

	private int renderActionRow(GuiGraphics graphics, Module module, ActionSetting setting, int x, int y, int width, int mouseX, int mouseY, float dt, int alpha, int clipTop, int clipBottom) {
		if (y + SET_H > clipTop && y < clipBottom) {
			boolean hovered = this.isH(mouseX, mouseY, x, y, width, SET_H);
			float hover = this.anim("ah" + module.getName() + setting.getName(), hovered ? 1.0f : 0.0f, dt, 14.0f);
			RenderUtil.drawRoundedRect(graphics, x, y, width, 20.0f, 5.0f, wA(blend(curPanel, this.dynHover, hover), alpha), false);
			ZenyaFont.draw(graphics, this.font, setting.getName(), x + 6, y + (SET_H - this.font.lineHeight) / 2, wA(acc(), alpha), false);
			this.hitR.add(new int[]{x, y, width, SET_H});
			this.hitH.add(new HitTarget(module, setting, 4));
			this.hitExtra.add(null);
		}
		return y + SET_H;
	}

	/** {@code hitKind} distinguishes plain booleans (3) from confirm-first ones (10). */
	private int renderBoolRow(GuiGraphics graphics, Module module, Setting<Boolean> setting, int x, int y, int width, int mouseX, int mouseY, float dt, int alpha, int clipTop, int clipBottom, int hitKind) {
		if (y + SET_H > clipTop && y < clipBottom) {
			boolean value = Boolean.TRUE.equals(setting.getValue());
			float on = this.anim("bv" + module.getName() + setting.getName(), value ? 1.0f : 0.0f, dt, 12.0f);
			boolean hovered = this.isH(mouseX, mouseY, x, y, width, SET_H);
			float hover = this.anim("bh" + module.getName() + setting.getName(), hovered ? 1.0f : 0.0f, dt, 14.0f);
			RenderUtil.drawRoundedRect(graphics, x, y, width, 20.0f, 5.0f, wA(blend(curPanel, this.dynHover, hover), alpha), false);
			ZenyaFont.draw(graphics, this.font, setting.getDisplayName(), x + 6, y + (SET_H - this.font.lineHeight) / 2, wA(C_MUTED, alpha), false);
			this.toggle(graphics, x + width - TGL_W - 4, y + 3, TGL_W, TGL_H, on);
			this.hitR.add(new int[]{x, y, width, SET_H});
			this.hitH.add(new HitTarget(module, setting, hitKind));
			this.hitExtra.add(null);
		}
		return y + SET_H;
	}

	/** A toggle row that reveals its slider only while the threshold is enabled. */
	private int renderThresholdRow(GuiGraphics graphics, Module module, ThresholdSetting setting, int x, int y, int width, int mouseX, int mouseY, float dt, int alpha, int clipTop, int clipBottom) {
		if (y + SET_H > clipTop && y < clipBottom) {
			float on = this.anim("ten" + module.getName() + setting.getName(), setting.isEnabled() ? 1.0f : 0.0f, dt, 12.0f);
			boolean hovered = this.isH(mouseX, mouseY, x, y, width, SET_H);
			float hover = this.anim("thh" + module.getName() + setting.getName(), hovered ? 1.0f : 0.0f, dt, 14.0f);
			RenderUtil.drawRoundedRect(graphics, x, y, width, 20.0f, 5.0f, wA(blend(curPanel, this.dynHover, hover), alpha), false);
			this.toggle(graphics, x + 4, y + 3, TGL_W, TGL_H, on);
			ZenyaFont.draw(graphics, this.font, setting.getDisplayName(), x + TGL_W + 10, y + (SET_H - this.font.lineHeight) / 2, wA(C_MUTED, alpha), false);
			this.hitR.add(new int[]{x, y, 40, SET_H});
			this.hitH.add(new HitTarget(module, setting, 8));
			this.hitExtra.add(null);
		}
		// ponytail: the header's SET_H is only added when enabled, and the clipped
		// slider path advances by SET_H instead of SLIDER_H, so both disagree with
		// settingsHeight() and shift the rows below.
		if (setting.isEnabled()) {
			y += SET_H;
			if (y + SET_H > clipTop && y < clipBottom) {
				y = this.renderRawSlider(graphics, module, setting, x, y, width, mouseX, mouseY, dt, alpha, clipTop, clipBottom, setting.getValue().intValue(), setting.getMin(), setting.getMax());
			} else {
				y += SET_H;
			}
		}
		return y;
	}

	private int renderSliderRow(GuiGraphics graphics, Module module, Setting<?> setting, int x, int y, int width, int mouseX, int mouseY, float dt, int alpha, int clipTop, int clipBottom) {
		if (y + SLIDER_H <= clipTop || y >= clipBottom) {
			return y + SLIDER_H;
		}
		double value = setting.getValue() instanceof Double exact ? exact : ((Float) setting.getValue()).doubleValue();
		double min;
		if (setting.getMin() instanceof Double minDouble) {
			min = minDouble;
		} else if (setting.getMin() instanceof Float minFloat) {
			min = minFloat.doubleValue();
		} else {
			min = 0.0;
		}
		double max;
		if (setting.getMax() instanceof Double maxDouble) {
			max = maxDouble;
		} else if (setting.getMax() instanceof Float maxFloat) {
			max = maxFloat.doubleValue();
		} else {
			max = 1.0;
		}
		return this.renderRawSlider(graphics, module, setting, x, y, width, mouseX, mouseY, dt, alpha, clipTop, clipBottom, value, min, max);
	}

	/** Integer settings whose name contains "bind" become a key capture row instead. */
	private int renderIntSliderRow(GuiGraphics graphics, Module module, Setting<?> setting, int x, int y, int width, int mouseX, int mouseY, float dt, int alpha, int clipTop, int clipBottom) {
		if (y + SLIDER_H <= clipTop || y >= clipBottom) {
			return y + SLIDER_H;
		}
		if (setting.getName().toLowerCase(Locale.ROOT).contains("bind")) {
			boolean listening = this.bindListeningSetting == setting;
			boolean hovered = this.isH(mouseX, mouseY, x, y, width, SET_H);
			float hover = this.anim("bindh" + module.getName() + setting.getName(), hovered ? 1.0f : 0.0f, dt, 14.0f);
			int rowColor = blend(curPanel, this.dynHover, hover);
			if (listening) {
				rowColor = blend(rowColor, acc(), 0.35f);
			}
			RenderUtil.drawRoundedRect(graphics, x, y, width, 20.0f, 5.0f, wA(rowColor, alpha), false);
			ZenyaFont.draw(graphics, this.font, setting.getDisplayName(), x + 6, y + (SET_H - this.font.lineHeight) / 2, wA(C_TEXT, alpha), false);

			int key = ((Integer) setting.getValue()).intValue();
			String label = listening ? "..." : (key == 0 ? "None" : ClickGUI.getKeyDisplayNameStatic(key));
			int pillW = ZenyaFont.width(this.font, label) + 12;
			int pillX = x + width - pillW - 6;
			int pillY = y + 4;
			int pillColor = listening ? wA(acc(), alpha) : wA(blend(curPanel, -16777216, 0.35f), alpha);
			RenderUtil.drawRoundedRect(graphics, pillX, pillY, pillW, 14.0f, 7.0f, pillColor, false);
			int labelColor = listening ? wA(-1, alpha) : wA(acc(), alpha);
			ZenyaFont.draw(graphics, this.font, label, pillX + (pillW - ZenyaFont.width(this.font, label)) / 2, pillY + (14 - this.font.lineHeight) / 2 + 1, labelColor, false);

			this.hitR.add(new int[]{x, y, width, SET_H});
			this.hitH.add(new HitTarget(module, setting, 201));
			this.hitExtra.add(null);
			// ponytail: a bind row advances SET_H while settingsHeight() reserved SLIDER_H.
			return y + SET_H;
		}
		double value = ((Integer) setting.getValue()).doubleValue();
		double min = setting.getMin() instanceof Integer minInt ? minInt.doubleValue() : 0.0;
		double max = setting.getMax() instanceof Integer maxInt ? maxInt.doubleValue() : 100.0;
		return this.renderRawSlider(graphics, module, setting, x, y, width, mouseX, mouseY, dt, alpha, clipTop, clipBottom, value, min, max);
	}

	/** The shared track/knob drawing for every numeric setting. */
	private int renderRawSlider(GuiGraphics graphics, Module module, Setting<?> setting, int x, int y, int width, int mouseX, int mouseY, float dt, int alpha, int clipTop, int clipBottom, double value, Object minBound, Object maxBound) {
		double min = minBound instanceof Number lower ? lower.doubleValue() : 0.0;
		double max = maxBound instanceof Number upper ? upper.doubleValue() : 1.0;
		double frac = max > min ? Math.max(0.0, Math.min(1.0, (value - min) / (max - min))) : 0.0;

		boolean dragging = this.sliderDrag == setting;
		boolean active = dragging || this.isH(mouseX, mouseY, x, y, width, SLIDER_H);
		float hover = this.anim("slh" + module.getName() + setting.getName(), active ? 1.0f : 0.0f, dt, 14.0f);
		RenderUtil.drawRoundedRect(graphics, x, y, width, 28.0f, 3.0f, wA(blend(curPanel, this.dynHover, hover), alpha), false);

		String text = fmtNum(value);
		int textW = ZenyaFont.width(this.font, text);
		ZenyaFont.draw(graphics, this.font, setting.getDisplayName(), x + 6, y + 5, wA(C_MUTED, alpha), false);
		ZenyaFont.draw(graphics, this.font, text, x + width - textW - 6, y + 5, wA(C_TEXT, alpha), false);

		int trackX = x + 6;
		int trackY = y + 19;
		int trackW = width - 12;
		RenderUtil.drawRoundedRect(graphics, trackX, trackY, trackW, 3.0f, 1.5f, wA(this.dynTrack, alpha), false);
		int filledW = (int) ((double) trackW * frac);
		if (filledW > 0) {
			RenderUtil.drawRoundedRect(graphics, trackX, trackY, filledW, 3.0f, 1.5f, wA(acc(), alpha), false);
		}
		int knobX = trackX + filledW;
		RenderUtil.drawRoundedRect(graphics, knobX - 5, trackY - 5 + 1, 10.0f, 10.0f, 5.0f, wA(-1, alpha), false);

		this.slTrkX = trackX;
		this.slTrkW = trackW;
		this.hitR.add(new int[]{x, y, width, SLIDER_H, trackX, trackY, trackW});
		this.hitH.add(new HitTarget(module, setting, 2));
		this.hitExtra.add(null);
		return y + SLIDER_H;
	}

	private int renderColorRow(GuiGraphics graphics, Module module, Setting<Color> setting, int x, int y, int width, int mouseX, int mouseY, float dt, int alpha, int clipTop, int clipBottom) {
		boolean open = this.openColor == setting;
		if (y + SET_H > clipTop && y < clipBottom) {
			boolean hovered = this.isH(mouseX, mouseY, x, y, width, SET_H);
			float hover = this.anim("ch" + module.getName() + setting.getName(), hovered ? 1.0f : 0.0f, dt, 14.0f);
			RenderUtil.drawRoundedRect(graphics, x, y, width, 20.0f, 5.0f, wA(blend(curPanel, this.dynHover, hover), alpha), false);
			ZenyaFont.draw(graphics, this.font, setting.getDisplayName(), x + 6, y + (SET_H - this.font.lineHeight) / 2, wA(C_MUTED, alpha), false);

			Color value = setting.getValue();
			if (value != null) {
				int swatch = value.getAlpha() << 24 | value.getRed() << 16 | value.getGreen() << 8 | value.getBlue();
				if (open) {
					RenderUtil.drawRoundedRect(graphics, x + width - 23, y + 3, 18.0f, 14.0f, 2.0f, -1, false);
				}
				RenderUtil.drawRoundedRect(graphics, x + width - 22, y + 4, 16.0f, 12.0f, 2.0f, swatch, false);
			}
			this.hitR.add(new int[]{x, y, width, SET_H});
			this.hitH.add(new HitTarget(module, setting, 6));
			this.hitExtra.add(null);
		}
		y += SET_H;
		if (open) {
			y = this.renderColorPicker(graphics, setting, x, y, width, alpha, mouseX, mouseY);
		}
		return y;
	}

	/**
	 * The inline HSV picker. Its hitboxes are stored as fields because the drag
	 * handlers run outside the render pass and need last frame's geometry.
	 */
	private int renderColorPicker(GuiGraphics graphics, Setting<Color> setting, int x, int y, int width, int alpha, int mouseX, int mouseY) {
		Color current = setting.getValue();
		if (current != null && this.cDrag == CDrag.NONE) {
			// Only resync while idle, otherwise the drag would fight the setting.
			float[] hsb = Color.RGBtoHSB(current.getRed(), current.getGreen(), current.getBlue(), null);
			this.cHSV[0] = hsb[0];
			this.cHSV[1] = hsb[1];
			this.cHSV[2] = hsb[2];
			this.cAlpha = current.getAlpha();
		}
		int background = blend(curPanel, -16777216, 0.22f);
		RenderUtil.drawRoundedRect(graphics, x, y, width, CP_TOTAL, 6.0f, wA(background, alpha), false);

		int innerX = x + CP_PAD;
		int innerW = width - CP_PAD * 2;
		int rowY = y + CP_PAD;

		this.cSvX = innerX;
		this.cSvY = rowY;
		this.cSvW = innerW;
		this.cSvH = CP_SV_H;
		for (int i = 0; i < innerW; ++i) {
			float saturation = (float) i / (float) innerW;
			int column = Color.HSBtoRGB(this.cHSV[0], saturation, 1.0f) | 0xFF000000;
			graphics.fillGradient(innerX + i, rowY, innerX + i + 1, rowY + CP_SV_H, column, -16777216);
		}
		int svKnobX = innerX + (int) (this.cHSV[1] * (float) innerW);
		int svKnobY = rowY + (int) ((1.0f - this.cHSV[2]) * (float) CP_SV_H);
		RenderUtil.drawRoundedRect(graphics, svKnobX - 5, svKnobY - 5, 10.0f, 10.0f, 5.0f, -872415232, false);
		RenderUtil.drawRoundedRect(graphics, svKnobX - 4, svKnobY - 4, 8.0f, 8.0f, 4.0f, -1, false);

		rowY += CP_SV_H + CP_GAP;
		this.cHueX = innerX;
		this.cHueY = rowY;
		this.cHueW = innerW;
		this.cHueH = CP_BAR_H;
		int hueSpan = Math.max(1, this.cHueW);
		for (int i = 0; i < hueSpan; ++i) {
			int column = Color.HSBtoRGB((float) i / (float) hueSpan, 1.0f, 1.0f) | 0xFF000000;
			graphics.fill(this.cHueX + i, this.cHueY + 1, this.cHueX + i + 1, this.cHueY + this.cHueH - 1, column);
		}
		// Rounded caps hide the square ends of the strip; also reused by the alpha knob.
		int capRadius = (this.cHueH - 2) / 2;
		int hueStart = Color.HSBtoRGB(0.0f, 1.0f, 1.0f) | 0xFF000000;
		int hueEnd = Color.HSBtoRGB(1.0f, 1.0f, 1.0f) | 0xFF000000;
		RenderUtil.drawRoundedRect(graphics, this.cHueX, this.cHueY + 1, capRadius * 2, this.cHueH - 2, capRadius, hueStart, false);
		RenderUtil.drawRoundedRect(graphics, this.cHueX + this.cHueW - capRadius * 2, this.cHueY + 1, capRadius * 2, this.cHueH - 2, capRadius, hueEnd, false);
		int hueKnobX = this.cHueX + (int) (this.cHSV[0] * (float) this.cHueW);
		hueKnobX = Math.max(this.cHueX + capRadius, Math.min(this.cHueX + this.cHueW - capRadius, hueKnobX));
		int hueKnobR = this.cHueH / 2 + 1;
		RenderUtil.drawRoundedRect(graphics, hueKnobX - hueKnobR, this.cHueY + this.cHueH / 2 - hueKnobR, hueKnobR * 2, hueKnobR * 2, hueKnobR, -1, false);
		int hueKnobColor = Color.HSBtoRGB(this.cHSV[0], 1.0f, 1.0f) | 0xFF000000;
		RenderUtil.drawRoundedRect(graphics, hueKnobX - hueKnobR + 2, this.cHueY + this.cHueH / 2 - hueKnobR + 2, hueKnobR * 2 - 4, hueKnobR * 2 - 4, hueKnobR - 2, hueKnobColor, false);

		rowY += this.cHueH + CP_GAP;
		this.cAlX = innerX;
		this.cAlY = rowY;
		this.cAlW = innerW;
		this.cAlH = CP_BAR_H;
		// Checkerboard so partial alpha reads as transparency.
		for (int cx = 0; cx < this.cAlW; cx += 3) {
			for (int cy = 0; cy < this.cAlH - 2; cy += 3) {
				boolean light = (cx / 3 + cy / 3) % 2 == 0;
				int cell = light ? -12961222 : -11184811;
				int right = Math.min(this.cAlX + cx + 3, this.cAlX + this.cAlW);
				int bottom = Math.min(this.cAlY + 1 + cy + 3, this.cAlY + this.cAlH - 1);
				graphics.fill(this.cAlX + cx, this.cAlY + 1 + cy, right, bottom, cell);
			}
		}
		int rgb = Color.HSBtoRGB(this.cHSV[0], this.cHSV[1], this.cHSV[2]) & 0xFFFFFF;
		for (int i = 0; i < this.cAlW; ++i) {
			int columnAlpha = (int) ((float) i / (float) this.cAlW * 255.0f);
			graphics.fill(this.cAlX + i, this.cAlY + 1, this.cAlX + i + 1, this.cAlY + this.cAlH - 1, columnAlpha << 24 | rgb);
		}
		int alphaKnobX = this.cAlX + (int) ((float) this.cAlpha / 255.0f * (float) this.cAlW);
		alphaKnobX = Math.max(this.cAlX + capRadius, Math.min(this.cAlX + this.cAlW - capRadius, alphaKnobX));
		int alphaKnobR = this.cAlH / 2 + 1;
		RenderUtil.drawRoundedRect(graphics, alphaKnobX - alphaKnobR, this.cAlY + this.cAlH / 2 - alphaKnobR, alphaKnobR * 2, alphaKnobR * 2, alphaKnobR, -1, false);
		int alphaKnobColor = this.cAlpha << 24 | rgb;
		RenderUtil.drawRoundedRect(graphics, alphaKnobX - alphaKnobR + 2, this.cAlY + this.cAlH / 2 - alphaKnobR + 2, alphaKnobR * 2 - 4, alphaKnobR * 2 - 4, alphaKnobR - 2, alphaKnobColor, false);

		int previewColor = this.cAlpha << 24 | rgb;
		rowY += this.cAlH + CP_GAP;
		RenderUtil.drawRoundedRect(graphics, innerX, rowY + 2, 14.0f, 14.0f, 3.0f, C_DIV, false);
		RenderUtil.drawRoundedRect(graphics, innerX + 1, rowY + 3, 12.0f, 12.0f, 2.0f, previewColor, false);

		String hex = String.format("#%02X%02X%02X", rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF);
		if (this.cAlpha < 255) {
			hex = String.format("#%02X%s", this.cAlpha, hex.substring(1));
		}
		ZenyaFont.draw(graphics, this.font, hex, innerX + 14 + 6, rowY + (CP_PREV_H - this.font.lineHeight) / 2 + 1, wA(C_TEXT, alpha), false);
		String percent = (int) ((float) this.cAlpha / 255.0f * 100.0f) + "%";
		int percentW = ZenyaFont.width(this.font, percent);
		ZenyaFont.draw(graphics, this.font, percent, innerX + innerW - percentW, rowY + (CP_PREV_H - this.font.lineHeight) / 2 + 1, wA(C_MUTED, alpha), false);
		return y + CP_TOTAL;
	}

	private int renderModeRow(GuiGraphics graphics, Module module, ModeSetting setting, int x, int y, int width, int mouseX, int mouseY, float dt, int alpha, int clipTop, int clipBottom) {
		boolean open = this.openListSetting == setting;
		y = this.renderListHeader(graphics, module, setting, setting.getValue(), x, y, width, mouseX, mouseY, dt, alpha, clipTop, clipBottom);
		if (open) {
			for (String mode : setting.getModes()) {
				if (y + SET_H > clipTop && y < clipBottom) {
					boolean selected = mode.equalsIgnoreCase(setting.getValue());
					y = this.renderRadioItem(graphics, module, setting, mode, mode, selected, x, y, width, mouseX, mouseY, dt, alpha);
				} else {
					y += SET_H;
				}
			}
		}
		return y;
	}

	private int renderOptionSelectRow(GuiGraphics graphics, Module module, OptionSelectSetting setting, int x, int y, int width, int mouseX, int mouseY, float dt, int alpha, int clipTop, int clipBottom) {
		boolean open = this.openListSetting == setting;
		y = this.renderListHeader(graphics, module, setting, setting.getSummary(), x, y, width, mouseX, mouseY, dt, alpha, clipTop, clipBottom);
		if (open) {
			for (OptionEntry option : setting.getOptions()) {
				if (y + SET_H > clipTop && y < clipBottom) {
					y = this.renderRadioItem(graphics, module, setting, option.label(), option.value(), setting.isSelected(option), x, y, width, mouseX, mouseY, dt, alpha);
				} else {
					y += SET_H;
				}
			}
		}
		return y;
	}

	private int renderOptionMultiRow(GuiGraphics graphics, Module module, OptionMultiSelectSetting setting, int x, int y, int width, int mouseX, int mouseY, float dt, int alpha, int clipTop, int clipBottom) {
		boolean open = this.openListSetting == setting;
		y = this.renderListHeader(graphics, module, setting, setting.getSummary(), x, y, width, mouseX, mouseY, dt, alpha, clipTop, clipBottom);
		if (open) {
			for (OptionEntry option : setting.getOptions()) {
				if (y + SET_H > clipTop && y < clipBottom) {
					y = this.renderCheckItem(graphics, module, setting, option.label(), option.value(), setting.isSelected(option), x, y, width, mouseX, mouseY, dt, alpha);
				} else {
					y += SET_H;
				}
			}
		}
		return y;
	}

	/** Storage blocks are edited in their own screen, so only a summary row is drawn. */
	private int renderStorageBlocksRow(GuiGraphics graphics, Module module, StorageBlocksSetting setting, int x, int y, int width, int mouseX, int mouseY, float dt, int alpha, int clipTop, int clipBottom) {
		int selected = setting.getSelectedEntries().size();
		String summary = selected == 0 ? "None" : selected + " selected";
		return this.renderListHeader(graphics, module, setting, summary, x, y, width, mouseX, mouseY, dt, alpha, clipTop, clipBottom);
	}

	private int renderBlocksRow(GuiGraphics graphics, Module module, BlocksSetting setting, int x, int y, int width, int mouseX, int mouseY, float dt, int alpha, int clipTop, int clipBottom) {
		return this.renderListHeader(graphics, module, setting, setting.getSummary(), x, y, width, mouseX, mouseY, dt, alpha, clipTop, clipBottom);
	}

	private int renderMobsRow(GuiGraphics graphics, Module module, MobsSetting setting, int x, int y, int width, int mouseX, int mouseY, float dt, int alpha, int clipTop, int clipBottom) {
		return this.renderListHeader(graphics, module, setting, setting.getSummary(), x, y, width, mouseX, mouseY, dt, alpha, clipTop, clipBottom);
	}

	/** The old inline mob list, kept for reference; nothing calls it since mobs moved to their own screen. */
	private int renderMobsRowLegacyUnused(GuiGraphics graphics, Module module, MobsSetting setting, int x, int y, int width, int mouseX, int mouseY, float dt, int alpha, int clipTop, int clipBottom) {
		boolean open = this.openListSetting == setting;
		y = this.renderListHeader(graphics, module, setting, setting.getSummary(), x, y, width, mouseX, mouseY, dt, alpha, clipTop, clipBottom);
		if (open) {
			y = this.renderListSearch(graphics, module, setting, x, y, width, mouseX, mouseY, alpha, clipTop, clipBottom);
			List<EntityType<?>> mobs = this.listSearchBuf.isEmpty() ? setting.getAvailableMobs() : setting.filter(this.listSearchBuf);
			int shown = 0;
			for (EntityType<?> type : mobs) {
				if (shown++ > 80) {
					break;
				}
				if (y + SET_H > clipTop && y < clipBottom) {
					y = this.renderCheckItem(graphics, module, setting, setting.getDisplayName(type), type, setting.contains(type), x, y, width, mouseX, mouseY, dt, alpha);
				} else {
					y += SET_H;
				}
			}
		}
		return y;
	}

	private int renderStringRow(GuiGraphics graphics, Module module, Setting<?> setting, int x, int y, int width, int mouseX, int mouseY, float dt, int alpha, int clipTop, int clipBottom) {
		if (y + SET_H <= clipTop || y >= clipBottom) {
			return y + SET_H;
		}
		boolean focused = this.strFocus == setting;
		boolean hovered = this.isH(mouseX, mouseY, x, y, width, SET_H);
		float hover = this.anim("sth" + module.getName() + setting.getName(), hovered ? 1.0f : 0.0f, dt, 14.0f);
		RenderUtil.drawRoundedRect(graphics, x, y, width, 20.0f, 5.0f, wA(focused ? -14799553 : blend(curPanel, this.dynHover, hover), alpha), false);
		ZenyaFont.draw(graphics, this.font, setting.getDisplayName(), x + 6, y + (SET_H - this.font.lineHeight) / 2, wA(C_MUTED, alpha), false);

		String text = focused ? this.strBuf + (System.nanoTime() / 500000000L % 2L == 0L ? "|" : " ") : String.valueOf(setting.getValue());
		int textW = ZenyaFont.width(this.font, text);
		ZenyaFont.draw(graphics, this.font, text, x + width - textW - 6, y + (SET_H - this.font.lineHeight) / 2, wA(focused ? acc() : C_TEXT, alpha), false);

		this.hitR.add(new int[]{x, y, width, SET_H});
		this.hitH.add(new HitTarget(module, setting, 7));
		this.hitExtra.add(null);
		return y + SET_H;
	}

	/** The collapsed row shown above any expandable list, with its current summary. */
	private int renderListHeader(GuiGraphics graphics, Module module, Setting<?> setting, String summary, int x, int y, int width, int mouseX, int mouseY, float dt, int alpha, int clipTop, int clipBottom) {
		boolean open = this.openListSetting == setting;
		if (y + SET_H > clipTop && y < clipBottom) {
			boolean hovered = this.isH(mouseX, mouseY, x, y, width, SET_H);
			float hover = this.anim("lhh" + module.getName() + setting.getName(), hovered ? 1.0f : 0.0f, dt, 14.0f);
			int rowColor = blend(blend(curPanel, this.dynHover, hover), 0xFF000000 | curAccFaint & 0xFFFFFF, open ? 0.15f : 0.0f);
			RenderUtil.drawRoundedRect(graphics, x, y, width, 20.0f, 5.0f, wA(rowColor, alpha), false);
			ZenyaFont.draw(graphics, this.font, setting.getName(), x + 6, y + (SET_H - this.font.lineHeight) / 2, wA(C_MUTED, alpha), false);
			int summaryW = ZenyaFont.width(this.font, summary);
			ZenyaFont.draw(graphics, this.font, summary, x + width - summaryW - 14, y + (SET_H - this.font.lineHeight) / 2, wA(C_TEXT, alpha), false);
			this.miniChevron(graphics, x + width - 6, y + 11, open, wA(C_MUTED, alpha));
			this.hitR.add(new int[]{x, y, width, SET_H});
			this.hitH.add(new HitTarget(module, setting, 100));
			this.hitExtra.add(null);
		}
		return y + SET_H;
	}

	private int renderRadioItem(GuiGraphics graphics, Module module, Setting<?> setting, String label, Object value, boolean selected, int x, int y, int width, int mouseX, int mouseY, float dt, int alpha) {
		int itemX = x + 10;
		int itemW = width - 10;
		boolean hovered = this.isH(mouseX, mouseY, itemX, y, itemW, SET_H);
		float hover = this.anim("ri" + module.getName() + setting.getName() + label, hovered ? 1.0f : 0.0f, dt, 14.0f);
		float select = this.anim("rs" + module.getName() + setting.getName() + label, selected ? 1.0f : 0.0f, dt, 12.0f);
		int rowColor = blend(blend(C_SUB, this.dynHover, hover), curSubSel, select);
		RenderUtil.drawRoundedRect(graphics, itemX, y, itemW, 20.0f, 3.0f, wA(rowColor, alpha), false);
		ZenyaFont.draw(graphics, this.font, label, itemX + 8, y + (SET_H - this.font.lineHeight) / 2, wA(blend(C_DIM, C_TEXT, Math.max(hover, select)), alpha), false);

		int dotX = itemX + itemW - 12;
		int dotY = y + 11;
		int ringColor = wA(selected ? acc() : this.dynTrack, alpha);
		RenderUtil.drawRoundedRect(graphics, dotX - 5, dotY - 5, 10.0f, 10.0f, 5.0f, ringColor, false);
		if (selected) {
			RenderUtil.drawRoundedRect(graphics, dotX - 3, dotY - 3, 6.0f, 6.0f, 3.0f, wA(-16777216, alpha), false);
		}
		this.hitR.add(new int[]{itemX, y, itemW, SET_H});
		this.hitH.add(new HitTarget(module, setting, 5));
		this.hitExtra.add(value);
		return y + SET_H;
	}

	private int renderCheckItem(GuiGraphics graphics, Module module, Setting<?> setting, String label, Object value, boolean selected, int x, int y, int width, int mouseX, int mouseY, float dt, int alpha) {
		int itemX = x + 10;
		int itemW = width - 10;
		boolean hovered = this.isH(mouseX, mouseY, itemX, y, itemW, SET_H);
		float hover = this.anim("ci" + module.getName() + setting.getName() + label, hovered ? 1.0f : 0.0f, dt, 14.0f);
		float select = this.anim("cs" + module.getName() + setting.getName() + label, selected ? 1.0f : 0.0f, dt, 12.0f);
		int rowColor = blend(blend(C_SUB, this.dynHover, hover), curSubSel, select);
		RenderUtil.drawRoundedRect(graphics, itemX, y, itemW, 20.0f, 3.0f, wA(rowColor, alpha), false);
		ZenyaFont.draw(graphics, this.font, label, itemX + 8, y + (SET_H - this.font.lineHeight) / 2, wA(blend(C_DIM, C_TEXT, Math.max(hover, select)), alpha), false);

		int boxX = itemX + itemW - 14;
		int boxY = y + 6;
		RenderUtil.drawRoundedRect(graphics, boxX, boxY, 10.0f, 10.0f, 2.0f, wA(selected ? acc() : this.dynTrack, alpha), false);
		if (selected) {
			// Two quads make the tick: the short down-stroke and the long up-stroke.
			graphics.fill(boxX + 2, boxY + 5, boxX + 4, boxY + 7, wA(-16777216, alpha));
			graphics.fill(boxX + 4, boxY + 7, boxX + 8, boxY + 3, wA(-16777216, alpha));
		}
		this.hitR.add(new int[]{itemX, y, itemW, SET_H});
		this.hitH.add(new HitTarget(module, setting, 5));
		this.hitExtra.add(value);
		return y + SET_H;
	}

	private int renderListSearch(GuiGraphics graphics, Module module, Setting<?> setting, int x, int y, int width, int mouseX, int mouseY, int alpha, int clipTop, int clipBottom) {
		if (y + SET_H <= clipTop || y >= clipBottom) {
			return y + SET_H;
		}
		boolean focused = this.listSearchFocus && this.openListSetting == setting;
		int borderColor = focused ? acc() : this.dynSrchBd;
		RenderUtil.drawRoundedRect(graphics, x + 10, y, width - 10, 20.0f, 3.0f, wA(this.dynSrchBg, alpha), false);
		RenderUtil.drawRoundedRect(graphics, x + 9, y - 1, width - 9, 22.0f, 3.0f, wA(borderColor, alpha), false);

		boolean placeholder = this.listSearchBuf.isEmpty() && !focused;
		String text = placeholder ? "Search..." : this.listSearchBuf + (focused && System.nanoTime() / 500000000L % 2L == 0L ? "|" : "");
		int textColor = placeholder ? C_DIM : C_TEXT;
		ZenyaFont.draw(graphics, this.font, text, x + 16, y + (SET_H - this.font.lineHeight) / 2, wA(textColor, alpha), false);

		this.hitR.add(new int[]{x + 10, y, width - 10, SET_H});
		this.hitH.add(new HitTarget(module, setting, 99));
		this.hitExtra.add(null);
		return y + SET_H;
	}

	/** The tooltip for the hovered module, parked just above the scale bar. */
	private void renderHoverDescription(GuiGraphics graphics) {
		Module module = this.hoveredModule;
		if (module == null) {
			return;
		}
		String description = module.getDescription();
		if (description == null || description.isEmpty()) {
			return;
		}
		int maxW = Math.min(360, this.width - 40);
		List<String> lines = wrapText(this.font, description, maxW - 24);
		if (lines.isEmpty()) {
			return;
		}
		int boxH = lines.size() * (this.font.lineHeight + 1) + 16;
		int widest = 0;
		for (String line : lines) {
			int lineW = ZenyaFont.width(this.font, line);
			if (lineW > widest) {
				widest = lineW;
			}
		}
		int boxW = Math.min(maxW, widest + 24);
		int boxX = this.width / 2 - boxW / 2;
		int anchorY = this.scaleBarBounds[3] > 0 ? this.scaleBarBounds[1] : this.height - 60;
		int boxY = anchorY - boxH - 6;
		if (boxY < 4) {
			boxY = 4;
		}
		RenderUtil.drawRoundedRect(graphics, boxX - 1, boxY - 1, boxW + 2, boxH + 2, 8.0f, this.dynSrchBd, false);
		RenderUtil.drawRoundedRect(graphics, boxX, boxY, boxW, boxH, 7.0f, this.dynSrchBg, false);
		int lineY = boxY + 8;
		for (String line : lines) {
			int lineW = ZenyaFont.width(this.font, line);
			ZenyaFont.draw(graphics, this.font, line, boxX + (boxW - lineW) / 2, lineY, C_TEXT, false);
			lineY += this.font.lineHeight + 1;
		}
	}

	/** The GUI scale slider; it sits above the search bar and follows its offset. */
	private void renderScaleBar(GuiGraphics graphics, int mouseX, int mouseY) {
		if (scaleCollapsed) {
			this.scaleBarBounds[0] = 0;
			this.scaleBarBounds[1] = 0;
			this.scaleBarBounds[2] = 0;
			this.scaleBarBounds[3] = 0;
			return;
		}
		int searchH = Math.max(20, searchDynH);
		int searchY = this.height - searchH - 50 + searchOY;
		searchY = clamp(searchY, 4, this.height - searchH - 50);
		int x = this.width / 2 - SCALE_W / 2 + scaleBarOX;
		int y = searchY - SCALE_H - 8 + scaleBarOY;
		x = clamp(x, 4, this.width - SCALE_W - 4);
		y = clamp(y, 4, this.height - SCALE_H - 4);
		this.scaleBarBounds[0] = x;
		this.scaleBarBounds[1] = y;
		this.scaleBarBounds[2] = SCALE_W;
		this.scaleBarBounds[3] = SCALE_H;

		RenderUtil.drawRoundedRect(graphics, x - 1, y - 1, SCALE_W + 2, SCALE_H + 2, 15.0f, this.scaleDragging ? wA(acc(), 170) : this.dynSrchBd, false);
		RenderUtil.drawRoundedRect(graphics, x, y, SCALE_W, SCALE_H, 14.0f, this.dynSrchBg, false);

		// Small "<" and ">" glyphs bracketing the track.
		int glyphX = x + 7;
		int glyphY = y + SCALE_H / 2 - 4;
		graphics.fill(glyphX, glyphY, glyphX + 4, glyphY + 2, C_DIM);
		graphics.fill(glyphX, glyphY, glyphX + 2, glyphY + 8, C_DIM);
		graphics.fill(glyphX, glyphY + 6, glyphX + 4, glyphY + 8, C_DIM);
		glyphX = x + SCALE_W - 12;
		glyphY = y + SCALE_H / 2 - 6;
		graphics.fill(glyphX, glyphY, glyphX + 6, glyphY + 2, C_MUTED);
		graphics.fill(glyphX + 4, glyphY, glyphX + 6, glyphY + 12, C_MUTED);
		graphics.fill(glyphX, glyphY + 10, glyphX + 6, glyphY + 12, C_MUTED);

		int trackX = x + 18;
		int trackY = y + SCALE_H / 2 - 2;
		RenderUtil.drawRoundedRect(graphics, trackX, trackY, 126.0f, 4.0f, 2.0f, this.dynTrack, false);
		float frac = (guiScale - 0.3f) / 0.7f;
		int filledW = (int) (126.0f * frac);
		if (filledW > 0) {
			RenderUtil.drawRoundedRect(graphics, trackX, trackY, filledW, 4.0f, 2.0f, acc(), false);
		}
		int knobX = trackX + filledW;
		boolean knobHovered = Math.abs(mouseX - knobX) < 10 && Math.abs(mouseY - (trackY + 2)) < 10;
		int knobColor = this.scaleDragging || knobHovered ? acc() : -1;
		RenderUtil.drawRoundedRect(graphics, knobX - 6, trackY + 2 - 6, 12.0f, 12.0f, 6.0f, knobColor, false);

		String label = (int) (guiScale * 100.0f) + "%";
		int labelW = ZenyaFont.width(this.font, label);
		ZenyaFont.draw(graphics, this.font, label, trackX + 63 - labelW / 2, trackY - this.font.lineHeight - 2, C_MUTED, false);
	}

	/** The search field plus the theme and scale-bar toggle buttons flanking it. */
	private void renderSearchBar(GuiGraphics graphics, int mouseX, int mouseY) {
		int barW = Math.max(120, searchDynW);
		int barH = Math.max(20, searchDynH);
		int x = this.width / 2 - barW / 2 + searchOX;
		int y = this.height - barH - 50 + searchOY;
		x = clamp(x, 4, this.width - barW - 4);
		y = clamp(y, 4, this.height - barH - 50);
		this.searchBounds[0] = x;
		this.searchBounds[1] = y;
		this.searchBounds[2] = barW;
		this.searchBounds[3] = barH;

		RenderUtil.drawRoundedRect(graphics, x - 1, y - 1, barW + 2, barH + 2, 19.0f, this.searchFocus ? wA(acc(), 187) : this.dynSrchBd, false);
		RenderUtil.drawRoundedRect(graphics, x, y, barW, barH, 18.0f, this.dynSrchBg, false);

		// The drag grip was dropped; zeroing it keeps its hit test inert.
		this.searchGrip[0] = 0;
		this.searchGrip[1] = 0;
		this.searchGrip[2] = 0;
		this.searchGrip[3] = 0;

		boolean placeholder = this.search.isEmpty() && !this.searchFocus;
		String text = placeholder ? "Search for module..." : this.search + (this.searchFocus && System.nanoTime() / 500000000L % 2L == 0L ? "|" : "");
		int textColor = placeholder ? C_DIM : C_TEXT;
		ZenyaFont.draw(graphics, this.font, text, x + 8 + 2, y + (barH - this.font.lineHeight) / 2 + 1, textColor, false);

		int resizerX = x + barW - 14 - 4;
		this.searchResizer[0] = resizerX;
		this.searchResizer[1] = y;
		this.searchResizer[2] = 14;
		this.searchResizer[3] = barH;
		boolean resizerActive = this.isH(mouseX, mouseY, resizerX, y, 14, barH) || this.searchResizing;
		int resizerColor = resizerActive ? acc() : C_DIM;
		int gripX = resizerX + 7;
		int gripY = y + barH / 2;
		for (int offset = -3; offset <= 3; offset += 3) {
			graphics.fill(gripX + offset, gripY - 4, gripX + offset + 1, gripY + 5, resizerColor);
		}

		int themeBtnW = barH;
		int themeBtnX = x - themeBtnW - 6;
		this.themeBtnBounds[0] = themeBtnX;
		this.themeBtnBounds[1] = y;
		this.themeBtnBounds[2] = themeBtnW;
		this.themeBtnBounds[3] = barH;
		boolean themeHovered = this.isH(mouseX, mouseY, themeBtnX, y, themeBtnW, barH);
		int themeFill = themeHovered ? blend(-15066598, acc(), 0.25f) : this.dynSrchBg | 0xFF000000;
		int themeBorder = themesOpen ? acc() : (themeHovered ? wA(acc(), 170) : this.dynSrchBd);
		RenderUtil.drawRoundedRect(graphics, themeBtnX - 1, y - 1, themeBtnW + 2, barH + 2, 19.0f, themeBorder, false);
		RenderUtil.drawRoundedRect(graphics, themeBtnX, y, themeBtnW, barH, 18.0f, themeFill, false);
		// Three dots as a palette icon.
		int dotAlpha = themesOpen || themeHovered ? 255 : 150;
		int themeCx = themeBtnX + themeBtnW / 2;
		int themeCy = y + barH / 2;
		RenderUtil.drawRoundedRect(graphics, themeCx - 7, themeCy - 4, 5.0f, 5.0f, 2.5f, wA(acc(), dotAlpha), false);
		RenderUtil.drawRoundedRect(graphics, themeCx + 2, themeCy - 4, 5.0f, 5.0f, 2.5f, wA(accDim(), dotAlpha), false);
		RenderUtil.drawRoundedRect(graphics, themeCx - 3, themeCy + 2, 5.0f, 5.0f, 2.5f, wA(-2236963, dotAlpha), false);

		int resizeBtnX = x + barW + 6;
		this.resizeBtnBounds[0] = resizeBtnX;
		this.resizeBtnBounds[1] = y;
		this.resizeBtnBounds[2] = barH;
		this.resizeBtnBounds[3] = barH;
		boolean resizeHovered = this.isH(mouseX, mouseY, resizeBtnX, y, barH, barH);
		int resizeFill = resizeHovered ? blend(this.dynSrchBg, acc(), 0.25f) : this.dynSrchBg | 0xFF000000;
		int resizeBorder = scaleCollapsed ? acc() : (resizeHovered ? wA(acc(), 170) : this.dynSrchBd);
		RenderUtil.drawRoundedRect(graphics, resizeBtnX - 1, y - 1, barH + 2, barH + 2, 19.0f, resizeBorder, false);
		RenderUtil.drawRoundedRect(graphics, resizeBtnX, y, barH, barH, 18.0f, resizeFill, false);
		// Two hollow squares joined by a bar: the "resize" glyph.
		int resizeCy = y + barH / 2;
		int resizeCx = resizeBtnX + barH / 2;
		int glyphColor = scaleCollapsed ? acc() : (resizeHovered ? acc() : C_MUTED);
		graphics.fill(resizeCx - 8, resizeCy - 4, resizeCx - 3, resizeCy + 4, glyphColor);
		graphics.fill(resizeCx - 7, resizeCy - 3, resizeCx - 4, resizeCy + 3, this.dynSrchBg & 0xFFFFFF | 0xFF000000);
		graphics.fill(resizeCx + 3, resizeCy - 4, resizeCx + 8, resizeCy + 4, glyphColor);
		graphics.fill(resizeCx + 4, resizeCy - 3, resizeCx + 7, resizeCy + 3, this.dynSrchBg & 0xFFFFFF | 0xFF000000);
		graphics.fill(resizeCx - 3, resizeCy - 1, resizeCx + 3, resizeCy + 1, glyphColor);
	}

	/** The modal theme picker; it draws in scaled space and eats every click. */
	private void renderThemesOverlay(GuiGraphics graphics, int mouseX, int mouseY, float dt) {
		int viewW = Math.round((float) this.width / guiScale);
		int viewH = Math.round((float) this.height / guiScale);
		int boxW = Math.min(500, viewW - 20);
		int boxH = Math.min(380, viewH - 20);
		int boxX = viewW / 2 - boxW / 2;
		int boxY = viewH / 2 - boxH / 2;
		this.themesOverlayBounds[0] = boxX;
		this.themesOverlayBounds[1] = boxY;
		this.themesOverlayBounds[2] = boxW;
		this.themesOverlayBounds[3] = boxH;
		this.themesHitR.clear();
		this.themesHitA.clear();

		float open = this.anim("thOverlay", 1.0f, dt, 16.0f);
		int alpha = (int) (255.0f * ease(open));
		graphics.fill(0, 0, viewW, viewH, wA(-15987438, (int) (255.0f * ease(open))));
		for (int ring = 10; ring >= 1; --ring) {
			int shadow = (int) (18.0f * ease(open) * (1.0f - (float) ring / 11.0f));
			RenderUtil.drawRoundedRect(graphics, boxX - ring, boxY - ring, boxW + ring * 2, boxH + ring * 2, RADIUS + ring, shadow << 24, false);
		}
		RenderUtil.drawRoundedRect(graphics, boxX - 1, boxY - 1, boxW + 2, boxH + 2, 19.0f, wA(acc(), (int) (50.0f * ease(open))), false);
		RenderUtil.drawRoundedRect(graphics, boxX, boxY, boxW, boxH, 18.0f, wA(curPanel, alpha), false);
		RenderUtil.drawRoundedRect(graphics, boxX, boxY, boxW, 38.0f, 18.0f, 18.0f, 0.0f, 0.0f, false, wA(curHeader, alpha));
		RenderUtil.drawRoundedRect(graphics, boxX, boxY + 8, 3.0f, 22.0f, 2.0f, wA(acc(), alpha), false);
		ZenyaFont.draw(graphics, this.font, "THEMES", boxX + 14, boxY + (38 - this.font.lineHeight) / 2 + 1, wA(C_TEXT, alpha), false);

		// Hand-plotted "X" close button.
		int closeX = boxX + boxW - 16 - 10;
		int closeY = boxY + 11;
		boolean closeHovered = this.isH(mouseX, mouseY, closeX - 2, closeY - 2, 20, 20);
		float closeHover = this.anim("thClose", closeHovered ? 1.0f : 0.0f, dt, 14.0f);
		int closeColor = wA(blend(C_DIM, C_TEXT, closeHover), alpha);
		graphics.fill(closeX, closeY, closeX + 2, closeY + 2, closeColor);
		graphics.fill(closeX + 14, closeY, closeX + 16, closeY + 2, closeColor);
		graphics.fill(closeX + 2, closeY + 2, closeX + 4, closeY + 4, closeColor);
		graphics.fill(closeX + 12, closeY + 2, closeX + 14, closeY + 4, closeColor);
		graphics.fill(closeX + 4, closeY + 4, closeX + 6, closeY + 6, closeColor);
		graphics.fill(closeX + 10, closeY + 4, closeX + 12, closeY + 6, closeColor);
		graphics.fill(closeX + 6, closeY + 6, closeX + 10, closeY + 8, closeColor);
		graphics.fill(closeX + 4, closeY + 8, closeX + 6, closeY + 10, closeColor);
		graphics.fill(closeX + 10, closeY + 8, closeX + 12, closeY + 10, closeColor);
		graphics.fill(closeX + 2, closeY + 10, closeX + 4, closeY + 12, closeColor);
		graphics.fill(closeX + 12, closeY + 10, closeX + 14, closeY + 12, closeColor);
		graphics.fill(closeX, closeY + 12, closeX + 2, closeY + 14, closeColor);
		graphics.fill(closeX + 14, closeY + 12, closeX + 16, closeY + 14, closeColor);
		this.themesHitR.add(new int[]{closeX - 2, closeY - 2, 20, 20});
		this.themesHitA.add(() -> {
			themesOpen = false;
			this.anims.remove("thOverlay");
		});

		graphics.fill(boxX, boxY + 38, boxX + boxW, boxY + 38 + 1, wA(this.dynSrchBd, alpha));

		int cardW = (boxW - 28 - 20) / 3;
		int listTop = boxY + 38 + 1;
		int listH = boxH - 38 - 1;
		graphics.enableScissor(boxX, listTop, boxX + boxW, listTop + listH);
		List<Themes.Theme> themes = Themes.ALL;
		String activeName = Themes.getInstance() != null ? Themes.getInstance().selectedSetting().getValue() : "Dark";
		int rows = (themes.size() + 3 - 1) / 3;
		int totalH = rows * 102 + 14;
		int maxScroll = Math.max(0, totalH - listH);
		themesScroll = clamp(themesScroll, 0, maxScroll);
		int gridTop = listTop + 14 - themesScroll;
		for (int i = 0; i < themes.size(); ++i) {
			Themes.Theme theme = themes.get(i);
			int cardX = boxX + 14 + i % 3 * (cardW + 10);
			int cardY = gridTop + i / 3 * 102;
			boolean active = theme.name().equalsIgnoreCase(activeName);
			boolean hovered = this.isH(mouseX, mouseY, cardX, cardY, cardW, 92);
			float hover = this.anim("th-h-" + theme.name(), hovered ? 1.0f : 0.0f, dt, 14.0f);
			float selected = this.anim("th-s-" + theme.name(), active ? 1.0f : 0.0f, dt, 14.0f);
			if (cardY + 92 >= listTop && cardY < listTop + listH) {
				this.drawThemeCard(graphics, cardX, cardY, cardW, 92, theme, hover, selected, alpha);
			}
			// Hit rectangles are registered even off-screen so the indices stay aligned.
			int index = i;
			this.themesHitR.add(new int[]{cardX, cardY, cardW, 92});
			this.themesHitA.add(() -> {
				Themes.apply(themes.get(index));
				themesOpen = false;
				this.anims.remove("thOverlay");
				this.openListSetting = null;
				this.openColor = null;
			});
		}
		graphics.disableScissor();

		if (maxScroll > 0) {
			float shown = (float) listH / (float) totalH;
			int thumbH = Math.max(16, (int) ((float) listH * shown));
			int thumbY = listTop + (int) ((float) (listH - thumbH) * (float) themesScroll / (float) maxScroll);
			RenderUtil.drawRoundedRect(graphics, boxX + boxW - 5, thumbY, 3.0f, thumbH, 1.5f, wA(0xFFFFFF, (int) (80.0f * ease(open))), false);
		}
	}

	private void drawThemeCard(GuiGraphics graphics, int x, int y, int width, int height, Themes.Theme theme, float hover, float selected, int alpha) {
		if (selected > 0.01f) {
			for (int ring = 0; ring < 8; ++ring) {
				int glow = (int) (16.0f * selected * ((float) alpha / 255.0f) * (1.0f - (float) ring / 9.0f));
				int inset = (ring + 1) * 2;
				RenderUtil.drawRoundedRect(graphics, x - inset, y - inset, width + inset * 2, height + inset * 2, 6 + inset, glow << 24 | acc() & 0xFFFFFF, false);
			}
		}
		int cardColor = blend(curHeader, blend(curHeader, -1, 0.07f), hover);
		RenderUtil.drawRoundedRect(graphics, x, y, width, height, 6.0f, wA(cardColor, alpha), false);
		if (selected > 0.01f) {
			int borderAlpha = (int) (255.0f * selected * (float) alpha / 255.0f);
			RenderUtil.drawRoundedRect(graphics, x - 2, y - 2, width + 4, height + 4, 8.0f, wA(acc(), borderAlpha), false);
			RenderUtil.drawRoundedRect(graphics, x, y, width, height, 6.0f, wA(cardColor, alpha), false);
		}

		int swatchX = x + 6;
		int swatchY = y + 8;
		int swatchTotalW = width - 12;
		int swatchCount = theme.palette().length;
		int swatchW = swatchTotalW / swatchCount;
		for (int i = 0; i < swatchCount; ++i) {
			int cellX = swatchX + i * swatchW;
			// The last swatch absorbs the rounding remainder.
			int cellW = i == swatchCount - 1 ? swatchX + swatchTotalW - cellX : swatchW;
			float radiusTopLeft = i == 0 ? 4.0f : 0.0f;
			float radiusBottomLeft = i == 0 ? 4.0f : 0.0f;
			float radiusTopRight = i == swatchCount - 1 ? 4.0f : 0.0f;
			float radiusBottomRight = i == swatchCount - 1 ? 4.0f : 0.0f;
			int color = theme.palette()[i];
			graphics.fill(cellX + (i == 0 ? 1 : 0), swatchY + 1, cellX + cellW - (i == swatchCount - 1 ? 1 : 0), swatchY + 34 - 1, color);
			for (int pass = 0; pass < 3; ++pass) {
				RenderUtil.drawRoundedRect(graphics, cellX, swatchY, cellW, 34.0f, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, false, color);
			}
		}

		int textX = x + 6;
		int textY = swatchY + 34 + 8;
		ZenyaFont.draw(graphics, this.font, theme.name(), textX, textY, wA(C_TEXT, alpha), false);
		ZenyaFont.draw(graphics, this.font, theme.description(), textX, textY + this.font.lineHeight + 3, wA(C_MUTED, alpha), false);

		if (selected > 0.01f) {
			// A filled circle with a hand-plotted tick, marking the active theme.
			int badgeX = x + width - 12 - 16;
			int badgeY = y + 10;
			int badgeAlpha = (int) (255.0f * selected * (float) alpha / 255.0f);
			for (int pass = 0; pass < 4; ++pass) {
				RenderUtil.drawRoundedRect(graphics, badgeX, badgeY, 16.0f, 16.0f, 8.0f, wA(-1, badgeAlpha), false);
			}
			int tickX = badgeX + 8;
			int tickY = badgeY + 8 + 1;
			int tickColor = wA(-15460577, badgeAlpha);
			for (int i = 0; i < 3; ++i) {
				int px = tickX - 3 + i;
				int py = tickY - 1 + i;
				graphics.fill(px, py, px + 2, py + 2, tickColor);
			}
			for (int i = 0; i < 5; ++i) {
				int px = tickX - 1 + i;
				int py = tickY + 1 - i;
				graphics.fill(px, py, px + 2, py + 2, tickColor);
			}
		}
	}

	/** A pill switch; {@code on} is the eased 0..1 progress, not a boolean. */
	private void toggle(GuiGraphics graphics, int x, int y, int width, int height, float on) {
		int trackColor = blend(this.dynTrack, acc(), on);
		RenderUtil.drawRoundedRect(graphics, x, y, width, height, (float) height / 2.0f, trackColor, false);
		int knobX = x + 2 + (int) ((float) (width - height + 2) * ease(on));
		int knobSize = height - 4;
		RenderUtil.drawRoundedRect(graphics, knobX, y + 2, knobSize, knobSize, (float) knobSize / 2.0f, -1, false);
	}

	/** Panel header arrow: points up once {@code collapse} passes the halfway mark. */
	private void chevron(GuiGraphics graphics, int centerX, int centerY, float collapse, int color) {
		boolean up = collapse > 0.5f;
		for (int i = 0; i <= 5; ++i) {
			int px = centerX - 5 + i;
			int py = up ? centerY + 2 - i : centerY - 2 + i;
			graphics.fill(px, py, px + 2, py + 2, color);
		}
		for (int i = 0; i <= 5; ++i) {
			int px = centerX + i;
			int py = up ? centerY - 3 + i : centerY + 3 - i;
			graphics.fill(px, py, px + 2, py + 2, color);
		}
	}

	private void miniChevron(GuiGraphics graphics, int x, int y, boolean open, int color) {
		if (open) {
			graphics.fill(x - 3, y - 1, x - 2, y, color);
			graphics.fill(x - 2, y, x - 1, y + 1, color);
			graphics.fill(x - 1, y + 1, x, y + 2, color);
			graphics.fill(x, y, x + 1, y + 1, color);
			graphics.fill(x + 1, y - 1, x + 2, y, color);
		} else {
			graphics.fill(x - 3, y + 1, x - 2, y + 2, color);
			graphics.fill(x - 2, y, x - 1, y + 1, color);
			graphics.fill(x - 1, y - 1, x, y, color);
			graphics.fill(x, y, x + 1, y + 1, color);
			graphics.fill(x + 1, y + 1, x + 2, y + 2, color);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		this.bindListening = null;
		this.bindListeningSetting = null;
		this.strFocus = null;
		int button = event.button();
		int rawX = (int) event.x();
		int rawY = (int) event.y();
		int scaledX = Math.round((float) rawX / guiScale);
		int scaledY = Math.round((float) rawY / guiScale);

		if (this.isH(rawX, rawY, this.themeBtnBounds[0], this.themeBtnBounds[1], this.themeBtnBounds[2], this.themeBtnBounds[3])) {
			themesOpen = !themesOpen;
			if (!themesOpen) {
				this.anims.remove("thOverlay");
			}
			return true;
		}
		if (themesOpen) {
			// Back to front: the last card drawn is the one on top.
			for (int i = this.themesHitR.size() - 1; i >= 0; --i) {
				int[] rect = this.themesHitR.get(i);
				if (!this.isH(scaledX, scaledY, rect[0], rect[1], rect[2], rect[3])) {
					continue;
				}
				this.themesHitA.get(i).run();
				return true;
			}
			if (!this.isH(scaledX, scaledY, this.themesOverlayBounds[0], this.themesOverlayBounds[1], this.themesOverlayBounds[2], this.themesOverlayBounds[3])) {
				themesOpen = false;
				this.anims.remove("thOverlay");
			}
			return true;
		}
		if (this.isH(rawX, rawY, this.resizeBtnBounds[0], this.resizeBtnBounds[1], this.resizeBtnBounds[2], this.resizeBtnBounds[3])) {
			scaleCollapsed = !scaleCollapsed;
			if (scaleCollapsed) {
				this.scaleDragging = false;
			}
			return true;
		}
		if (!scaleCollapsed && this.isH(rawX, rawY, this.scaleBarBounds[0], this.scaleBarBounds[1], this.scaleBarBounds[2], this.scaleBarBounds[3])) {
			int trackX = this.scaleBarBounds[0] + 20;
			int knobX = trackX + (int) (124.0f * (guiScale - 0.3f) / 0.7f);
			if (Math.abs(rawX - knobX) < 16) {
				this.scaleDragging = true;
				this.applyScaleAt(rawX);
			}
			return true;
		}
		if (this.isH(rawX, rawY, this.searchGrip[0], this.searchGrip[1], this.searchGrip[2], this.searchGrip[3])) {
			return true;
		}
		if (this.isH(rawX, rawY, this.searchResizer[0], this.searchResizer[1], this.searchResizer[2], this.searchResizer[3])) {
			this.searchResizing = true;
			this.searchResizeStartX = rawX;
			this.searchResizeStartW = searchDynW;
			this.searchResizeStartY = rawY;
			this.searchResizeStartH = searchDynH;
			return true;
		}
		if (this.isH(rawX, rawY, this.searchBounds[0], this.searchBounds[1], this.searchBounds[2], this.searchBounds[3])) {
			this.searchFocus = true;
			this.listSearchFocus = false;
			this.strFocus = null;
			return true;
		}
		this.searchFocus = false;

		if (this.strFocus != null && button == 0) {
			this.commitStringEdit(this.strFocus);
			this.strFocus = null;
			return true;
		}
		if (this.openColor != null && button == 0) {
			if (this.isH(scaledX, scaledY, this.cSvX, this.cSvY, this.cSvW, this.cSvH)) {
				this.cDrag = CDrag.SV;
				this.applyPickerAt(scaledX, scaledY);
				return true;
			}
			if (this.isH(scaledX, scaledY, this.cHueX, this.cHueY, this.cHueW, this.cHueH)) {
				this.cDrag = CDrag.HUE;
				this.applyPickerAt(scaledX, scaledY);
				return true;
			}
			if (this.isH(scaledX, scaledY, this.cAlX, this.cAlY, this.cAlW, this.cAlH)) {
				this.cDrag = CDrag.ALPHA;
				this.applyPickerAt(scaledX, scaledY);
				return true;
			}
		}

		for (int panel = 0; panel < N; ++panel) {
			if (!this.isH(scaledX, scaledY, this.px[panel], this.py[panel], PANEL_W, HEADER_H)) {
				continue;
			}
			// Only the chevron corner collapses; the rest of the header starts a drag.
			if (button == 0 && scaledX >= this.px[panel] + PANEL_W - 32) {
				this.collapsed[panel] = !this.collapsed[panel];
				this.openListSetting = null;
				this.openColor = null;
			}
			return true;
		}

		for (int i = this.hitR.size() - 1; i >= 0; --i) {
			int[] rect = this.hitR.get(i);
			if (scaledX < rect[0] || scaledX >= rect[0] + rect[2] || scaledY < rect[1] || scaledY >= rect[1] + rect[3]) {
				continue;
			}
			HitTarget target = this.hitH.get(i);
			Object extra = this.hitExtra.get(i);
			switch (target.kind()) {
				case 1 -> {
					Module module = target.mod();
					if (button == 1) {
						this.toggleExpanded(module);
					} else if (module instanceof Themes) {
						themesOpen = true;
					} else if (!(module instanceof ZenyaPlus)) {
						// ZenyaPlus has nothing to toggle, so left-clicking it does nothing.
						module.toggle();
					}
				}
				case 2, 9 -> {
					this.sliderDrag = target.set();
					this.applySliderAt(scaledX);
				}
				case 3, 10 -> {
					@SuppressWarnings("unchecked")
					Setting<Boolean> flag = (Setting<Boolean>) target.set();
					flag.setValue(!Boolean.TRUE.equals(flag.getValue()));
				}
				case 4 -> {
					if (target.set() instanceof ActionSetting action) {
						action.trigger();
					}
				}
				case 5 -> this.handleListItem(target.mod(), target.set(), extra);
				case 6 -> {
					@SuppressWarnings("unchecked")
					Setting<Color> color = (Setting<Color>) target.set();
					this.openColor = this.openColor == color ? null : color;
					this.cDrag = CDrag.NONE;
					this.openListSetting = null;
				}
				case 7 -> {
					if (target.mod() instanceof Friends && target.set().matchesName("Names")) {
						this.minecraft.setScreen(new FriendsPickerScreen(this));
					} else {
						this.strFocus = target.set();
						this.strBuf = String.valueOf(target.set().getValue());
						this.searchFocus = false;
					}
				}
				case 8 -> {
					if (target.set() instanceof ThresholdSetting threshold) {
						threshold.setEnabled(!threshold.isEnabled());
					}
				}
				case 99 -> {
					this.listSearchFocus = true;
					this.listSearchBuf = "";
					this.searchFocus = false;
				}
				case 100 -> this.openListSettingAt(target);
				case 200 -> this.bindListening = this.bindListening == target.mod() ? null : target.mod();
				case 201 -> {
					@SuppressWarnings("unchecked")
					Setting<Integer> bind = (Setting<Integer>) target.set();
					this.bindListeningSetting = bind;
				}
				case 300 -> {
					if (target.set() instanceof StorageBlocksSetting storage && extra instanceof String value) {
						if (button == 1) {
							if (storage.isSelected(value)) {
								Setting<Color> color = storage.colorSettingFor(value);
								this.openColor = this.openColor == color ? null : color;
								this.cDrag = CDrag.NONE;
							}
						} else {
							storage.toggle(value);
							if (!storage.isSelected(value) && this.openColor == storage.colorSettingFor(value)) {
								this.openColor = null;
							}
						}
					}
				}
				case 301 -> {
					if (target.set() instanceof StorageBlocksSetting storage && extra instanceof String value) {
						Setting<Color> color = storage.colorSettingFor(value);
						this.openColor = this.openColor == color ? null : color;
						this.cDrag = CDrag.NONE;
					}
				}
				default -> {
				}
			}
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	/** Expanding a row closes any open list or picker so only one is ever open. */
	private void toggleExpanded(Module module) {
		boolean wasExpanded = this.isExpanded(module);
		this.expanded.put(module, !wasExpanded);
		if (!wasExpanded) {
			this.openListSetting = null;
			this.openColor = null;
		}
	}

	@SuppressWarnings("unchecked")
	private void commitStringEdit(Setting<?> setting) {
		((Setting<String>) setting).setValue(this.strBuf);
	}

	/** List headers either open a dedicated picker screen or expand in place. */
	private void openListSettingAt(HitTarget target) {
		Setting<?> setting = target.set();
		if (setting instanceof StorageBlocksSetting storage) {
			this.minecraft.setScreen(new StoragePickerScreen(this, storage));
			return;
		}
		if (setting instanceof BlocksSetting blocks) {
			Module owner = target.mod();
			if (owner instanceof BlockESP esp) {
				this.minecraft.setScreen(new BlockPickerScreen(this, blocks, esp));
			} else {
				ZenyaClient.LOGGER.warn("[BlockPicker] Couldn't open: module is {} (not BlockESP)", owner == null ? "null" : owner.getClass().getSimpleName());
			}
			return;
		}
		if (setting instanceof MobsSetting mobs) {
			Module owner = target.mod();
			if (owner instanceof MobESP esp) {
				this.minecraft.setScreen(new MobPickerScreen(this, mobs, esp));
			} else {
				ZenyaClient.LOGGER.warn("[MobPicker] Couldn't open: module is {} (not MobESP)", owner == null ? "null" : owner.getClass().getSimpleName());
			}
			return;
		}
		if (this.openListSetting == setting) {
			this.openListSetting = null;
			this.listSearchBuf = "";
			this.listSearchFocus = false;
			return;
		}
		this.openListSetting = setting;
		this.listSearchBuf = "";
		this.listSearchFocus = false;
		this.openColor = null;
	}

	/** Applies a click on a list item to whichever collection setting owns it. */
	private void handleListItem(Module module, Setting<?> setting, Object value) {
		if (setting instanceof ModeSetting mode && value instanceof String name) {
			mode.setValue(name);
			return;
		}
		if (setting instanceof OptionSelectSetting select && value instanceof String name) {
			select.select(name);
			return;
		}
		if (setting instanceof OptionMultiSelectSetting multi && value instanceof String name) {
			multi.toggle(name);
			return;
		}
		if (setting instanceof BlocksSetting blocks && value instanceof Block block) {
			blocks.toggle(block);
			return;
		}
		if (setting instanceof MobsSetting mobs && value instanceof EntityType<?> type) {
			mobs.toggle(type);
		}
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (event.button() == 0) {
			this.sliderDrag = null;
			this.cDrag = CDrag.NONE;
			this.scaleDragging = false;
			this.scaleBarDragging = false;
			this.searchDragging = false;
			this.searchResizing = false;
			if (this.dragPanel >= 0) {
				// Persist the dragged panel's origin so it survives a reopen.
				SPX[this.dragPanel] = this.px[this.dragPanel];
				SPY[this.dragPanel] = this.py[this.dragPanel];
				this.dragPanel = -1;
			}
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		int button = event.button();
		int rawX = (int) event.x();
		int rawY = (int) event.y();
		int scaledX = Math.round((float) rawX / guiScale);
		int scaledY = Math.round((float) rawY / guiScale);
		if (button != 0) {
			return false;
		}
		if (this.scaleDragging && !scaleCollapsed) {
			this.applyScaleAt(rawX);
			return true;
		}
		this.scaleDragging = false;
		if (this.scaleBarDragging && !scaleCollapsed) {
			scaleBarOX = rawX - this.scaleBarDragStartX;
			scaleBarOY = rawY - this.scaleBarDragStartY;
			return true;
		}
		if (this.searchDragging) {
			searchOX = rawX - this.searchDragStartX;
			searchOY = rawY - this.searchDragStartY;
			return true;
		}
		if (this.searchResizing) {
			int deltaX = rawX - this.searchResizeStartX;
			int deltaY = rawY - this.searchResizeStartY;
			// The bar is centred, so it grows twice as fast as the cursor moves.
			searchDynW = Math.max(120, Math.min(500, this.searchResizeStartW + deltaX * 2));
			searchDynH = Math.max(20, Math.min(60, this.searchResizeStartH + deltaY));
			return true;
		}
		if (this.openColor != null && this.cDrag != CDrag.NONE) {
			this.applyPickerAt(scaledX, scaledY);
			return true;
		}
		if (this.openColor != null) {
			if (this.isH(scaledX, scaledY, this.cSvX, this.cSvY, this.cSvW, this.cSvH)) {
				this.cDrag = CDrag.SV;
				this.applyPickerAt(scaledX, scaledY);
				return true;
			}
			if (this.isH(scaledX, scaledY, this.cHueX, this.cHueY, this.cHueW, this.cHueH)) {
				this.cDrag = CDrag.HUE;
				this.applyPickerAt(scaledX, scaledY);
				return true;
			}
			if (this.isH(scaledX, scaledY, this.cAlX, this.cAlY, this.cAlW, this.cAlH)) {
				this.cDrag = CDrag.ALPHA;
				this.applyPickerAt(scaledX, scaledY);
				return true;
			}
		}
		if (this.sliderDrag != null) {
			this.applySliderAt(scaledX);
			return true;
		}
		if (this.dragPanel >= 0) {
			this.px[this.dragPanel] = scaledX - this.dragOx;
			this.py[this.dragPanel] = scaledY - this.dragOy;
			return true;
		}
		for (int panel = 0; panel < N; ++panel) {
			if (!this.isH(scaledX, scaledY, this.px[panel], this.py[panel], PANEL_W, HEADER_H)) {
				continue;
			}
			this.dragPanel = panel;
			this.dragOx = scaledX - this.px[panel];
			this.dragOy = scaledY - this.py[panel];
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int overlayX = Math.round((float) mouseX / guiScale);
		int overlayY = Math.round((float) mouseY / guiScale);
		if (themesOpen && this.isH(overlayX, overlayY, this.themesOverlayBounds[0], this.themesOverlayBounds[1], this.themesOverlayBounds[2], this.themesOverlayBounds[3])) {
			themesScroll = Math.max(0, themesScroll - (int) (scrollY * 12.0));
			return true;
		}
		int panelX = Math.round((float) mouseX / guiScale);
		int panelY = Math.round((float) mouseY / guiScale);
		for (int panel = 0; panel < N; ++panel) {
			int panelH = HEADER_H + (this.collapsed[panel] ? 0 : Math.min(this.contentH(panel), MAX_VIS_H));
			if (!this.isH(panelX, panelY, this.px[panel], this.py[panel], PANEL_W, panelH)) {
				continue;
			}
			this.scroll[panel] = clamp(this.scroll[panel] - (int) (scrollY * 12.0), 0, this.maxSc[panel]);
			return true;
		}
		// Swallowed either way so the world never scrolls behind the GUI.
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (this.bindListening != null) {
			// Escape clears the bind rather than cancelling the capture.
			int bind = key == 256 ? 0 : key;
			this.bindListening.setBind(bind);
			if (this.bindListening instanceof ActivatableModule activatable) {
				activatable.setActivationKey(bind);
			}
			this.bindListening = null;
			return true;
		}
		if (key == 256) {
			// Escape unwinds one layer of state per press before closing the screen.
			if (themesOpen) {
				themesOpen = false;
				this.anims.remove("thOverlay");
				return true;
			}
			if (this.openColor != null) {
				this.openColor = null;
				return true;
			}
			if (this.openListSetting != null) {
				this.openListSetting = null;
				this.listSearchBuf = "";
				return true;
			}
			if (this.listSearchFocus) {
				this.listSearchFocus = false;
				return true;
			}
			if (this.strFocus != null) {
				this.strFocus = null;
				return true;
			}
			if (this.searchFocus) {
				this.searchFocus = false;
				return true;
			}
			this.minecraft.setScreen(null);
			return true;
		}
		if (this.bindListeningSetting != null) {
			this.bindListeningSetting.setValue(key == 256 ? 0 : key);
			this.bindListeningSetting = null;
			return true;
		}
		if (this.strFocus != null) {
			if (event.isPaste()) {
				this.strBuf = this.strBuf + this.getClipboardText();
				return true;
			}
			if (key == 259 && !this.strBuf.isEmpty()) {
				this.strBuf = this.strBuf.substring(0, this.strBuf.length() - 1);
				return true;
			}
			if (key == 257) {
				this.commitStringEdit(this.strFocus);
				this.strFocus = null;
				return true;
			}
			return true;
		}
		if (this.listSearchFocus) {
			if (event.isPaste()) {
				this.listSearchBuf = this.listSearchBuf + this.getClipboardText();
				return true;
			}
			if (key == 259 && !this.listSearchBuf.isEmpty()) {
				this.listSearchBuf = this.listSearchBuf.substring(0, this.listSearchBuf.length() - 1);
				return true;
			}
			return true;
		}
		if (this.searchFocus) {
			if (event.isPaste()) {
				this.search = this.search + this.getClipboardText();
				return true;
			}
			if (key == 259 && !this.search.isEmpty()) {
				this.search = this.search.substring(0, this.search.length() - 1);
				return true;
			}
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		int codepoint = event.codepoint();
		if (codepoint >= 32 && codepoint < 127) {
			if (this.strFocus != null) {
				this.strBuf = this.strBuf + (char) codepoint;
				return true;
			}
			if (this.listSearchFocus) {
				this.listSearchBuf = this.listSearchBuf + (char) codepoint;
				return true;
			}
			if (this.searchFocus) {
				this.search = this.search + (char) codepoint;
				return true;
			}
		}
		return false;
	}

	@Override
	public void onClose() {
		System.arraycopy(this.px, 0, SPX, 0, N);
		System.arraycopy(this.py, 0, SPY, 0, N);
		super.onClose();
	}

	/** Clipboard text with control characters stripped, so paste can't inject newlines. */
	private String getClipboardText() {
		String raw = Minecraft.getInstance().keyboardHandler.getClipboard();
		if (raw == null || raw.isEmpty()) {
			return "";
		}
		StringBuilder cleaned = new StringBuilder(raw.length());
		raw.codePoints().filter(codepoint -> !Character.isISOControl(codepoint)).forEach(cleaned::appendCodePoint);
		return cleaned.toString();
	}

	/** The modules a panel shows, already filtered by the search box. */
	private List<Module> modsFor(int panel) {
		Category category = panel == OTHER_IDX ? Category.CLIENT : ORDER[panel];
		List<Module> matches = new ArrayList<>();
		for (Module module : ModuleManager.INSTANCE.getModules()) {
			if (module.getCategory() != category || !this.search.isEmpty() && !module.getName().toLowerCase(Locale.ROOT).contains(this.search.toLowerCase(Locale.ROOT))) {
				continue;
			}
			matches.add(module);
		}
		return matches;
	}

	private boolean isExpanded(Module module) {
		return Boolean.TRUE.equals(this.expanded.get(module));
	}

	/**
	 * Height of a module's settings body. Must stay in step with the render
	 * methods, which advance by exactly these amounts.
	 */
	private int settingsHeight(Module module) {
		int height = SET_H;
		for (Setting<?> setting : module.getSettings()) {
			if (!setting.isVisible()) {
				continue;
			}
			if (setting instanceof SectionSetting || setting instanceof ActionSetting || setting instanceof ConfirmBooleanSetting
					|| setting.getValue() instanceof Boolean || setting.getValue() instanceof Color
					|| setting instanceof ModeSetting && this.openListSetting != setting
					|| setting instanceof OptionSelectSetting && this.openListSetting != setting
					|| setting.getValue() instanceof String) {
				height += SET_H;
				if (setting.getValue() instanceof Color && this.openColor == setting) {
					height += CP_TOTAL;
				}
				continue;
			}
			if (setting instanceof ThresholdSetting threshold) {
				height += SET_H;
				if (!threshold.isEnabled()) {
					continue;
				}
				height += SLIDER_H;
				continue;
			}
			if (setting.getValue() instanceof Double || setting.getValue() instanceof Float
					|| setting.getValue() instanceof Integer && !(setting instanceof ThresholdSetting)) {
				height += SLIDER_H;
				continue;
			}
			if (setting instanceof ModeSetting mode && this.openListSetting == setting) {
				height += SET_H + mode.getModes().size() * SET_H;
				continue;
			}
			if (setting instanceof OptionSelectSetting select && this.openListSetting == setting) {
				height += SET_H + select.getOptions().size() * SET_H;
				continue;
			}
			if (setting instanceof OptionMultiSelectSetting multi && this.openListSetting == setting) {
				height += SET_H + (SET_H + Math.min(multi.getOptions().size(), 20) * SET_H);
				continue;
			}
			if (setting instanceof BlocksSetting) {
				height += SET_H;
				continue;
			}
			if (setting instanceof MobsSetting) {
				height += SET_H;
				continue;
			}
			if (setting instanceof StorageBlocksSetting storage) {
				height += SET_H;
				if (this.openListSetting != setting) {
					continue;
				}
				int rows = (storage.getOptions().size() + STORAGE_GRID_COLS - 1) / STORAGE_GRID_COLS;
				height += rows * 31;
				int selected = storage.getSelectedEntries().size();
				if (selected <= 0) {
					continue;
				}
				height += SET_H + selected * SET_H;
				// At most one entry's picker can be open, so stop at the first match.
				for (StorageBlocksSetting.Entry entry : storage.getSelectedEntries()) {
					if (this.openColor == storage.colorSettingFor(entry.value())) {
						height += CP_TOTAL;
						break;
					}
				}
				continue;
			}
			height += SET_H;
		}
		return height;
	}

	/** Unclipped height of a panel's rows, used for scroll hit testing. */
	private int contentH(int panel) {
		int height = 0;
		for (Module module : this.modsFor(panel)) {
			height += MOD_H;
			if (!this.isExpanded(module)) {
				continue;
			}
			height += this.settingsHeight(module);
		}
		return height;
	}

	private void applySliderAt(int mouseX) {
		if (this.sliderDrag == null) {
			return;
		}
		double frac = Math.max(0.0, Math.min(1.0, (double) (mouseX - this.slTrkX) / (double) this.slTrkW));
		this.applyFrac(this.sliderDrag, frac);
	}

	private void applyScaleAt(int mouseX) {
		int trackX = this.scaleBarBounds[0] + 20;
		float frac = (float) Math.max(0.0, Math.min(1.0, (double) (mouseX - trackX) / (double) 124));
		guiScale = 0.3f + frac * 0.7f;
		// Snapped to whole percent so the readout never shows drift.
		guiScale = (float) Math.round(guiScale * 100.0f) / 100.0f;
	}

	/** Maps a 0..1 track position onto the setting's own numeric type. */
	@SuppressWarnings("unchecked")
	private <T> void applyFrac(Setting<T> setting, double frac) {
		Object value = setting.getValue();
		if (value instanceof Double) {
			double min = ((Number) setting.getMin()).doubleValue();
			double max = ((Number) setting.getMax()).doubleValue();
			((Setting<Double>) setting).setValue((double) Math.round((min + (max - min) * frac) * 100.0) / 100.0);
		} else if (value instanceof Float) {
			float min = ((Number) setting.getMin()).floatValue();
			float max = ((Number) setting.getMax()).floatValue();
			((Setting<Float>) setting).setValue((float) Math.round((min + (max - min) * (float) frac) * 100.0f) / 100.0f);
		} else if (value instanceof Integer) {
			int min = ((Number) setting.getMin()).intValue();
			int max = ((Number) setting.getMax()).intValue();
			((Setting<Integer>) setting).setValue((int) Math.round((double) min + (double) (max - min) * frac));
		}
	}

	private void applyPickerAt(int mouseX, int mouseY) {
		if (this.openColor == null) {
			return;
		}
		switch (this.cDrag) {
			case SV -> {
				this.cHSV[1] = clamp01((float) (mouseX - this.cSvX) / (float) this.cSvW);
				this.cHSV[2] = 1.0f - clamp01((float) (mouseY - this.cSvY) / (float) this.cSvH);
			}
			case HUE -> this.cHSV[0] = clamp01((float) (mouseX - this.cHueX) / (float) this.cHueW);
			case ALPHA -> this.cAlpha = (int) (clamp01((float) (mouseX - this.cAlX) / (float) this.cAlW) * 255.0f);
			case NONE -> {
			}
		}
		int rgb = Color.HSBtoRGB(this.cHSV[0], this.cHSV[1], this.cHSV[2]);
		this.openColor.setValue(new Color(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, this.cAlpha));
	}

	private void applyPickerAt(int mouseX) {
		this.applyPickerAt(mouseX, 0);
	}

	private boolean isH(int mouseX, int mouseY, int x, int y, int width, int height) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	/** Replaces a colour's alpha channel, keeping its RGB. */
	private static int wA(int argb, int alpha) {
		return alpha << 24 | argb & 0xFFFFFF;
	}

	private static int blend(int from, int to, float t) {
		int fromR = from >> 16 & 0xFF;
		int fromG = from >> 8 & 0xFF;
		int fromB = from & 0xFF;
		int fromA = from >> 24 & 0xFF;
		int toR = to >> 16 & 0xFF;
		int toG = to >> 8 & 0xFF;
		int toB = to & 0xFF;
		int toA = to >> 24 & 0xFF;
		return (int) ((float) fromA + (float) (toA - fromA) * t) << 24
				| (int) ((float) fromR + (float) (toR - fromR) * t) << 16
				| (int) ((float) fromG + (float) (toG - fromG) * t) << 8
				| (int) ((float) fromB + (float) (toB - fromB) * t);
	}

	private static float ease(float t) {
		return t * t * (3.0f - 2.0f * t);
	}

	/** Frame-rate independent approach: the step size scales with the delta time. */
	private static float exp(float current, float target, float dt, float speed) {
		return current + (target - current) * (1.0f - (float) Math.exp(-speed * dt));
	}

	private static float clamp01(float value) {
		return Math.max(0.0f, Math.min(1.0f, value));
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	/** Advances the animation stored under {@code key}, creating it on first use. */
	private float anim(Object key, float target, float dt, float speed) {
		float[] state = this.anims.computeIfAbsent(key, unused -> new float[]{target});
		state[0] = exp(state[0], target, dt, speed);
		return state[0];
	}

	/** Greedy word wrap; a single word longer than {@code maxWidth} still overflows. */
	private static List<String> wrapText(Font font, String text, int maxWidth) {
		List<String> lines = new ArrayList<>(4);
		if (text == null || text.isEmpty() || maxWidth <= 0) {
			return lines;
		}
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String candidate = line.length() == 0 ? word : line + " " + word;
			if (ZenyaFont.width(font, candidate) <= maxWidth) {
				if (line.length() > 0) {
					line.append(' ');
				}
				line.append(word);
				continue;
			}
			if (line.length() > 0) {
				lines.add(line.toString());
			}
			line.setLength(0);
			line.append(word);
		}
		if (line.length() > 0) {
			lines.add(line.toString());
		}
		return lines;
	}

	/** Drops the decimals from whole numbers so sliders read "3" and not "3.00". */
	private static String fmtNum(double value) {
		if (value == (double) ((long) value)) {
			return String.valueOf((long) value);
		}
		return String.format("%.2f", value);
	}

	/** Which colour-picker band the mouse grabbed. */
	private enum CDrag {
		NONE,
		SV,
		HUE,
		ALPHA
	}

	/**
	 * One clickable rectangle's payload. {@code kind} selects the click handler:
	 * 1 module row, 2/9 slider, 3/10 boolean, 4 action, 5 list item, 6 colour,
	 * 7 string, 8 threshold toggle, 99 list search, 100 list header, 200/201 bind,
	 * 300/301 storage entry.
	 */
	private record HitTarget(Module mod, Setting<?> set, int kind) {
	}
}
