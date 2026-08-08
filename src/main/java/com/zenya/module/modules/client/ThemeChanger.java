package com.zenya.module.modules.client;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.ModeSetting;

/**
 * Exposes the {@link Themes} palette as a normal module setting.
 *
 * <p>The selection is pushed on tick rather than on click so that a config load — which
 * writes the setting directly and fires no callback — still reaches {@link Themes}.
 * Re-applying the already-active theme is free: the underlying setting ignores a write
 * of the value it already holds.
 */
public final class ThemeChanger extends Module {
	private final ModeSetting theme = new ModeSetting("Theme", "Ocean",
			"Ocean", "Frost", "Lavender", "Emerald", "Gold", "Ruby", "Amethyst", "Mint",
			"Midnight", "Sakura", "Rose", "Sky", "Forest", "Sunset", "Rainbow");

	public ThemeChanger() {
		super("ThemeChanger", Category.CLIENT);
		this.setDescription("Quickly switch between multiple premium Frost client color themes.");
		this.addSetting(this.theme);
	}

	@Override
	public void onTick() {
		if (!this.isEnabled()) {
			return;
		}

		for (Themes.Theme candidate : Themes.ALL) {
			if (candidate.name().equalsIgnoreCase(this.theme.getValue())) {
				Themes.apply(candidate);
				return;
			}
		}
	}
}
