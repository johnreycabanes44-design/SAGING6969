package com.zenya.module.modules.render;

import net.minecraft.client.OptionInstance;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import com.zenya.module.Category;
import com.zenya.module.Module;

/**
 * Pins the game gamma far above the vanilla maximum and keeps a night vision effect
 * topped up, so caves render as if fully lit.
 *
 * <p>{@link #previousGamma} is captured once, on the first successful apply, and
 * {@link #gammaApplied} exists so a re-enable never overwrites that snapshot with the
 * boosted value — otherwise disabling would restore 16.0 instead of the player's setting.
 *
 * <p>Every options call is wrapped: a resource pack or another mod can install a gamma
 * option that rejects out-of-range values, and losing full bright is preferable to
 * throwing out of the tick loop.
 */
public class FullBright extends Module {
	public static final double FULL_BRIGHT_GAMMA = 16.0;

	public double previousGamma;
	public boolean gammaApplied;

	public FullBright() {
		super("FullBright", Category.RENDER);
		this.previousGamma = 1.0;
		this.gammaApplied = false;
		this.setDescription("Maxes out the game gamma and applies night vision for fully lit caves and dark areas.");
	}

	@Override
	public void onEnable() {
		this.tryApplyGamma();
	}

	@Override
	public void onDisable() {
		if (!this.gammaApplied) {
			return;
		}
		if (mc.options != null) {
			this.setGamma(this.previousGamma);
		}
		this.gammaApplied = false;
	}

	@Override
	public void onTick() {
		if (mc.options == null || mc.player == null) {
			return;
		}
		if (!this.gammaApplied) {
			this.tryApplyGamma();
			return;
		}
		try {
			double currentGamma = mc.options.gamma().get();
			if (Math.abs(currentGamma - 16.0) > 1.0E-4) {
				this.setGamma(16.0);
			}
		} catch (Exception e) {
			// A foreign gamma option may not be readable; the next tick retries.
		}
		try {
			MobEffectInstance nightVision = mc.player.getEffect(MobEffects.NIGHT_VISION);
			if (nightVision == null || nightVision.getDuration() < 400) {
				mc.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false, false));
			}
		} catch (Exception e) {
			// Client-side effect application is best effort; the server may clear it back.
		}
	}

	/** Snapshots the player's gamma the first time through, then boosts it. */
	public void tryApplyGamma() {
		if (mc.options == null) {
			return;
		}
		try {
			if (!this.gammaApplied) {
				this.previousGamma = mc.options.gamma().get();
			}
			this.setGamma(16.0);
			this.gammaApplied = true;
		} catch (Exception e) {
			// Leaves gammaApplied false so onTick keeps trying and onDisable restores nothing.
		}
	}

	public void setGamma(double gamma) {
		OptionInstance<Double> option = mc.options.gamma();
		try {
			option.set(gamma);
		} catch (Exception e) {
			// Options that clamp to the vanilla 0..1 range reject the boost; settle for their maximum.
			if (gamma > 1.0) {
				option.set(1.0);
			}
		}
	}
}
