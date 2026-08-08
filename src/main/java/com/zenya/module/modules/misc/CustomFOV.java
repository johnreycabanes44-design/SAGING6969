package com.zenya.module.modules.misc;

import net.minecraft.client.OptionInstance;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;

/**
 * Forces the vanilla FOV option to the configured value every tick.
 *
 * <p>It is re-applied on every tick rather than once on enable so anything that writes the
 * option back -- the video settings screen, a resource pack reload -- loses. Disable restores
 * the vanilla default of 90, not whatever the user had before; the old value is never captured.
 */
public class CustomFOV extends Module {
	public Setting<Integer> fov;

	public CustomFOV() {
		super("Custom FOV", Category.MISC);
		this.fov = new Setting<>("FOV", 90, 30, 180);
		this.setDescription("Allows you to set custom FOV from 30 to 180.");
		this.addSetting(this.fov);
	}

	@Override
	public void onTick() {
		if (mc.options == null || mc.player == null) {
			return;
		}

		OptionInstance<Integer> fovOption = mc.options.fov();
		if (fovOption != null) {
			fovOption.set(this.fov.getValue());
		}
	}

	@Override
	public void onDisable() {
		if (mc.options == null) {
			return;
		}

		OptionInstance<Integer> fovOption = mc.options.fov();
		if (fovOption != null) {
			// ponytail: clobbers the user's own FOV with a hardcoded 90 -- the pre-enable value is never captured. Kept as-is; it is the shipped behaviour.
			fovOption.set(90);
		}
	}
}
