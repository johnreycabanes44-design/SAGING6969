package com.zenya.module.modules.combat;

import net.minecraft.network.protocol.Packet;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;

/**
 * Puts a hotbar mace in hand when a packet leaves while an entity is under the crosshair,
 * then hands the old slot back.
 *
 * <p>The restore runs off {@link #switchBackClock} ticking down in {@link #onTick} rather
 * than straight after the swap, so the server still sees the mace when the attack lands.
 * A clock of 0 means "nothing pending" and {@link #previousSlot} of -1 means "nothing to
 * restore" — both are reset on enable and disable so a stale slot never gets selected.
 */
public class MaceSwap extends Module {
	public Setting<Boolean> switchBack;
	public Setting<Integer> switchBackDelay;
	public Setting<Boolean> ignoreEndCrystals;
	public int previousSlot;
	public int switchBackClock;

	public MaceSwap() {
		super("Mace Swap", Category.COMBAT);
		this.switchBack = new Setting<>("Switch Back", true);
		this.switchBackDelay = new Setting<>("Switch Back Delay", 1, 0, 20);
		this.ignoreEndCrystals = new Setting<>("Ignore End Crystals", false);
		this.previousSlot = -1;
		this.switchBackClock = 0;
		this.setDescription("Switches to a mace when attacking");
		this.addSetting(this.switchBack);
		this.addSetting(this.switchBackDelay);
		this.addSetting(this.ignoreEndCrystals);
	}

	@Override
	public void onEnable() {
		this.previousSlot = -1;
		this.switchBackClock = 0;
	}

	@Override
	public void onDisable() {
		this.previousSlot = -1;
		this.switchBackClock = 0;
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.level == null) {
			return;
		}
		if (this.switchBackClock <= 0) {
			return;
		}
		this.switchBackClock -= 1;
		if (this.switchBackClock == 0 && this.previousSlot != -1) {
			mc.player.getInventory().setSelectedSlot(this.previousSlot);
			this.previousSlot = -1;
		}
	}

	/** Swaps as a side effect; the packet itself is never cancelled. */
	@Override
	public boolean onPacketSend(Packet packet) {
		// ponytail: triggers on every outgoing packet, not only attack packets
		if (mc.player == null || mc.level == null) {
			return false;
		}
		Entity target = this.getTargetEntity();
		if (target == null) {
			return false;
		}
		if (this.ignoreEndCrystals.getValue() && target instanceof EndCrystal) {
			return false;
		}
		int maceSlot = this.findMaceSlot();
		if (maceSlot == -1) {
			return false;
		}
		// ponytail: rewritten on every packet, so after the first swap this stores the mace slot itself
		this.previousSlot = this.switchBack.getValue() ? mc.player.getInventory().getSelectedSlot() : -1;
		mc.player.getInventory().setSelectedSlot(maceSlot);
		this.switchBackClock = this.switchBack.getValue() && this.previousSlot != -1 ? this.switchBackDelay.getValue() : 0;
		return false;
	}

	/** @return the entity the crosshair is on, or null when it is not on one. */
	public Entity getTargetEntity() {
		HitResult hit = mc.hitResult;
		if (!(hit instanceof EntityHitResult entityHit)) {
			return null;
		}
		if (mc.hitResult.getType() != HitResult.Type.ENTITY) {
			return null;
		}
		return entityHit.getEntity();
	}

	/** @return the first hotbar slot holding a mace, or -1. */
	public int findMaceSlot() {
		if (mc.player == null) {
			return -1;
		}
		for (int slot = 0; slot < 9; slot++) {
			if (mc.player.getInventory().getItem(slot).getItem() == Items.MACE) {
				return slot;
			}
		}
		return -1;
	}
}
