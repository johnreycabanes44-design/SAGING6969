package com.zenya.module.modules.misc;

import com.zenya.module.Category;
import com.zenya.module.Module;

/**
 * Sneaks automatically at block edges so bridging does not drop the player.
 *
 * <p>Carries only the identity and description — the sneak toggling lives in the tick
 * hooks, which key off this module's enabled state rather than calling it.
 */
public class FastBridge extends Module {

	public FastBridge() {
		super("Fast Bridge", Category.MISC);
		this.setDescription("Allows you to bridge faster by automatically sneaking at block edges.");
	}
}
