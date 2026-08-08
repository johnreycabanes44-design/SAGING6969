package com.zenya.mixin;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Write access to the movement state a {@link ClientInput} produced this tick.
 *
 * <p>Freecam has to blank both fields together: {@code keyPresses} is what the server
 * and most game logic read, {@code moveVector} is the already-resolved impulse the
 * player entity applies, and clearing only one still moves the body.
 */
@Mixin(ClientInput.class)
public interface InputAccessor {
	@Accessor("keyPresses")
	void zenya$setPlayerInput(Input input);

	@Accessor("moveVector")
	void zenya$setMovementVector(Vec2 movement);

	@Accessor("keyPresses")
	Input zenya$getPlayerInput();
}
