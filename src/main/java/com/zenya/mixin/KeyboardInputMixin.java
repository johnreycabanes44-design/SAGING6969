package com.zenya.mixin;

import com.zenya.module.modules.render.Freecam;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Swallows keyboard movement while Freecam is running so the real player stays put.
 *
 * <p>This runs at RETURN, after vanilla has already filled the fields from the key
 * bindings — Freecam reads that live state separately, then this wipes it so nothing
 * downstream moves the body.
 */
@Mixin(KeyboardInput.class)
public class KeyboardInputMixin {
	@Inject(method = "Lnet/minecraft/client/player/KeyboardInput;tick()V", at = @At("RETURN"))
	private void onTickReturn(CallbackInfo info) {
		if (Freecam.instance == null || !Freecam.instance.isEnabled()) {
			return;
		}

		InputAccessor input = (InputAccessor) (Object) this;
		input.zenya$setPlayerInput(new Input(false, false, false, false, false, false, false));
		input.zenya$setMovementVector(Vec2.ZERO);
	}
}
