package com.zenya.gui;

import com.zenya.module.ActivatableModule;
import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.module.ModuleManager;
import com.zenya.module.modules.client.Themes;
import com.zenya.module.modules.client.ZenyaPlus;
import com.zenya.module.modules.render.BlockESP;
import com.zenya.setting.BlocksSetting;
import com.zenya.setting.MobsSetting;
import com.zenya.setting.ModeSetting;
import com.zenya.setting.Setting;
import com.zenya.setting.StorageBlocksSetting;
import com.zenya.sound.SoundManager;
import com.zenya.utils.ZenyaFont;
import com.zenya.utils.renderer.RenderUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * The click GUI: one narrow panel per category on a fixed 134px grid, plus a
 * right-click popup that holds the selected module's bind and settings.
 *
 * <p>Nothing about the layout is cached. Both the renderer and every mouse handler
 * walk the same category/module/setting loop and re-add the same row heights, so a
 * click only lands where the last frame drew - and any change to a row's height has
 * to be mirrored in all four walkers.
 *
 * <p>Panels are drawn at {@link #getCategoryX}/{@link #getCategoryY}, which add a
 * per-category pixel offset from {@link #categoryOffsets}; dragging a panel header
 * edits that offset rather than an absolute position, so panels keep their place
 * when the search filter changes the panel heights.
 *
 * <p>Every animation is a named entry in {@link #animValues} eased once per frame by
 * {@link #anim}, which is why the render pass must run even for rows it skips.
 */
public class ClickGUI extends Screen {
	private static final Category[] CACHED_CATEGORIES = Category.values();
	private static final int SLIDER_TRACK_COLOR_ARGB = -15196373;
	private static final float PANEL_RADIUS = 14.0f;
	private static final float ROW_RADIUS = 8.0f;
	private static final float TAB_RADIUS = 8.0f;
	private static final int PANEL_W = 120;
	private static final int PANEL_GAP = 14;
	private static final int PANEL_HEADER_H = 24;
	private static final int PANEL_HEADER_SPACING = 6;
	private static final int POPUP_HEADER_H = 30;
	private static final int PANEL_PAD = 10;
	private static final int ROW_H = 18;
	private static final int ROW_STEP = 20;
	private static final int SEARCH_H = 20;
	private static final int COLOR_PICKER_SV_HEIGHT = 60;
	private static final int COLOR_PICKER_HUE_HEIGHT = 8;
	private static final int COLOR_PICKER_ALPHA_HEIGHT = 8;
	private static final int COLOR_PICKER_GAP = 6;
	private static final int COLOR_PICKER_BOTTOM_PAD = 6;
	private static final int COLOR_PICKER_EXTRA_HEIGHT = 100;
	private static final int BLOCK_PICKER_SEARCH_H = 16;
	private static final int BLOCK_PICKER_ROW_H = 18;
	private static final int BLOCK_PICKER_VISIBLE_ROWS = 5;
	private static final int BLOCK_PICKER_GAP = 6;
	private static final int BLOCK_PICKER_CLEAR_W = 30;
	private static final int BLOCK_PICKER_BOTTOM_PAD = 6;
	private static final float BLOCK_PICKER_SCROLLBAR_W = 4.0f;
	private static final float BLOCK_PICKER_INDICATOR_SIZE = 6.0f;
	private static final float BLOCK_PICKER_TEXT_SCALE = 0.9f;
	private static final int STORAGE_PICKER_ROW_H = 18;
	private static final int STORAGE_PICKER_GAP = 2;
	private static final int STORAGE_PICKER_PAD = 4;
	private static final int COLOR_SCREEN_BG = -1442840576;
	private static final int COLOR_ROW_BG = 0;
	private static final int COLOR_ROW_HOVER = 0x12FFFFFF;
	private static final int COLOR_ROW_ACTIVE = 0x1FFFFFFF;
	private static final int COLOR_TEXT = -1249035;
	private static final int COLOR_TEXT_MUTED = -7694680;
	private static final int COLOR_DIVIDER = -15394270;
	private static final int COLOR_KEY_BG = -15327186;
	private static final int SCROLL_STEP = 24;

	/** Re-derived from the active theme at the top of every {@link #render}. */
	private static int COLOR_PANEL_BG = -15657443;
	private static int COLOR_PANEL_OUTLINE = -14670547;
	// ponytail: COLOR_HEADER_BG is assigned every frame and never read.
	private static int COLOR_HEADER_BG = -15657443;
	private static int COLOR_ACCENT = -1096636;
	private static int COLOR_ACCENT_DIM = -11620474;
	private static int COLOR_SEARCH_OUTLINE = -14340032;
	private static int COLOR_ROW_OUTLINE = -14932424;

	public static ClickGUI INSTANCE;

	private Module listeningBind = null;
	private ActivatableModule listeningActivationBind = null;
	private Setting<String> listeningString = null;
	private Setting<Integer> listeningBindSetting = null;
	private Setting<String> expandedStringListSetting = null;
	private boolean stringListAddActive = false;
	private String stringListAddBuffer = "";
	private Setting<Color> expandedColorSetting = null;
	private Setting<Color> activeColorSetting = null;
	// ponytail: nothing ever assigns expandedBlocksSetting - a BlocksSetting row opens
	// BlockPickerScreen instead - so every inline block-picker path below is unreachable.
	private BlocksSetting expandedBlocksSetting = null;
	private MobsSetting expandedMobsSetting = null;
	private StorageBlocksSetting expandedStorageBlocksSetting = null;
	private ColorDragMode colorDragMode = ColorDragMode.NONE;
	private boolean searchActive = false;
	private boolean blockSearchActive = false;
	private boolean mobSearchActive = false;
	private String searchQuery = "";
	private String blockSearchQuery = "";
	private String mobSearchQuery = "";
	private int mobPickerScroll = 0;
	private int verticalScroll = 0;
	private int blockPickerScroll = 0;
	private Module popupModule = null;
	private int popupX = 200;
	private int popupY = 200;
	private int popupW = 160;
	private boolean draggingPopup = false;
	private int popupDragOffsetX = 0;
	private int popupDragOffsetY = 0;
	private float popupAnimScale = 0.0f;
	private long popupAnimLastNano = 0L;
	private float openAnimScale = 0.0f;
	private float categoryStagger = 0.0f;
	private boolean closing = false;
	private float uiScale = 1.0f;
	private Setting<?> draggingNumericSetting = null;
	private Module draggingNumericModule = null;
	private int draggingNumericCatX = 0;
	/** Pixel offset from the default grid slot, written by header dragging. */
	private final EnumMap<Category, int[]> categoryOffsets = new EnumMap<>(Category.class);
	private Category draggingCategory = null;
	private int dragGrabOffsetX = 0;
	private int dragGrabOffsetY = 0;
	private final Map<String, Float> animValues = new HashMap<>();
	// ponytail: pulseStarts, lastHoveredModuleSoundKey and lastHoverSoundNanos are never read.
	private final Map<String, Long> pulseStarts = new HashMap<>();
	private String lastHoveredModuleSoundKey = "";
	private long lastHoverSoundNanos = 0L;
	private long lastAnimNanos = 0L;
	private float frameDt = 0.016666668f;

	public ClickGUI() {
		super(Component.literal("Frost Client"));
		INSTANCE = this;
	}

	private void updateAnimDt() {
		long now = System.nanoTime();
		if (this.lastAnimNanos != 0L) {
			this.frameDt = Math.min(0.1f, (now - this.lastAnimNanos) / 1.0E9f);
		}
		this.lastAnimNanos = now;
	}

	/** Eases the value stored under {@code key} towards {@code target} by one frame. */
	private float anim(String key, float target, float speed) {
		if (!ZenyaPlus.animationsEnabled()) {
			this.animValues.put(key, target);
			return target;
		}
		float current = this.animValues.getOrDefault(key, target);
		float factor = 1.0f - (float) Math.exp(-speed * this.frameDt);
		float next = current + (target - current) * factor;
		this.animValues.put(key, next);
		return next;
	}

	/** Height of a module's popup body: the bind row, plus one row per setting and any open editor. */
	private int getModuleExpandedHeight(Module module) {
		int height = ROW_STEP;
		if (module instanceof ActivatableModule) {
			height += ROW_STEP;
		}
		for (Setting<?> setting : module.getSettings()) {
			height += ROW_STEP;
			if (setting instanceof BlocksSetting blocks && this.expandedBlocksSetting == blocks) {
				height += this.getBlockPickerExtraHeight(blocks);
			}
			if (setting instanceof MobsSetting mobs && this.expandedMobsSetting == mobs) {
				height += this.getMobPickerExtraHeight(mobs);
			}
			if (setting instanceof StorageBlocksSetting storage && this.expandedStorageBlocksSetting == storage) {
				height += this.getStoragePickerExtraHeight(storage);
			}
			if (setting.getValue() instanceof Color && this.expandedColorSetting == setting) {
				height += COLOR_PICKER_EXTRA_HEIGHT;
			}
			if (this.isStringListSetting(module, setting) && this.expandedStringListSetting == setting) {
				height += this.getStringListEditorExtraHeight(setting);
			}
		}
		return height;
	}

	/** Only the Friends module's "Names" setting is edited as a list rather than as raw text. */
	private boolean isStringListSetting(Module module, Setting<?> setting) {
		if (module == null || setting == null) {
			return false;
		}
		if (!(setting.getValue() instanceof String)) {
			return false;
		}
		return "Friends".equalsIgnoreCase(module.getName()) && setting.matchesName("Names");
	}

	private int getStringListEditorExtraHeight(Setting<?> setting) {
		int visible = Math.min(6, this.parseStringList(setting).size());
		return (visible + 1) * ROW_STEP;
	}

	/** Splits the stored comma/newline separated value into lower-cased, de-duplicated names. */
	private List<String> parseStringList(Setting<?> setting) {
		if (setting == null || !(setting.getValue() instanceof String raw)) {
			return new ArrayList<>();
		}
		if (raw.isBlank()) {
			return new ArrayList<>();
		}
		String normalized = raw.replace('\n', ',').replace('\r', ',');
		LinkedHashSet<String> unique = new LinkedHashSet<>();
		for (String part : normalized.split(",")) {
			String trimmed = part == null ? "" : part.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			unique.add(trimmed.toLowerCase(Locale.ROOT));
		}
		return new ArrayList<>(unique);
	}

	private void setStringListFromLowerList(Setting<String> setting, List<String> lowerNames) {
		if (setting == null) {
			return;
		}
		StringBuilder joined = new StringBuilder();
		for (String name : lowerNames) {
			String trimmed = name == null ? "" : name.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			if (!joined.isEmpty()) {
				joined.append(", ");
			}
			joined.append(trimmed);
		}
		setting.setValue(joined.toString());
	}

	private float computeUiScale() {
		return 1.0f;
	}

	/** Overshooting ease used for the open/close pop. */
	private static float easeOutBack(float t) {
		if (t <= 0.0f) {
			return 0.0f;
		}
		if (t >= 1.0f) {
			return 1.0f;
		}
		float c1 = 1.70158f;
		float c3 = c1 + 1.1f;
		return 1.0f + c3 * (float) Math.pow(t - 1.0f, 3.0) + c1 * (float) Math.pow(t - 1.0f, 2.0);
	}

	private double toUiX(double rawX) {
		return rawX / Math.max(1.0E-4f, this.uiScale);
	}

	private double toUiY(double rawY) {
		return rawY / Math.max(1.0E-4f, this.uiScale);
	}

	private int uiWidth() {
		return Math.round(this.width / Math.max(1.0E-4f, this.uiScale));
	}

	private int uiHeight() {
		return Math.round(this.height / Math.max(1.0E-4f, this.uiScale));
	}

	private float getExpandProgress(Module module, String modKey) {
		return this.anim(modKey + "/expand", module.isExpanded() ? 1.0f : 0.0f, 20.0f);
	}

	private static int lerpARGB(int from, int to, float t) {
		if (t <= 0.0f) {
			return from;
		}
		if (t >= 1.0f) {
			return to;
		}
		int fromA = from >>> 24 & 0xFF;
		int fromR = from >>> 16 & 0xFF;
		int fromG = from >>> 8 & 0xFF;
		int fromB = from & 0xFF;
		int toA = to >>> 24 & 0xFF;
		int toR = to >>> 16 & 0xFF;
		int toG = to >>> 8 & 0xFF;
		int toB = to & 0xFF;
		int a = (int) (fromA + (toA - fromA) * t);
		int r = (int) (fromR + (toR - fromR) * t);
		int g = (int) (fromG + (toG - fromG) * t);
		int b = (int) (fromB + (toB - fromB) * t);
		return a << 24 | r << 16 | g << 8 | b;
	}

	/** Rainbow theme spreads the hue down the module list; every other theme is flat accent. */
	private static int getRainbowColor(int index) {
		if (!Themes.isRainbow()) {
			return COLOR_ACCENT;
		}
		return Themes.rainbowAt(index, 0.05f);
	}

	private int[] getCategoryOffset(Category category) {
		return this.categoryOffsets.computeIfAbsent(category, key -> new int[2]);
	}

	private int getCategoryX(Category category, int index) {
		return 30 + index * (PANEL_W + PANEL_GAP) + this.getCategoryOffset(category)[0];
	}

	private int getCategoryY(Category category) {
		return this.getContentTop() + this.verticalScroll + this.getCategoryOffset(category)[1];
	}

	/** Starts the close animation; the screen is only really closed by {@link #finishClose}. */
	public void requestClose() {
		if (!this.closing) {
			SoundManager.playGuiClose();
		}
		this.closing = true;
	}

	@Override
	public void onClose() {
		this.requestClose();
	}

	private void finishClose() {
		this.closing = false;
		this.animValues.put("guiOpen", 0.0f);
		this.animValues.put("categoryStagger", 0.0f);
		super.onClose();
	}

	@Override
	protected void init() {
		super.init();
		this.openAnimScale = 0.0f;
		this.categoryStagger = 0.0f;
		this.lastAnimNanos = 0L;
		this.animValues.put("guiOpen", 0.0f);
		this.animValues.put("categoryStagger", 0.0f);
		this.closing = false;
		Minecraft mc = Minecraft.getInstance();
		if (mc != null && mc.player != null) {
			SoundManager.playGuiOpen();
		}
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
		COLOR_ACCENT = Themes.isRainbow() ? Themes.rainbowAt(0, 0.05f) : ZenyaPlus.getAccentARGB();
		COLOR_PANEL_BG = ZenyaPlus.getBackgroundARGB();
		COLOR_PANEL_OUTLINE = COLOR_ACCENT & 0xFFFFFF | 0x3A000000;
		COLOR_SEARCH_OUTLINE = COLOR_ACCENT & 0xFFFFFF | 0x48000000;
		COLOR_ROW_OUTLINE = COLOR_ACCENT & 0xFFFFFF | 0x22000000;
		Color accent = ZenyaPlus.getAccentColor();
		COLOR_ACCENT_DIM = 0xFF000000
				| Math.max(0, accent.getRed() - 30) << 16
				| Math.max(0, accent.getGreen() - 35) << 8
				| Math.max(0, accent.getBlue() - 20);

		this.updateAnimDt();
		this.uiScale = this.computeUiScale();
		int uiMouseX = Math.round(mouseX / this.uiScale);
		int uiMouseY = Math.round(mouseY / this.uiScale);
		context.pose().pushMatrix();
		context.pose().scale(this.uiScale, this.uiScale);

		this.openAnimScale = this.anim("guiOpen", this.closing ? 0.0f : 1.0f, this.closing ? 22.0f : 18.0f);
		this.categoryStagger = this.anim("categoryStagger", this.closing ? 0.0f : 1.0f, this.closing ? 16.0f : 10.0f);
		float openScale = easeOutBack(this.openAnimScale);
		float fadeAlpha = this.clamp01(this.openAnimScale);
		float slideY = (1.0f - openScale) * 20.0f;
		float centerX = this.uiWidth() / 2.0f;
		float centerY = this.uiHeight() / 2.0f;
		context.pose().translate(centerX, centerY + slideY);
		context.pose().scale(openScale, openScale);
		context.pose().translate(-centerX, -centerY);

		this.verticalScroll = this.clampVerticalScroll(this.verticalScroll);
		int dimAlpha = (int) (170.0f * fadeAlpha);
		context.fill(0, 0, this.uiWidth(), this.uiHeight(), dimAlpha << 24);
		COLOR_HEADER_BG = COLOR_PANEL_BG;
		this.drawSearchBar(context, uiMouseX, uiMouseY, fadeAlpha);

		Category[] categories = CACHED_CATEGORIES;
		for (int categoryIndex = 0; categoryIndex < categories.length; ++categoryIndex) {
			Category category = categories[categoryIndex];
			float stagger = this.easeOutCubic(this.clamp01(this.categoryStagger - categoryIndex * 0.055f));
			int catX = this.getCategoryX(category, categoryIndex) + Math.round((1.0f - stagger) * -16.0f);
			int catY = this.getCategoryY(category) + Math.round((1.0f - stagger) * 10.0f);
			int panelHeight = this.getPanelHeight(category);
			if (ZenyaPlus.blurBackgroundEnabled()) {
				RenderUtil.drawBlur(context, catX, catY, PANEL_W, panelHeight, PANEL_RADIUS, 4.0f, false);
			}
			RenderUtil.drawRoundedRect(context, catX, catY, PANEL_W, panelHeight, PANEL_RADIUS,
					this.multiplyAlpha(COLOR_PANEL_BG, stagger), false);
			RenderUtil.drawOutline(context, catX, catY, PANEL_W, panelHeight, PANEL_RADIUS, 1.0f,
					this.multiplyAlpha(COLOR_PANEL_OUTLINE, stagger), false);

			int iconSize = 14;
			CategoryIconRenderer.draw(context, catX + PANEL_PAD, catY + 5, iconSize, category,
					this.multiplyAlpha(-1, stagger));
			ZenyaFont.draw(context, this.font, category.getName().toUpperCase(),
					catX + PANEL_PAD + iconSize + 8, catY + 7, this.multiplyAlpha(COLOR_TEXT, stagger), false);

			int panelBottom = catY + panelHeight;
			int modY = catY + PANEL_HEADER_H + PANEL_HEADER_SPACING;
			int visibleCount = 0;
			for (Module module : ModuleManager.INSTANCE.getModulesInCategory(category)) {
				if (!this.matchesQuery(module)) {
					continue;
				}
				++visibleCount;
				if (modY + ROW_H > panelBottom) {
					modY += ROW_STEP;
					continue;
				}

				float rowEnter = this.easeOutCubic(this.clamp01(
						this.categoryStagger - categoryIndex * 0.055f - visibleCount * 0.025f));
				int rowSlide = Math.round((1.0f - rowEnter) * 8.0f);
				boolean hovered = uiMouseX >= catX + 4 && uiMouseX <= catX + PANEL_W - 4
						&& uiMouseY >= modY && uiMouseY <= modY + ROW_H;
				String modKey = category.name() + "/" + module.getName();
				float hoverA = this.anim(modKey + "/hover", hovered ? 1.0f : 0.0f, 14.0f);
				float enabledA = this.anim(modKey + "/enabled", module.isEnabled() ? 1.0f : 0.0f, 12.0f);
				int textBase = lerpARGB(COLOR_TEXT_MUTED, COLOR_TEXT, hoverA);
				int modRainbow = getRainbowColor(visibleCount);
				int textColor = lerpARGB(textBase, modRainbow, enabledA);
				if (hoverA > 0.05f) {
					// The hover wash keeps fading in after the mouse leaves, at half strength.
					float wash = hovered ? hoverA * rowEnter : hoverA * rowEnter * 0.5f;
					RenderUtil.drawRoundedRect(context, catX + 4 + rowSlide, modY, 112 - rowSlide, ROW_H, ROW_RADIUS,
							this.multiplyAlpha(0x1AFFFFFF & (modRainbow | 0xFF000000), wash), false);
				}
				int textOffset = Math.round(6.0f * enabledA);
				if (enabledA > 0.01f) {
					int dotColor = modRainbow & 0xFFFFFF | (int) (255.0f * enabledA * rowEnter) << 24;
					float dotX = catX + PANEL_PAD + 1.0f + rowSlide + (1.0f - enabledA) * -3.0f;
					RenderUtil.drawRoundedRect(context, dotX, modY + 7.5f, 3.0f, 3.0f, 1.5f, dotColor, false);
				}
				ZenyaFont.draw(context, this.font, module.getDisplayName(),
						catX + PANEL_PAD + 4 + rowSlide + textOffset, modY + 4,
						this.multiplyAlpha(textColor, rowEnter), false);
				modY += ROW_STEP;

				// Keeps every module's expand animation ticking, not just the popped-out one.
				this.getExpandProgress(module, modKey);
				if (module != this.popupModule) {
					continue;
				}

				long now = System.nanoTime();
				if (this.popupAnimLastNano == 0L) {
					this.popupAnimLastNano = now;
				}
				float dtSec = (now - this.popupAnimLastNano) / 1.0E9f;
				this.popupAnimLastNano = now;
				float target = this.popupModule != null ? 1.0f : 0.0f;
				float speed = 15.0f;
				this.popupAnimScale = this.popupAnimScale < target
						? Math.min(target, this.popupAnimScale + dtSec * speed)
						: Math.max(target, this.popupAnimScale - dtSec * speed);
				float popupScale = easeOutBack(this.popupAnimScale);
				float contentAlpha = popupScale;
				float contentSlideY = 0.0f;

				// The popup borrows catX/modY so the row drawing below is shared with the panel.
				int savedCatX = catX;
				int savedModY = modY;
				catX = this.popupX;
				modY = this.popupY + POPUP_HEADER_H;
				int animExpandH = this.getModuleExpandedHeight(module);
				int popupH = Math.max(110, animExpandH + POPUP_HEADER_H + 8);
				int contentStartY = modY;
				float popupCenterX = this.popupX + 60.0f;
				float popupCenterY = this.popupY + popupH * 0.5f;

				context.pose().pushMatrix();
				context.pose().translate(popupCenterX, popupCenterY);
				context.pose().scale(popupScale, popupScale);
				context.pose().translate(-popupCenterX, -popupCenterY);
				RenderUtil.drawRoundedRect(context, this.popupX - 2, this.popupY - 2, 124.0f, popupH + 4, 16.0f,
						this.multiplyAlpha(0xFF000000, 0.4f * popupScale), false);
				if (ZenyaPlus.blurBackgroundEnabled()) {
					RenderUtil.drawBlur(context, this.popupX, this.popupY, PANEL_W, popupH, PANEL_RADIUS, 4.0f, false);
				}
				RenderUtil.drawRoundedRect(context, this.popupX, this.popupY, PANEL_W, popupH, PANEL_RADIUS,
						COLOR_PANEL_BG, false);
				RenderUtil.drawOutline(context, this.popupX, this.popupY, PANEL_W, popupH, PANEL_RADIUS, 1.0f,
						this.multiplyAlpha(COLOR_PANEL_OUTLINE, popupScale), false);
				RenderUtil.drawRoundedRect(context, this.popupX + 5, this.popupY + 6, 4.0f, 16.0f, 2.0f,
						this.multiplyAlpha(COLOR_ACCENT, popupScale), false);
				ZenyaFont.draw(context, this.font, module.getDisplayName().toUpperCase(),
						this.popupX + 12, this.popupY + 8, this.multiplyAlpha(COLOR_TEXT, popupScale), false);
				ZenyaFont.draw(context, this.font, "x", this.popupX + PANEL_W - 18, this.popupY + 7,
						this.multiplyAlpha(COLOR_TEXT_MUTED, popupScale), false);
				context.pose().popMatrix();

				context.enableScissor(this.popupX, this.popupY, this.popupX + PANEL_W, this.popupY + popupH);
				context.pose().pushMatrix();
				context.pose().translate(popupCenterX, popupCenterY);
				context.pose().scale(popupScale, popupScale);
				context.pose().translate(-popupCenterX, -popupCenterY);

				String bindText = this.listeningBind == module
						? "Bind: ..."
						: "Bind: " + this.getKeyDisplayName(module.getBind());
				// Rows fade in one by one as the popup body grows past their Y.
				float rowReveal = this.clamp01((animExpandH - (modY - contentStartY)) / (float) ROW_H);
				float rowAlpha = contentAlpha * rowReveal;
				context.pose().pushMatrix();
				context.pose().translate(0.0f, contentSlideY);
				RenderUtil.drawRoundedRect(context, catX + 4, modY, 112.0f, ROW_H, ROW_RADIUS,
						this.multiplyAlpha(SLIDER_TRACK_COLOR_ARGB, rowAlpha), false);
				RenderUtil.drawOutline(context, catX + 4, modY, 112.0f, ROW_H, ROW_RADIUS, 1.0f,
						this.multiplyAlpha(COLOR_ROW_OUTLINE, rowAlpha), false);
				ZenyaFont.draw(context, this.font, bindText, catX + PANEL_PAD, modY + 4,
						this.multiplyAlpha(COLOR_TEXT_MUTED, rowAlpha), false);
				context.pose().popMatrix();
				modY += ROW_STEP;

				if (module instanceof ActivatableModule activatable) {
					String activationText = this.listeningActivationBind == activatable
							? "Activation: ..."
							: "Activation: " + this.getKeyDisplayName(activatable.getActivationKey());
					rowReveal = this.clamp01((animExpandH - (modY - contentStartY)) / (float) ROW_H);
					rowAlpha = contentAlpha * rowReveal;
					context.pose().pushMatrix();
					context.pose().translate(0.0f, contentSlideY);
					RenderUtil.drawRoundedRect(context, catX + 4, modY, 112.0f, ROW_H, ROW_RADIUS,
							this.multiplyAlpha(SLIDER_TRACK_COLOR_ARGB, rowAlpha), false);
					RenderUtil.drawOutline(context, catX + 4, modY, 112.0f, ROW_H, ROW_RADIUS, 1.0f,
							this.multiplyAlpha(COLOR_ROW_OUTLINE, rowAlpha), false);
					ZenyaFont.draw(context, this.font, activationText, catX + PANEL_PAD, modY + 4,
							this.multiplyAlpha(COLOR_TEXT_MUTED, rowAlpha), false);
					context.pose().popMatrix();
					modY += ROW_STEP;
				}

				for (Setting<?> setting : module.getSettings()) {
					rowReveal = this.clamp01((animExpandH - (modY - contentStartY)) / (float) ROW_H);
					rowAlpha = contentAlpha * rowReveal;
					context.pose().pushMatrix();
					context.pose().translate(0.0f, contentSlideY);
					RenderUtil.drawRoundedRect(context, catX + 4, modY, 112.0f, ROW_H, ROW_RADIUS,
							this.multiplyAlpha(COLOR_ROW_BG, rowAlpha), false);
					RenderUtil.drawOutline(context, catX + 4, modY, 112.0f, ROW_H, ROW_RADIUS, 1.0f,
							this.multiplyAlpha(COLOR_ROW_OUTLINE, rowAlpha), false);

					Object val = setting.getValue();
					if (setting instanceof ModeSetting modeSetting) {
						this.drawModeSetting(context, modeSetting, catX + 4, 112, modY, rowAlpha);
					} else if (val instanceof Boolean enabled) {
						int toggleX = catX + PANEL_W - PANEL_PAD - 20;
						int toggleY = modY + 4;
						float toggleAnim = this.anim(System.identityHashCode(setting) + "/tog",
								enabled ? 1.0f : 0.0f, 16.0f);
						int trackColor = this.multiplyAlpha(lerpARGB(-13682875, COLOR_ACCENT_DIM, toggleAnim), rowAlpha);
						int knobColor = this.multiplyAlpha(lerpARGB(-1511950, COLOR_ACCENT, toggleAnim), rowAlpha);
						int knobX = toggleX + 2 + Math.round(10.0f * toggleAnim);
						int labelColor = this.multiplyAlpha(lerpARGB(COLOR_TEXT_MUTED, COLOR_ACCENT, toggleAnim), rowAlpha);
						RenderUtil.drawRoundedRect(context, toggleX, toggleY, 20.0f, 8.0f, 4.0f, trackColor, false);
						RenderUtil.drawRoundedRect(context, knobX, toggleY + 1, 6.0f, 6.0f, 3.0f, knobColor, false);
						ZenyaFont.draw(context, this.font, setting.getDisplayName(), catX + PANEL_PAD, modY + 4,
								labelColor, false);
					} else if (val instanceof Float || val instanceof Double || val instanceof Integer) {
						if (setting.getName().toLowerCase(Locale.ROOT).contains("bind") && val instanceof Integer) {
							String bindLabel = this.listeningBindSetting == setting
									? setting.getDisplayName() + ": ..."
									: setting.getDisplayName() + ": " + getKeyDisplayNameStatic((Integer) val);
							ZenyaFont.draw(context, this.font, bindLabel, catX + PANEL_PAD, modY + 4,
									this.multiplyAlpha(COLOR_TEXT, rowAlpha), false);
						} else if (!(setting.getMin() instanceof Number && setting.getMax() instanceof Number)) {
							ZenyaFont.draw(context, this.font, setting.getDisplayName() + ": " + val,
									catX + PANEL_PAD, modY + 4, this.multiplyAlpha(COLOR_TEXT, rowAlpha), false);
						} else {
							float value;
							float min;
							float max;
							String displayValue;
							if (val instanceof Integer intValue
									&& setting.getMin() instanceof Integer
									&& setting.getMax() instanceof Integer) {
								value = intValue;
								min = (Integer) setting.getMin();
								max = (Integer) setting.getMax();
								displayValue = Integer.toString(intValue);
							} else {
								// ponytail: an Integer value with non-Integer bounds lands here and
								// blows up on the Double cast.
								value = val instanceof Float floatValue
										? floatValue
										: (val == null ? 0.0f : ((Double) val).floatValue());
								max = setting.getMax() instanceof Float floatMax
										? floatMax
										: (setting.getMax() == null ? 1.0f : ((Double) setting.getMax()).floatValue());
								min = setting.getMin() instanceof Float floatMin
										? floatMin
										: (setting.getMin() == null ? 0.0f : ((Double) setting.getMin()).floatValue());
								if (this.allowDecimalForModule(module)) {
									displayValue = String.format("%.1f", value);
								} else {
									value = Math.round(value);
									displayValue = Integer.toString(Math.round(value));
								}
							}
							float range = max - min;
							float progress = range == 0.0f ? 0.0f : (value - min) / range;
							int barX = catX + PANEL_PAD;
							int barY = modY + 11;
							int barW = 100;
							int fillW = (int) (barW * this.clamp01(progress));
							RenderUtil.drawRoundedRect(context, barX, barY, barW, 4.0f, 2.0f,
									this.multiplyAlpha(-14998734, rowAlpha), false);
							RenderUtil.drawRoundedRect(context, barX, barY, fillW, 4.0f, 2.0f,
									this.multiplyAlpha(COLOR_ACCENT, rowAlpha), false);
							// Knob centred on the 4px track: +2 to the track centre, -4 for half the knob.
							float knobRectX = barX + fillW - 4.0f;
							float knobRectY = barY - 2.0f;
							RenderUtil.drawRoundedRect(context, knobRectX, knobRectY, 8.0f, 8.0f, 4.0f,
									this.multiplyAlpha(COLOR_ACCENT, rowAlpha), false);
							RenderUtil.drawOutline(context, knobRectX, knobRectY, 8.0f, 8.0f, 4.0f, 1.0f,
									this.multiplyAlpha(-1426063361, rowAlpha), false);
							ZenyaFont.draw(context, this.font, setting.getDisplayName() + ": " + displayValue,
									catX + PANEL_PAD, modY + 2, this.multiplyAlpha(COLOR_TEXT, rowAlpha), false);
						}
					} else if (val instanceof String stringValue) {
						String text;
						if (this.isStringListSetting(module, setting)) {
							text = setting.getDisplayName() + ": " + this.parseStringList(setting).size() + " entries";
							if (this.expandedStringListSetting == setting) {
								text = text + " (edit)";
							}
						} else {
							text = setting.getDisplayName() + ": "
									+ this.formatStringSettingValue(module, setting, stringValue);
							if (this.listeningString == setting) {
								text = text + "_";
							}
						}
						this.drawInputTextClipped(context, catX + 4, modY, 112, ROW_H, text,
								catX + PANEL_PAD, modY + 4, this.multiplyAlpha(COLOR_TEXT, rowAlpha));
					} else if (setting instanceof StorageBlocksSetting storageSetting) {
						this.drawStorageBlocksSettingSummary(context, storageSetting, catX + 4, 112.0f, modY, ROW_H,
								rowAlpha, mouseX, mouseY);
					} else if (setting instanceof BlocksSetting blocksSetting) {
						this.drawBlocksSettingSummary(context, blocksSetting, catX + 4, 112.0f, modY, ROW_H, rowAlpha);
					} else if (setting instanceof MobsSetting mobsSetting) {
						this.drawMobsSettingSummary(context, mobsSetting, catX + 4, 112.0f, modY, ROW_H, rowAlpha);
					} else if (val instanceof Color) {
						boolean colorHovered = uiMouseX >= catX + 4 && uiMouseX <= catX + PANEL_W - 4
								&& uiMouseY >= modY && uiMouseY <= modY + ROW_H;
						ZenyaFont.draw(context, this.font, setting.getDisplayName(), catX + PANEL_PAD, modY + 4,
								this.multiplyAlpha(COLOR_TEXT, rowAlpha), false);
						this.drawColorSetting(context, setting, catX + 4, 112.0f, modY, ROW_H,
								colorHovered ? 1.0f : 0.0f, this.expandedColorSetting == setting ? 1.0f : 0.0f,
								rowAlpha);
					}
					context.pose().popMatrix();
					modY += ROW_STEP;

					if (this.isStringListSetting(module, setting) && this.expandedStringListSetting == setting) {
						float editorAlpha = contentAlpha
								* this.clamp01((animExpandH - (modY - contentStartY)) / (float) ROW_H);
						if (editorAlpha > 0.01f) {
							context.pose().pushMatrix();
							context.pose().translate(0.0f, contentSlideY);
							this.drawStringListEditor(context, this.expandedStringListSetting, catX + 4, modY, 112,
									editorAlpha);
							context.pose().popMatrix();
						}
						modY += this.getStringListEditorExtraHeight(setting);
					}
					if (setting instanceof StorageBlocksSetting expandedStorage
							&& this.expandedStorageBlocksSetting == expandedStorage) {
						float pickerAlpha = contentAlpha
								* this.clamp01((animExpandH - (modY - contentStartY)) / (float) ROW_H);
						context.pose().pushMatrix();
						context.pose().translate(0.0f, contentSlideY);
						if (pickerAlpha > 0.01f) {
							this.drawStorageBlocksPicker(context, expandedStorage, catX + 4, 112.0f, modY,
									mouseX, mouseY, pickerAlpha);
						}
						context.pose().popMatrix();
						modY += this.getStoragePickerExtraHeight(expandedStorage);
					}
					if (setting instanceof MobsSetting expandedMobs && this.expandedMobsSetting == expandedMobs) {
						float pickerAlpha = contentAlpha
								* this.clamp01((animExpandH - (modY - contentStartY)) / (float) ROW_H);
						context.pose().pushMatrix();
						context.pose().translate(0.0f, contentSlideY);
						if (pickerAlpha > 0.01f) {
							this.drawMobsPicker(context, expandedMobs, catX + 4, 112.0f, modY, mouseX, mouseY);
						}
						context.pose().popMatrix();
						modY += this.getMobPickerExtraHeight(expandedMobs);
					}
					if (setting.getValue() instanceof Color && this.expandedColorSetting == setting) {
						modY += COLOR_PICKER_EXTRA_HEIGHT;
					}
				}

				context.pose().popMatrix();
				context.disableScissor();
				catX = savedCatX;
				modY = savedModY;
			}

			if (visibleCount == 0) {
				RenderUtil.drawRoundedRect(context, catX + 4, modY, 112.0f, ROW_H, ROW_RADIUS, COLOR_ROW_BG, false);
				RenderUtil.drawOutline(context, catX + 4, modY, 112.0f, ROW_H, ROW_RADIUS, 1.0f, COLOR_ROW_OUTLINE,
						false);
				ZenyaFont.draw(context, this.font, "No results", catX + PANEL_PAD, modY + 4, COLOR_TEXT_MUTED, false);
			}
		}
		context.pose().popMatrix();

		if (this.closing && this.openAnimScale < 0.02f) {
			this.finishClose();
		}

		// Bind capture polls GLFW directly so it also sees keys Screen never forwards.
		if (this.listeningBind != null) {
			long window = Minecraft.getInstance().getWindow().handle();
			for (int key = 32; key <= 348; ++key) {
				if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_BACKSPACE
						|| GLFW.glfwGetKey(window, key) != GLFW.GLFW_PRESS) {
					continue;
				}
				this.listeningBind.setBind(key);
				this.listeningBind = null;
				break;
			}
			if (this.listeningBind != null
					&& GLFW.glfwGetKey(window, GLFW.GLFW_KEY_BACKSPACE) == GLFW.GLFW_PRESS) {
				this.listeningBind.setBind(0);
				this.listeningBind = null;
			}
			if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS) {
				this.listeningBind = null;
			}
		}
		if (this.listeningActivationBind != null) {
			long window = Minecraft.getInstance().getWindow().handle();
			for (int key = 32; key <= 348; ++key) {
				if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_BACKSPACE
						|| GLFW.glfwGetKey(window, key) != GLFW.GLFW_PRESS) {
					continue;
				}
				this.listeningActivationBind.setActivationKey(key);
				this.listeningActivationBind = null;
				break;
			}
			if (this.listeningActivationBind != null
					&& GLFW.glfwGetKey(window, GLFW.GLFW_KEY_BACKSPACE) == GLFW.GLFW_PRESS) {
				this.listeningActivationBind.setActivationKey(0);
				this.listeningActivationBind = null;
			}
			if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS) {
				this.listeningActivationBind = null;
			}
		}
	}

	private void drawBlocksSettingSummary(GuiGraphics context, BlocksSetting setting, float panelX, float panelWidth,
			float rowY, int rowHeight, float revealAlpha) {
		String arrow = this.expandedBlocksSetting == setting ? "v" : ">";
		int arrowWidth = ZenyaFont.width(this.font, arrow);
		int arrowX = Math.round(panelX + panelWidth - 6.0f - arrowWidth);
		int previewMaxWidth = Math.max(30, arrowX
				- (Math.round(panelX) + PANEL_PAD + ZenyaFont.width(this.font, setting.getDisplayName()) + 14));
		int textColor = this.multiplyAlpha(
				this.expandedBlocksSetting == setting || setting.size() > 0 ? COLOR_TEXT : COLOR_TEXT_MUTED,
				revealAlpha);
		String previewText = setting.size() == 0 ? "Choose" : this.buildBlocksPreviewText(setting);
		String previewLabel = this.trimWithEllipsis(previewText,
				Math.round((previewMaxWidth - 18) / BLOCK_PICKER_TEXT_SCALE));
		int previewWidth = Math.max(34, Math.min(previewMaxWidth, ZenyaFont.width(this.font, previewLabel) + 22));
		int previewX = arrowX - previewWidth - 6;
		int previewColor = this.multiplyAlpha(setting.size() > 0 ? COLOR_ACCENT_DIM : COLOR_KEY_BG, revealAlpha);
		ItemStack previewStack = this.getPreviewBlockStack(setting);

		ZenyaFont.draw(context, this.font, setting.getDisplayName(), Math.round(panelX) + PANEL_PAD,
				Math.round(rowY) + 4, textColor, false);
		RenderUtil.drawRoundedRect(context, previewX, rowY + 2.0f, previewWidth, 12.0f, 5.0f, previewColor, false);
		RenderUtil.drawOutline(context, previewX, rowY + 2.0f, previewWidth, 12.0f, 5.0f, 1.0f,
				this.multiplyAlpha(COLOR_ROW_OUTLINE, revealAlpha), false);
		if (!previewStack.isEmpty()) {
			context.renderItem(previewStack, previewX + 2, Math.round(rowY) + 1);
		}
		int previewTextX = previewX + (previewStack.isEmpty() ? 6 : 16);
		this.drawScaledText(context, previewLabel, previewTextX, rowY + 4.0f, BLOCK_PICKER_TEXT_SCALE,
				this.multiplyAlpha(COLOR_TEXT, revealAlpha));
		ZenyaFont.draw(context, this.font, arrow, arrowX, Math.round(rowY) + 4,
				this.multiplyAlpha(COLOR_TEXT_MUTED, revealAlpha), false);
	}

	private void drawStorageBlocksSettingSummary(GuiGraphics context, StorageBlocksSetting setting, float panelX,
			float panelWidth, float rowY, int rowHeight, float revealAlpha, int mouseX, int mouseY) {
		String arrow = ">";
		int arrowWidth = ZenyaFont.width(this.font, arrow);
		int arrowX = Math.round(panelX + panelWidth - 6.0f - arrowWidth);
		int selected = setting.getSelectedEntries().size();
		int total = setting.getOptions().size();
		String countText = selected == 0 ? "None" : selected + "/" + total;
		int countX = arrowX - ZenyaFont.width(this.font, countText) - 10;
		boolean hovered = mouseX >= panelX && mouseX <= panelX + panelWidth
				&& mouseY >= rowY && mouseY <= rowY + rowHeight;
		float hoverAnim = this.anim("storage/" + setting.getName() + "/hover", hovered ? 1.0f : 0.0f, 18.0f);
		int textColor = this.multiplyAlpha(selected > 0 ? COLOR_TEXT : COLOR_TEXT_MUTED, revealAlpha);

		ZenyaFont.draw(context, this.font, setting.getDisplayName(), Math.round(panelX) + PANEL_PAD,
				Math.round(rowY) + 4, textColor, false);
		ZenyaFont.draw(context, this.font, countText, countX, Math.round(rowY) + 4,
				this.multiplyAlpha(selected > 0 ? COLOR_ACCENT : COLOR_TEXT_MUTED, revealAlpha), false);
		ZenyaFont.draw(context, this.font, arrow, Math.round(arrowX + hoverAnim * 2.0f), Math.round(rowY) + 4,
				this.multiplyAlpha(COLOR_TEXT_MUTED, revealAlpha), false);
	}

	private void drawStorageBlocksPicker(GuiGraphics context, StorageBlocksSetting setting, float panelX,
			float panelWidth, float pickerY, int mouseX, int mouseY, float revealAlpha) {
		int pickerH = this.getStoragePickerExtraHeight(setting);
		RenderUtil.drawRoundedRect(context, panelX, pickerY, panelWidth, pickerH, ROW_RADIUS,
				this.multiplyAlpha(COLOR_KEY_BG, revealAlpha), false);
		RenderUtil.drawOutline(context, panelX, pickerY, panelWidth, pickerH, ROW_RADIUS, 1.0f,
				this.multiplyAlpha(COLOR_ROW_OUTLINE, revealAlpha), false);

		float rowX = panelX + STORAGE_PICKER_PAD;
		float rowW = panelWidth - 2 * STORAGE_PICKER_PAD;
		List<StorageBlocksSetting.Entry> entries = setting.getOptions();
		for (int i = 0; i < entries.size(); ++i) {
			StorageBlocksSetting.Entry entry = entries.get(i);
			float rowY = pickerY + STORAGE_PICKER_PAD + i * (STORAGE_PICKER_ROW_H + STORAGE_PICKER_GAP);
			boolean hovered = mouseX >= rowX && mouseX <= rowX + rowW
					&& mouseY >= rowY && mouseY <= rowY + STORAGE_PICKER_ROW_H;
			boolean selected = setting.isSelected(entry.value());
			int rowColor = selected ? COLOR_ROW_ACTIVE : (hovered ? COLOR_ROW_HOVER : COLOR_ROW_BG);
			int textColor = selected ? COLOR_ACCENT : COLOR_TEXT;
			RenderUtil.drawRoundedRect(context, rowX, rowY, rowW, STORAGE_PICKER_ROW_H, ROW_RADIUS,
					this.multiplyAlpha(rowColor, revealAlpha), false);
			RenderUtil.drawOutline(context, rowX, rowY, rowW, STORAGE_PICKER_ROW_H, ROW_RADIUS, 1.0f,
					this.multiplyAlpha(COLOR_ROW_OUTLINE, revealAlpha), false);

			float indicatorX = rowX + rowW - BLOCK_PICKER_INDICATOR_SIZE - 6.0f;
			String label = this.trimWithEllipsis(entry.label(),
					Math.round((indicatorX - (rowX + 8.0f)) / BLOCK_PICKER_TEXT_SCALE));
			this.drawScaledText(context, label, rowX + 8.0f, rowY + 5.0f, BLOCK_PICKER_TEXT_SCALE,
					this.multiplyAlpha(textColor, revealAlpha));
			int indicatorColor = selected ? COLOR_ACCENT : COLOR_SEARCH_OUTLINE;
			RenderUtil.drawRoundedRect(context, indicatorX, rowY + 6.0f, BLOCK_PICKER_INDICATOR_SIZE,
					BLOCK_PICKER_INDICATOR_SIZE, BLOCK_PICKER_INDICATOR_SIZE * 0.5f,
					this.multiplyAlpha(selected ? COLOR_ACCENT : COLOR_KEY_BG, revealAlpha), false);
			RenderUtil.drawOutline(context, indicatorX, rowY + 6.0f, BLOCK_PICKER_INDICATOR_SIZE,
					BLOCK_PICKER_INDICATOR_SIZE, BLOCK_PICKER_INDICATOR_SIZE * 0.5f, 1.0f,
					this.multiplyAlpha(indicatorColor, revealAlpha), false);
		}
	}

	private void drawBlocksPicker(GuiGraphics context, BlocksSetting setting, float panelX, float panelWidth,
			float pickerY, int mouseX, int mouseY) {
		BlockPickerLayout layout = this.buildBlockPickerLayout(panelX, panelWidth, pickerY, setting);
		List<Block> filteredBlocks = this.getFilteredBlocks(setting);
		this.blockPickerScroll = this.clampBlockPickerScroll(filteredBlocks.size(), this.blockPickerScroll);
		RenderUtil.drawRoundedRect(context, layout.x, layout.y, layout.width, layout.height, 6.0f, COLOR_KEY_BG, false);
		RenderUtil.drawOutline(context, layout.x, layout.y, layout.width, layout.height, 6.0f, 1.0f,
				COLOR_ROW_OUTLINE, false);

		int searchOutline = this.blockSearchActive && this.expandedBlocksSetting == setting
				? COLOR_ACCENT
				: COLOR_SEARCH_OUTLINE;
		RenderUtil.drawRoundedRect(context, layout.searchX, layout.searchY, layout.searchWidth, layout.searchHeight,
				5.0f, COLOR_PANEL_BG, false);
		RenderUtil.drawOutline(context, layout.searchX, layout.searchY, layout.searchWidth, layout.searchHeight, 5.0f,
				1.0f, searchOutline, false);
		RenderUtil.drawRoundedRect(context, layout.clearX, layout.clearY, layout.clearWidth, layout.clearHeight, 5.0f,
				COLOR_ROW_BG, false);
		RenderUtil.drawOutline(context, layout.clearX, layout.clearY, layout.clearWidth, layout.clearHeight, 5.0f,
				1.0f, COLOR_ROW_OUTLINE, false);

		String searchText = this.blockSearchQuery.isEmpty() ? "Search blocks..." : this.blockSearchQuery;
		if (this.blockSearchActive && this.expandedBlocksSetting == setting
				&& System.currentTimeMillis() / 500L % 2L == 0L) {
			searchText = searchText + "_";
		}
		int searchColor = this.blockSearchQuery.isEmpty() && !this.blockSearchActive ? COLOR_TEXT_MUTED : COLOR_TEXT;
		this.drawInputTextClipped(context, layout.searchX, layout.searchY, Math.max(0.0f, layout.searchWidth),
				Math.max(0.0f, layout.searchHeight), searchText, Math.round(layout.searchX) + 6,
				Math.round(layout.searchY) + 4, searchColor);
		ZenyaFont.draw(context, this.font, "Clear", Math.round(layout.clearX) + 4, Math.round(layout.clearY) + 4,
				COLOR_TEXT_MUTED, false);

		if (filteredBlocks.isEmpty()) {
			RenderUtil.drawRoundedRect(context, layout.listX, layout.listY, layout.listWidth, 16.0f, 5.0f,
					COLOR_ROW_BG, false);
			RenderUtil.drawOutline(context, layout.listX, layout.listY, layout.listWidth, 16.0f, 5.0f, 1.0f,
					COLOR_ROW_OUTLINE, false);
			ZenyaFont.draw(context, this.font, "No blocks found", Math.round(layout.listX) + 6,
					Math.round(layout.listY) + 4, COLOR_TEXT_MUTED, false);
			return;
		}

		int visibleRows = Math.min(BLOCK_PICKER_VISIBLE_ROWS, filteredBlocks.size());
		boolean showScrollbar = filteredBlocks.size() > visibleRows;
		float rowWidth = layout.listWidth;
		for (int row = 0; row < visibleRows; ++row) {
			int index = this.blockPickerScroll + row;
			if (index >= filteredBlocks.size()) {
				break;
			}
			Block block = filteredBlocks.get(index);
			float rowY = layout.listY + row * BLOCK_PICKER_ROW_H;
			boolean hovered = mouseX >= layout.listX && mouseX <= layout.listX + layout.listWidth
					&& mouseY >= rowY && mouseY <= rowY + BLOCK_PICKER_ROW_H - 2.0f;
			boolean selected = setting.contains(block);
			int rowColor = selected ? COLOR_ROW_ACTIVE : (hovered ? COLOR_ROW_HOVER : COLOR_ROW_BG);
			int textColor = selected ? COLOR_ACCENT : COLOR_TEXT;
			RenderUtil.drawRoundedRect(context, layout.listX, rowY, rowWidth, 16.0f, 5.0f, rowColor, false);
			RenderUtil.drawOutline(context, layout.listX, rowY, rowWidth, 16.0f, 5.0f, 1.0f, COLOR_ROW_OUTLINE, false);

			ItemStack stack = new ItemStack(block);
			int textX = Math.round(layout.listX) + 5;
			if (!stack.isEmpty()) {
				context.renderItem(stack, Math.round(layout.listX) + 2, Math.round(rowY) + 1);
				textX += 16;
			}
			float indicatorX = layout.listX + rowWidth - 10.0f;
			int textWidth = Math.max(20, Math.round(indicatorX) - textX - 4);
			String displayName = this.trimWithEllipsis(setting.getDisplayName(block),
					Math.round(textWidth / BLOCK_PICKER_TEXT_SCALE));
			this.drawScaledText(context, displayName, textX, rowY + 4.0f, BLOCK_PICKER_TEXT_SCALE, textColor);
			int indicatorColor = selected ? COLOR_ACCENT : COLOR_SEARCH_OUTLINE;
			RenderUtil.drawRoundedRect(context, indicatorX, rowY + 5.0f, BLOCK_PICKER_INDICATOR_SIZE,
					BLOCK_PICKER_INDICATOR_SIZE, 2.5f, selected ? COLOR_ACCENT : COLOR_KEY_BG, false);
			RenderUtil.drawOutline(context, indicatorX, rowY + 5.0f, BLOCK_PICKER_INDICATOR_SIZE,
					BLOCK_PICKER_INDICATOR_SIZE, 2.5f, 1.0f, indicatorColor, false);
		}

		if (showScrollbar) {
			int maxScroll = Math.max(1, filteredBlocks.size() - visibleRows);
			float trackX = layout.listX + layout.listWidth - BLOCK_PICKER_SCROLLBAR_W;
			float trackY = layout.listY + 1.0f;
			float trackHeight = layout.listHeight - 2.0f;
			float thumbHeight = Math.max(12.0f, trackHeight * ((float) visibleRows / filteredBlocks.size()));
			float thumbOffset = (trackHeight - thumbHeight) * ((float) this.blockPickerScroll / maxScroll);
			RenderUtil.drawRoundedRect(context, trackX, trackY, BLOCK_PICKER_SCROLLBAR_W, trackHeight, 2.0f,
					COLOR_PANEL_BG, false);
			RenderUtil.drawRoundedRect(context, trackX, trackY + thumbOffset, BLOCK_PICKER_SCROLLBAR_W, thumbHeight,
					2.0f, COLOR_ACCENT_DIM, false);
		}
	}

	/** Draws the swatch and hex label, plus the SV/hue/alpha bars once the row is expanded. */
	private void drawColorSetting(GuiGraphics context, Setting<?> setting, float panelX, float panelWidth, float rowY,
			int rowHeight, float hoverProgress, float expansionProgress, float revealAlpha) {
		Color color = (Color) setting.getValue();
		float swatchSize = 12.0f;
		float swatchX = panelX + panelWidth - 5.0f - swatchSize;
		float swatchY = rowY + (rowHeight - 4.0f - swatchSize) / 2.0f;
		int swatchArgb = color.getAlpha() << 24 | color.getRGB() & 0xFFFFFF;
		RenderUtil.drawRoundedRect(context, swatchX, swatchY + 2.0f, swatchSize, swatchSize, 4.0f,
				this.multiplyAlpha(-15064526, revealAlpha), false);
		RenderUtil.drawRoundedRect(context, swatchX, swatchY + 2.0f, swatchSize, swatchSize, 4.0f,
				this.multiplyAlpha(swatchArgb, revealAlpha), false);
		RenderUtil.drawOutline(context, swatchX, swatchY + 2.0f, swatchSize, swatchSize, 4.0f, 1.0f,
				this.multiplyAlpha(-2002072321, revealAlpha), false);

		String hex = String.format("#%06X", color.getRGB() & 0xFFFFFF);
		int hexColor = this.multiplyAlpha(this.lerpColor(-10847585, -7228200, hoverProgress), revealAlpha);
		int hexWidth = ZenyaFont.width(Minecraft.getInstance().font, hex);
		ZenyaFont.draw(context, Minecraft.getInstance().font, hex, (int) (swatchX - 5.0f - hexWidth),
				(int) rowY + 4, hexColor, false);
		if (expansionProgress <= 0.01f) {
			return;
		}

		float reveal = this.easeOutCubic(expansionProgress) * revealAlpha;
		float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
		float hue = hsb[0];
		float saturation = hsb[1];
		float brightness = hsb[2];
		float alpha = color.getAlpha() / 255.0f;
		float fieldX = panelX + 8.0f;
		float fieldW = panelWidth - 16.0f;
		float svY = rowY + rowHeight + COLOR_PICKER_GAP * reveal;
		float svH = Math.max(1.0f, COLOR_PICKER_SV_HEIGHT * reveal);
		float svRadius = 5.0f;
		float hueY = svY + svH + COLOR_PICKER_GAP * reveal;
		float hueH = Math.max(1.0f, COLOR_PICKER_HUE_HEIGHT * reveal);
		float alphaBarY = hueY + hueH + COLOR_PICKER_GAP * reveal;
		float alphaBarH = Math.max(1.0f, COLOR_PICKER_ALPHA_HEIGHT * reveal);

		// The SV square is faked as vertical gradient strips at fixed hue.
		int svSegments = 32;
		float segmentW = Math.max(1.0f, fieldW / svSegments);
		for (int i = 0; i < svSegments; ++i) {
			float segmentX = fieldX + segmentW * i;
			float drawWidth = i == svSegments - 1 ? fieldX + fieldW - segmentX : segmentW + 1.0f;
			float segmentSat = (float) i / (svSegments - 1);
			int topArgb = this.withAlpha(0xFF000000 | Color.HSBtoRGB(hue, segmentSat, 1.0f) & 0xFFFFFF, reveal);
			int bottomArgb = this.withAlpha(0xFF000000, reveal);
			float leftRadius = i == 0 ? svRadius : 0.0f;
			float rightRadius = i == svSegments - 1 ? svRadius : 0.0f;
			RenderUtil.drawRoundedRect(context, segmentX, svY, drawWidth, svH, leftRadius, rightRadius, rightRadius,
					leftRadius, false, topArgb, topArgb, bottomArgb, bottomArgb);
		}
		RenderUtil.drawOutline(context, fieldX, svY, fieldW, svH, svRadius, 1.0f, this.withAlpha(1437256959, reveal),
				false);
		float svCursorX = fieldX + saturation * fieldW;
		float svCursorY = svY + (1.0f - brightness) * svH;
		float svCursorR = 5.0f;
		RenderUtil.drawRoundedRect(context, svCursorX - svCursorR, svCursorY - svCursorR, svCursorR * 2.0f,
				svCursorR * 2.0f, svCursorR, this.withAlpha(-872415232, reveal), false);
		RenderUtil.drawOutline(context, svCursorX - svCursorR, svCursorY - svCursorR, svCursorR * 2.0f,
				svCursorR * 2.0f, svCursorR, 1.5f, this.withAlpha(-1, reveal), false);

		this.drawStripBar(context, fieldX, hueY, fieldW, hueH, 4.0f, 48,
				index -> this.withAlpha(0xFF000000 | Color.HSBtoRGB(index / 47.0f, 1.0f, 1.0f) & 0xFFFFFF, reveal));
		RenderUtil.drawOutline(context, fieldX, hueY, fieldW, hueH, 4.0f, 1.0f, this.withAlpha(1437256959, reveal),
				false);
		float hueCursorX = fieldX + hue * fieldW;
		float handleW = 4.0f;
		float handleH = hueH + 4.0f;
		RenderUtil.drawRoundedRect(context, hueCursorX - handleW * 0.5f, hueY - 2.0f, handleW, handleH, 2.0f,
				this.withAlpha(-1, reveal), false);
		RenderUtil.drawOutline(context, hueCursorX - handleW * 0.5f, hueY - 2.0f, handleW, handleH, 2.0f, 1.0f,
				this.withAlpha(-2013265920, reveal), false);

		int rgb = color.getRGB() & 0xFFFFFF;
		RenderUtil.drawRoundedRect(context, fieldX, alphaBarY, fieldW, alphaBarH, 4.0f,
				this.withAlpha(-14340032, reveal), false);
		this.drawStripBar(context, fieldX, alphaBarY, fieldW, alphaBarH, 4.0f, 32,
				index -> this.multiplyAlpha((int) (index / 31.0f * 255.0f) << 24 | rgb, reveal));
		RenderUtil.drawOutline(context, fieldX, alphaBarY, fieldW, alphaBarH, 4.0f, 1.0f,
				this.withAlpha(1437256959, reveal), false);
		float alphaCursorX = fieldX + alpha * fieldW;
		RenderUtil.drawRoundedRect(context, alphaCursorX - handleW * 0.5f, alphaBarY - 2.0f, handleW, alphaBarH + 4.0f,
				2.0f, this.withAlpha(-1, reveal), false);
		RenderUtil.drawOutline(context, alphaCursorX - handleW * 0.5f, alphaBarY - 2.0f, handleW, alphaBarH + 4.0f,
				2.0f, 1.0f, this.withAlpha(-2013265920, reveal), false);
	}

	public float getHue(Color color) {
		return Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null)[0];
	}

	public float getSaturation(Color color) {
		return Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null)[1];
	}

	public float getBrightness(Color color) {
		return Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null)[2];
	}

	public float getAlphaFloat(Color color) {
		return color.getAlpha() / 255.0f;
	}

	/** Horizontal gradient faked as {@code segments} flat strips, rounded only at the ends. */
	private void drawStripBar(GuiGraphics context, float x, float y, float width, float height, float radius,
			int segments, IntFunction<Integer> colorProvider) {
		if (height <= 0.0f) {
			return;
		}
		float segmentWidth = Math.max(1.0f, width / segments);
		for (int i = 0; i < segments; ++i) {
			float segmentX = x + segmentWidth * i;
			float drawWidth = i == segments - 1 ? x + width - segmentX : segmentWidth + 1.0f;
			float leftRadius = i == 0 ? radius : 0.0f;
			float rightRadius = i == segments - 1 ? radius : 0.0f;
			int color = colorProvider.apply(i);
			RenderUtil.drawRoundedRect(context, segmentX, y, drawWidth, height, leftRadius, rightRadius, rightRadius,
					leftRadius, false, color);
		}
	}

	@Override
	public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubleClick) {
		this.uiScale = this.computeUiScale();
		double mouseX = this.toUiX(click.x());
		double mouseY = this.toUiY(click.y());
		int button = click.button();
		this.activeColorSetting = null;
		this.colorDragMode = ColorDragMode.NONE;
		this.draggingNumericSetting = null;
		this.draggingNumericModule = null;
		this.searchActive = false;

		int searchX = this.getSearchX();
		int searchY = this.getSearchY();
		int searchW = this.getSearchWidth();
		if (mouseX >= searchX && mouseX <= searchX + searchW && mouseY >= searchY && mouseY <= searchY + SEARCH_H) {
			if (button == 1) {
				this.searchQuery = "";
			}
			this.searchActive = true;
			return true;
		}

		if (button == 0) {
			if (this.popupModule != null) {
				int popupH = Math.max(110, this.getModuleExpandedHeight(this.popupModule) + POPUP_HEADER_H + 8);
				int closeX = this.popupX + PANEL_W - 18;
				int closeY = this.popupY + 7;
				if (mouseX >= closeX - 4 && mouseX <= closeX + 12 && mouseY >= closeY - 2 && mouseY <= closeY + 12) {
					this.popupModule = null;
					this.popupAnimScale = 0.0f;
					return true;
				}
				if (mouseX >= this.popupX && mouseX <= this.popupX + PANEL_W
						&& mouseY >= this.popupY && mouseY <= this.popupY + POPUP_HEADER_H - 2) {
					this.draggingPopup = true;
					this.popupDragOffsetX = (int) (mouseX - this.popupX);
					this.popupDragOffsetY = (int) (mouseY - this.popupY);
					return true;
				}
				if (mouseX < this.popupX || mouseX > this.popupX + PANEL_W
						|| mouseY < this.popupY || mouseY > this.popupY + popupH) {
					this.popupModule = null;
					this.popupAnimScale = 0.0f;
				}
			}
			Category[] dragCategories = CACHED_CATEGORIES;
			for (int i = 0; i < dragCategories.length; ++i) {
				int headerX = this.getCategoryX(dragCategories[i], i);
				int headerY = this.getCategoryY(dragCategories[i]);
				if (mouseX >= headerX && mouseX <= headerX + PANEL_W
						&& mouseY >= headerY && mouseY <= headerY + PANEL_HEADER_H) {
					this.draggingCategory = dragCategories[i];
					this.dragGrabOffsetX = (int) (mouseX - headerX);
					this.dragGrabOffsetY = (int) (mouseY - headerY);
					return true;
				}
			}
		}

		Category[] categories = CACHED_CATEGORIES;
		for (int categoryIndex = 0; categoryIndex < categories.length; ++categoryIndex) {
			Category category = categories[categoryIndex];
			int catX = this.getCategoryX(category, categoryIndex);
			int catY = this.getCategoryY(category);
			int modY = catY + PANEL_HEADER_H + PANEL_HEADER_SPACING;
			for (Module module : ModuleManager.INSTANCE.getModulesInCategory(category)) {
				if (!this.matchesQuery(module)) {
					continue;
				}
				if (mouseX >= catX + 4 && mouseX <= catX + PANEL_W - 4
						&& mouseY >= modY && mouseY <= modY + ROW_H) {
					if (button == 0) {
						module.toggle();
					} else if (button == 1) {
						if (this.popupModule == module) {
							this.popupModule = null;
						} else {
							this.popupModule = module;
							this.popupX = (int) mouseX + 16;
							this.popupY = (int) mouseY;
						}
					}
					return true;
				}
				modY += ROW_STEP;
				if (module != this.popupModule) {
					continue;
				}

				int savedCatX = catX;
				int savedModY = modY;
				catX = this.popupX;
				modY = this.popupY + POPUP_HEADER_H;

				if (mouseX >= catX + 4 && mouseX <= catX + PANEL_W - 4
						&& mouseY >= modY && mouseY <= modY + ROW_H) {
					if (button == 1) {
						module.setBind(0);
						this.listeningBind = null;
						this.listeningActivationBind = null;
					} else if (button == 0) {
						this.listeningBind = module;
						this.listeningActivationBind = null;
					}
					return true;
				}
				modY += ROW_STEP;

				if (module instanceof ActivatableModule activatable) {
					if (mouseX >= catX + 4 && mouseX <= catX + PANEL_W - 4
							&& mouseY >= modY && mouseY <= modY + ROW_H) {
						if (button == 1) {
							activatable.setActivationKey(0);
							this.listeningActivationBind = null;
							this.listeningBind = null;
						} else if (button == 0) {
							this.listeningActivationBind = activatable;
							this.listeningBind = null;
						}
						return true;
					}
					modY += ROW_STEP;
				}

				for (Setting<?> setting : module.getSettings()) {
					if (mouseX >= catX + 4 && mouseX <= catX + PANEL_W - 4
							&& mouseY >= modY && mouseY <= modY + ROW_H) {
						this.clickSettingRow(module, setting, button, mouseX, catX);
						return true;
					}

					if (this.isStringListSetting(module, setting) && this.expandedStringListSetting == setting) {
						int editorX = catX + 4;
						int editorY = modY + ROW_STEP;
						int editorW = 112;
						int editorH = this.getStringListEditorExtraHeight(setting);
						if (this.pointInRect(mouseX, mouseY, editorX, editorY, editorW, editorH)) {
							this.clickStringListEditor(button, mouseX, mouseY, editorX, editorY, editorW);
							return true;
						}
					}

					if (setting instanceof StorageBlocksSetting storage
							&& this.expandedStorageBlocksSetting == storage) {
						int pickerX = catX + 4;
						int pickerY = modY + ROW_STEP;
						int pickerW = 112;
						int pickerH = this.getStoragePickerExtraHeight(storage);
						if (this.pointInRect(mouseX, mouseY, pickerX, pickerY, pickerW, pickerH)) {
							StorageBlocksSetting.Entry entry = button == 0
									? this.getStorageEntryAt(storage, mouseX, mouseY, pickerX, pickerY, pickerW)
									: null;
							if (entry != null) {
								storage.toggle(entry.value());
							}
							return true;
						}
					}

					// ponytail: the mob picker height is added here AND again after the row step
					// below, so anything under an open mob picker hit-tests one picker too low.
					if (setting instanceof MobsSetting mobs && this.expandedMobsSetting == mobs) {
						modY += this.getMobPickerExtraHeight(mobs);
					}
					if (button == 0 && setting.getValue() instanceof Color && this.expandedColorSetting == setting) {
						ColorPickerLayout layout = this.buildColorPickerLayout(catX + 4, 112.0f, modY, ROW_H);
						if (this.pointInRect(mouseX, mouseY, layout.fieldX, layout.fieldY, layout.fieldWidth,
								layout.fieldHeight)) {
							this.updateColorFromField(this.expandedColorSetting, layout, mouseX, mouseY);
							this.activeColorSetting = this.expandedColorSetting;
							this.colorDragMode = ColorDragMode.FIELD;
							return true;
						}
						if (this.pointInRect(mouseX, mouseY, layout.fieldX, layout.hueY, layout.fieldWidth,
								layout.hueHeight)) {
							this.updateColorFromHue(this.expandedColorSetting, layout, mouseX);
							this.activeColorSetting = this.expandedColorSetting;
							this.colorDragMode = ColorDragMode.HUE;
							return true;
						}
						if (this.pointInRect(mouseX, mouseY, layout.fieldX, layout.alphaY, layout.fieldWidth,
								layout.alphaHeight)) {
							this.updateColorFromAlpha(this.expandedColorSetting, layout, mouseX);
							this.activeColorSetting = this.expandedColorSetting;
							this.colorDragMode = ColorDragMode.ALPHA;
							return true;
						}
					}

					modY += ROW_STEP;
					modY += this.getExpandedSettingExtraHeight(module, setting);
				}
				catX = savedCatX;
				modY = savedModY;
			}
		}

		this.listeningBind = null;
		this.listeningBindSetting = null;
		this.listeningActivationBind = null;
		this.listeningString = null;
		this.stringListAddActive = false;
		this.blockSearchActive = false;
		this.mobSearchActive = false;
		return super.mouseClicked(click, doubleClick);
	}

	/** Applies a click that landed on a setting's own row. */
	@SuppressWarnings("unchecked")
	private void clickSettingRow(Module module, Setting<?> setting, int button, double mouseX, int catX) {
		if (setting instanceof ModeSetting modeSetting) {
			if (button == 1) {
				modeSetting.cyclePrevious();
			} else {
				modeSetting.cycleNext();
			}
		} else if (setting.getValue() instanceof Boolean) {
			Setting<Boolean> flag = (Setting<Boolean>) setting;
			flag.setValue(!flag.getValue());
		} else if (setting.getValue() instanceof String) {
			Setting<String> stringSetting = (Setting<String>) setting;
			if (this.isStringListSetting(module, setting)) {
				if (button == 0) {
					this.expandedStringListSetting = this.expandedStringListSetting == setting ? null : stringSetting;
					this.stringListAddActive = this.expandedStringListSetting == setting;
					this.stringListAddBuffer = "";
					this.listeningString = null;
				} else if (button == 1) {
					this.expandedStringListSetting = null;
					this.stringListAddActive = false;
					this.stringListAddBuffer = "";
				}
			} else {
				this.expandedStringListSetting = null;
				this.stringListAddActive = false;
				this.stringListAddBuffer = "";
				this.listeningString = stringSetting;
			}
		} else if (setting.getValue() instanceof Float || setting.getValue() instanceof Double
				|| setting.getValue() instanceof Integer) {
			if (button == 0) {
				if (setting.getName().toLowerCase(Locale.ROOT).contains("bind")
						&& setting.getValue() instanceof Integer) {
					this.listeningBindSetting = (Setting<Integer>) setting;
					this.listeningBind = null;
					this.listeningActivationBind = null;
				} else {
					this.draggingNumericSetting = setting;
					this.draggingNumericModule = module;
					this.draggingNumericCatX = catX;
					this.updateNumericSetting(module, setting, mouseX, catX);
				}
			}
		} else if (setting instanceof StorageBlocksSetting storage) {
			if (button == 0) {
				Minecraft.getInstance().setScreen(new StoragePickerScreen(this, storage));
			} else if (button == 1) {
				storage.setValue(new LinkedHashSet<>());
				this.expandedStorageBlocksSetting = null;
			}
		} else if (setting instanceof BlocksSetting blocks) {
			if (button == 0) {
				BlockESP esp = module instanceof BlockESP blockEsp ? blockEsp : null;
				Minecraft.getInstance().setScreen(new BlockPickerScreen(this, blocks, esp));
			} else if (button == 1) {
				blocks.clear();
			}
		} else if (setting instanceof MobsSetting mobs) {
			if (button == 0) {
				if (this.expandedMobsSetting != mobs) {
					this.mobSearchQuery = "";
					this.mobPickerScroll = 0;
				}
				this.expandedMobsSetting = this.expandedMobsSetting == mobs ? null : mobs;
				this.mobSearchActive = this.expandedMobsSetting == mobs;
			} else if (button == 1) {
				mobs.clear();
				this.mobPickerScroll = 0;
			}
		} else if (setting.getValue() instanceof Color) {
			if (button == 0) {
				this.expandedColorSetting = this.expandedColorSetting == setting ? null : (Setting<Color>) setting;
			} else if (button == 1) {
				this.expandedColorSetting = null;
			}
		}
	}

	/** Applies a click inside the open Friends name editor. Always consumes the click. */
	private void clickStringListEditor(int button, double mouseX, double mouseY, int editorX, int editorY,
			int editorW) {
		Setting<String> nameList = this.expandedStringListSetting;
		List<String> names = this.parseStringList(nameList);
		int visible = Math.min(6, names.size());
		for (int i = 0; i < visible; ++i) {
			int btnSize = 12;
			int btnX = editorX + editorW - PANEL_PAD - btnSize;
			int btnY = editorY + i * ROW_STEP + 2;
			if (!this.pointInRect(mouseX, mouseY, btnX, btnY, btnSize, btnSize) || button != 0) {
				continue;
			}
			String toRemove = names.get(i);
			names.removeIf(name -> name.equalsIgnoreCase(toRemove));
			this.setStringListFromLowerList(nameList, names);
			return;
		}

		int addRowTop = editorY + visible * ROW_STEP;
		int plusSize = 12;
		int plusX = editorX + editorW - PANEL_PAD - plusSize;
		int plusY = addRowTop + 2;
		if (button == 0 && this.pointInRect(mouseX, mouseY, plusX, plusY, plusSize, plusSize)) {
			String candidate = this.stringListAddBuffer == null ? "" : this.stringListAddBuffer.trim();
			if (!candidate.isEmpty()) {
				String lower = candidate.toLowerCase(Locale.ROOT);
				boolean exists = false;
				for (String name : names) {
					if (!name.equalsIgnoreCase(lower)) {
						continue;
					}
					exists = true;
					break;
				}
				if (!exists) {
					names.add(lower);
					this.setStringListFromLowerList(nameList, names);
				}
			}
			this.stringListAddBuffer = "";
			this.stringListAddActive = true;
			this.listeningString = null;
			return;
		}
		if (button == 0 && this.pointInRect(mouseX, mouseY, editorX, addRowTop, editorW, ROW_H)) {
			this.stringListAddActive = true;
			this.listeningString = null;
		}
	}

	/** Height an expanded editor/picker adds under a setting row, as the click walkers count it. */
	private int getExpandedSettingExtraHeight(Module module, Setting<?> setting) {
		int extra = 0;
		if (this.isStringListSetting(module, setting) && this.expandedStringListSetting == setting) {
			extra += this.getStringListEditorExtraHeight(setting);
		}
		if (setting instanceof BlocksSetting blocks && this.expandedBlocksSetting == blocks) {
			extra += this.getBlockPickerExtraHeight(blocks);
		}
		if (setting instanceof StorageBlocksSetting storage && this.expandedStorageBlocksSetting == storage) {
			extra += this.getStoragePickerExtraHeight(storage);
		}
		if (setting instanceof MobsSetting mobs && this.expandedMobsSetting == mobs) {
			extra += this.getMobPickerExtraHeight(mobs);
		}
		if (setting.getValue() instanceof Color && this.expandedColorSetting == setting) {
			extra += COLOR_PICKER_EXTRA_HEIGHT;
		}
		return extra;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
		this.uiScale = this.computeUiScale();
		double mouseX = this.toUiX(click.x());
		double mouseY = this.toUiY(click.y());
		if (click.button() != 0) {
			return super.mouseDragged(click, deltaX, deltaY);
		}
		if (this.colorDragMode != ColorDragMode.NONE && this.activeColorSetting != null
				&& this.updateActiveColorDrag(mouseX, mouseY)) {
			return true;
		}
		if (this.draggingNumericSetting != null && this.draggingNumericModule != null) {
			this.updateNumericSetting(this.draggingNumericModule, this.draggingNumericSetting, mouseX,
					this.draggingNumericCatX);
			return true;
		}
		if (this.draggingPopup) {
			this.popupX = (int) (mouseX - this.popupDragOffsetX);
			this.popupY = (int) (mouseY - this.popupDragOffsetY);
			return true;
		}
		if (this.draggingCategory != null) {
			Category[] categories = CACHED_CATEGORIES;
			int index = 0;
			for (int i = 0; i < categories.length; ++i) {
				if (categories[i] != this.draggingCategory) {
					continue;
				}
				index = i;
				break;
			}
			int defaultX = 30 + index * (PANEL_W + PANEL_GAP);
			int defaultY = this.getContentTop() + this.verticalScroll;
			int[] offset = this.getCategoryOffset(this.draggingCategory);
			offset[0] = (int) (mouseX - this.dragGrabOffsetX) - defaultX;
			offset[1] = (int) (mouseY - this.dragGrabOffsetY) - defaultY;
			return true;
		}

		// Dragging outside a grabbed control still nudges whichever slider is under the cursor.
		Category[] categories = CACHED_CATEGORIES;
		for (int categoryIndex = 0; categoryIndex < categories.length; ++categoryIndex) {
			Category category = categories[categoryIndex];
			int catX = this.getCategoryX(category, categoryIndex);
			int modY = this.getCategoryY(category) + PANEL_HEADER_H + PANEL_HEADER_SPACING;
			for (Module module : ModuleManager.INSTANCE.getModulesInCategory(category)) {
				if (!this.matchesQuery(module)) {
					continue;
				}
				modY += ROW_STEP;
				if (module != this.popupModule) {
					continue;
				}
				int savedCatX = catX;
				int savedModY = modY;
				catX = this.popupX;
				modY = this.popupY + POPUP_HEADER_H;
				// ponytail: skips the bind AND activation rows even for plain Modules, which
				// only have a bind row - unlike mouseClicked, which skips activation only for
				// an ActivatableModule.
				modY += 2 * ROW_STEP;
				for (Setting<?> setting : module.getSettings()) {
					if (mouseX >= catX + 4 && mouseX <= catX + PANEL_W - 4
							&& mouseY >= modY && mouseY <= modY + ROW_H
							&& (setting.getValue() instanceof Float || setting.getValue() instanceof Double
									|| setting.getValue() instanceof Integer)) {
						this.updateNumericSetting(module, setting, mouseX, catX);
					}
					modY += ROW_STEP;
					modY += this.getDraggedSettingExtraHeight(setting);
				}
				catX = savedCatX;
				modY = savedModY;
			}
		}
		return super.mouseDragged(click, deltaX, deltaY);
	}

	/** Same as {@link #getExpandedSettingExtraHeight} but without the Friends editor. */
	private int getDraggedSettingExtraHeight(Setting<?> setting) {
		int extra = 0;
		if (setting instanceof BlocksSetting blocks && this.expandedBlocksSetting == blocks) {
			extra += this.getBlockPickerExtraHeight(blocks);
		}
		if (setting instanceof StorageBlocksSetting storage && this.expandedStorageBlocksSetting == storage) {
			extra += this.getStoragePickerExtraHeight(storage);
		}
		if (setting instanceof MobsSetting mobs && this.expandedMobsSetting == mobs) {
			extra += this.getMobPickerExtraHeight(mobs);
		}
		if (setting.getValue() instanceof Color && this.expandedColorSetting == setting) {
			extra += COLOR_PICKER_EXTRA_HEIGHT;
		}
		return extra;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent click) {
		this.activeColorSetting = null;
		this.colorDragMode = ColorDragMode.NONE;
		this.draggingPopup = false;
		this.draggingCategory = null;
		this.draggingNumericSetting = null;
		this.draggingNumericModule = null;
		return super.mouseReleased(click);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		this.uiScale = this.computeUiScale();
		mouseX = this.toUiX(mouseX);
		mouseY = this.toUiY(mouseY);
		double amount = verticalAmount != 0.0 ? verticalAmount : horizontalAmount;
		if (amount == 0.0) {
			return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
		}
		BlockPickerContext blockContext = this.getExpandedBlocksPickerContext();
		if (blockContext != null && this.pointInRect(mouseX, mouseY, blockContext.layout().x, blockContext.layout().y,
				blockContext.layout().width, blockContext.layout().height)) {
			int direction = amount > 0.0 ? -1 : 1;
			this.blockPickerScroll = this.clampBlockPickerScroll(
					this.getFilteredBlocks(blockContext.setting()).size(), this.blockPickerScroll + direction);
			return true;
		}
		MobPickerContext mobContext = this.getExpandedMobsPickerContext();
		if (mobContext != null && this.pointInRect(mouseX, mouseY, mobContext.layout().x, mobContext.layout().y,
				mobContext.layout().width, mobContext.layout().height)) {
			int direction = amount > 0.0 ? -1 : 1;
			this.mobPickerScroll = this.clampMobPickerScroll(this.getFilteredMobs(mobContext.setting()).size(),
					this.mobPickerScroll + direction);
			return true;
		}
		this.verticalScroll = this.clampVerticalScroll(this.verticalScroll + (int) Math.round(amount * SCROLL_STEP));
		return true;
	}

	@Override
	public boolean charTyped(CharacterEvent input) {
		String typed = this.sanitizeTextInput(input.codepointAsString());
		if (typed.isEmpty()) {
			return super.charTyped(input);
		}
		if (this.blockSearchActive && this.expandedBlocksSetting != null) {
			this.blockSearchQuery = this.blockSearchQuery + typed;
			this.blockPickerScroll = 0;
			return true;
		}
		if (this.mobSearchActive && this.expandedMobsSetting != null) {
			this.mobSearchQuery = this.mobSearchQuery + typed;
			this.mobPickerScroll = 0;
			return true;
		}
		if (this.searchActive && this.listeningString == null) {
			this.searchQuery = this.searchQuery + typed;
			return true;
		}
		if (this.stringListAddActive && this.expandedStringListSetting != null) {
			this.stringListAddBuffer = (this.stringListAddBuffer == null ? "" : this.stringListAddBuffer) + typed;
			return true;
		}
		if (this.listeningString != null) {
			this.listeningString.setValue(this.listeningString.getValue() + typed);
			return true;
		}
		return super.charTyped(input);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		if (this.blockSearchActive && this.expandedBlocksSetting != null && this.handleBlockSearchKeyInput(input)) {
			return true;
		}
		if (this.mobSearchActive && this.expandedMobsSetting != null && this.handleMobSearchKeyInput(input)) {
			return true;
		}
		if (this.searchActive) {
			if (this.handleSearchKeyInput(input)) {
				this.searchActive = false;
				return true;
			}
			if (input.input() == GLFW.GLFW_KEY_BACKSPACE) {
				this.searchQuery = this.removeLastCodePoint(this.searchQuery);
				return true;
			}
			if (input.isPaste()) {
				this.searchQuery = this.searchQuery + this.getClipboardText();
				return true;
			}
			return true;
		}
		if (input.isEscape()) {
			this.requestClose();
			return true;
		}
		if (this.listeningString != null && this.handleStringKeyInput(input)) {
			return true;
		}
		if (this.stringListAddActive && this.expandedStringListSetting != null
				&& this.handleFriendsAddKeyInput(input)) {
			return true;
		}
		if (this.listeningBindSetting != null) {
			// Escape above already returned, so this only ever takes the second branch.
			this.listeningBindSetting.setValue(input.isEscape() ? 0 : input.input());
			this.listeningBindSetting = null;
			return true;
		}
		return super.keyPressed(input);
	}

	/** Always consumes the key while the Friends "add" field is focused. */
	private boolean handleFriendsAddKeyInput(KeyEvent input) {
		if (input.input() == GLFW.GLFW_KEY_BACKSPACE) {
			this.stringListAddBuffer = this.removeLastCodePoint(
					this.stringListAddBuffer == null ? "" : this.stringListAddBuffer);
			return true;
		}
		if (input.isPaste()) {
			this.stringListAddBuffer = (this.stringListAddBuffer == null ? "" : this.stringListAddBuffer)
					+ this.getClipboardText();
			return true;
		}
		if (input.isEscape()) {
			this.stringListAddActive = false;
			this.stringListAddBuffer = "";
			return true;
		}
		if (input.isConfirmation()) {
			if (this.expandedStringListSetting != null) {
				List<String> names = this.parseStringList(this.expandedStringListSetting);
				String candidate = this.stringListAddBuffer == null ? "" : this.stringListAddBuffer.trim();
				if (!candidate.isEmpty()) {
					String lower = candidate.toLowerCase(Locale.ROOT);
					boolean exists = false;
					for (String name : names) {
						if (!name.equalsIgnoreCase(lower)) {
							continue;
						}
						exists = true;
						break;
					}
					if (!exists) {
						names.add(lower);
						this.setStringListFromLowerList(this.expandedStringListSetting, names);
					}
				}
			}
			this.stringListAddBuffer = "";
			return true;
		}
		return true;
	}

	/** The three GUI-scale modules are configured elsewhere, so they never show up here. */
	private boolean matchesQuery(Module module) {
		String name = module.getName().toLowerCase(Locale.ROOT);
		if (name.equals("themes") || name.equals("gui scale") || name.equals("menu size")) {
			return false;
		}
		if (this.searchQuery.isBlank()) {
			return true;
		}
		String query = this.searchQuery.trim().toLowerCase(Locale.ROOT);
		return name.contains(query) || module.getDisplayName().toLowerCase(Locale.ROOT).contains(query);
	}

	private String getBindLabel(Module module) {
		String keyName = this.getKeyDisplayName(module.getBind());
		return "None".equals(keyName) ? "" : keyName;
	}

	private String getKeyDisplayName(int keyCode) {
		return getKeyDisplayNameStatic(keyCode);
	}

	/** Short label for a key code; GLFW's own name wins when the layout has one. */
	public static String getKeyDisplayNameStatic(int keyCode) {
		if (keyCode == 0) {
			return "None";
		}
		String glfwName = GLFW.glfwGetKeyName(keyCode, 0);
		if (glfwName != null && !glfwName.isBlank()) {
			return normalizeKeyName(glfwName);
		}
		if (keyCode >= GLFW.GLFW_KEY_F1 && keyCode <= GLFW.GLFW_KEY_F25) {
			return "F" + (keyCode - GLFW.GLFW_KEY_F1 + 1);
		}
		if (keyCode >= GLFW.GLFW_KEY_KP_0 && keyCode <= GLFW.GLFW_KEY_KP_9) {
			return "Num" + (keyCode - GLFW.GLFW_KEY_KP_0);
		}
		return switch (keyCode) {
			case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RShift";
			case GLFW.GLFW_KEY_LEFT_SHIFT -> "LShift";
			case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RCtrl";
			case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCtrl";
			case GLFW.GLFW_KEY_RIGHT_ALT -> "RAlt";
			case GLFW.GLFW_KEY_LEFT_ALT -> "LAlt";
			case GLFW.GLFW_KEY_RIGHT_SUPER -> "RSuper";
			case GLFW.GLFW_KEY_LEFT_SUPER -> "LSuper";
			case GLFW.GLFW_KEY_ENTER -> "Enter";
			case GLFW.GLFW_KEY_TAB -> "Tab";
			case GLFW.GLFW_KEY_BACKSPACE -> "Backspace";
			case GLFW.GLFW_KEY_INSERT -> "Insert";
			case GLFW.GLFW_KEY_DELETE -> "Delete";
			case GLFW.GLFW_KEY_HOME -> "Home";
			case GLFW.GLFW_KEY_END -> "End";
			case GLFW.GLFW_KEY_PAGE_UP -> "PgUp";
			case GLFW.GLFW_KEY_PAGE_DOWN -> "PgDn";
			case GLFW.GLFW_KEY_ESCAPE -> "Esc";
			case GLFW.GLFW_KEY_SPACE -> "Space";
			case GLFW.GLFW_KEY_CAPS_LOCK -> "Caps";
			case GLFW.GLFW_KEY_NUM_LOCK -> "NumLock";
			case GLFW.GLFW_KEY_SCROLL_LOCK -> "ScrLock";
			case GLFW.GLFW_KEY_PRINT_SCREEN -> "PrtScr";
			case GLFW.GLFW_KEY_PAUSE -> "Pause";
			case GLFW.GLFW_KEY_MENU -> "Menu";
			case GLFW.GLFW_KEY_UP -> "Up";
			case GLFW.GLFW_KEY_DOWN -> "Down";
			case GLFW.GLFW_KEY_LEFT -> "Left";
			case GLFW.GLFW_KEY_RIGHT -> "Right";
			case GLFW.GLFW_KEY_KP_DECIMAL -> "Num.";
			case GLFW.GLFW_KEY_KP_DIVIDE -> "Num/";
			case GLFW.GLFW_KEY_KP_MULTIPLY -> "Num*";
			case GLFW.GLFW_KEY_KP_SUBTRACT -> "Num-";
			case GLFW.GLFW_KEY_KP_ADD -> "Num+";
			case GLFW.GLFW_KEY_KP_ENTER -> "NumEnter";
			case GLFW.GLFW_KEY_KP_EQUAL -> "Num=";
			default -> "Key " + keyCode;
		};
	}

	private static String normalizeKeyName(String value) {
		if (value == null) {
			return "";
		}
		String trimmed = value.trim();
		if (trimmed.isEmpty()) {
			return "";
		}
		return switch (trimmed.toLowerCase()) {
			case "right shift" -> "RShift";
			case "left shift" -> "LShift";
			case "right control", "right ctrl" -> "RCtrl";
			case "left control", "left ctrl" -> "LCtrl";
			case "right alt" -> "RAlt";
			case "left alt" -> "LAlt";
			case "escape" -> "Esc";
			case "caps lock" -> "Caps";
			case "page up" -> "Page Up";
			case "page down" -> "Page Down";
			default -> trimmed.length() == 1 ? trimmed.toUpperCase() : trimmed;
		};
	}

	private void drawModeSetting(GuiGraphics context, ModeSetting setting, int rowX, int rowWidth, int rowY,
			float revealAlpha) {
		String value = setting.getValue();
		int valueWidth = ZenyaFont.width(this.font, value) + 12;
		int valueX = rowX + rowWidth - PANEL_PAD - valueWidth;
		int textY = rowY + 4;
		int labelX = rowX + PANEL_PAD;
		int maxLabelW = Math.max(0, valueX - 4 - labelX);

		String displayLabel = setting.getDisplayName();
		if (ZenyaFont.width(this.font, displayLabel) > maxLabelW) {
			String ellipsis = "...";
			int ellipsisWidth = ZenyaFont.width(this.font, ellipsis);
			while (!displayLabel.isEmpty()
					&& ZenyaFont.width(this.font, displayLabel) + ellipsisWidth > maxLabelW) {
				displayLabel = displayLabel.substring(0, displayLabel.length() - 1);
			}
			displayLabel = displayLabel + ellipsis;
		}
		ZenyaFont.draw(context, this.font, displayLabel, labelX, textY, this.multiplyAlpha(COLOR_TEXT, revealAlpha),
				false);
		RenderUtil.drawRoundedRect(context, valueX, rowY + 2, valueWidth, 12.0f, 5.0f,
				this.multiplyAlpha(COLOR_KEY_BG, revealAlpha), false);
		RenderUtil.drawOutline(context, valueX, rowY + 2, valueWidth, 12.0f, 5.0f, 1.0f,
				this.multiplyAlpha(COLOR_ROW_OUTLINE, revealAlpha), false);
		ZenyaFont.draw(context, this.font, value, valueX + 6, textY, this.multiplyAlpha(COLOR_ACCENT, revealAlpha),
				false);
	}

	/** One row per name with a remove button, then an "Add:" row with a plus button. */
	private void drawStringListEditor(GuiGraphics context, Setting<String> setting, int x, int y, int width,
			float alpha) {
		List<String> names = this.parseStringList(setting);
		int visible = Math.min(6, names.size());
		int height = this.getStringListEditorExtraHeight(setting);
		RenderUtil.drawRoundedRect(context, x, y, width, height, ROW_RADIUS,
				this.multiplyAlpha(COLOR_ROW_BG, alpha), false);
		RenderUtil.drawOutline(context, x, y, width, height, ROW_RADIUS, 1.0f,
				this.multiplyAlpha(COLOR_ROW_OUTLINE, alpha), false);

		int rowY = y;
		for (int i = 0; i < visible; ++i) {
			ZenyaFont.draw(context, this.font, names.get(i), x + PANEL_PAD, rowY + 4,
					this.multiplyAlpha(COLOR_TEXT, alpha), false);
			int btnSize = 12;
			int btnX = x + width - PANEL_PAD - btnSize;
			int btnY = rowY + 2;
			RenderUtil.drawRoundedRect(context, btnX, btnY, btnSize, btnSize, 4.0f,
					this.multiplyAlpha(-14011323, alpha), false);
			RenderUtil.drawOutline(context, btnX, btnY, btnSize, btnSize, 4.0f, 1.0f,
					this.multiplyAlpha(COLOR_ROW_OUTLINE, alpha), false);
			ZenyaFont.draw(context, this.font, "x", btnX + 4, rowY + 4, this.multiplyAlpha(-1938838, alpha), false);
			rowY += ROW_STEP;
		}

		String addText = "Add: " + (this.stringListAddBuffer == null ? "" : this.stringListAddBuffer);
		if (this.stringListAddActive && this.expandedStringListSetting == setting) {
			addText = addText + "_";
		}
		this.drawInputTextClipped(context, x, rowY, width, ROW_H, addText, x + PANEL_PAD, rowY + 4,
				this.multiplyAlpha(COLOR_TEXT_MUTED, alpha));
		int plusSize = 12;
		int plusX = x + width - PANEL_PAD - plusSize;
		int plusY = rowY + 2;
		RenderUtil.drawRoundedRect(context, plusX, plusY, plusSize, plusSize, 4.0f,
				this.multiplyAlpha(COLOR_KEY_BG, alpha), false);
		RenderUtil.drawOutline(context, plusX, plusY, plusSize, plusSize, 4.0f, 1.0f,
				this.multiplyAlpha(COLOR_ROW_OUTLINE, alpha), false);
		ZenyaFont.draw(context, this.font, "+", plusX + 4, rowY + 4, this.multiplyAlpha(COLOR_ACCENT, alpha), false);
	}

	/** Header plus one row per visible module; an empty panel still reserves the "No results" row. */
	private int getPanelHeight(Category category) {
		int height = 40;
		int visibleCount = 0;
		for (Module module : ModuleManager.INSTANCE.getModulesInCategory(category)) {
			if (!this.matchesQuery(module)) {
				continue;
			}
			++visibleCount;
			height += ROW_STEP;
		}
		if (visibleCount == 0) {
			height += ROW_STEP;
		}
		return height;
	}

	private int getStoragePickerExtraHeight(StorageBlocksSetting setting) {
		int count = setting == null ? 0 : setting.getOptions().size();
		if (count <= 0) {
			return 26;
		}
		return 2 * STORAGE_PICKER_PAD + count * STORAGE_PICKER_ROW_H
				+ Math.max(0, count - 1) * STORAGE_PICKER_GAP;
	}

	private StorageBlocksSetting.Entry getStorageEntryAt(StorageBlocksSetting setting, double mouseX, double mouseY,
			float pickerX, float pickerY, float pickerW) {
		if (setting == null) {
			return null;
		}
		float rowX = pickerX + STORAGE_PICKER_PAD;
		float rowW = pickerW - 2 * STORAGE_PICKER_PAD;
		List<StorageBlocksSetting.Entry> entries = setting.getOptions();
		for (int i = 0; i < entries.size(); ++i) {
			float rowY = pickerY + STORAGE_PICKER_PAD + i * (STORAGE_PICKER_ROW_H + STORAGE_PICKER_GAP);
			if (!this.pointInRect(mouseX, mouseY, rowX, rowY, rowW, STORAGE_PICKER_ROW_H)) {
				continue;
			}
			return entries.get(i);
		}
		return null;
	}

	private ItemStack getPreviewStorageStack(StorageBlocksSetting setting) {
		if (setting == null) {
			return ItemStack.EMPTY;
		}
		List<StorageBlocksSetting.Entry> entries = setting.getSelectedEntries();
		if (entries.isEmpty()) {
			return ItemStack.EMPTY;
		}
		ItemStack icon = entries.get(0).icon();
		return icon == null || icon.isEmpty() ? ItemStack.EMPTY : icon;
	}

	private BlockPickerLayout buildBlockPickerLayout(float panelX, float panelWidth, float pickerY,
			BlocksSetting setting) {
		int visibleRows = Math.min(BLOCK_PICKER_VISIBLE_ROWS, Math.max(1, this.getFilteredBlocks(setting).size()));
		float searchX = panelX + BLOCK_PICKER_GAP;
		float searchY = pickerY + BLOCK_PICKER_GAP;
		float clearX = panelX + panelWidth - BLOCK_PICKER_CLEAR_W - BLOCK_PICKER_GAP;
		float searchWidth = Math.max(24.0f, clearX - searchX - 4.0f);
		float listX = panelX + BLOCK_PICKER_GAP;
		float listY = searchY + BLOCK_PICKER_SEARCH_H + BLOCK_PICKER_GAP;
		float listWidth = panelWidth - 2 * BLOCK_PICKER_GAP;
		float listHeight = visibleRows * BLOCK_PICKER_ROW_H;
		float height = 28.0f + listHeight + BLOCK_PICKER_BOTTOM_PAD;
		return new BlockPickerLayout(panelX, pickerY, panelWidth, height, searchX, searchY, searchWidth,
				BLOCK_PICKER_SEARCH_H, clearX, searchY, BLOCK_PICKER_CLEAR_W, BLOCK_PICKER_SEARCH_H, listX, listY,
				listWidth, listHeight);
	}

	/** Search hits, selected blocks first, then case-insensitive by display name. */
	private List<Block> getFilteredBlocks(BlocksSetting setting) {
		List<Block> filtered = new ArrayList<>(setting.filter(this.blockSearchQuery));
		filtered.sort(Comparator.comparing((Block block) -> !setting.contains(block))
				.thenComparing(setting::getDisplayName, String.CASE_INSENSITIVE_ORDER));
		return filtered;
	}

	private String buildBlocksPreviewText(BlocksSetting setting) {
		Block firstBlock = setting.getSelectedBlocks().stream().findFirst().orElse(null);
		if (firstBlock == null) {
			return "Choose";
		}
		String name = setting.getDisplayName(firstBlock);
		int extra = setting.size() - 1;
		return extra > 0 ? name + " +" + extra : name;
	}

	private ItemStack getPreviewBlockStack(BlocksSetting setting) {
		Block firstBlock = setting.getSelectedBlocks().stream().findFirst().orElse(null);
		if (firstBlock == null) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = new ItemStack(firstBlock);
		return stack.isEmpty() ? ItemStack.EMPTY : stack;
	}

	private String trimWithEllipsis(String text, int maxWidth) {
		if (text == null || text.isEmpty() || maxWidth <= 0) {
			return "";
		}
		if (ZenyaFont.width(this.font, text) <= maxWidth) {
			return text;
		}
		String ellipsis = "...";
		int ellipsisWidth = ZenyaFont.width(this.font, ellipsis);
		if (ellipsisWidth >= maxWidth) {
			return this.font.plainSubstrByWidth(text, maxWidth);
		}
		return this.font.plainSubstrByWidth(text, maxWidth - ellipsisWidth) + ellipsis;
	}

	private String formatStringSettingValue(Module module, Setting<?> setting, String value) {
		if (value == null || value.isEmpty()) {
			return "";
		}
		if (this.isCoordSnapperWebhookSetting(module, setting)) {
			return this.abbreviateSensitiveSuffix(value, 10);
		}
		return value;
	}

	private boolean isCoordSnapperWebhookSetting(Module module, Setting<?> setting) {
		return module != null && setting != null
				&& "CoordSnapper".equalsIgnoreCase(module.getName())
				&& setting.matchesName("Webhook");
	}

	/** Shows only the tail of a webhook URL so a stream never reveals the token. */
	private String abbreviateSensitiveSuffix(String value, int visibleChars) {
		String trimmed = value == null ? "" : value.trim();
		if (trimmed.isEmpty()) {
			return "";
		}
		int lastSlash = Math.max(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'));
		String tailSource = lastSlash >= 0 && lastSlash < trimmed.length() - 1
				? trimmed.substring(lastSlash + 1)
				: trimmed;
		if (tailSource.length() <= visibleChars) {
			return "..." + tailSource;
		}
		return "..." + tailSource.substring(tailSource.length() - visibleChars);
	}

	private void drawScaledText(GuiGraphics context, String text, float x, float y, float scale, int color) {
		if (text == null || text.isEmpty()) {
			return;
		}
		Matrix3x2fStack matrices = context.pose();
		matrices.pushMatrix();
		matrices.translate(x, y);
		matrices.scale(scale, scale);
		ZenyaFont.draw(context, this.font, text, 0, 0, color, false);
		matrices.popMatrix();
	}

	/** Draws text scissored to its container so an over-long input never spills out of the row. */
	private void drawInputTextClipped(GuiGraphics context, float containerX, float containerY, float containerWidth,
			float containerHeight, String text, int textX, int textY, int color) {
		if (text == null) {
			text = "";
		}
		float width = Math.max(0.0f, containerWidth);
		float height = Math.max(0.0f, containerHeight);
		RenderUtil.setScissor(containerX * this.uiScale, containerY * this.uiScale, width * this.uiScale,
				height * this.uiScale, false);
		ZenyaFont.draw(context, this.font, text, textX, textY, color, false);
		RenderUtil.clearScissor(false);
	}

	private int getBlockPickerExtraHeight(BlocksSetting setting) {
		return Math.round(this.buildBlockPickerLayout(0.0f, 112.0f, 0.0f, setting).height);
	}

	private int clampBlockPickerScroll(int itemCount, int value) {
		int maxScroll = Math.max(0, itemCount - BLOCK_PICKER_VISIBLE_ROWS);
		return Math.max(0, Math.min(maxScroll, value));
	}

	private BlockPickerLayout buildMobPickerLayout(float panelX, float panelWidth, float pickerY, MobsSetting setting) {
		int visibleRows = Math.min(BLOCK_PICKER_VISIBLE_ROWS, Math.max(1, this.getFilteredMobs(setting).size()));
		float searchX = panelX + BLOCK_PICKER_GAP;
		float searchY = pickerY + BLOCK_PICKER_GAP;
		float clearX = panelX + panelWidth - BLOCK_PICKER_CLEAR_W - BLOCK_PICKER_GAP;
		float searchWidth = Math.max(24.0f, clearX - searchX - 4.0f);
		float listX = panelX + BLOCK_PICKER_GAP;
		float listY = searchY + BLOCK_PICKER_SEARCH_H + BLOCK_PICKER_GAP;
		float listWidth = panelWidth - 2 * BLOCK_PICKER_GAP;
		float listHeight = visibleRows * BLOCK_PICKER_ROW_H;
		float height = 28.0f + listHeight + BLOCK_PICKER_BOTTOM_PAD;
		return new BlockPickerLayout(panelX, pickerY, panelWidth, height, searchX, searchY, searchWidth,
				BLOCK_PICKER_SEARCH_H, clearX, searchY, BLOCK_PICKER_CLEAR_W, BLOCK_PICKER_SEARCH_H, listX, listY,
				listWidth, listHeight);
	}

	private List<EntityType<?>> getFilteredMobs(MobsSetting setting) {
		List<EntityType<?>> filtered = new ArrayList<>(setting.filter(this.mobSearchQuery));
		filtered.sort(Comparator.comparing((EntityType<?> type) -> !setting.contains(type))
				.thenComparing(setting::getDisplayName, String.CASE_INSENSITIVE_ORDER));
		return filtered;
	}

	private int getMobPickerExtraHeight(MobsSetting setting) {
		return Math.round(this.buildMobPickerLayout(0.0f, 112.0f, 0.0f, setting).height);
	}

	private int clampMobPickerScroll(int itemCount, int value) {
		int maxScroll = Math.max(0, itemCount - BLOCK_PICKER_VISIBLE_ROWS);
		return Math.max(0, Math.min(maxScroll, value));
	}

	private String buildMobsPreviewText(MobsSetting setting) {
		EntityType<?> first = setting.getSelectedMobs().stream().findFirst().orElse(null);
		if (first == null) {
			return "Choose";
		}
		String name = setting.getDisplayName(first);
		int extra = setting.size() - 1;
		return extra > 0 ? name + " +" + extra : name;
	}

	private ItemStack getPreviewMobStack(MobsSetting setting) {
		EntityType<?> first = setting.getSelectedMobs().stream().findFirst().orElse(null);
		if (first == null) {
			return ItemStack.EMPTY;
		}
		return this.getMobStack(first);
	}

	private ItemStack getMobStack(EntityType<?> type) {
		try {
			SpawnEggItem egg = SpawnEggItem.byId(type);
			if (egg != null) {
				return new ItemStack(egg);
			}
		} catch (Throwable ignored) {
			// Entity types without a spawn egg fall back to a plain egg icon.
		}
		return new ItemStack(Items.EGG);
	}

	private void drawMobsSettingSummary(GuiGraphics context, MobsSetting setting, float panelX, float panelWidth,
			float rowY, int rowHeight, float revealAlpha) {
		String arrow = this.expandedMobsSetting == setting ? "v" : ">";
		int arrowWidth = ZenyaFont.width(this.font, arrow);
		int arrowX = Math.round(panelX + panelWidth - 6.0f - arrowWidth);
		int previewMaxWidth = Math.max(30, arrowX
				- (Math.round(panelX) + PANEL_PAD + ZenyaFont.width(this.font, setting.getDisplayName()) + 14));
		int textColor = this.multiplyAlpha(
				this.expandedMobsSetting == setting || setting.size() > 0 ? COLOR_TEXT : COLOR_TEXT_MUTED,
				revealAlpha);
		String previewText = setting.size() == 0 ? "Choose" : this.buildMobsPreviewText(setting);
		String previewLabel = this.trimWithEllipsis(previewText,
				Math.round((previewMaxWidth - 18) / BLOCK_PICKER_TEXT_SCALE));
		int previewWidth = Math.max(34, Math.min(previewMaxWidth, ZenyaFont.width(this.font, previewLabel) + 22));
		int previewX = arrowX - previewWidth - 6;
		int previewColor = this.multiplyAlpha(setting.size() > 0 ? COLOR_ACCENT_DIM : COLOR_KEY_BG, revealAlpha);
		ItemStack previewStack = this.getPreviewMobStack(setting);

		ZenyaFont.draw(context, this.font, setting.getDisplayName(), Math.round(panelX) + PANEL_PAD,
				Math.round(rowY) + 4, textColor, false);
		RenderUtil.drawRoundedRect(context, previewX, rowY + 2.0f, previewWidth, 12.0f, 5.0f, previewColor, false);
		RenderUtil.drawOutline(context, previewX, rowY + 2.0f, previewWidth, 12.0f, 5.0f, 1.0f,
				this.multiplyAlpha(COLOR_ROW_OUTLINE, revealAlpha), false);
		if (!previewStack.isEmpty()) {
			context.renderItem(previewStack, previewX + 2, Math.round(rowY) + 1);
		}
		int previewTextX = previewX + (previewStack.isEmpty() ? 6 : 16);
		this.drawScaledText(context, previewLabel, previewTextX, rowY + 4.0f, BLOCK_PICKER_TEXT_SCALE,
				this.multiplyAlpha(COLOR_TEXT, revealAlpha));
		ZenyaFont.draw(context, this.font, arrow, arrowX, Math.round(rowY) + 4,
				this.multiplyAlpha(COLOR_TEXT_MUTED, revealAlpha), false);
	}

	private void drawMobsPicker(GuiGraphics context, MobsSetting setting, float panelX, float panelWidth,
			float pickerY, int mouseX, int mouseY) {
		BlockPickerLayout layout = this.buildMobPickerLayout(panelX, panelWidth, pickerY, setting);
		List<EntityType<?>> filtered = this.getFilteredMobs(setting);
		this.mobPickerScroll = this.clampMobPickerScroll(filtered.size(), this.mobPickerScroll);
		RenderUtil.drawRoundedRect(context, layout.x, layout.y, layout.width, layout.height, 6.0f, COLOR_KEY_BG, false);
		RenderUtil.drawOutline(context, layout.x, layout.y, layout.width, layout.height, 6.0f, 1.0f,
				COLOR_ROW_OUTLINE, false);

		int searchOutline = this.mobSearchActive && this.expandedMobsSetting == setting
				? COLOR_ACCENT
				: COLOR_SEARCH_OUTLINE;
		RenderUtil.drawRoundedRect(context, layout.searchX, layout.searchY, layout.searchWidth, layout.searchHeight,
				5.0f, COLOR_PANEL_BG, false);
		RenderUtil.drawOutline(context, layout.searchX, layout.searchY, layout.searchWidth, layout.searchHeight, 5.0f,
				1.0f, searchOutline, false);
		RenderUtil.drawRoundedRect(context, layout.clearX, layout.clearY, layout.clearWidth, layout.clearHeight, 5.0f,
				COLOR_ROW_BG, false);
		RenderUtil.drawOutline(context, layout.clearX, layout.clearY, layout.clearWidth, layout.clearHeight, 5.0f,
				1.0f, COLOR_ROW_OUTLINE, false);

		String searchText = this.mobSearchQuery.isEmpty() ? "Search mobs..." : this.mobSearchQuery;
		if (this.mobSearchActive && this.expandedMobsSetting == setting
				&& System.currentTimeMillis() / 500L % 2L == 0L) {
			searchText = searchText + "_";
		}
		int searchColor = this.mobSearchQuery.isEmpty() && !this.mobSearchActive ? COLOR_TEXT_MUTED : COLOR_TEXT;
		this.drawInputTextClipped(context, layout.searchX, layout.searchY, Math.max(0.0f, layout.searchWidth),
				Math.max(0.0f, layout.searchHeight), searchText, Math.round(layout.searchX) + 6,
				Math.round(layout.searchY) + 4, searchColor);
		ZenyaFont.draw(context, this.font, "Clear", Math.round(layout.clearX) + 4, Math.round(layout.clearY) + 4,
				COLOR_TEXT_MUTED, false);

		if (filtered.isEmpty()) {
			RenderUtil.drawRoundedRect(context, layout.listX, layout.listY, layout.listWidth, 16.0f, 5.0f,
					COLOR_ROW_BG, false);
			RenderUtil.drawOutline(context, layout.listX, layout.listY, layout.listWidth, 16.0f, 5.0f, 1.0f,
					COLOR_ROW_OUTLINE, false);
			ZenyaFont.draw(context, this.font, "No mobs found", Math.round(layout.listX) + 6,
					Math.round(layout.listY) + 4, COLOR_TEXT_MUTED, false);
			return;
		}

		int visibleRows = Math.min(BLOCK_PICKER_VISIBLE_ROWS, filtered.size());
		boolean showScrollbar = filtered.size() > visibleRows;
		float rowWidth = layout.listWidth;
		for (int row = 0; row < visibleRows; ++row) {
			int index = this.mobPickerScroll + row;
			if (index >= filtered.size()) {
				break;
			}
			EntityType<?> mob = filtered.get(index);
			float rowY = layout.listY + row * BLOCK_PICKER_ROW_H;
			boolean hovered = mouseX >= layout.listX && mouseX <= layout.listX + layout.listWidth
					&& mouseY >= rowY && mouseY <= rowY + BLOCK_PICKER_ROW_H - 2.0f;
			boolean selected = setting.contains(mob);
			int rowColor = selected ? COLOR_ROW_ACTIVE : (hovered ? COLOR_ROW_HOVER : COLOR_ROW_BG);
			int textColor = selected ? COLOR_ACCENT : COLOR_TEXT;
			RenderUtil.drawRoundedRect(context, layout.listX, rowY, rowWidth, 16.0f, 5.0f, rowColor, false);
			RenderUtil.drawOutline(context, layout.listX, rowY, rowWidth, 16.0f, 5.0f, 1.0f, COLOR_ROW_OUTLINE, false);

			ItemStack stack = this.getMobStack(mob);
			int textX = Math.round(layout.listX) + 5;
			if (!stack.isEmpty()) {
				context.renderItem(stack, Math.round(layout.listX) + 2, Math.round(rowY) + 1);
				textX += 16;
			}
			float indicatorX = layout.listX + rowWidth - 10.0f;
			int textWidth = Math.max(20, Math.round(indicatorX) - textX - 4);
			String displayName = this.trimWithEllipsis(setting.getDisplayName(mob),
					Math.round(textWidth / BLOCK_PICKER_TEXT_SCALE));
			this.drawScaledText(context, displayName, textX, rowY + 4.0f, BLOCK_PICKER_TEXT_SCALE, textColor);
			int indicatorColor = selected ? COLOR_ACCENT : COLOR_SEARCH_OUTLINE;
			RenderUtil.drawRoundedRect(context, indicatorX, rowY + 5.0f, BLOCK_PICKER_INDICATOR_SIZE,
					BLOCK_PICKER_INDICATOR_SIZE, 2.5f, selected ? COLOR_ACCENT : COLOR_KEY_BG, false);
			RenderUtil.drawOutline(context, indicatorX, rowY + 5.0f, BLOCK_PICKER_INDICATOR_SIZE,
					BLOCK_PICKER_INDICATOR_SIZE, 2.5f, 1.0f, indicatorColor, false);
		}

		if (showScrollbar) {
			int maxScroll = Math.max(1, filtered.size() - visibleRows);
			float trackX = layout.listX + layout.listWidth - BLOCK_PICKER_SCROLLBAR_W;
			float trackY = layout.listY + 1.0f;
			float trackHeight = layout.listHeight - 2.0f;
			float thumbHeight = Math.max(12.0f, trackHeight * ((float) visibleRows / filtered.size()));
			float thumbOffset = (trackHeight - thumbHeight) * ((float) this.mobPickerScroll / maxScroll);
			RenderUtil.drawRoundedRect(context, trackX, trackY, BLOCK_PICKER_SCROLLBAR_W, trackHeight, 2.0f,
					COLOR_PANEL_BG, false);
			RenderUtil.drawRoundedRect(context, trackX, trackY + thumbOffset, BLOCK_PICKER_SCROLLBAR_W, thumbHeight,
					2.0f, COLOR_ACCENT_DIM, false);
		}
	}

	private ColorPickerLayout buildColorPickerLayout(float panelX, float panelWidth, float rowY, int rowHeight) {
		float fieldX = panelX + 8.0f;
		float fieldWidth = panelWidth - 16.0f;
		float fieldY = rowY + rowHeight + COLOR_PICKER_GAP;
		float fieldHeight = COLOR_PICKER_SV_HEIGHT;
		float hueY = fieldY + fieldHeight + COLOR_PICKER_GAP;
		float hueHeight = COLOR_PICKER_HUE_HEIGHT;
		float alphaY = hueY + hueHeight + COLOR_PICKER_GAP;
		return new ColorPickerLayout(fieldX, fieldWidth, fieldY, fieldHeight, hueY, hueHeight, alphaY,
				COLOR_PICKER_ALPHA_HEIGHT);
	}

	/**
	 * Re-walks the layout to find the colour picker being dragged and applies the drag.
	 *
	 * <p>ponytail: this walker starts at a fixed 30 + n*134 grid, so it ignores
	 * {@link #categoryOffsets} - dragging a colour inside a moved panel misses.
	 */
	private boolean updateActiveColorDrag(double mouseX, double mouseY) {
		int catX = 30;
		int catY = this.getContentTop() + this.verticalScroll;
		for (Category category : CACHED_CATEGORIES) {
			int modY = catY + PANEL_HEADER_H + PANEL_HEADER_SPACING;
			for (Module module : ModuleManager.INSTANCE.getModulesInCategory(category)) {
				if (!this.matchesQuery(module)) {
					continue;
				}
				modY += ROW_STEP;
				if (module != this.popupModule) {
					continue;
				}
				catX = this.popupX;
				modY = this.popupY + POPUP_HEADER_H + 2 * ROW_STEP;
				for (Setting<?> setting : module.getSettings()) {
					if (setting == this.activeColorSetting && setting.getValue() instanceof Color) {
						ColorPickerLayout layout = this.buildColorPickerLayout(catX + 4, 112.0f, modY, ROW_H);
						if (this.colorDragMode == ColorDragMode.FIELD) {
							this.updateColorFromField(this.activeColorSetting, layout, mouseX, mouseY);
						} else if (this.colorDragMode == ColorDragMode.HUE) {
							this.updateColorFromHue(this.activeColorSetting, layout, mouseX);
						} else if (this.colorDragMode == ColorDragMode.ALPHA) {
							this.updateColorFromAlpha(this.activeColorSetting, layout, mouseX);
						}
						return true;
					}
					modY += ROW_STEP;
					modY += this.getDraggedSettingExtraHeight(setting);
				}
			}
			catX += PANEL_W + PANEL_GAP;
		}
		return false;
	}

	/**
	 * Locates the open mob picker so the scroll wheel can be routed into it.
	 *
	 * <p>ponytail: like {@link #updateActiveColorDrag} this ignores panel offsets, and it
	 * gates on {@code module.isExpanded()} while everything else gates on the popup.
	 */
	private MobPickerContext getExpandedMobsPickerContext() {
		if (this.expandedMobsSetting == null) {
			return null;
		}
		int catX = 30;
		int catY = this.getContentTop() + this.verticalScroll;
		for (Category category : CACHED_CATEGORIES) {
			int modY = catY + PANEL_HEADER_H + PANEL_HEADER_SPACING;
			for (Module module : ModuleManager.INSTANCE.getModulesInCategory(category)) {
				if (!this.matchesQuery(module)) {
					continue;
				}
				modY += ROW_STEP;
				if (!module.isExpanded()) {
					continue;
				}
				modY += 2 * ROW_STEP;
				for (Setting<?> setting : module.getSettings()) {
					if (setting == this.expandedMobsSetting) {
						return new MobPickerContext(this.expandedMobsSetting,
								this.buildMobPickerLayout(catX + 4, 112.0f, modY + ROW_STEP,
										this.expandedMobsSetting));
					}
					modY += ROW_STEP;
					modY += this.getDraggedSettingExtraHeight(setting);
				}
			}
			catX += PANEL_W + PANEL_GAP;
		}
		return null;
	}

	/** Same walk as {@link #getExpandedMobsPickerContext}, for the (unreachable) block picker. */
	private BlockPickerContext getExpandedBlocksPickerContext() {
		if (this.expandedBlocksSetting == null) {
			return null;
		}
		int catX = 30;
		int catY = this.getContentTop() + this.verticalScroll;
		for (Category category : CACHED_CATEGORIES) {
			int modY = catY + PANEL_HEADER_H + PANEL_HEADER_SPACING;
			for (Module module : ModuleManager.INSTANCE.getModulesInCategory(category)) {
				if (!this.matchesQuery(module)) {
					continue;
				}
				modY += ROW_STEP;
				if (!module.isExpanded()) {
					continue;
				}
				modY += 2 * ROW_STEP;
				for (Setting<?> setting : module.getSettings()) {
					if (setting == this.expandedBlocksSetting) {
						return new BlockPickerContext(this.expandedBlocksSetting,
								this.buildBlockPickerLayout(catX + 4, 112.0f, modY + ROW_STEP,
										this.expandedBlocksSetting));
					}
					modY += ROW_STEP;
					modY += this.getDraggedSettingExtraHeight(setting);
				}
			}
			catX += PANEL_W + PANEL_GAP;
		}
		return null;
	}

	private boolean handleSearchKeyInput(KeyEvent input) {
		return input.isEscape() || input.isConfirmation();
	}

	/** Always consumes the key while the block picker's search field is focused. */
	private boolean handleBlockSearchKeyInput(KeyEvent input) {
		if (input.input() == GLFW.GLFW_KEY_BACKSPACE) {
			this.blockSearchQuery = this.removeLastCodePoint(this.blockSearchQuery);
			this.blockPickerScroll = 0;
			return true;
		}
		if (input.isPaste()) {
			this.blockSearchQuery = this.blockSearchQuery + this.getClipboardText();
			this.blockPickerScroll = 0;
			return true;
		}
		if (input.isEscape() || input.isConfirmation()) {
			this.blockSearchActive = false;
		}
		return true;
	}

	/** Always consumes the key while the mob picker's search field is focused. */
	private boolean handleMobSearchKeyInput(KeyEvent input) {
		if (input.input() == GLFW.GLFW_KEY_BACKSPACE) {
			this.mobSearchQuery = this.removeLastCodePoint(this.mobSearchQuery);
			this.mobPickerScroll = 0;
			return true;
		}
		if (input.isPaste()) {
			this.mobSearchQuery = this.mobSearchQuery + this.getClipboardText();
			this.mobPickerScroll = 0;
			return true;
		}
		if (input.isEscape() || input.isConfirmation()) {
			this.mobSearchActive = false;
		}
		return true;
	}

	/** Always consumes the key while a text setting is being edited in place. */
	private boolean handleStringKeyInput(KeyEvent input) {
		if (input.input() == GLFW.GLFW_KEY_BACKSPACE) {
			this.listeningString.setValue(this.removeLastCodePoint(this.listeningString.getValue()));
			return true;
		}
		if (input.isPaste()) {
			this.listeningString.setValue(this.listeningString.getValue() + this.getClipboardText());
			return true;
		}
		if (input.isEscape() || input.isConfirmation()) {
			this.listeningString = null;
		}
		return true;
	}

	private int getContentTop() {
		return 50;
	}

	private int getSearchX() {
		return 30;
	}

	private int getSearchY() {
		return 16;
	}

	private int getSearchWidth() {
		return Math.max(120, Math.min(260, this.uiWidth() - 60));
	}

	private void drawSearchBar(GuiGraphics context, int mouseX, int mouseY, float alpha) {
		int x = this.getSearchX();
		int y = this.getSearchY();
		int width = this.getSearchWidth();
		boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + SEARCH_H;
		int outline = this.searchActive ? COLOR_ACCENT : (hovered ? COLOR_TEXT_MUTED : COLOR_SEARCH_OUTLINE);
		int fill = hovered || this.searchActive ? -234221802 : COLOR_PANEL_BG;
		RenderUtil.drawRoundedRect(context, x, y, width, SEARCH_H, ROW_RADIUS, this.multiplyAlpha(fill, alpha), false);
		RenderUtil.drawOutline(context, x, y, width, SEARCH_H, ROW_RADIUS, 1.0f, this.multiplyAlpha(outline, alpha),
				false);

		String text = this.searchQuery.isEmpty() ? "Search modules..." : this.searchQuery;
		if (this.searchActive && System.currentTimeMillis() / 500L % 2L == 0L) {
			text = text + "_";
		}
		int textColor = this.searchQuery.isEmpty() && !this.searchActive ? COLOR_TEXT_MUTED : COLOR_TEXT;
		this.drawInputTextClipped(context, x + 8, y, width - 16, SEARCH_H, text, x + 8, y + 5,
				this.multiplyAlpha(textColor, alpha));
	}

	private int getTallestPanelHeight() {
		int maxHeight = 0;
		for (Category category : CACHED_CATEGORIES) {
			maxHeight = Math.max(maxHeight, this.getPanelHeight(category));
		}
		return maxHeight;
	}

	/** Scroll is negative-only: 0 is the top, and the tallest panel sets the limit. */
	private int clampVerticalScroll(int value) {
		int availableHeight = Math.max(0, this.uiHeight() - this.getContentTop() - 16);
		int minScroll = Math.min(0, availableHeight - this.getTallestPanelHeight());
		return Math.max(minScroll, Math.min(0, value));
	}

	private String getClipboardText() {
		return this.sanitizeTextInput(Minecraft.getInstance().keyboardHandler.getClipboard());
	}

	private String sanitizeTextInput(String input) {
		if (input == null || input.isEmpty()) {
			return "";
		}
		StringBuilder builder = new StringBuilder(input.length());
		input.codePoints().filter(codePoint -> !Character.isISOControl(codePoint)).forEach(builder::appendCodePoint);
		return builder.toString();
	}

	private String removeLastCodePoint(String value) {
		if (value == null || value.isEmpty()) {
			return "";
		}
		return value.substring(0, value.offsetByCodePoints(value.length(), -1));
	}

	private void updateColorFromField(Setting<Color> setting, ColorPickerLayout layout, double mouseX, double mouseY) {
		float saturation = this.clamp01((float) ((mouseX - layout.fieldX) / layout.fieldWidth));
		float brightness = 1.0f - this.clamp01((float) ((mouseY - layout.fieldY) / layout.fieldHeight));
		Color current = setting.getValue();
		float[] hsb = Color.RGBtoHSB(current.getRed(), current.getGreen(), current.getBlue(), null);
		int rgb = Color.HSBtoRGB(hsb[0], saturation, brightness);
		setting.setValue(new Color(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, current.getAlpha()));
	}

	private void updateColorFromHue(Setting<Color> setting, ColorPickerLayout layout, double mouseX) {
		float hue = this.clamp01((float) ((mouseX - layout.fieldX) / layout.fieldWidth));
		Color current = setting.getValue();
		float[] hsb = Color.RGBtoHSB(current.getRed(), current.getGreen(), current.getBlue(), null);
		int rgb = Color.HSBtoRGB(hue, hsb[1], hsb[2]);
		setting.setValue(new Color(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, current.getAlpha()));
	}

	private void updateColorFromAlpha(Setting<Color> setting, ColorPickerLayout layout, double mouseX) {
		float alpha = this.clamp01((float) ((mouseX - layout.fieldX) / layout.fieldWidth));
		Color current = setting.getValue();
		setting.setValue(new Color(current.getRed(), current.getGreen(), current.getBlue(),
				Math.round(alpha * 255.0f)));
	}

	private boolean pointInRect(double x, double y, float rectX, float rectY, float rectW, float rectH) {
		return x >= rectX && x <= rectX + rectW && y >= rectY && y <= rectY + rectH;
	}

	/** Modules whose sliders are meaningful below 1; everything else snaps to whole numbers. */
	private boolean allowDecimalForModule(Module module) {
		if (module == null) {
			return false;
		}
		String name = module.getName() == null ? "" : module.getName().toLowerCase().replace(" ", "");
		return name.equals("swingspeed") || name.equals("freelook") || name.equals("fastplace")
				|| name.equals("playeresp") || name.equals("storageesp") || name.equals("freecam")
				|| name.equals("holeesp") || name.equals("jumpcircles") || name.equals("autototem")
				|| name.equals("autoinvtotem") || name.equals("hitbox") || name.equals("anchormacro")
				|| name.equals("autocrystal") || name.equals("doubleanchor") || name.equals("triggerbot")
				|| name.equals("shieldbreaker") || name.equals("spotifyhud") || name.equals("zenya+");
	}

	/** Maps the cursor onto the 100px slider track that starts at {@code catX + PANEL_PAD}. */
	@SuppressWarnings("unchecked")
	private void updateNumericSetting(Module module, Setting<?> setting, double mouseX, int catX) {
		double progress = (mouseX - (catX + PANEL_PAD)) / 100.0;
		double clampedProgress = Math.max(0.0, Math.min(1.0, progress));
		boolean allowDecimal = this.allowDecimalForModule(module);
		if (setting.getValue() instanceof Float && setting.getMin() instanceof Float
				&& setting.getMax() instanceof Float) {
			float min = (Float) setting.getMin();
			float max = (Float) setting.getMax();
			float value = (float) (min + (max - min) * clampedProgress);
			if (!allowDecimal) {
				value = Math.round(value);
			}
			((Setting<Float>) setting).setValue(value);
			return;
		}
		if (setting.getValue() instanceof Integer && setting.getMin() instanceof Integer
				&& setting.getMax() instanceof Integer) {
			int min = (Integer) setting.getMin();
			int max = (Integer) setting.getMax();
			int value = (int) Math.round(min + (max - min) * clampedProgress);
			((Setting<Integer>) setting).setValue(Math.max(min, Math.min(max, value)));
			return;
		}
		if (setting.getValue() instanceof Double && setting.getMin() instanceof Double
				&& setting.getMax() instanceof Double) {
			double min = (Double) setting.getMin();
			double max = (Double) setting.getMax();
			double value = min + (max - min) * clampedProgress;
			if (!allowDecimal) {
				value = Math.round(value);
			}
			((Setting<Double>) setting).setValue(value);
		}
	}

	private float clamp01(float value) {
		return Math.max(0.0f, Math.min(1.0f, value));
	}

	/** Replaces the alpha channel outright. */
	private int withAlpha(int color, float alpha) {
		int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0f)));
		return color & 0xFFFFFF | a << 24;
	}

	/** Scales the existing alpha channel, used to fade whole rows in and out. */
	private int multiplyAlpha(int color, float alphaMul) {
		int a = color >> 24 & 0xFF;
		int newA = Math.max(0, Math.min(255, Math.round(a * alphaMul)));
		return color & 0xFFFFFF | newA << 24;
	}

	private int multiplyAlpha(Color color, float alphaMul) {
		int newA = Math.max(0, Math.min(255, Math.round(color.getAlpha() * alphaMul)));
		return newA << 24 | color.getRed() << 16 | color.getGreen() << 8 | color.getBlue();
	}

	private int lerpColor(int from, int to, float t) {
		float clamped = this.clamp01(t);
		int fromA = from >> 24 & 0xFF;
		int fromR = from >> 16 & 0xFF;
		int fromG = from >> 8 & 0xFF;
		int fromB = from & 0xFF;
		int toA = to >> 24 & 0xFF;
		int toR = to >> 16 & 0xFF;
		int toG = to >> 8 & 0xFF;
		int toB = to & 0xFF;
		int a = (int) (fromA + (toA - fromA) * clamped);
		int r = (int) (fromR + (toR - fromR) * clamped);
		int g = (int) (fromG + (toG - fromG) * clamped);
		int b = (int) (fromB + (toB - fromB) * clamped);
		return a << 24 | r << 16 | g << 8 | b;
	}

	private float easeOutCubic(float t) {
		return 1.0f - (float) Math.pow(1.0f - this.clamp01(t), 3.0);
	}

	private enum ColorDragMode {
		NONE,
		FIELD,
		HUE,
		ALPHA
	}

	/** Geometry of one inline block/mob picker: the frame, its search row and its list. */
	private static final class BlockPickerLayout {
		private final float x;
		private final float y;
		private final float width;
		private final float height;
		private final float searchX;
		private final float searchY;
		private final float searchWidth;
		private final float searchHeight;
		private final float clearX;
		private final float clearY;
		private final float clearWidth;
		private final float clearHeight;
		private final float listX;
		private final float listY;
		private final float listWidth;
		private final float listHeight;

		private BlockPickerLayout(float x, float y, float width, float height, float searchX, float searchY,
				float searchWidth, float searchHeight, float clearX, float clearY, float clearWidth,
				float clearHeight, float listX, float listY, float listWidth, float listHeight) {
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
			this.searchX = searchX;
			this.searchY = searchY;
			this.searchWidth = searchWidth;
			this.searchHeight = searchHeight;
			this.clearX = clearX;
			this.clearY = clearY;
			this.clearWidth = clearWidth;
			this.clearHeight = clearHeight;
			this.listX = listX;
			this.listY = listY;
			this.listWidth = listWidth;
			this.listHeight = listHeight;
		}
	}

	/** Geometry of the SV square and the hue/alpha bars under an expanded colour row. */
	private static final class ColorPickerLayout {
		private final float fieldX;
		private final float fieldWidth;
		private final float fieldY;
		private final float fieldHeight;
		private final float hueY;
		private final float hueHeight;
		private final float alphaY;
		private final float alphaHeight;

		private ColorPickerLayout(float fieldX, float fieldWidth, float fieldY, float fieldHeight, float hueY,
				float hueHeight, float alphaY, float alphaHeight) {
			this.fieldX = fieldX;
			this.fieldWidth = fieldWidth;
			this.fieldY = fieldY;
			this.fieldHeight = fieldHeight;
			this.hueY = hueY;
			this.hueHeight = hueHeight;
			this.alphaY = alphaY;
			this.alphaHeight = alphaHeight;
		}
	}

	private record BlockPickerContext(BlocksSetting setting, BlockPickerLayout layout) {
	}

	private record MobPickerContext(MobsSetting setting, BlockPickerLayout layout) {
	}
}
