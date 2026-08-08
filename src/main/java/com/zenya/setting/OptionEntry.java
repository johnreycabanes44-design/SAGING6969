package com.zenya.setting;

import net.minecraft.world.item.ItemStack;

/**
 * One row of a dropdown or multi-select: the stored {@code value}, the {@code label}
 * shown for it and an optional item to draw beside it.
 */
public record OptionEntry(String value, String label, ItemStack previewStack) {
	/** A copy, so a caller mutating the stack cannot corrupt the option list. */
	public ItemStack getPreviewStack() {
		return previewStack == null ? ItemStack.EMPTY : previewStack.copy();
	}
}
