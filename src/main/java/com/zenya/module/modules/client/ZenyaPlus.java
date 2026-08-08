package com.zenya.module.modules.client;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;

import java.awt.Color;

/**
 * Client-wide appearance settings. Registered under the display name "Frost+", which is
 * why the class name and the module name disagree.
 *
 * <p>The GUI and the HUD read these through the static accessors below, and they can be
 * drawn before the module list exists, so every accessor falls back to a hard-coded
 * default while {@link #INSTANCE} is still null. The accent colour is kept in a static
 * field rather than a setting because {@link Themes} owns it.
 */
public final class ZenyaPlus extends Module {
	/** GLFW_KEY_RIGHT_SHIFT — opens the click GUI. */
	private static final int DEFAULT_GUI_BIND = 344;

	private static ZenyaPlus INSTANCE;
	private final Setting<Boolean> animations = new Setting<>("Animations", true);
	private final Setting<Boolean> soundAnimations = new Setting<>("Sound Animations", true);
	private final Setting<Boolean> mcFont = new Setting<>("Original Font", false);
	private final Setting<Integer> guiBind = new Setting<>("ClickGUI Bind", DEFAULT_GUI_BIND);
	private final Setting<Integer> backgroundOpacity = new Setting<>("Background Opacity", 180, 0, 255);
	private static Color accentColor = new Color(59, 130, 246, 255);

	public ZenyaPlus() {
		super("SAGING+", Category.CLIENT);
		setDescription("Global Frost client appearance settings.");
		addSetting(animations);
		addSetting(soundAnimations);
		addSetting(mcFont);
		addSetting(guiBind);
		addSetting(backgroundOpacity);
		INSTANCE = this;
	}

	public static int getAccentARGB() {
		Color accent = getAccentColor();
		return accent.getAlpha() << 24 | accent.getRed() << 16 | accent.getGreen() << 8 | accent.getBlue();
	}

	public static Color getAccentColor() {
		return accentColor;
	}

	/** Ignores null so a theme that fails to resolve leaves the previous accent in place. */
	public static void setAccentColor(Color color) {
		if (color != null) {
			accentColor = color;
		}
	}

	public static boolean blackBackground() {
		return true;
	}

	/** 0xFF0C0D12 with the alpha byte replaced by the opacity setting. */
	public static int getBackgroundARGB() {
		if (INSTANCE == null) {
			return 0xFF0C0D12;
		}
		int opacity = INSTANCE.backgroundOpacity.getValue();
		return opacity << 24 | 0x0C0D12;
	}

	public static Color getBackgroundColor() {
		if (INSTANCE == null) {
			return new Color(12, 13, 18, 255);
		}
		int opacity = INSTANCE.backgroundOpacity.getValue();
		return new Color(12, 13, 18, opacity);
	}

	public static float backgroundDim() {
		return 0.35f;
	}

	public static boolean blurBackgroundEnabled() {
		return false;
	}

	/** Null value counts as enabled, matching the setting's default. */
	public static boolean animationsEnabled() {
		if (INSTANCE == null) {
			return true;
		}
		Boolean enabled = INSTANCE.animations.getValue();
		return enabled == null || enabled;
	}

	public static boolean soundAnimationsEnabled() {
		if (INSTANCE == null) {
			return true;
		}
		Boolean enabled = INSTANCE.soundAnimations.getValue();
		return enabled == null || enabled;
	}

	public static int menuSizePercent() {
		return 5;
	}

	public static float menuSizeRaw() {
		return 5.0f;
	}

	public static String getGuiStyle() {
		return "GUI 2";
	}

	/** Inert: the GUI style is fixed. Kept so callers that still set it compile. */
	public static void setGuiStyle(String style) {
	}

	/** Inert: the menu size is fixed. Kept so callers that still set it compile. */
	public static void setMenuSize(float size) {
	}

	public static boolean useMinecraftFont() {
		if (INSTANCE == null) {
			return false;
		}
		Boolean enabled = INSTANCE.mcFont.getValue();
		return enabled != null && enabled;
	}

	public static int getMenuBind() {
		if (INSTANCE == null) {
			return DEFAULT_GUI_BIND;
		}
		return INSTANCE.guiBind.getValue();
	}

	public static void setMenuBind(int bind) {
		if (INSTANCE != null) {
			INSTANCE.guiBind.setValue(bind);
		}
	}

	public static float tracerLineWidth() {
		return 1.0f;
	}
}
