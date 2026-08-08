package com.zenya.mixin;

import com.zenya.module.modules.render.NoRender;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Routes the weather renderer's per-column precipitation lookup through NoRender.
 *
 * <p>Both the geometry pass and the particle/sound pass are redirected so a filtered
 * precipitation type cannot be visible in one and audible in the other. The private
 * lookup is re-exposed as an invoker because the redirect has to call the original.
 */
@Mixin(WeatherEffectRenderer.class)
public abstract class WeatherRenderingMixin {
	@Invoker("getPrecipitationAt")
	protected abstract Biome.Precipitation zenya$getPrecipitationAt(Level level, BlockPos pos);

	@Redirect(method = "extractRenderState",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/WeatherEffectRenderer;getPrecipitationAt(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;"))
	private Biome.Precipitation zenya$filterRenderedPrecipitation(WeatherEffectRenderer instance, Level level, BlockPos pos) {
		return NoRender.filterPrecipitation(this.zenya$getPrecipitationAt(level, pos));
	}

	@Redirect(method = "tickRainParticles",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/WeatherEffectRenderer;getPrecipitationAt(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;"))
	private Biome.Precipitation zenya$filterWeatherParticlesAndSounds(WeatherEffectRenderer instance, Level level, BlockPos pos) {
		return NoRender.filterPrecipitation(this.zenya$getPrecipitationAt(level, pos));
	}
}
