package com.zenya.module.modules.client;

import com.zenya.module.Category;
import com.zenya.module.Module;

import java.awt.Color;

/**
 * Friend-list lookups for the combat and render modules.
 *
 * <p>Every query is a constant stub in this build — there is no backing name list, so
 * only {@link #getColor()} carries real data. Callers still route through here rather
 * than hard-coding a colour, so wiring an actual list back in stays a one-file change.
 */
public final class Friends extends Module {
	public Friends() {
		super("Friends", Category.CLIENT);
	}

	// ponytail: no name list exists, so this always reports false and the friend
	// highlight in PlayerESP is unreachable.
	public static boolean isFriend(String name) {
		return false;
	}

	public static boolean isAutoLog() {
		return false;
	}

	public static boolean isAntiTriggerbot() {
		return false;
	}

	public static boolean isEspColor() {
		return false;
	}

	public static Color getColor() {
		return new Color(0, 200, 255);
	}
}
