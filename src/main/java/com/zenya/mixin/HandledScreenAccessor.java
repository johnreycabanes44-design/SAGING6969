package com.zenya.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the slot currently under the cursor in any container screen.
 *
 * <p>Null whenever the pointer sits over the screen background rather than a
 * slot, so every caller has to null-check before dereferencing.
 */
@Mixin(AbstractContainerScreen.class)
public interface HandledScreenAccessor {
	@Accessor("hoveredSlot")
	@Nullable
	Slot zenya$getFocusedSlot();
}
