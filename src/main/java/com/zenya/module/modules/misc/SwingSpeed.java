package com.zenya.module.modules.misc;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;

/**
 * Multiplier applied to the arm-swing animation while attacking or mining.
 *
 * <p>The static {@link #instance} exists so the render hook can reach the multiplier without
 * walking the module list every frame. {@link #getSwingSpeed} re-clamps to 0.1-2.0 rather than
 * trusting the setting's own bounds, because a hand-edited config can put anything in there.
 */
public class SwingSpeed extends Module {
	public static SwingSpeed instance;

	public Setting<Float> swingSpeed;

	public SwingSpeed() {
		super("SwingSpeed", Category.MISC);
		this.swingSpeed = new Setting<>("Swing Speed", 1.0f, 0.1f, 2.0f);
		this.setDescription("Adjusts how quickly your arm swing animation plays when attacking or breaking blocks for a smoother visual feel.");
		instance = this;
		this.addSetting(this.swingSpeed);
	}

	public float getSwingSpeed() {
		float speed = this.swingSpeed.getValue() == null ? 1.0f : this.swingSpeed.getValue().floatValue();
		if (speed < 0.1f) {
			return 0.1f;
		}
		return Math.min(speed, 2.0f);
	}
}
