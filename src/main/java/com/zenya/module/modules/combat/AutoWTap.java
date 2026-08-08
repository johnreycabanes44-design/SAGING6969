package com.zenya.module.modules.combat;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;

/**
 * Drops sprint for a few ticks after a charged hit so the follow-up carries knockback.
 *
 * <p>The two counters run in strict sequence and never overlap: {@code releaseTimer}
 * counts the ticks sprint and forward are held cleared, and only when it hits zero is
 * {@code cooldownTimer} loaded, which is what stops the tap re-arming on every swing.
 */
public class AutoWTap extends Module {
	public Setting<Boolean> onHit;
	public Setting<Integer> releaseTicks;
	public Setting<Integer> cooldown;
	public int releaseTimer;
	public int cooldownTimer;
	// ponytail: written once in the constructor and never read again - the release is
	// driven purely by the tick counters, not by a remembered sprint state.
	public boolean wasSprinting;

	public AutoWTap() {
		super("Auto W-Tap", Category.COMBAT);
		this.onHit = new Setting<>("On Hit", true);
		this.releaseTicks = new Setting<>("Release Ticks", 2, 1, 5);
		this.cooldown = new Setting<>("Cooldown", 5, 1, 20);
		this.releaseTimer = 0;
		this.cooldownTimer = 0;
		this.wasSprinting = false;
		this.addSetting(this.onHit);
		this.addSetting(this.releaseTicks);
		this.addSetting(this.cooldown);
	}

	@Override
	public void onEnable() {
		this.releaseTimer = 0;
		this.cooldownTimer = 0;
	}

	@Override
	public void onTick() {
		if (mc.player == null) {
			return;
		}

		if (this.cooldownTimer > 0) {
			this.cooldownTimer -= 1;
			return;
		}

		if (this.releaseTimer > 0) {
			this.releaseTimer -= 1;
			mc.options.keySprint.setDown(false);
			mc.options.keyUp.setDown(false);

			// Arm the cooldown only on the tick the release finishes.
			if (this.releaseTimer == 0) {
				this.cooldownTimer = this.cooldown.getValue();
			}

			return;
		}

		if (this.onHit.getValue()
				&& mc.player.getLastHurtMob() != null
				&& mc.player.getAttackStrengthScale(0.0f) > 0.9f
				&& mc.player.isSprinting()) {
			this.releaseTimer = this.releaseTicks.getValue();
		}
	}
}
