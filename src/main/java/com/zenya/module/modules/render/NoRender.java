package com.zenya.module.modules.render;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Query surface the render mixins ask before suppressing a world or screen effect.
 *
 * <p>Every query answers false and both filters are identity: this build ships the
 * hooks without the toggles behind them, so the mixins fall through to vanilla
 * rendering. The methods exist so the mixins keep compiling and linking.
 */
public class NoRender {

	public static boolean isActive() {
		return false;
	}

	public static boolean hideFog() {
		return false;
	}

	public static boolean hideBlindness() {
		return false;
	}

	public static boolean hideFireOverlay() {
		return false;
	}

	public static boolean hideWaterOverlay() {
		return false;
	}

	public static boolean shouldCancelWeatherParticle() {
		return false;
	}

	public static boolean hideShadows() {
		return false;
	}

	public static boolean hideFireSmoke() {
		return false;
	}

	public static boolean hideVignette() {
		return false;
	}

	public static boolean hidePumpkin() {
		return false;
	}

	public static boolean hidePortalMultiplier() {
		return false;
	}

	public static boolean hideLightning() {
		return false;
	}

	public static boolean hideItemActivationEffects() {
		return false;
	}

	public static boolean hideElderGuardian() {
		return false;
	}

	public static boolean hideRainGradient() {
		return false;
	}

	public static boolean hideThunder() {
		return false;
	}

	public static boolean shouldCancelWeatherSound(SoundEvent sound) {
		return false;
	}

	public static boolean hideAllPrecipitation() {
		return false;
	}

	public static boolean hideTotemAnimation() {
		return false;
	}

	public static boolean hideNametags() {
		return false;
	}

	public static boolean hideLightningFlash() {
		return false;
	}

	public static boolean hideNoRenderEntity(Entity entity) {
		return false;
	}

	public static boolean showInvisibleEntities() {
		return false;
	}

	public static boolean hideGlowing() {
		return false;
	}

	public static boolean hidePotionIcons() {
		return false;
	}

	public static boolean hideCrosshair() {
		return false;
	}

	public static boolean hideBossBar() {
		return false;
	}

	public static boolean hideScoreboard() {
		return false;
	}

	public static boolean hideTitle() {
		return false;
	}

	public static boolean hideHeldItemName() {
		return false;
	}

	public static boolean hideSpyglassOverlay() {
		return false;
	}

	public static boolean hidePortalOverlay() {
		return false;
	}

	public static boolean hideNausea() {
		return false;
	}

	public static boolean hidePumpkinOverlay() {
		return false;
	}

	public static boolean hidePowderedSnowOverlay() {
		return false;
	}

	public static boolean hideInWallOverlay() {
		return false;
	}

	public static boolean hideLiquidOverlay() {
		return false;
	}

	/** Lets a mixin swap the biome's precipitation before it is rendered; currently passes it through. */
	public static Biome.Precipitation filterPrecipitation(Biome.Precipitation precipitation) {
		return precipitation;
	}

	/** Lets a mixin swap the state a block is drawn as; currently passes it through. */
	public static BlockState filterBlockState(BlockState state) {
		return state;
	}
}
