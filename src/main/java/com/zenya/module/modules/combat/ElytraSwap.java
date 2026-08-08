package com.zenya.module.modules.combat;

import net.minecraft.world.item.Items;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;

/**
 * Holds a hotbar elytra while a player is under the crosshair within four blocks, and
 * gives the previous slot back once they are not.
 *
 * <p>{@link #previousSlot} of -1 means "nothing to restore", which is also what Swap Back
 * being off leaves behind, so the restore branch is a no-op in that case. The module skips
 * ticks with a screen open so it cannot fight the inventory the user is holding.
 */
public class ElytraSwap extends Module {
	public Setting<Boolean> autoSwitch;
	public Setting<Boolean> swapBack;
	public int previousSlot;

	public ElytraSwap() {
		super("Elytra Swap", Category.COMBAT);
		this.autoSwitch = new Setting<>("Auto Switch", true);
		this.swapBack = new Setting<>("Swap Back", true);
		this.previousSlot = -1;
		this.addSetting(this.autoSwitch);
		this.addSetting(this.swapBack);
	}

	@Override
	public void onEnable() {
		this.previousSlot = -1;
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.level == null) {
			return;
		}
		if (mc.screen != null) {
			return;
		}
		HitResult hit = mc.hitResult;
		if (!(hit instanceof EntityHitResult entityHit)) {
			return;
		}
		if (hit.getType() != HitResult.Type.ENTITY) {
			return;
		}
		Entity hovered = entityHit.getEntity();
		if (hovered instanceof Player && hovered.distanceTo(mc.player) < 4.0f) {
			if (this.autoSwitch.getValue()) {
				int elytraSlot = this.findElytraSlot();
				if (elytraSlot != -1) {
					// ponytail: rewritten every tick, so from the second tick on this stores the elytra slot itself
					this.previousSlot = this.swapBack.getValue() ? mc.player.getInventory().getSelectedSlot() : -1;
					mc.player.getInventory().setSelectedSlot(elytraSlot);
				}
			}
		} else if (this.swapBack.getValue() && this.previousSlot != -1) {
			mc.player.getInventory().setSelectedSlot(this.previousSlot);
			this.previousSlot = -1;
		}
	}

	/** @return the first hotbar slot holding an elytra, or -1. */
	public int findElytraSlot() {
		if (mc.player == null) {
			return -1;
		}
		for (int slot = 0; slot < 9; slot++) {
			if (mc.player.getInventory().getItem(slot).getItem() == Items.ELYTRA) {
				return slot;
			}
		}
		return -1;
	}

	@Override
	public void onDisable() {
		if (this.swapBack.getValue() && this.previousSlot != -1 && mc.player != null) {
			mc.player.getInventory().setSelectedSlot(this.previousSlot);
		}
		this.previousSlot = -1;
	}
}
