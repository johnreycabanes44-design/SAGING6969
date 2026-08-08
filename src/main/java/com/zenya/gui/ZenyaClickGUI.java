package com.zenya.gui;

import com.zenya.module.ActivatableModule;
import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.module.ModuleManager;
import com.zenya.module.modules.client.Themes;
import com.zenya.module.modules.client.ZenyaPlus;
import com.zenya.setting.ActionSetting;
import com.zenya.setting.ConfirmBooleanSetting;
import com.zenya.setting.ModeSetting;
import com.zenya.setting.SectionSetting;
import com.zenya.setting.Setting;
import com.zenya.sound.SoundManager;
import com.zenya.utils.ConfigStore;
import com.zenya.utils.ZenyaFont;
import com.zenya.utils.renderer.RenderUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The panel click GUI: category sidebar on the left, and on the right either the module
 * grid, one module's settings, or the cloud-config page.
 *
 * <p>Nothing is retained between frames but selection, scroll and animation state. Every
 * rect is recomputed while rendering, and the {@code hover*} fields written there are what
 * the mouse handlers hit-test against, so a click always acts on the last frame drawn.
 *
 * <p>All layout numbers are in unscaled UI space; the render pass scales by {@link #uiScale},
 * so mouse coordinates are divided by it before any comparison.
 */
public class ZenyaClickGUI extends Screen {
	private static final int PANEL_W = 900;
	private static final int PANEL_H = 600;
	private static final int SIDEBAR_W = 210;
	private static final int HEADER_H = 64;
	private static final int CARD_H = 56;
	private static final int CARD_GAP = 10;
	private static final int CONTENT_PAD = 16;
	private static final float PANEL_RADIUS = 22.0f;
	private static final float CARD_RADIUS = 14.0f;
	private static final int COLOR_DIM = -2013265920;
	private static final int COLOR_DIVIDER = -13290182;
	private static final int COLOR_CARD_BG = -14408662;
	private static final int COLOR_CARD_HOVER = -12430744;
	private static final int COLOR_CARD_ENABLED = -8446691;
	private static final int COLOR_TEXT = -986896;
	private static final int COLOR_TEXT_MUTED = -5197632;
	private static final int COLOR_TEXT_DIM = -7829351;
	private static final int COLOR_ACCENT_STRONG = -495247;
	private static final int COLOR_ACCENT_BG = -4645860;
	private static final int COLOR_ACCENT_BG_SOFT = -8446691;
	private static final int COLOR_CHIP_BG = -10853256;
	private static final int COLOR_SEARCH_BG = -11708821;
	private static final int COLOR_KNOB_OFF = -1;
	private static final int COLOR_ROW_BORDER = -12760478;
	private static final Identifier MODULE_ENABLE_SOUND = Identifier.fromNamespaceAndPath("zenya", "module_enable");
	private static final Identifier MODULE_DISABLE_SOUND = Identifier.fromNamespaceAndPath("zenya", "module_disable");
	private static final Identifier SOUND_GUI_OPEN = Identifier.fromNamespaceAndPath("zenya", "gui_open");
	private static final Identifier SOUND_GUI_CLOSE = Identifier.fromNamespaceAndPath("zenya", "gui_close");
	private static final Category[] CATEGORY_ORDER = Category.values();
	/** Animation slots per key: hover, select/enable, toggle knob, header knob. */
	private static final int ANIM_SLOTS = 6;
	private static final Object GLOBAL_KEY = new Object();
	/** Modules that get their own sidebar entry instead of a card in the grid. */
	private static final Set<String> OTHER_MODULE_NAMES = Set.of("hud", "friends", "cloud configs");

	/** Recomputed every frame from the active theme, so it is not final. */
	private static int COLOR_ACCENT = -1096636;
	private static ZenyaClickGUI lastInstance;

	private Category selectedCategory = Category.COMBAT;
	private String searchQuery = "";
	private boolean searchActive = false;
	private int scrollY = 0;
	private float uiScale = 1.0f;
	private final IdentityHashMap<Object, float[]> anims = new IdentityHashMap<>();
	private long lastFrameNanos = 0L;
	private float deltaSec = 0.0f;
	private long openedAtNanos = 0L;
	private Module settingsTarget = null;
	private long settingsOpenedAtNanos = 0L;
	private int settingsScrollY = 0;
	private boolean configsView = false;
	private long configsOpenedAtNanos = 0L;
	private String configNameBuffer = "";
	private boolean configNameFocused = false;
	private String configShareBuffer = "";
	private boolean configShareFocused = false;
	private int configsListScroll = 0;
	private String configsToast = null;
	private long configsToastShownAt = 0L;
	private Runnable configActionToTrigger = null;
	/** Parallel lists: rect {x, y, w, h} at index i is clicked by action i. Rebuilt each frame. */
	private final List<int[]> configsButtonRects = new ArrayList<>();
	private final List<Runnable> configsButtonActions = new ArrayList<>();
	private boolean listeningBind = false;
	private boolean listeningActivationBind = false;
	private Setting<?> draggingSlider = null;
	private Setting<Color> expandedColorSetting = null;
	private long colorPickerOpenedAtNanos = 0L;
	private final float[] pickerHSV = new float[3];
	private int pickerAlpha = 255;
	private ColorDragMode colorDragMode = ColorDragMode.NONE;
	private int picSvX;
	private int picSvY;
	private int picSvW;
	private int picSvH;
	private int picHueX;
	private int picHueY;
	private int picHueW;
	private int picHueH;
	private int picAlphaX;
	private int picAlphaY;
	private int picAlphaW;
	private int picAlphaH;
	private Category hoverCategory;
	private OtherAction hoverOther;
	private Module hoverModule;
	private boolean hoverBackButton = false;
	private Setting<?> hoverSetting = null;
	private SettingHitKind hoverSettingKind = SettingHitKind.NONE;
	private int hoverSettingX;
	private int hoverSettingY;
	private int hoverSettingW;
	private int hoverSettingH;
	private Setting<String> focusedStringSetting = null;
	private boolean openingSoundPlayed = false;

	private static int COLOR_PANEL_BG() {
		return -585228766;
	}

	private static int COLOR_SIDEBAR_BG() {
		return -584768214;
	}

	/** Frame timing for every animation in this screen; {@link #deltaSec} is capped so a lag spike does not snap. */
	private void tickAnimations() {
		long now = System.nanoTime();
		if (this.lastFrameNanos == 0L) {
			this.lastFrameNanos = now;
		}
		if (this.openedAtNanos == 0L) {
			this.openedAtNanos = now;
		}
		this.deltaSec = Math.min(0.1f, (float) (now - this.lastFrameNanos) / 1.0E9f);
		this.lastFrameNanos = now;
	}

	/** Eases slot {@code slot} of {@code key} towards {@code target}; keys are compared by identity. */
	private float animate(Object key, int slot, float target, float speed) {
		float[] slots = this.anims.computeIfAbsent(key, unused -> new float[ANIM_SLOTS]);
		float step = 1.0f - (float) Math.exp(-this.deltaSec * speed);
		slots[slot] += (target - slots[slot]) * step;
		return slots[slot];
	}

	private float animValue(Object key, int slot) {
		float[] slots = this.anims.get(key);
		return slots == null ? 0.0f : slots[slot];
	}

	private float openedFor() {
		return (float) (System.nanoTime() - this.openedAtNanos) / 1.0E9f;
	}

	/** Back-eased 0..1 curve, overshooting slightly before settling. */
	private static float easeOut(float progress) {
		if (progress <= 0.0f) {
			return 0.0f;
		}
		if (progress >= 1.0f) {
			return 1.0f;
		}
		return 1.0f + 2.80158f * (float) Math.pow(progress - 1.0f, 3.0) + 1.70158f * (float) Math.pow(progress - 1.0f, 2.0);
	}

	/** Lets the ESP renderers know their picker screen is on top of this one. */
	public static boolean isBlockOrStorageEspOpen() {
		return lastInstance != null && lastInstance.settingsTarget != null
				&& (lastInstance.settingsTarget.getName().equals("Block ESP")
						|| lastInstance.settingsTarget.getName().equals("Storage ESP"));
	}

	public ZenyaClickGUI() {
		super(Component.literal("Frost Client"));
	}

	@Override
	protected void init() {
		super.init();
		lastInstance = this;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** The panel paints its own dim layer, so vanilla must not draw or blur behind it. */
	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
	}

	@Override
	protected void renderBlurredBackground(GuiGraphics graphics) {
	}

	private float computeUiScale() {
		return 1.1944444f;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		this.tickAnimations();
		this.uiScale = this.computeUiScale();
		int scaledMouseX = Math.round(mouseX / this.uiScale);
		int scaledMouseY = Math.round(mouseY / this.uiScale);
		COLOR_ACCENT = Themes.isRainbow() ? Themes.rainbowAt(0, 0.05f) : ZenyaPlus.getAccentARGB();
		if (!this.openingSoundPlayed && this.openedFor() > 0.02f) {
			this.playCustomSound(SOUND_GUI_OPEN, 1.0f, 1.0f);
			this.openingSoundPlayed = true;
		}

		float fade = Math.min(1.0f, this.openedFor() / 0.2f);
		float grow = easeOut(Math.min(1.0f, this.openedFor() / 0.3f));
		int dimColor = ((int) (89.0f * fade) & 0xFF) << 24;
		graphics.pose().pushMatrix();
		graphics.pose().scale(this.uiScale, this.uiScale);

		int scaledWidth = Math.round(this.width / this.uiScale);
		int scaledHeight = Math.round(this.height / this.uiScale);
		graphics.fill(0, 0, scaledWidth, scaledHeight, dimColor);

		int panelX = (scaledWidth - PANEL_W) / 2;
		int panelY = (scaledHeight - PANEL_H) / 2 + (int) ((1.0f - grow) * 40.0f);
		float centerX = panelX + PANEL_W / 2.0f;
		float centerY = panelY + PANEL_H / 2.0f;
		graphics.pose().translate(centerX, centerY);
		graphics.pose().scale(grow, grow);
		graphics.pose().translate(-centerX, -centerY);
		RenderUtil.drawRoundedRect(graphics, panelX, panelY, PANEL_W, PANEL_H,
				0.0f, PANEL_RADIUS, PANEL_RADIUS, 0.0f, false, COLOR_PANEL_BG());
		RenderUtil.drawRoundedRect(graphics, panelX, panelY, SIDEBAR_W, PANEL_H,
				PANEL_RADIUS, 0.0f, 0.0f, PANEL_RADIUS, false, COLOR_SIDEBAR_BG());

		this.hoverCategory = null;
		this.hoverOther = null;
		this.hoverModule = null;
		this.hoverBackButton = false;
		this.hoverSetting = null;
		this.hoverSettingKind = SettingHitKind.NONE;
		this.renderSidebar(graphics, panelX, panelY, scaledMouseX, scaledMouseY);

		int contentX = panelX + SIDEBAR_W;
		if (this.configsView) {
			float slide = easeOut(Math.min(1.0f, (float) (System.nanoTime() - this.configsOpenedAtNanos) / 3.0E8f));
			graphics.pose().pushMatrix();
			graphics.pose().translate((int) ((1.0f - slide) * 24.0f), 0.0f);
			RenderUtil.drawRoundedRect(graphics, contentX, panelY, 690, PANEL_H,
					0.0f, PANEL_RADIUS, PANEL_RADIUS, 0.0f, false, COLOR_PANEL_BG());
			this.renderConfigsHeader(graphics, contentX, panelY, scaledMouseX, scaledMouseY);
			this.renderConfigsPanel(graphics, contentX, panelY + HEADER_H, scaledMouseX, scaledMouseY);
			graphics.pose().popMatrix();
		} else if (this.settingsTarget != null) {
			float slide = easeOut(Math.min(1.0f, (float) (System.nanoTime() - this.settingsOpenedAtNanos) / 3.0E8f));
			graphics.pose().pushMatrix();
			graphics.pose().translate((int) ((1.0f - slide) * 24.0f), 0.0f);
			RenderUtil.drawRoundedRect(graphics, contentX, panelY, 690, PANEL_H,
					0.0f, PANEL_RADIUS, PANEL_RADIUS, 0.0f, false, COLOR_PANEL_BG());
			this.renderSettingsHeader(graphics, contentX, panelY, scaledMouseX, scaledMouseY);
			this.renderSettingsPanel(graphics, contentX, panelY + HEADER_H, scaledMouseX, scaledMouseY);
			graphics.pose().popMatrix();
		} else {
			this.renderHeader(graphics, contentX, panelY, scaledMouseX, scaledMouseY);
			this.renderModuleGrid(graphics, contentX, panelY + HEADER_H, scaledMouseX, scaledMouseY);
		}
		graphics.pose().popMatrix();
	}

	private void renderSidebar(GuiGraphics graphics, int panelX, int panelY, int mouseX, int mouseY) {
		int listHeight = CATEGORY_ORDER.length * 40 - 6;
		int itemY = panelY + (PANEL_H - listHeight) / 2;
		for (Category category : CATEGORY_ORDER) {
			boolean selected = category == this.selectedCategory;
			boolean hovered = isHover(mouseX, mouseY, panelX + 12, itemY, 186, 34);
			if (hovered) {
				if (this.hoverCategory == null) {
					this.playClickSound(0.05f, 1.2f);
				}
				this.hoverCategory = category;
			}
			this.renderSidebarItem(graphics, panelX + 12, itemY, 186, 34,
					category.getName(), selected, hovered, category.getIconShape());
			itemY += 40;
		}
	}

	private void playCustomSound(Identifier sound, float volume, float pitch) {
		SoundManager.play(sound, volume, pitch);
	}

	/** Hover/click feedback is deliberately silent; only the open and close sounds play. */
	private void playClickSound(float volume, float pitch) {
	}

	private static ItemStack otherIcon(OtherAction action) {
		return switch (action) {
			case FRIENDS -> new ItemStack((ItemLike) Items.PLAYER_HEAD);
			case CONFIGS -> new ItemStack((ItemLike) Items.WRITABLE_BOOK);
			case HUD -> new ItemStack((ItemLike) Items.ITEM_FRAME);
		};
	}

	private void renderSidebarItem(GuiGraphics graphics, int x, int y, int width, int height, String label,
			boolean selected, boolean hovered, String iconShape) {
		float hoverAnim = this.animate(label, 0, hovered ? 1.0f : 0.0f, 18.0f);
		int hoverTint = (int) (hoverAnim * 255.0f) << 24 | 0x425268;
		float selectAnim = this.animate(label, 1, selected ? 1.0f : 0.0f, 18.0f);
		int background = blend(hoverTint, COLOR_ACCENT_BG, selectAnim);
		if ((background >>> 24 & 0xFF) > 0) {
			RenderUtil.drawRoundedRect(graphics, x, y, width, height, CARD_RADIUS, background, false);
		}
		if (selectAnim > 0.01f) {
			int markerHeight = (int) (height * 0.5f * selectAnim);
			int markerY = y + (height - markerHeight) / 2;
			graphics.fill(x + 2, markerY, x + 4, markerY + markerHeight, COLOR_ACCENT);
		}
		int iconLeft = x + 14 + (int) (hoverAnim * 3.0f);
		int iconTop = y + height / 2 - 9;
		CategoryIconRenderer.draw(graphics, iconLeft, iconTop, 18, iconShape, -1);
		int textColor = blend(COLOR_TEXT, -1, selectAnim);
		ZenyaFont.draw(graphics, this.font, label, iconLeft + 18 + 8,
				y + height / 2 - this.font.lineHeight / 2 + 1, textColor, false);
	}

	private static void drawSearchIcon(GuiGraphics graphics, int x, int y, int size, int color) {
		drawIconCircle(graphics, x, y, size, color, 10.0f, 10.0f, 5.0f);
		drawIconLine(graphics, x, y, size, color, 14.0f, 14.0f, 20.0f, 20.0f);
	}

	/** Icon primitives are authored on a 24x24 grid and scaled to {@code size}. */
	private static void drawIconCircle(GuiGraphics graphics, int x, int y, int size, int color,
			float centerX, float centerY, float radius) {
		float scale = size / 24.0f;
		float diameter = radius * 2.0f * scale;
		RenderUtil.drawArc(graphics, x + (centerX - radius) * scale, y + (centerY - radius) * scale, diameter,
				Math.max(1.0f, size / 12.0f), 360.0f, 0.0f, color, false);
	}

	private static void drawIconLine(GuiGraphics graphics, int x, int y, int size, int color,
			float fromX, float fromY, float toX, float toY) {
		int startX = iconX(x, size, fromX);
		int startY = iconY(y, size, fromY);
		int endX = iconX(x, size, toX);
		int endY = iconY(y, size, toY);
		int deltaX = endX - startX;
		int deltaY = endY - startY;
		int steps = Math.max(1, Math.max(Math.abs(deltaX), Math.abs(deltaY)));
		float thickness = Math.max(1.6f, size / 8.5f);
		for (int step = 0; step <= steps; ++step) {
			float progress = (float) step / steps;
			drawStrokeDot(graphics, Math.round(startX + deltaX * progress), Math.round(startY + deltaY * progress),
					thickness, color);
		}
	}

	private static int iconX(int x, int size, float gridX) {
		return x + Math.round(gridX / 24.0f * size);
	}

	private static int iconY(int y, int size, float gridY) {
		return y + Math.round(gridY / 24.0f * size);
	}

	private static void drawStrokeDot(GuiGraphics graphics, int x, int y, float size, int color) {
		float half = size * 0.5f;
		RenderUtil.drawRoundedRect(graphics, x - half, y - half, size, size, half, color, false);
	}

	private static void drawSidebarIcon(GuiGraphics graphics, int x, int y, int size, int color, IconShape shape) {
		switch (shape) {
			case SQUARE -> graphics.fill(x, y, x + size, y + size, color);
			case RING -> {
				drawDot(graphics, x, y, size, color);
				int hole = Math.max(2, size - 6);
				int holeX = x + (size - hole) / 2;
				int holeY = y + (size - hole) / 2;
				graphics.fill(holeX, holeY, holeX + hole, holeY + hole, COLOR_PANEL_BG());
			}
			case CIRCLE -> drawDot(graphics, x, y, size, color);
			case DIAMOND -> {
				int half = size / 2;
				for (int row = 0; row < size; ++row) {
					int inset = Math.abs(row - half);
					int left = x + inset;
					int right = x + size - inset;
					if (right <= left) {
						continue;
					}
					graphics.fill(left, y + row, right, y + row + 1, color);
				}
			}
			case STAR -> {
				int arm = Math.max(2, size / 3);
				int offset = (size - arm) / 2;
				graphics.fill(x + offset, y, x + offset + arm, y + size, color);
				graphics.fill(x, y + offset, x + size, y + offset + arm, color);
			}
			case PEOPLE -> {
				int headSize = size - 3;
				drawDot(graphics, x - 1, y + 1, headSize, color);
				drawDot(graphics, x + 4, y + 1, headSize, color);
			}
			case FLOPPY -> {
				graphics.fill(x, y, x + size, y + size, color);
				int labelHeight = Math.max(2, size / 3);
				graphics.fill(x + 2, y + size - labelHeight, x + size - 2, y + size, COLOR_PANEL_BG());
			}
			case GRID -> {
				int cell = (size - 1) / 2;
				graphics.fill(x, y, x + cell, y + cell, color);
				graphics.fill(x + cell + 1, y, x + cell * 2 + 1, y + cell, color);
				graphics.fill(x, y + cell + 1, x + cell, y + cell * 2 + 1, color);
				graphics.fill(x + cell + 1, y + cell + 1, x + cell * 2 + 1, y + cell * 2 + 1, color);
			}
		}
	}

	private void renderHeader(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
		int titleX = x + CONTENT_PAD;
		int titleY = y + 20;
		String title = this.selectedCategory == null ? "Frost Client" : this.selectedCategory.getName();
		ZenyaFont.draw(graphics, this.font, title.toUpperCase(), titleX, titleY, COLOR_TEXT, false);

		int searchX = x + 690 - CONTENT_PAD - 180;
		int searchY = titleY - 4;
		RenderUtil.drawRoundedRect(graphics, searchX, searchY, 180.0f, 24.0f, 8.0f, COLOR_SIDEBAR_BG(), false);
		RenderUtil.drawOutline(graphics, searchX, searchY, 180.0f, 24.0f, 8.0f, 1.0f,
				this.searchActive ? COLOR_ACCENT : COLOR_DIVIDER, false);
		drawSearchIcon(graphics, searchX + 8, searchY + 5, 14, this.searchActive ? -1 : -7367516);

		int queryX = searchX + 30;
		String query = this.searchQuery.isEmpty() ? (this.searchActive ? "" : "Search...") : this.searchQuery;
		int queryColor = this.searchQuery.isEmpty() && !this.searchActive ? COLOR_TEXT_DIM : COLOR_TEXT;
		ZenyaFont.draw(graphics, this.font, query, queryX, searchY + 6, queryColor, false);
		if (this.searchActive && System.currentTimeMillis() / 500L % 2L == 0L) {
			int caretX = queryX + ZenyaFont.width(this.font, this.searchQuery);
			graphics.fill(caretX, searchY + 6, caretX + 1, searchY + 18, COLOR_TEXT);
		}
	}

	private void renderModuleGrid(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
		List<Module> modules = this.visibleModules();
		int gridX = x + CONTENT_PAD;
		int gridY = y + CONTENT_PAD - this.scrollY;
		graphics.enableScissor(x, y, x + 690, y + 536);
		for (int i = 0; i < modules.size(); ++i) {
			Module module = modules.get(i);
			int cardX = gridX + i % 2 * 334;
			int cardY = gridY + i / 2 * (CARD_H + CARD_GAP);
			boolean hovered = isHover(mouseX, mouseY, cardX, cardY, 324, CARD_H);
			if (hovered) {
				this.hoverModule = module;
			}
			this.renderModuleCard(graphics, cardX, cardY, 324, CARD_H, module, hovered);
		}
		if (modules.isEmpty()) {
			String message = this.searchQuery.isEmpty()
					? "No modules in this category."
					: "No modules match \"" + this.searchQuery + "\".";
			ZenyaFont.draw(graphics, this.font, message, gridX, gridY + 12, COLOR_TEXT_DIM, false);
		}
		graphics.disableScissor();

		int rows = (modules.size() + 1) / 2;
		int contentHeight = rows * (CARD_H + CARD_GAP) - CARD_GAP + CONTENT_PAD * 2;
		if (contentHeight > 536) {
			this.scrollY = Math.max(0, Math.min(this.scrollY, contentHeight - 536));
		} else {
			this.scrollY = 0;
		}
	}

	private void renderModuleCard(GuiGraphics graphics, int x, int y, int width, int height, Module module,
			boolean hovered) {
		float hoverAnim = this.animate(module, 0, hovered ? 1.0f : 0.0f, 20.0f);
		float enabledAnim = this.animate(module, 1, module.isEnabled() ? 1.0f : 0.0f, 18.0f);
		if (hovered && hoverAnim < 0.1f) {
			this.playClickSound(0.02f, 1.4f);
		}
		if (hoverAnim > 0.01f) {
			int glowAlpha = (int) (hoverAnim * hoverAnim * 35.0f);
			RenderUtil.drawRoundedRect(graphics, x, y, width, height, CARD_RADIUS, glowAlpha << 24 | 0xFFFFFF, false);
		}
		RenderUtil.drawOutline(graphics, x, y, width, height, CARD_RADIUS, 1.0f,
				blend(-13750732, COLOR_ACCENT, enabledAnim), false);
		if (enabledAnim > 0.01f) {
			int markerHeight = (int) (Math.max(6, height - 20) * enabledAnim);
			int markerY = y + (height - markerHeight) / 2;
			RenderUtil.drawRoundedRect(graphics, x + 4, markerY, 2.5f, markerHeight, 1.25f, COLOR_ACCENT, false);
		}

		int titleColor = blend(COLOR_TEXT, COLOR_ACCENT, Math.max(enabledAnim, hoverAnim * 0.25f));
		int textX = x + CONTENT_PAD + (int) (hoverAnim * 3.0f);
		ZenyaFont.draw(graphics, this.font, module.getDisplayName(), textX, y + 11, titleColor, false);
		String description = module.getDescription();
		if (description == null || description.isEmpty()) {
			description = this.defaultDescription(module);
		}
		int descriptionColor = (int) (176.0f + hoverAnim * 32.0f) << 24 | 0xB0B0C0;
		ZenyaFont.draw(graphics, this.font, description, textX, y + 11 + this.font.lineHeight + 5,
				descriptionColor, false);
		// ponytail: pops a matrix this method never pushed, so it eats the caller's transform.
		graphics.pose().popMatrix();
	}

	/** Fallback card subtitle: the display name with camel-case boundaries spaced out. */
	private String defaultDescription(Module module) {
		String name = module.getDisplayName();
		if (name == null || name.isBlank()) {
			return "";
		}
		StringBuilder spaced = new StringBuilder(name.length() + 6);
		for (int i = 0; i < name.length(); ++i) {
			char current = name.charAt(i);
			if (i > 0 && Character.isUpperCase(current) && !Character.isUpperCase(name.charAt(i - 1))) {
				spaced.append(' ');
			}
			spaced.append(current);
		}
		return spaced.toString();
	}

	/** Modules of the selected category that survive the search box, minus the sidebar-only ones. */
	private List<Module> visibleModules() {
		List<Module> visible = new ArrayList<>();
		if (this.selectedCategory == null) {
			return visible;
		}
		String query = this.searchQuery.toLowerCase(Locale.ROOT);
		for (Module module : ModuleManager.INSTANCE.getModules()) {
			if (module.getCategory() != this.selectedCategory
					|| OTHER_MODULE_NAMES.contains(module.getName().toLowerCase(Locale.ROOT))
					|| !query.isEmpty() && !module.getName().toLowerCase(Locale.ROOT).contains(query)
							&& !module.getDisplayName().toLowerCase(Locale.ROOT).contains(query)) {
				continue;
			}
			visible.add(module);
		}
		return visible;
	}
	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int button = event.button();
		double mouseX = event.x() / this.uiScale;
		double mouseY = event.y() / this.uiScale;
		int scaledWidth = Math.round(this.width / this.uiScale);
		int scaledHeight = Math.round(this.height / this.uiScale);
		int panelX = (scaledWidth - PANEL_W) / 2;
		int panelY = (scaledHeight - PANEL_H) / 2;

		int searchX = panelX + 690 + SIDEBAR_W - CONTENT_PAD - 180;
		int searchY = panelY + 20 - 4;
		if (mouseX >= searchX && mouseX <= searchX + 180 && mouseY >= searchY && mouseY <= searchY + 24) {
			this.searchActive = true;
			this.playClickSound(0.1f, 1.0f);
			return true;
		}
		this.searchActive = false;

		if (button == 0) {
			if (this.configsView) {
				if (this.hoverBackButton) {
					this.configsView = false;
					this.configNameFocused = false;
					this.configShareFocused = false;
					return true;
				}
				// Last drawn wins, so walk the rects backwards.
				for (int i = this.configsButtonRects.size() - 1; i >= 0; --i) {
					int[] rect = this.configsButtonRects.get(i);
					if (mouseX >= rect[0] && mouseX < rect[0] + rect[2]
							&& mouseY >= rect[1] && mouseY < rect[1] + rect[3]) {
						Runnable action = this.configsButtonActions.get(i);
						if (action != null) {
							action.run();
						}
						return true;
					}
				}
				this.configNameFocused = false;
				this.configShareFocused = false;
				return true;
			}
			if (this.settingsTarget != null && this.hoverBackButton) {
				this.settingsTarget = null;
				this.listeningBind = false;
				this.draggingSlider = null;
				this.settingsScrollY = 0;
				return true;
			}
			if (this.settingsTarget != null && this.expandedColorSetting != null
					&& this.handlePickerMouseDown((int) mouseX, (int) mouseY)) {
				return true;
			}
			if (this.settingsTarget != null && this.hoverSettingKind != SettingHitKind.NONE) {
				this.clickSetting();
				if (this.hoverSettingKind == SettingHitKind.SLIDER) {
					this.applySliderAt((int) mouseX);
				}
				return true;
			}
			if (this.settingsTarget != null) {
				this.focusedStringSetting = null;
			}
			if (this.hoverCategory != null) {
				this.selectedCategory = this.hoverCategory;
				this.settingsTarget = null;
				this.scrollY = 0;
				return true;
			}
			if (this.hoverOther != null) {
				this.handleOther(this.hoverOther);
				return true;
			}
			if (this.hoverModule != null) {
				this.hoverModule.toggle();
				return true;
			}
		} else if (button == 1 && this.settingsTarget == null && this.hoverModule != null) {
			this.settingsTarget = this.hoverModule;
			this.settingsOpenedAtNanos = System.nanoTime();
			this.settingsScrollY = 0;
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
		if (this.draggingSlider != null) {
			this.applySliderAt((int) (event.x() / this.uiScale));
			return true;
		}
		if (this.colorDragMode != ColorDragMode.NONE) {
			this.applyPickerDrag((int) (event.x() / this.uiScale), (int) (event.y() / this.uiScale));
			return true;
		}
		return super.mouseDragged(event, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		this.draggingSlider = null;
		this.colorDragMode = ColorDragMode.NONE;
		return super.mouseReleased(event);
	}

	private void handleOther(OtherAction action) {
		switch (action) {
			case FRIENDS, HUD -> {
				Module target = this.findModule(action == OtherAction.FRIENDS ? "Friends" : "Hud");
				if (target != null) {
					this.settingsTarget = target;
					this.settingsOpenedAtNanos = System.nanoTime();
					this.settingsScrollY = 0;
					this.listeningBind = false;
					this.draggingSlider = null;
				}
			}
			case CONFIGS -> {
				this.configsView = true;
				this.configsOpenedAtNanos = System.nanoTime();
				this.configsListScroll = 0;
				this.settingsTarget = null;
				this.configNameFocused = false;
				this.configShareFocused = false;
			}
		}
	}

	private Module findModule(String name) {
		for (Module module : ModuleManager.INSTANCE.getModules()) {
			if (name.equalsIgnoreCase(module.getName())) {
				return module;
			}
		}
		return null;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (this.configsView) {
			this.configsListScroll -= (int) (scrollY * 24.0);
			if (this.configsListScroll < 0) {
				this.configsListScroll = 0;
			}
		} else if (this.settingsTarget != null) {
			this.settingsScrollY -= (int) (scrollY * 24.0);
			if (this.settingsScrollY < 0) {
				this.settingsScrollY = 0;
			}
		} else {
			this.scrollY -= (int) (scrollY * 24.0);
			if (this.scrollY < 0) {
				this.scrollY = 0;
			}
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (this.configsView && (this.configNameFocused || this.configShareFocused)) {
			if (key == GLFW.GLFW_KEY_BACKSPACE) {
				if (this.configNameFocused && !this.configNameBuffer.isEmpty()) {
					this.configNameBuffer = this.configNameBuffer.substring(0, this.configNameBuffer.length() - 1);
				} else if (this.configShareFocused && !this.configShareBuffer.isEmpty()) {
					this.configShareBuffer = this.configShareBuffer.substring(0, this.configShareBuffer.length() - 1);
				}
				return true;
			}
			if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) {
				this.configNameFocused = false;
				this.configShareFocused = false;
			}
			return true;
		}
		if (this.configsView && key == GLFW.GLFW_KEY_ESCAPE) {
			this.configsView = false;
			return true;
		}

		if (this.focusedStringSetting != null) {
			String current = this.focusedStringSetting.getValue() == null ? "" : this.focusedStringSetting.getValue();
			if (this.isControlDown() && key == GLFW.GLFW_KEY_C) {
				Minecraft.getInstance().keyboardHandler.setClipboard(current);
				return true;
			}
			if (this.isControlDown() && key == GLFW.GLFW_KEY_X) {
				Minecraft.getInstance().keyboardHandler.setClipboard(current);
				this.focusedStringSetting.setValue("");
				return true;
			}
			if (key == GLFW.GLFW_KEY_BACKSPACE) {
				if (!current.isEmpty()) {
					// Step back a whole code point so surrogate pairs are not split.
					int end = current.offsetByCodePoints(current.length(), -1);
					this.focusedStringSetting.setValue(current.substring(0, end));
				}
				return true;
			}
			if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) {
				this.focusedStringSetting = null;
				return true;
			}
			if (this.isControlDown() && key == GLFW.GLFW_KEY_V) {
				String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
				if (clipboard != null && !clipboard.isEmpty()) {
					this.focusedStringSetting.setValue(
							this.limitStringInput(current + this.sanitizeStringInput(clipboard), this.focusedStringSetting));
				}
				return true;
			}
			return true;
		}

		if (this.listeningBind && this.settingsTarget != null) {
			// Escape clears the bind instead of assigning Escape itself.
			this.settingsTarget.setBind(key == GLFW.GLFW_KEY_ESCAPE ? 0 : key);
			this.listeningBind = false;
			return true;
		}
		if (this.listeningActivationBind && this.settingsTarget instanceof ActivatableModule activatable) {
			activatable.setActivationKey(key == GLFW.GLFW_KEY_ESCAPE ? 0 : key);
			this.listeningActivationBind = false;
			return true;
		}

		if (this.searchActive) {
			if (key == GLFW.GLFW_KEY_BACKSPACE) {
				if (!this.searchQuery.isEmpty()) {
					this.searchQuery = this.searchQuery.substring(0, this.searchQuery.length() - 1);
				}
				return true;
			}
			if (key == GLFW.GLFW_KEY_ESCAPE) {
				this.searchActive = false;
				this.searchQuery = "";
				return true;
			}
			if (key == GLFW.GLFW_KEY_ENTER) {
				this.searchActive = false;
			}
			return true;
		}
		if (key == GLFW.GLFW_KEY_ESCAPE && this.settingsTarget != null) {
			this.settingsTarget = null;
			this.settingsScrollY = 0;
			return true;
		}
		if (key == GLFW.GLFW_KEY_RIGHT_SHIFT || key == GLFW.GLFW_KEY_ESCAPE) {
			this.onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		int codepoint = event.codepoint();
		if (this.configsView && this.configNameFocused) {
			if (codepoint >= 32 && codepoint < 127 && this.configNameBuffer.length() < 48) {
				this.configNameBuffer = this.configNameBuffer + (char) codepoint;
			}
			return true;
		}
		if (this.configsView && this.configShareFocused) {
			if (codepoint >= 32 && codepoint < 127 && this.configShareBuffer.length() < 64) {
				this.configShareBuffer = this.configShareBuffer + (char) codepoint;
			}
			return true;
		}
		if (this.focusedStringSetting != null) {
			if (codepoint >= 32 && codepoint < 127) {
				String current = this.focusedStringSetting.getValue() == null
						? ""
						: this.focusedStringSetting.getValue();
				this.focusedStringSetting.setValue(
						this.limitStringInput(current + (char) codepoint, this.focusedStringSetting));
			}
			return true;
		}
		if (!this.searchActive) {
			return false;
		}
		if (codepoint >= 32 && codepoint < 127 && this.searchQuery.length() < 32) {
			this.searchQuery = this.searchQuery + (char) codepoint;
			return true;
		}
		return false;
	}
	private void renderSettingsHeader(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
		graphics.fill(x + 1, y + HEADER_H, x + 690 - 1, y + HEADER_H + 1, COLOR_DIVIDER);
		int backX = x + CONTENT_PAD;
		int backY = y + 18;
		this.hoverBackButton = isHover(mouseX, mouseY, backX, backY, 28, 28);
		RenderUtil.drawRoundedRect(graphics, backX, backY, 28.0f, 28.0f, 6.0f,
				this.hoverBackButton ? COLOR_CARD_HOVER : COLOR_CARD_BG, false);
		ZenyaFont.draw(graphics, this.font, "<", backX + 14 - 2, backY + 14 - this.font.lineHeight / 2 + 1,
				COLOR_TEXT, false);

		int titleX = backX + 28 + 12;
		ZenyaFont.draw(graphics, this.font, this.settingsTarget.getDisplayName(), titleX,
				y + 32 - this.font.lineHeight - 1, COLOR_TEXT, false);
		String subtitle = this.settingsTarget.getCategory().getName() + " · "
				+ (this.settingsTarget.isEnabled() ? "Enabled" : "Disabled");
		ZenyaFont.draw(graphics, this.font, subtitle, titleX, y + 32 + 2, COLOR_TEXT_MUTED, false);

		int toggleX = x + 690 - CONTENT_PAD - 64;
		int toggleY = y + 20;
		boolean hovered = isHover(mouseX, mouseY, toggleX, toggleY, 64, 24);
		boolean enabled = this.settingsTarget.isEnabled();
		int trackColor = enabled ? COLOR_ACCENT_BG : (hovered ? -10852224 : -11904904);
		RenderUtil.drawRoundedRect(graphics, toggleX, toggleY, 64.0f, 24.0f, 12.0f, trackColor, false);
		float knobAnim = this.animate(this.settingsTarget, 3, enabled ? 1.0f : 0.0f, 18.0f);
		RenderUtil.drawOutline(graphics, toggleX, toggleY, 64.0f, 24.0f, 12.0f, 1.0f,
				blend(COLOR_ROW_BORDER, COLOR_ACCENT_STRONG, knobAnim), false);
		int knobLeft = toggleX + 3;
		int knobRight = toggleX + 64 - 18 - 3;
		int knobX = (int) (knobLeft + (knobRight - knobLeft) * knobAnim);
		drawDot(graphics, knobX, toggleY + 3, 18, blend(COLOR_KNOB_OFF, COLOR_ACCENT, knobAnim));
		if (hovered) {
			this.hoverSettingKind = SettingHitKind.TOGGLE;
			this.hoverSetting = null;
			this.hoverSettingX = toggleX;
			this.hoverSettingY = toggleY;
			this.hoverSettingW = 64;
			this.hoverSettingH = 24;
		}
	}

	private void renderSettingsPanel(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
		int rowX = x + CONTENT_PAD;
		int rowY = y + CONTENT_PAD - this.settingsScrollY;
		graphics.enableScissor(x, y, x + 690, y + 536);
		rowY = this.drawActivateRow(graphics, rowX, rowY, 658, mouseX, mouseY) + 8;
		rowY = this.drawBindRow(graphics, rowX, rowY, 658, mouseX, mouseY) + 8;
		if (this.settingsTarget instanceof ActivatableModule) {
			rowY = this.drawActivationBindRow(graphics, rowX, rowY, 658, mouseX, mouseY) + 8;
		}
		for (Setting<?> setting : this.settingsTarget.getSettings()) {
			if (!setting.isVisible()) {
				continue;
			}
			rowY = this.drawSettingRow(graphics, setting, rowX, rowY, 658, mouseX, mouseY) + 8;
		}
		graphics.disableScissor();

		int contentHeight = rowY + this.settingsScrollY - (y + CONTENT_PAD);
		this.settingsScrollY = contentHeight > 504
				? Math.max(0, Math.min(this.settingsScrollY, contentHeight - 504))
				: 0;
	}

	private int drawActivateRow(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
		boolean enabled = this.settingsTarget.isEnabled();
		boolean hovered = isHover(mouseX, mouseY, x, y, width, 44);
		int background = enabled ? (hovered ? -3196615 : COLOR_ACCENT_BG) : (hovered ? COLOR_CARD_HOVER : COLOR_CARD_BG);
		RenderUtil.drawRoundedRect(graphics, x, y, width, 44.0f, CARD_RADIUS, background, false);
		RenderUtil.drawOutline(graphics, x, y, width, 44.0f, CARD_RADIUS, 1.0f,
				enabled ? COLOR_ACCENT_STRONG : COLOR_ROW_BORDER, false);
		String label = (enabled ? "Click to deactivate " : "Click to activate ") + this.settingsTarget.getDisplayName();
		int labelWidth = ZenyaFont.width(this.font, label);
		ZenyaFont.draw(graphics, this.font, label, x + (width - labelWidth) / 2,
				y + (44 - this.font.lineHeight) / 2 + 1, enabled ? -1 : COLOR_ACCENT, false);
		if (hovered) {
			this.hoverSetting = null;
			this.hoverSettingKind = SettingHitKind.TOGGLE;
			this.hoverSettingX = x;
			this.hoverSettingY = y;
			this.hoverSettingW = width;
			this.hoverSettingH = 44;
		}
		return y + 44;
	}

	private int drawActivationBindRow(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
		ActivatableModule target = (ActivatableModule) this.settingsTarget;
		RenderUtil.drawRoundedRect(graphics, x, y, width, 36.0f, CARD_RADIUS, COLOR_CARD_BG, false);
		RenderUtil.drawOutline(graphics, x, y, width, 36.0f, CARD_RADIUS, 1.0f, COLOR_ROW_BORDER, false);
		ZenyaFont.draw(graphics, this.font, "Activation Key", x + 14, y + 8, COLOR_TEXT, false);
		ZenyaFont.draw(graphics, this.font, "Hold or toggle this module while a key is held", x + 14,
				y + 8 + this.font.lineHeight + 2, COLOR_TEXT_MUTED, false);

		String label = this.listeningActivationBind
				? "..."
				: (target.getActivationKey() == 0 ? "None" : ClickGUI.getKeyDisplayNameStatic(target.getActivationKey()));
		int buttonWidth = Math.max(48, ZenyaFont.width(this.font, label) + 18);
		int buttonX = x + width - 14 - buttonWidth;
		int buttonY = y + 7;
		boolean hovered = isHover(mouseX, mouseY, buttonX, buttonY, buttonWidth, 22);
		float pulse = this.listeningActivationBind ? 0.5f + 0.5f * (float) Math.sin(this.openedFor() * 6.0) : 0.0f;
		int idleColor = hovered ? COLOR_CARD_HOVER : COLOR_SEARCH_BG;
		int listeningColor = blend(COLOR_ACCENT_BG, COLOR_ACCENT, pulse * 0.3f);
		RenderUtil.drawRoundedRect(graphics, buttonX, buttonY, buttonWidth, 22.0f, 11.0f,
				this.listeningActivationBind ? listeningColor : idleColor, false);
		ZenyaFont.draw(graphics, this.font, label, buttonX + (buttonWidth - ZenyaFont.width(this.font, label)) / 2,
				buttonY + (22 - this.font.lineHeight) / 2 + 1,
				this.listeningActivationBind ? COLOR_ACCENT : COLOR_TEXT, false);
		if (hovered) {
			this.hoverSettingKind = SettingHitKind.ACTIVATION_BIND;
			this.hoverSetting = null;
			this.hoverSettingX = buttonX;
			this.hoverSettingY = buttonY;
			this.hoverSettingW = buttonWidth;
			this.hoverSettingH = 22;
		}
		return y + 36;
	}

	private int drawBindRow(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
		RenderUtil.drawRoundedRect(graphics, x, y, width, 36.0f, CARD_RADIUS, COLOR_CARD_BG, false);
		RenderUtil.drawOutline(graphics, x, y, width, 36.0f, CARD_RADIUS, 1.0f, COLOR_ROW_BORDER, false);
		ZenyaFont.draw(graphics, this.font, "Keybind", x + 14, y + 8, COLOR_TEXT, false);
		ZenyaFont.draw(graphics, this.font, "Trigger this module from a key press", x + 14,
				y + 8 + this.font.lineHeight + 2, COLOR_TEXT_MUTED, false);

		String label = this.listeningBind
				? "..."
				: (this.settingsTarget.getBind() == 0
						? "None"
						: ClickGUI.getKeyDisplayNameStatic(this.settingsTarget.getBind()));
		int buttonWidth = Math.max(48, ZenyaFont.width(this.font, label) + 18);
		int buttonX = x + width - 14 - buttonWidth;
		int buttonY = y + 7;
		boolean hovered = isHover(mouseX, mouseY, buttonX, buttonY, buttonWidth, 22);
		float pulse = this.listeningBind ? 0.5f + 0.5f * (float) Math.sin(this.openedFor() * 6.0) : 0.0f;
		int idleColor = hovered ? COLOR_CARD_HOVER : COLOR_SEARCH_BG;
		int listeningColor = blend(COLOR_ACCENT_BG, COLOR_ACCENT, pulse * 0.3f);
		RenderUtil.drawRoundedRect(graphics, buttonX, buttonY, buttonWidth, 22.0f, 11.0f,
				this.listeningBind ? listeningColor : idleColor, false);
		ZenyaFont.draw(graphics, this.font, label, buttonX + (buttonWidth - ZenyaFont.width(this.font, label)) / 2,
				buttonY + (22 - this.font.lineHeight) / 2 + 1, this.listeningBind ? COLOR_ACCENT : COLOR_TEXT, false);
		if (hovered) {
			this.hoverSettingKind = SettingHitKind.BIND;
			this.hoverSetting = null;
			this.hoverSettingX = buttonX;
			this.hoverSettingY = buttonY;
			this.hoverSettingW = buttonWidth;
			this.hoverSettingH = 22;
		}
		return y + 36;
	}
	/**
	 * Draws one setting row and, when the mouse is over an interactive part, records what a click
	 * would hit. The widget is chosen from the runtime type of the value, so a setting needs no
	 * subclass to get the right control.
	 *
	 * @return the y coordinate just below the row
	 */
	private int drawSettingRow(GuiGraphics graphics, Setting<?> setting, int x, int y, int width,
			int mouseX, int mouseY) {
		if (setting instanceof SectionSetting) {
			ZenyaFont.draw(graphics, this.font, setting.getDisplayName().toUpperCase(Locale.ROOT),
					x + 4, y + 4, COLOR_TEXT_DIM, false);
			return y + this.font.lineHeight + 8;
		}

		RenderUtil.drawRoundedRect(graphics, x, y, width, 36.0f, CARD_RADIUS, COLOR_CARD_BG, false);
		RenderUtil.drawOutline(graphics, x, y, width, 36.0f, CARD_RADIUS, 1.0f, COLOR_ROW_BORDER, false);
		ZenyaFont.draw(graphics, this.font, setting.getDisplayName(), x + 14, y + 12, COLOR_TEXT, false);

		if (setting instanceof ActionSetting action) {
			String label = action.getValue();
			int buttonWidth = Math.max(56, ZenyaFont.width(this.font, label) + 18);
			int buttonX = x + width - 14 - buttonWidth;
			int buttonY = y + 7;
			boolean hovered = isHover(mouseX, mouseY, buttonX, buttonY, buttonWidth, 22);
			RenderUtil.drawRoundedRect(graphics, buttonX, buttonY, buttonWidth, 22.0f, 11.0f,
					hovered ? COLOR_ACCENT_BG : COLOR_ACCENT_BG_SOFT, false);
			ZenyaFont.draw(graphics, this.font, label, buttonX + (buttonWidth - ZenyaFont.width(this.font, label)) / 2,
					buttonY + (22 - this.font.lineHeight) / 2 + 1, COLOR_ACCENT, false);
			if (hovered) {
				this.hoverSetting = setting;
				this.hoverSettingKind = SettingHitKind.ACTION;
				this.hoverSettingX = buttonX;
				this.hoverSettingY = buttonY;
				this.hoverSettingW = buttonWidth;
				this.hoverSettingH = 22;
			}
			return y + 36;
		}

		if (setting instanceof ModeSetting mode) {
			String label = mode.getValue();
			int buttonWidth = Math.max(64, ZenyaFont.width(this.font, label) + 22);
			int buttonX = x + width - 14 - buttonWidth;
			int buttonY = y + 7;
			boolean hovered = isHover(mouseX, mouseY, buttonX, buttonY, buttonWidth, 22);
			RenderUtil.drawRoundedRect(graphics, buttonX, buttonY, buttonWidth, 22.0f, 11.0f,
					hovered ? COLOR_CARD_HOVER : COLOR_SEARCH_BG, false);
			ZenyaFont.draw(graphics, this.font, label, buttonX + (buttonWidth - ZenyaFont.width(this.font, label)) / 2,
					buttonY + (22 - this.font.lineHeight) / 2 + 1, COLOR_ACCENT, false);
			if (hovered) {
				this.hoverSetting = setting;
				this.hoverSettingKind = SettingHitKind.MODE;
				this.hoverSettingX = buttonX;
				this.hoverSettingY = buttonY;
				this.hoverSettingW = buttonWidth;
				this.hoverSettingH = 22;
			}
			return y + 36;
		}

		Object value = setting.getValue();
		if (value instanceof Boolean || setting instanceof ConfirmBooleanSetting) {
			boolean on = value instanceof Boolean flag ? flag : Boolean.TRUE.equals(value);
			int trackX = x + width - 14 - 36;
			int trackY = y + 9;
			boolean hovered = isHover(mouseX, mouseY, trackX, trackY, 36, 18);
			int idleColor = hovered ? -10852224 : -11904904;
			RenderUtil.drawRoundedRect(graphics, trackX, trackY, 36.0f, 18.0f, 9.0f,
					on ? COLOR_ACCENT_BG : idleColor, false);
			float knobAnim = this.animate(setting, 2, on ? 1.0f : 0.0f, 18.0f);
			RenderUtil.drawOutline(graphics, trackX, trackY, 36.0f, 18.0f, 9.0f, 1.0f,
					blend(COLOR_ROW_BORDER, COLOR_ACCENT_STRONG, knobAnim), false);
			int knobLeft = trackX + 2;
			int knobRight = trackX + 36 - 14 - 2;
			int knobX = (int) (knobLeft + (knobRight - knobLeft) * knobAnim);
			drawDot(graphics, knobX, trackY + 2, 14, blend(COLOR_KNOB_OFF, COLOR_ACCENT, knobAnim));
			if (hovered) {
				this.hoverSetting = setting;
				this.hoverSettingKind = SettingHitKind.TOGGLE;
				this.hoverSettingX = trackX;
				this.hoverSettingY = trackY;
				this.hoverSettingW = 36;
				this.hoverSettingH = 18;
			}
			return y + 36;
		}

		if (value instanceof Number number && setting.getMin() instanceof Number
				&& setting.getMax() instanceof Number) {
			double min = ((Number) setting.getMin()).doubleValue();
			double max = ((Number) setting.getMax()).doubleValue();
			double current = number.doubleValue();
			double ratio = max > min ? (current - min) / (max - min) : 0.0;
			ratio = Math.max(0.0, Math.min(1.0, ratio));

			int trackX = x + width - 14 - 140 - 56;
			int trackY = y + 16;
			String text = this.formatNumber(current, number);
			ZenyaFont.draw(graphics, this.font, text, x + width - 14 - ZenyaFont.width(this.font, text),
					y + (36 - this.font.lineHeight) / 2 + 1, COLOR_TEXT, false);
			RenderUtil.drawRoundedRect(graphics, trackX, trackY, 140.0f, 4.0f, 2.0f, -11904904, false);
			int filled = (int) (140 * ratio);
			RenderUtil.drawRoundedRect(graphics, trackX, trackY, filled, 4.0f, 2.0f, COLOR_ACCENT, false);
			drawDot(graphics, trackX + filled - 6, trackY + 2 - 6, 12, COLOR_ACCENT);
			// The grab area is padded well past the 4px track so the slider is not pixel hunting.
			if (isHover(mouseX, mouseY, trackX - 8, trackY - 12, 156, 28)) {
				this.hoverSetting = setting;
				this.hoverSettingKind = SettingHitKind.SLIDER;
				this.hoverSettingX = trackX;
				this.hoverSettingY = trackY;
				this.hoverSettingW = 140;
				this.hoverSettingH = 4;
			}
			return y + 36;
		}

		if (value instanceof Color color) {
			String hex = String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
			int swatchX = x + width - 14 - 18;
			int swatchY = y + 9;
			int hexX = swatchX - 8 - ZenyaFont.width(this.font, hex);
			ZenyaFont.draw(graphics, this.font, hex, hexX, y + (36 - this.font.lineHeight) / 2 + 1, COLOR_TEXT, false);
			RenderUtil.drawRoundedRect(graphics, swatchX - 1, swatchY - 1, 20.0f, 20.0f, 5.0f, -12236196, false);
			RenderUtil.drawRoundedRect(graphics, swatchX, swatchY, 18.0f, 18.0f, 4.0f,
					0xFF000000 | color.getRGB() & 0xFFFFFF, false);
			if (isHover(mouseX, mouseY, x, y, width, 36)) {
				this.hoverSetting = setting;
				this.hoverSettingKind = SettingHitKind.COLOR_TOGGLE;
				this.hoverSettingX = x;
				this.hoverSettingY = y;
				this.hoverSettingW = width;
				this.hoverSettingH = 36;
			}
			if (this.expandedColorSetting == setting) {
				int pickerY = y + 36 + 6;
				return pickerY + this.drawColorPicker(graphics, x, pickerY, width, mouseX, mouseY);
			}
			return y + 36;
		}

		if (value instanceof String text) {
			boolean focused = this.focusedStringSetting == setting;
			String display = focused && System.currentTimeMillis() / 500L % 2L == 0L ? text + "_" : text;
			if (display == null || display.isEmpty()) {
				display = focused ? "_" : "Click to edit";
			}
			int fieldWidth = Math.max(124, Math.min(210, width / 2));
			int fieldX = x + width - 14 - fieldWidth;
			int fieldY = y + 7;
			boolean hovered = isHover(mouseX, mouseY, fieldX, fieldY, fieldWidth, 22);
			RenderUtil.drawRoundedRect(graphics, fieldX, fieldY, fieldWidth, 22.0f, 11.0f,
					focused ? COLOR_CARD_HOVER : COLOR_SEARCH_BG, false);
			RenderUtil.drawOutline(graphics, fieldX, fieldY, fieldWidth, 22.0f, 11.0f, 1.0f,
					focused ? COLOR_ACCENT_STRONG : COLOR_ROW_BORDER, false);
			ZenyaFont.draw(graphics, this.font, this.trimWithEllipsis(display, fieldWidth - 18), fieldX + 9,
					fieldY + (22 - this.font.lineHeight) / 2 + 1,
					text.isEmpty() && !focused ? COLOR_TEXT_DIM : COLOR_TEXT, false);
			if (hovered) {
				this.hoverSetting = setting;
				this.hoverSettingKind = SettingHitKind.STRING;
				this.hoverSettingX = fieldX;
				this.hoverSettingY = fieldY;
				this.hoverSettingW = fieldWidth;
				this.hoverSettingH = 22;
			}
			return y + 36;
		}

		String fallback = String.valueOf(value);
		if (fallback.length() > 32) {
			fallback = fallback.substring(0, 29) + "...";
		}
		ZenyaFont.draw(graphics, this.font, fallback, x + width - 14 - ZenyaFont.width(this.font, fallback),
				y + (36 - this.font.lineHeight) / 2 + 1, COLOR_TEXT_DIM, false);
		return y + 36;
	}
	/**
	 * Draws the inline HSV picker under a colour row and stores the three band rects, which
	 * {@link #handlePickerMouseDown} and {@link #applyPickerDrag} hit-test against.
	 *
	 * @return the fixed height the picker occupies
	 */
	private int drawColorPicker(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
		int pickerX = x + 10;
		int svY = y + 10;
		int pickerWidth = width - 20;
		RenderUtil.drawRoundedRect(graphics, x, y, width, 188.0f, CARD_RADIUS, COLOR_CARD_BG, false);

		this.picSvX = pickerX;
		this.picSvY = svY;
		this.picSvW = pickerWidth;
		this.picSvH = 100;
		// Saturation across, value down: one vertical gradient strip per column.
		for (int column = 0; column < pickerWidth; ++column) {
			int top = 0xFF000000 | Color.HSBtoRGB(this.pickerHSV[0], (float) column / pickerWidth, 1.0f);
			graphics.fillGradient(this.picSvX + column, this.picSvY, this.picSvX + column + 1,
					this.picSvY + 100, top, -16777216);
		}
		int svCursorX = this.picSvX + (int) (this.pickerHSV[1] * pickerWidth);
		int svCursorY = this.picSvY + (int) ((1.0f - this.pickerHSV[2]) * 100.0f);
		RenderUtil.drawRoundedRect(graphics, svCursorX - 5 - 1, svCursorY - 5 - 1, 12.0f, 12.0f, 6.0f, -872415232, false);
		RenderUtil.drawRoundedRect(graphics, svCursorX - 5, svCursorY - 5, 10.0f, 10.0f, 5.0f, -1, false);

		int hueY = svY + 100 + 8;
		this.picHueX = pickerX;
		this.picHueY = hueY;
		this.picHueW = pickerWidth;
		this.picHueH = 12;
		for (int column = 0; column < pickerWidth; ++column) {
			int hue = 0xFF000000 | Color.HSBtoRGB((float) column / pickerWidth, 1.0f, 1.0f);
			graphics.fill(this.picHueX + column, this.picHueY + 1, this.picHueX + column + 1,
					this.picHueY + 12 - 1, hue);
		}
		// Rounded caps over the square ends of the strip.
		RenderUtil.drawRoundedRect(graphics, this.picHueX, this.picHueY + 1, 10.0f, 10.0f, 5.0f,
				Color.HSBtoRGB(0.0f, 1.0f, 1.0f) | 0xFF000000, false);
		RenderUtil.drawRoundedRect(graphics, this.picHueX + pickerWidth - 10, this.picHueY + 1, 10.0f, 10.0f, 5.0f,
				Color.HSBtoRGB(1.0f, 1.0f, 1.0f) | 0xFF000000, false);
		int hueKnobX = this.picHueX + (int) (this.pickerHSV[0] * pickerWidth);
		hueKnobX = Math.max(this.picHueX + 5, Math.min(this.picHueX + pickerWidth - 5, hueKnobX));
		RenderUtil.drawRoundedRect(graphics, hueKnobX - 7, this.picHueY + 6 - 7, 14.0f, 14.0f, 7.0f, -1, false);
		RenderUtil.drawRoundedRect(graphics, hueKnobX - 7 + 2, this.picHueY + 6 - 7 + 2, 10.0f, 10.0f, 5.0f,
				Color.HSBtoRGB(this.pickerHSV[0], 1.0f, 1.0f) | 0xFF000000, false);

		int alphaY = hueY + 20;
		this.picAlphaX = pickerX;
		this.picAlphaY = alphaY;
		this.picAlphaW = pickerWidth;
		this.picAlphaH = 12;
		// Checkerboard first, then the colour ramp over it, so transparency reads as transparent.
		for (int checkerX = 0; checkerX < this.picAlphaW; checkerX += 4) {
			for (int checkerY = 0; checkerY < 10; checkerY += 4) {
				int checkerColor = (checkerX / 4 + checkerY / 4) % 2 == 0 ? -12961222 : -11184811;
				int right = Math.min(this.picAlphaX + checkerX + 4, this.picAlphaX + this.picAlphaW);
				int bottom = Math.min(this.picAlphaY + 1 + checkerY + 4, this.picAlphaY + 12 - 1);
				graphics.fill(this.picAlphaX + checkerX, this.picAlphaY + 1 + checkerY, right, bottom, checkerColor);
			}
		}
		int rgb = Color.HSBtoRGB(this.pickerHSV[0], this.pickerHSV[1], this.pickerHSV[2]) & 0xFFFFFF;
		for (int column = 0; column < this.picAlphaW; ++column) {
			int alpha = (int) ((float) column / this.picAlphaW * 255.0f);
			graphics.fill(this.picAlphaX + column, this.picAlphaY + 1, this.picAlphaX + column + 1,
					this.picAlphaY + 12 - 1, alpha << 24 | rgb);
		}
		int alphaKnobX = this.picAlphaX + (int) (this.pickerAlpha / 255.0f * this.picAlphaW);
		alphaKnobX = Math.max(this.picAlphaX + 5, Math.min(this.picAlphaX + this.picAlphaW - 5, alphaKnobX));
		RenderUtil.drawRoundedRect(graphics, alphaKnobX - 7, this.picAlphaY + 6 - 7, 14.0f, 14.0f, 7.0f, -1, false);
		int currentColor = this.pickerAlpha << 24 | rgb;
		RenderUtil.drawRoundedRect(graphics, alphaKnobX - 7 + 2, this.picAlphaY + 6 - 7 + 2, 10.0f, 10.0f, 5.0f,
				currentColor, false);

		int previewY = alphaY + 20;
		RenderUtil.drawRoundedRect(graphics, pickerX, previewY + 2, 16.0f, 16.0f, 3.0f, -14013910, false);
		RenderUtil.drawRoundedRect(graphics, pickerX + 1, previewY + 3, 14.0f, 14.0f, 2.0f, currentColor, false);
		String hex = String.format("#%02X%02X%02X", rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF);
		if (this.pickerAlpha < 255) {
			hex = String.format("#%02X%s", this.pickerAlpha, hex.substring(1));
		}
		ZenyaFont.draw(graphics, this.font, hex, pickerX + 16 + 8,
				previewY + (20 - this.font.lineHeight) / 2 + 1, COLOR_TEXT, false);
		String percent = (int) (this.pickerAlpha / 255.0f * 100.0f) + "%";
		ZenyaFont.draw(graphics, this.font, percent, pickerX + pickerWidth - ZenyaFont.width(this.font, percent),
				previewY + (20 - this.font.lineHeight) / 2 + 1, COLOR_TEXT_MUTED, false);
		return 188;
	}

	private void applyColorFromHsv() {
		if (this.expandedColorSetting == null) {
			return;
		}
		int rgb = Color.HSBtoRGB(this.pickerHSV[0], this.pickerHSV[1], this.pickerHSV[2]);
		this.expandedColorSetting.setValue(new Color(this.pickerAlpha << 24 | rgb & 0xFFFFFF, true));
	}

	private void openColorPicker(Setting<Color> setting) {
		this.expandedColorSetting = setting;
		this.colorPickerOpenedAtNanos = System.nanoTime();
		Color current = setting.getValue();
		Color.RGBtoHSB(current.getRed(), current.getGreen(), current.getBlue(), this.pickerHSV);
		this.pickerAlpha = current.getAlpha();
	}

	private void closeColorPicker() {
		this.expandedColorSetting = null;
		this.colorDragMode = ColorDragMode.NONE;
	}

	/** Whole numbers keep no decimals; large floats drop theirs so the value fits the row. */
	private String formatNumber(double value, Number type) {
		if (type instanceof Integer || type instanceof Long) {
			return String.valueOf(Math.round(value));
		}
		return String.format(Locale.ROOT, Math.abs(value) >= 100.0 ? "%.0f" : "%.2f", value);
	}

	/** Per-setting input rules: player names are Minecraft-legal and short, webhooks are long. */
	private String limitStringInput(String input, Setting<String> setting) {
		String text = this.sanitizeStringInput(input);
		if ("Player Name".equalsIgnoreCase(setting.getName())) {
			StringBuilder filtered = new StringBuilder(text.length());
			for (int i = 0; i < text.length(); ++i) {
				char current = text.charAt(i);
				if (!Character.isLetterOrDigit(current) && current != '_') {
					continue;
				}
				filtered.append(current);
			}
			text = filtered.toString();
		}
		int limit = "Player Name".equalsIgnoreCase(setting.getName())
				? 16
				: ("Webhook".equalsIgnoreCase(setting.getName()) ? 512 : 64);
		return text.length() <= limit ? text : text.substring(0, limit);
	}

	/** Keeps printable ASCII only, so pasted control characters cannot reach a setting. */
	private String sanitizeStringInput(String input) {
		if (input == null || input.isEmpty()) {
			return "";
		}
		StringBuilder clean = new StringBuilder(input.length());
		for (int i = 0; i < input.length(); ++i) {
			char current = input.charAt(i);
			if (current < ' ' || current >= '\u007f') {
				continue;
			}
			clean.append(current);
		}
		return clean.toString();
	}

	private boolean isControlDown() {
		long window = Minecraft.getInstance().getWindow().handle();
		return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
				|| GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
	}

	private String trimWithEllipsis(String text, int maxWidth) {
		if (text == null || text.isEmpty() || ZenyaFont.width(this.font, text) <= maxWidth) {
			return text == null ? "" : text;
		}
		int ellipsisWidth = ZenyaFont.width(this.font, "...");
		String trimmed = text;
		while (!trimmed.isEmpty() && ZenyaFont.width(this.font, trimmed) + ellipsisWidth > maxWidth) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed + "...";
	}

	/** Maps a mouse x onto the slider track last drawn, keeping the value's numeric type. */
	private void applySliderAt(int mouseX) {
		Setting<?> slider = this.draggingSlider;
		if (slider == null || this.hoverSettingW <= 0) {
			return;
		}
		double ratio = (double) (mouseX - this.hoverSettingX) / this.hoverSettingW;
		ratio = Math.max(0.0, Math.min(1.0, ratio));
		double min = ((Number) slider.getMin()).doubleValue();
		double max = ((Number) slider.getMax()).doubleValue();
		double value = min + ratio * (max - min);
		Object current = slider.getValue();
		if (current instanceof Integer) {
			retype(slider).setValue((int) Math.round(value));
		} else if (current instanceof Long) {
			retype(slider).setValue(Math.round(value));
		} else if (current instanceof Float) {
			retype(slider).setValue((float) value);
		} else if (current instanceof Double) {
			retype(slider).setValue(value);
		}
	}

	/** Acts on whatever the last render pass decided the mouse is over. */
	private void clickSetting() {
		switch (this.hoverSettingKind) {
			case TOGGLE -> {
				if (this.hoverSetting == null) {
					this.settingsTarget.toggle();
					return;
				}
				Object value = this.hoverSetting.getValue();
				if (value instanceof Boolean enabled) {
					retype(this.hoverSetting).setValue(!enabled);
				} else if (this.hoverSetting instanceof ConfirmBooleanSetting confirm) {
					confirm.setValue(!Boolean.TRUE.equals(confirm.getValue()));
				}
			}
			case MODE -> {
				if (this.hoverSetting instanceof ModeSetting mode) {
					mode.cycleNext();
				}
			}
			case ACTION -> {
				if (this.hoverSetting instanceof ActionSetting action) {
					action.trigger();
				}
			}
			case BIND -> {
				this.listeningBind = true;
				this.listeningActivationBind = false;
			}
			case ACTIVATION_BIND -> {
				this.listeningActivationBind = true;
				this.listeningBind = false;
			}
			case SLIDER -> this.draggingSlider = this.hoverSetting;
			case COLOR_TOGGLE -> {
				Setting<Color> colorSetting = retype(this.hoverSetting);
				if (this.expandedColorSetting == colorSetting) {
					this.closeColorPicker();
				} else {
					this.openColorPicker(colorSetting);
				}
			}
			case STRING -> {
				this.focusedStringSetting = retype(this.hoverSetting);
				this.listeningBind = false;
				this.listeningActivationBind = false;
			}
			default -> {
			}
		}
	}

	private boolean handlePickerMouseDown(int mouseX, int mouseY) {
		if (this.expandedColorSetting == null) {
			return false;
		}
		if (mouseX >= this.picSvX && mouseX < this.picSvX + this.picSvW
				&& mouseY >= this.picSvY && mouseY < this.picSvY + this.picSvH) {
			this.colorDragMode = ColorDragMode.SV;
			this.applyPickerDrag(mouseX, mouseY);
			return true;
		}
		if (mouseX >= this.picHueX && mouseX < this.picHueX + this.picHueW
				&& mouseY >= this.picHueY && mouseY < this.picHueY + this.picHueH) {
			this.colorDragMode = ColorDragMode.HUE;
			this.applyPickerDrag(mouseX, mouseY);
			return true;
		}
		if (mouseX >= this.picAlphaX && mouseX < this.picAlphaX + this.picAlphaW
				&& mouseY >= this.picAlphaY && mouseY < this.picAlphaY + this.picAlphaH) {
			this.colorDragMode = ColorDragMode.ALPHA;
			this.applyPickerDrag(mouseX, mouseY);
			return true;
		}
		return false;
	}

	private void applyPickerDrag(int mouseX, int mouseY) {
		if (this.expandedColorSetting == null) {
			return;
		}
		switch (this.colorDragMode) {
			case SV -> {
				this.pickerHSV[1] = clamp01((float) (mouseX - this.picSvX) / this.picSvW);
				this.pickerHSV[2] = clamp01(1.0f - (float) (mouseY - this.picSvY) / this.picSvH);
				this.applyColorFromHsv();
			}
			case HUE -> {
				// ponytail: the hue bar is horizontal but reads the mouse y, so dragging it barely moves the hue.
				this.pickerHSV[0] = clamp01((float) (mouseY - this.picHueY) / this.picHueH);
				this.applyColorFromHsv();
			}
			case ALPHA -> {
				this.pickerAlpha = (int) (clamp01((float) (mouseX - this.picAlphaX) / this.picAlphaW) * 255.0f);
				this.applyColorFromHsv();
			}
			default -> {
			}
		}
	}

	private static float clamp01(float value) {
		return value < 0.0f ? 0.0f : (value > 1.0f ? 1.0f : value);
	}
	private void renderConfigsHeader(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
		graphics.fill(x + 1, y + HEADER_H, x + 690 - 1, y + HEADER_H + 1, COLOR_DIVIDER);
		int backX = x + CONTENT_PAD;
		int backY = y + 18;
		this.hoverBackButton = isHover(mouseX, mouseY, backX, backY, 28, 28);
		RenderUtil.drawRoundedRect(graphics, backX, backY, 28.0f, 28.0f, 6.0f,
				this.hoverBackButton ? COLOR_CARD_HOVER : COLOR_CARD_BG, false);
		ZenyaFont.draw(graphics, this.font, "<", backX + 14 - 2, backY + 14 - this.font.lineHeight / 2 + 1,
				COLOR_TEXT, false);
		int titleX = backX + 28 + 12;
		ZenyaFont.draw(graphics, this.font, "Cloud Configs", titleX, y + 32 - this.font.lineHeight - 1,
				COLOR_TEXT, false);
		ZenyaFont.draw(graphics, this.font, "Save / load / share your settings", titleX, y + 32 + 2,
				COLOR_TEXT_MUTED, false);
	}

	/**
	 * Draws the configs page. Every clickable part registers its rect and action here, so
	 * {@link #mouseClicked} needs no second layout pass.
	 */
	private void renderConfigsPanel(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
		this.configsButtonRects.clear();
		this.configsButtonActions.clear();
		int rowX = x + CONTENT_PAD;
		int rowY = y + CONTENT_PAD - this.configsListScroll;
		graphics.enableScissor(x, y, x + 690, y + 536);

		RenderUtil.drawRoundedRect(graphics, rowX, rowY, 658.0f, 64.0f, CARD_RADIUS, COLOR_CARD_BG, false);
		ZenyaFont.draw(graphics, this.font, "Save current config", rowX + 14, rowY + 10, COLOR_TEXT, false);
		int nameX = rowX + 14;
		int nameY = rowY + 64 - 22 - 12;
		this.drawInput(graphics, nameX, nameY, 544, 22, this.configNameBuffer, this.configNameFocused,
				"Name (e.g. pvp, donut)", mouseX, mouseY, () -> {
					this.configNameFocused = true;
					this.configShareFocused = false;
				});
		int saveX = rowX + 658 - 14 - 80;
		boolean canSave = !this.configNameBuffer.trim().isEmpty();
		this.drawConfigsButton(graphics, saveX, nameY, 80, 22, "Save",
				canSave ? COLOR_ACCENT_BG : COLOR_CARD_HOVER, canSave ? COLOR_ACCENT : COLOR_TEXT_DIM,
				mouseX, mouseY, () -> {
					if (!canSave) {
						return;
					}
					String name = this.configNameBuffer.trim();
					if (ConfigStore.saveAs(name)) {
						this.showConfigsToast("Saved as " + ConfigStore.sanitize(name));
						this.configNameBuffer = "";
					} else {
						this.showConfigsToast("Save failed");
					}
				});
		rowY += 74;

		List<String> configs = ConfigStore.list();
		if (configs.isEmpty()) {
			RenderUtil.drawRoundedRect(graphics, rowX, rowY, 658.0f, 40.0f, CARD_RADIUS, COLOR_CARD_BG, false);
			ZenyaFont.draw(graphics, this.font, "No saved configs yet.", rowX + 14, rowY + 14, COLOR_TEXT_MUTED, false);
			rowY += 50;
		} else {
			for (String name : configs) {
				rowY = this.drawConfigRow(graphics, name, rowX, rowY, 658, mouseX, mouseY) + 6;
			}
			rowY += 4;
		}

		RenderUtil.drawRoundedRect(graphics, rowX, rowY, 658.0f, 64.0f, CARD_RADIUS, COLOR_CARD_BG, false);
		ZenyaFont.draw(graphics, this.font, "Share code", rowX + 14, rowY + 10, COLOR_TEXT, false);
		int shareX = rowX + 14;
		int shareY = rowY + 64 - 22 - 12;
		this.drawInput(graphics, shareX, shareY, 450, 22, this.configShareBuffer, this.configShareFocused,
				"Paste a code...", mouseX, mouseY, () -> {
					this.configShareFocused = true;
					this.configNameFocused = false;
				});
		int generateX = shareX + 450 + 8;
		this.drawConfigsButton(graphics, generateX, shareY, 80, 22, "Generate", COLOR_CARD_HOVER, COLOR_TEXT,
				mouseX, mouseY, () -> {
					String code = ConfigStore.generateShareCode();
					if (code != null) {
						ConfigStore.writeClipboard(code);
						this.configShareBuffer = code;
						this.showConfigsToast("Copied " + code + " to clipboard");
					} else {
						this.showConfigsToast("Generate failed");
					}
				});
		int redeemX = generateX + 88;
		this.drawConfigsButton(graphics, redeemX, shareY, 80, 22, "Redeem", COLOR_ACCENT_BG, COLOR_ACCENT,
				mouseX, mouseY, () -> {
					String code = this.configShareBuffer.trim();
					if (code.isEmpty()) {
						this.showConfigsToast("Enter a code first");
						return;
					}
					if (ConfigStore.redeemShareCode(code)) {
						this.showConfigsToast("Redeemed");
						this.configShareBuffer = "";
					} else {
						this.showConfigsToast("Invalid code");
					}
				});
		rowY += 70;
		graphics.disableScissor();

		if (this.configsToast != null && System.currentTimeMillis() - this.configsToastShownAt < 2200L) {
			int toastWidth = ZenyaFont.width(this.font, this.configsToast) + 24;
			int toastX = x + (690 - toastWidth) / 2;
			int toastY = y + 536 - 32;
			RenderUtil.drawRoundedRect(graphics, toastX, toastY, toastWidth, 22.0f, 11.0f, COLOR_ACCENT_BG, false);
			ZenyaFont.draw(graphics, this.font, this.configsToast, toastX + 12,
					toastY + (22 - this.font.lineHeight) / 2 + 1, COLOR_ACCENT, false);
		}

		int contentHeight = rowY + this.configsListScroll - (y + CONTENT_PAD);
		this.configsListScroll = contentHeight > 504
				? Math.max(0, Math.min(this.configsListScroll, contentHeight - 504))
				: 0;
	}

	private int drawConfigRow(GuiGraphics graphics, String name, int x, int y, int width, int mouseX, int mouseY) {
		RenderUtil.drawRoundedRect(graphics, x, y, width, 36.0f, CARD_RADIUS, COLOR_CARD_BG, false);
		ZenyaFont.draw(graphics, this.font, name, x + 14, y + (36 - this.font.lineHeight) / 2 + 1, COLOR_TEXT, false);
		int buttonY = y + 7;
		int deleteX = x + width - 14 - 60;
		int loadX = deleteX - 60 - 6;
		this.drawConfigsButton(graphics, loadX, buttonY, 60, 22, "Load", COLOR_ACCENT_BG, COLOR_ACCENT,
				mouseX, mouseY, () -> {
					if (ConfigStore.load(name)) {
						this.showConfigsToast("Loaded " + name);
					} else {
						this.showConfigsToast("Load failed");
					}
				});
		this.drawConfigsButton(graphics, deleteX, buttonY, 60, 22, "Delete", -10737110, -19276,
				mouseX, mouseY, () -> {
					if (ConfigStore.delete(name)) {
						this.showConfigsToast("Deleted " + name);
					} else {
						this.showConfigsToast("Delete failed");
					}
				});
		return y + 36;
	}

	private void drawConfigsButton(GuiGraphics graphics, int x, int y, int width, int height, String label,
			int background, int textColor, int mouseX, int mouseY, Runnable action) {
		boolean hovered = isHover(mouseX, mouseY, x, y, width, height);
		RenderUtil.drawRoundedRect(graphics, x, y, width, height, height / 2.0f,
				hovered ? blend(background, COLOR_CARD_HOVER, 0.25f) : background, false);
		ZenyaFont.draw(graphics, this.font, label, x + (width - ZenyaFont.width(this.font, label)) / 2,
				y + (height - this.font.lineHeight) / 2 + 1, textColor, false);
		this.configsButtonRects.add(new int[] {x, y, width, height});
		this.configsButtonActions.add(action);
	}

	private void drawInput(GuiGraphics graphics, int x, int y, int width, int height, String text, boolean focused,
			String placeholder, int mouseX, int mouseY, Runnable onClick) {
		boolean hovered = isHover(mouseX, mouseY, x, y, width, height);
		RenderUtil.drawRoundedRect(graphics, x, y, width, height, height / 2.0f,
				focused || hovered ? COLOR_CARD_HOVER : COLOR_SEARCH_BG, false);
		if (focused) {
			// Accent ring: a slightly larger rect behind the field, then the field on top of it.
			RenderUtil.drawRoundedRect(graphics, x - 1, y - 1, width + 2, height + 2, height / 2.0f + 1.0f,
					COLOR_ACCENT, false);
			RenderUtil.drawRoundedRect(graphics, x, y, width, height, height / 2.0f, COLOR_CARD_HOVER, false);
		}
		boolean empty = text == null || text.isEmpty();
		ZenyaFont.draw(graphics, this.font, empty ? placeholder : text, x + 12,
				y + (height - this.font.lineHeight) / 2 + 1, empty ? COLOR_TEXT_DIM : COLOR_TEXT, false);
		if (focused && System.currentTimeMillis() / 500L % 2L == 0L) {
			int caretX = x + 12 + ZenyaFont.width(this.font, text == null ? "" : text) + 1;
			graphics.fill(caretX, y + 5, caretX + 1, y + height - 5, COLOR_TEXT);
		}
		this.configsButtonRects.add(new int[] {x, y, width, height});
		this.configsButtonActions.add(onClick);
	}

	private void showConfigsToast(String message) {
		this.configsToast = message;
		this.configsToastShownAt = System.currentTimeMillis();
	}

	/** Filled square with its corners cut away: a cheap circle that needs no arc geometry. */
	private static void drawDot(GuiGraphics graphics, int x, int y, int size, int color) {
		if (size <= 0) {
			return;
		}
		int inset = Math.max(1, size / 5);
		graphics.fill(x + inset, y, x + size - inset, y + size, color);
		graphics.fill(x, y + inset, x + inset, y + size - inset, color);
		graphics.fill(x + size - inset, y + inset, x + size, y + size - inset, color);
		int corner = inset / 2;
		if (size >= 8 && corner >= 1) {
			graphics.fill(x + corner, y + corner, x + inset, y + inset, color);
			graphics.fill(x + size - inset, y + corner, x + size - corner, y + inset, color);
			graphics.fill(x + corner, y + size - inset, x + inset, y + size - corner, color);
			graphics.fill(x + size - inset, y + size - inset, x + size - corner, y + size - corner, color);
		}
	}

	@Override
	public void onClose() {
		this.playCustomSound(SOUND_GUI_CLOSE, 1.0f, 1.0f);
		super.onClose();
	}

	private static boolean isHover(int mouseX, int mouseY, int x, int y, int width, int height) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	/** Per-channel lerp between two ARGB colours. */
	private static int blend(int from, int to, float progress) {
		int fromA = from >>> 24 & 0xFF;
		int fromR = from >>> 16 & 0xFF;
		int fromG = from >>> 8 & 0xFF;
		int fromB = from & 0xFF;
		int toA = to >>> 24 & 0xFF;
		int toR = to >>> 16 & 0xFF;
		int toG = to >>> 8 & 0xFF;
		int toB = to & 0xFF;
		int alpha = (int) (fromA + (toA - fromA) * progress);
		int red = (int) (fromR + (toR - fromR) * progress);
		int green = (int) (fromG + (toG - fromG) * progress);
		int blue = (int) (fromB + (toB - fromB) * progress);
		return alpha << 24 | red << 16 | green << 8 | blue;
	}

	/**
	 * Re-types a hovered setting so a value can be written back through it. The row that recorded
	 * the hit already matched on the runtime type of the value, so this is unprovable, not unsafe.
	 */
	@SuppressWarnings("unchecked")
	private static <T> Setting<T> retype(Setting<?> setting) {
		return (Setting<T>) setting;
	}

	private enum ColorDragMode {
		NONE,
		SV,
		HUE,
		ALPHA
	}

	/** What a click on the recorded hover rect would act on. */
	private enum SettingHitKind {
		NONE,
		TOGGLE,
		SLIDER,
		MODE,
		ACTION,
		BIND,
		ACTIVATION_BIND,
		COLOR_TOGGLE,
		STRING
	}

	/** Sidebar entries that open something other than a category of module cards. */
	private enum OtherAction {
		FRIENDS,
		CONFIGS,
		HUD
	}

	private enum IconShape {
		SQUARE,
		RING,
		CIRCLE,
		DIAMOND,
		STAR,
		PEOPLE,
		FLOPPY,
		GRID
	}
}
