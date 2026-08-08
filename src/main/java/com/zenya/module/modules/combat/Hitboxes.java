package com.zenya.module.modules.combat;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;

/**
 * Widens the pick radius vanilla adds to an entity's box during the crosshair raycast.
 *
 * <p>The module does no tick or render work of its own — {@code EntityPickRadiusMixin}
 * reads the two accessors below straight off the instance, so both must stay public
 * even though nothing in this package calls them.
 */
public class Hitboxes extends Module {
	public Setting<Float> expandAmount;
	public Setting<Boolean> playersOnly;

	public Hitboxes() {
		super("Hitboxes", Category.COMBAT);
		this.expandAmount = new Setting<>("Expand", 0.1f, 0.0f, 1.0f);
		this.playersOnly = new Setting<>("Players Only", true);
		this.addSetting(this.expandAmount);
		this.addSetting(this.playersOnly);
	}

	public float getExpand() {
		return this.expandAmount.getValue();
	}

	public boolean isPlayersOnly() {
		return this.playersOnly.getValue();
	}
}
