package com.zenya.mixin;

import com.zenya.module.modules.render.NoRender;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Zeroes the weather strengths and drops weather sounds for NoRender.
 *
 * <p>The gradients are forced to 0 rather than the renderer being cancelled, so everything
 * that reads them - sky colour, mob spawning checks, shaders - agrees the sky is clear.
 * Both client-side sound entry points are covered because rain and thunder arrive through
 * a positional call while block-anchored weather sounds arrive through the block-centre one.
 */
@Mixin(Level.class)
public class WorldMixin {
	@Inject(method = "getRainLevel", at = @At("HEAD"), cancellable = true)
	private void zenya$hideRainGradient(float delta, CallbackInfoReturnable<Float> info) {
		if (NoRender.hideRainGradient()) {
			info.setReturnValue(0.0f);
		}
	}

	@Inject(method = "getThunderLevel", at = @At("HEAD"), cancellable = true)
	private void zenya$hideThunderGradient(float delta, CallbackInfoReturnable<Float> info) {
		if (NoRender.hideThunder()) {
			info.setReturnValue(0.0f);
		}
	}

	// Both overloads are playLocalSound under Mojang mappings, so the descriptor is
	// what tells them apart.
	@Inject(method = "playLocalSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V",
			at = @At("HEAD"), cancellable = true)
	private void zenya$cancelWeatherPointSound(double x, double y, double z, SoundEvent sound, SoundSource category,
			float volume, float pitch, boolean useDistance, CallbackInfo info) {
		if (NoRender.shouldCancelWeatherSound(sound)) {
			info.cancel();
		}
	}

	@Inject(method = "playLocalSound(Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V",
			at = @At("HEAD"), cancellable = true)
	private void zenya$cancelWeatherBlockSound(BlockPos pos, SoundEvent sound, SoundSource category,
			float volume, float pitch, boolean useDistance, CallbackInfo info) {
		if (NoRender.shouldCancelWeatherSound(sound)) {
			info.cancel();
		}
	}
}
