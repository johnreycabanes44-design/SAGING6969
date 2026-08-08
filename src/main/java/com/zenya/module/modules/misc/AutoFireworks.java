package com.zenya.module.modules.misc;

import com.zenya.module.Category;
import com.zenya.module.Module;

/**
 * Fires rockets to keep elytra flight going.
 *
 * <p>Carries only the identity and description — the rocket use lives in the tick
 * hooks, which key off this module's enabled state rather than calling it.
 */
public class AutoFireworks extends Module {

	public AutoFireworks() {
		super("Auto Fireworks", Category.MISC);
		this.setDescription("Automatically uses fireworks while flying with an elytra.");
	}
}
