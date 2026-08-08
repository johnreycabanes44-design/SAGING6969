package com.zenya.module.modules.combat;

import net.minecraft.world.item.Items;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;
import com.zenya.utils.InventoryUtils;
import com.zenya.utils.MouseSimulation;
import com.zenya.utils.WorldUtils;

/**
 * Breaks a raised shield by landing an axe (or mace) hit on whoever is blocking.
 *
 * <p>There is no target scan: the module acts only on the entity the crosshair already
 * picks, and only once the attack cooldown is essentially full, because a shield is
 * disabled by a charged axe hit and by nothing else.
 */
public class ShieldBreaker extends Module {
	public Setting<Float> range;
	public Setting<Boolean> autoSwitch;
	public Setting<Boolean> swingHand;
	public Setting<Boolean> clickSimulation;
	public Setting<Boolean> onlyPlayers;

	public ShieldBreaker() {
		super("Shield Breaker", Category.COMBAT);
		this.range = new Setting<>("Range", 4.5f, 1.0f, 6.0f);
		this.autoSwitch = new Setting<>("Auto Switch", true);
		this.swingHand = new Setting<>("Swing Hand", true);
		this.clickSimulation = new Setting<>("Click Simulation", true);
		this.onlyPlayers = new Setting<>("Only Players", true);
		this.addSetting(this.range);
		this.addSetting(this.autoSwitch);
		this.addSetting(this.swingHand);
		this.addSetting(this.clickSimulation);
		this.addSetting(this.onlyPlayers);
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.level == null) {
			return;
		}

		if (mc.screen != null) {
			return;
		}

		HitResult hitResult = mc.hitResult;

		if (!(hitResult instanceof EntityHitResult entityHit)) {
			return;
		}

		if (entityHit.getType() != HitResult.Type.ENTITY) {
			return;
		}

		Entity target = entityHit.getEntity();

		if (!(target instanceof Player) && this.onlyPlayers.getValue()) {
			return;
		}

		// ponytail: with "Only Players" off, a non-player target falls through the guard
		// above and this cast throws ClassCastException.
		Player blocker = (Player) target;

		if (!blocker.isBlocking()) {
			return;
		}

		if (blocker.distanceTo(mc.player) > this.range.getValue()) {
			return;
		}

		// Nothing else disables a shield, so give up rather than swing with the wrong item.
		if (this.autoSwitch.getValue()
				&& !(mc.player.getMainHandItem().getItem() instanceof AxeItem)
				&& mc.player.getMainHandItem().getItem() != Items.MACE
				&& !InventoryUtils.switchToAxe()) {
			return;
		}

		if (mc.player.getAttackStrengthScale(0.5f) < 0.9f) {
			return;
		}

		if (this.clickSimulation.getValue()) {
			MouseSimulation.mouseClick(0);
		}

		WorldUtils.hitEntity(target, this.swingHand.getValue());
	}
}
