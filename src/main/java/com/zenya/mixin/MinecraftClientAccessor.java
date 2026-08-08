package com.zenya.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the private item-use cooldown so FastPlace can drive it directly.
 *
 * <p>Vanilla resets the field to 4 ticks after every right click; overwriting it
 * each tick is the only way to place faster without faking input.
 */
@Mixin(Minecraft.class)
public interface MinecraftClientAccessor {
	@Accessor("rightClickDelay")
	int zenya$getItemUseCooldown();

	@Accessor("rightClickDelay")
	void zenya$setItemUseCooldown(int cooldown);
}
