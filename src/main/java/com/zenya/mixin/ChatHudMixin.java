package com.zenya.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Vestigial hook point on incoming chat: the shipped client returned the message
 * untouched here, and the actual name filtering lives in {@link ChatComponentMixin}.
 * Kept because the mixin config still lists it and removing it would change which
 * handlers vanilla sees on {@code addMessage}.
 */
@Mixin(ChatComponent.class)
public class ChatHudMixin {
	// ponytail: unqualified "addMessage" matches both overloads, so this no-op is
	// applied twice. Harmless as long as the body stays a passthrough.
	@ModifyVariable(method = "addMessage", at = @At("HEAD"), ordinal = 0, argsOnly = true)
	private Component modifyChatMessage(Component message) {
		return message;
	}
}
