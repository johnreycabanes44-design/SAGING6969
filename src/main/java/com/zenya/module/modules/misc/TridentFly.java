package com.zenya.module.modules.misc;

import com.zenya.module.Category;
import com.zenya.module.Module;

/**
 * Flight by repeated trident riptides.
 *
 * <p>Carries only the identity and description — the movement lives in the tick and
 * packet hooks, which key off this module's enabled state rather than calling it.
 */
public class TridentFly extends Module {

	public TridentFly() {
		super("Trident Fly", Category.MISC);
		this.setDescription("Lets you fly using a trident.");
	}
}
