package com.zenya.module.modules.combat;

import net.minecraft.world.item.Items;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;

/**
 * Swings at the player under the crosshair while a mace is already in hand.
 *
 * <p>It never swaps the mace in — that is MaceSwap's job — so the two can be run together
 * without fighting over the selected slot. {@link #attackCooldown} is a tick counter, not
 * a flag: it is reloaded from Attack Delay after each swing and zeroed the moment the mace
 * leaves the hand, so re-equipping does not inherit a stale wait.
 */
public class AutoMace extends Module {
	public Setting<Float> range;
	public Setting<Integer> attackDelay;
	public Setting<Float> fovCheck;
	public int attackCooldown;

	public AutoMace() {
		super("Auto Mace", Category.COMBAT);
		this.range = new Setting<>("Range", 4.5f, 1.0f, 6.0f);
		this.attackDelay = new Setting<>("Attack Delay", 3, 0, 10);
		this.fovCheck = new Setting<>("FOV Check", 30.0f, 0.0f, 180.0f);
		this.setDescription("Automatically attacks players in your crosshair when holding a mace");
		this.addSetting(this.range);
		this.addSetting(this.attackDelay);
		this.addSetting(this.fovCheck);
	}

	@Override
	public void onEnable() {
		this.attackCooldown = 0;
	}

	@Override
	public void onDisable() {
		this.attackCooldown = 0;
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.level == null) {
			return;
		}
		if (this.attackCooldown > 0) {
			this.attackCooldown -= 1;
			return;
		}
		if (!this.isHoldingMace()) {
			this.attackCooldown = 0;
			return;
		}
		HitResult hit = mc.hitResult;
		if (!(hit instanceof EntityHitResult entityHit)) {
			return;
		}
		if (mc.hitResult.getType() != HitResult.Type.ENTITY) {
			return;
		}
		Entity hovered = entityHit.getEntity();
		if (!(hovered instanceof Player target)) {
			return;
		}
		if (hovered == mc.player) {
			return;
		}
		if (!this.isValidTarget(target) || this.attackCooldown > 0) {
			return;
		}
		mc.gameMode.attack(mc.player, hovered);
		mc.player.swing(InteractionHand.MAIN_HAND);
		this.attackCooldown = this.attackDelay.getValue();
	}

	public boolean isHoldingMace() {
		return mc.player != null && mc.player.getMainHandItem().getItem() == Items.MACE;
	}

	/**
	 * Alive, inside Range, and no more than FOV Check degrees off the view vector. A FOV
	 * Check of zero or less disables the angle test rather than rejecting everything.
	 */
	public boolean isValidTarget(Player target) {
		if (mc.player == null || !target.isAlive()) {
			return false;
		}
		if (mc.player.distanceTo(target) > this.range.getValue()) {
			return false;
		}
		if (this.fovCheck.getValue() <= 0.0f) {
			return true;
		}
		Vec3 view = mc.player.getViewVector(1.0f);
		Vec3 targetPos = new Vec3(target.getX(), target.getY(), target.getZ());
		Vec3 selfPos = new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ());
		double alignment = Mth.clamp(view.dot(targetPos.subtract(selfPos).normalize()), -1.0, 1.0);
		double angle = Math.toDegrees(Math.acos(alignment));
		return angle <= this.fovCheck.getValue();
	}
}
