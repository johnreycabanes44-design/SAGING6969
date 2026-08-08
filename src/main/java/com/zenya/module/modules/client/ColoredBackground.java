package com.zenya.module.modules.client;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;

import java.awt.Color;

/**
 * Tints the dark overlay Minecraft draws behind menus and inventories.
 *
 * <p>The screen mixin has no handle on the module list, so the constructor parks the
 * live instance in {@link #INSTANCE} and the mixin goes through the static accessors.
 * Both accessors therefore have to survive {@code INSTANCE} still being null during
 * early startup, before the module has been constructed.
 */
public final class ColoredBackground extends Module {
	private static ColoredBackground INSTANCE;

	private final Setting<String> theme = new Setting<>("Theme", "White Black Mixed");

	public ColoredBackground() {
		super("Colored Background", Category.CLIENT);
		this.setDescription("Custom background themes for GUI and HUD.");
		this.addSetting(this.theme);
		INSTANCE = this;
	}

	/** @return the overlay colour; opaque-ish black whenever the theme name is unknown. */
	public static Color getThemeColor() {
		if (INSTANCE == null) {
			return new Color(0, 0, 0, 180);
		}

		String themeName = INSTANCE.theme.getValue();
		if (themeName == null) {
			themeName = "White Black Mixed";
		}

		return switch (themeName) {
			case "White Black Mixed" -> new Color(128, 128, 128, 180);
			case "Glassy Theme" -> new Color(200, 220, 255, 120);
			case "Dark White" -> new Color(240, 240, 240, 200);
			case "Dark Purple" -> new Color(75, 0, 130, 180);
			case "Purple Mixed White" -> new Color(147, 112, 219, 160);
			case "Ocean Blue" -> new Color(0, 105, 148, 180);
			case "Sunset Orange" -> new Color(255, 140, 0, 180);
			case "Forest Green" -> new Color(34, 139, 34, 180);
			default -> new Color(0, 0, 0, 180);
		};
	}

	public static boolean isModuleEnabled() {
		return INSTANCE != null && INSTANCE.isEnabled();
	}
}
