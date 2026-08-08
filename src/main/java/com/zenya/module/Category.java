package com.zenya.module;

import net.minecraft.world.item.ItemStack;

/**
 * The tabs modules are grouped under in the click GUI.
 *
 * <p>The icon shape is the texture id CategoryIconRenderer resolves, not the display
 * name: MISC deliberately draws the "layers" icon rather than a "misc" one.
 */
public enum Category {
	COMBAT("Combat", "combat"),
	RENDER("Render", "render"),
	DONUT("Donut", "donut"),
	SMPS("SMP's", "smps"),
	MISC("Misc", "layers"),
	CLIENT("Client", "client");

	public String name;
	private final String iconShape;

	Category(String name, String iconShape) {
		this.name = name;
		this.iconShape = iconShape;
	}

	public String getName() {
		return this.name;
	}

	/** Categories have no item icon; the GUI draws {@link #getIconShape()} instead. */
	public ItemStack getIcon() {
		return ItemStack.EMPTY;
	}

	public String getIconShape() {
		return this.iconShape;
	}
}
