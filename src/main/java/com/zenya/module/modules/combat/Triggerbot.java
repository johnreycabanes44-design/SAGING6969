package com.zenya.module.modules.combat;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;
import com.zenya.utils.KeyUtils;
import com.zenya.utils.MathUtils;
import com.zenya.utils.MouseSimulation;
import com.zenya.utils.TimerUtils;
import com.zenya.utils.WorldUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Random;

import org.lwjgl.glfw.GLFW;

/**
 * Attacks whatever the crosshair is already on, on its own timer, so the hit looks
 * like a human click instead of an instant reaction.
 *
 * <p>Two pieces of state make that work and must stay in step: a per-target reaction
 * delay that restarts whenever the crosshair moves to a different entity, and a
 * pending-crit latch that holds a ready hit back for up to {@code critPatienceMs}
 * waiting for a fall window. Both are cleared the moment the target is lost or hit,
 * otherwise a stale latch fires at the wrong entity.
 */
public class Triggerbot
extends Module {
	public Setting<Boolean> workInScreen;
	public Setting<Boolean> whileUse;
	public Setting<Boolean> onLeftClick;
	public Setting<Boolean> allItems;
	public Setting<Integer> swordMinDelay;
	public Setting<Integer> swordMaxDelay;
	public Setting<Integer> axeMinDelay;
	public Setting<Integer> axeMaxDelay;
	public Setting<Boolean> checkShield;
	public Setting<Boolean> onlyCritSword;
	public Setting<Boolean> onlyCritAxe;
	public Setting<Boolean> prioritizeCrits;
	public Setting<Integer> critPatienceMs;
	public Setting<Float> critFallHeight;
	public Setting<Boolean> swingHand;
	public Setting<Boolean> cooldownCheck;
	public Setting<Float> cooldownPercent;
	public Setting<Boolean> clickSimulation;
	public Setting<Integer> clickHoldMs;
	public Setting<Boolean> strayBypass;
	public Setting<Boolean> allEntities;
	public Setting<Boolean> useShield;
	public Setting<Integer> shieldTime;
	public Setting<Boolean> samePlayer;
	public Setting<Boolean> whileAscending;
	public Setting<Float> missChance;
	public Setting<Float> hitChance;
	public Setting<Integer> reactionMinMs;
	public Setting<Integer> reactionMaxMs;
	public Setting<Integer> slotChangeCooldownMs;
	public Setting<Integer> jitterMs;
	public TimerUtils timer;
	public TimerUtils critWaitTimer;
	public Random random;
	public int swordDelay;
	public int axeDelay;
	public boolean pendingCritHit;
	public Entity pendingTarget;
	// ponytail: pendingIsSword is written when a crit is latched but never read again -
	// the deferred hit re-derives the weapon from the current main hand instead.
	public boolean pendingIsSword;
	public Entity currentTarget;
	public long targetAcquiredTime;
	public int lastSlot;
	public long lastSlotChangeTime;
	public int reactionMs;

	public Triggerbot() {
		super("Trigger Bot", Category.COMBAT);
		this.workInScreen = new Setting<>("Work In Screen", false);
		this.whileUse = new Setting<>("While Use", false);
		this.onLeftClick = new Setting<>("On Left Click", false);
		this.allItems = new Setting<>("All Items", false);
		this.swordMinDelay = new Setting<>("Sword Min Delay", 540, 0, 1000);
		this.swordMaxDelay = new Setting<>("Sword Max Delay", 570, 0, 1000);
		this.axeMinDelay = new Setting<>("Axe Min Delay", 780, 0, 1000);
		this.axeMaxDelay = new Setting<>("Axe Max Delay", 820, 0, 1000);
		this.checkShield = new Setting<>("Check Shield", false);
		this.onlyCritSword = new Setting<>("Only Crit Sword", false);
		this.onlyCritAxe = new Setting<>("Only Crit Axe", false);
		this.prioritizeCrits = new Setting<>("Prioritize Crits", false);
		this.critPatienceMs = new Setting<>("Crit Patience Ms", 250, 0, 800);
		this.critFallHeight = new Setting<>("Crit Fall Threshold", 0.0f, 0.0f, 1.0f);
		this.swingHand = new Setting<>("Swing Hand", true);
		this.cooldownCheck = new Setting<>("Cooldown Check", true);
		this.cooldownPercent = new Setting<>("Cooldown %", 95.0f, 80.0f, 100.0f);
		this.clickSimulation = new Setting<>("Click Simulation", true);
		this.clickHoldMs = new Setting<>("Click Hold Ms", 40, 10, 120);
		this.strayBypass = new Setting<>("Stray Bypass", false);
		this.allEntities = new Setting<>("All Entities", false);
		this.useShield = new Setting<>("Use Shield", false);
		this.shieldTime = new Setting<>("Shield Time", 350, 100, 1000);
		this.samePlayer = new Setting<>("Same Player", false);
		this.whileAscending = new Setting<>("While Ascending", false);
		this.missChance = new Setting<>("Miss Chance", 0.0f, 0.0f, 100.0f);
		this.hitChance = new Setting<>("Hit Chance", 100.0f, 0.0f, 100.0f);
		this.reactionMinMs = new Setting<>("Reaction Min Ms", 50, 0, 300);
		this.reactionMaxMs = new Setting<>("Reaction Max Ms", 120, 0, 500);
		this.slotChangeCooldownMs = new Setting<>("Slot Change Cooldown", 100, 0, 500);
		this.jitterMs = new Setting<>("Jitter Ms", 15, 0, 50);
		this.timer = new TimerUtils();
		this.critWaitTimer = new TimerUtils();
		this.random = new Random();
		this.pendingCritHit = false;
		this.pendingTarget = null;
		this.pendingIsSword = false;
		this.currentTarget = null;
		this.targetAcquiredTime = 0L;
		this.lastSlot = -1;
		this.lastSlotChangeTime = 0L;
		this.reactionMs = 0;
		this.setDescription("Automatically hits players when looking at them");
		this.addSetting(this.workInScreen);
		this.addSetting(this.whileUse);
		this.addSetting(this.onLeftClick);
		this.addSetting(this.allItems);
		this.addSetting(this.swordMinDelay);
		this.addSetting(this.swordMaxDelay);
		this.addSetting(this.axeMinDelay);
		this.addSetting(this.axeMaxDelay);
		this.addSetting(this.checkShield);
		this.addSetting(this.onlyCritSword);
		this.addSetting(this.onlyCritAxe);
		this.addSetting(this.prioritizeCrits);
		this.addSetting(this.critPatienceMs);
		this.addSetting(this.critFallHeight);
		this.addSetting(this.swingHand);
		this.addSetting(this.cooldownCheck);
		this.addSetting(this.cooldownPercent);
		this.addSetting(this.clickSimulation);
		this.addSetting(this.clickHoldMs);
		this.addSetting(this.strayBypass);
		this.addSetting(this.allEntities);
		this.addSetting(this.useShield);
		this.addSetting(this.shieldTime);
		this.addSetting(this.samePlayer);
		this.addSetting(this.whileAscending);
		this.addSetting(this.missChance);
		this.addSetting(this.hitChance);
		this.addSetting(this.reactionMinMs);
		this.addSetting(this.reactionMaxMs);
		this.addSetting(this.slotChangeCooldownMs);
		this.addSetting(this.jitterMs);
	}

	@Override
	public void onEnable() {
		this.swordDelay = MathUtils.randomInt(this.swordMinDelay.getValue(), this.swordMaxDelay.getValue());
		this.axeDelay = MathUtils.randomInt(this.axeMinDelay.getValue(), this.axeMaxDelay.getValue());
		this.pendingCritHit = false;
		this.pendingTarget = null;
		this.currentTarget = null;
		this.lastSlot = mc.player != null ? mc.player.getInventory().getSelectedSlot() : -1;
	}

	@Override
	public void onDisable() {
		this.pendingCritHit = false;
		this.pendingTarget = null;
		this.currentTarget = null;
	}

	/** True while a hit would land as a critical: falling freely, past the configured fall distance. */
	public boolean isCritWindow() {
		if (mc.player == null) {
			return false;
		}
		return !mc.player.onGround()
				&& mc.player.getDeltaMovement().y < 0.0
				&& mc.player.fallDistance > this.critFallHeight.getValue()
				&& !mc.player.onClimbable()
				&& !mc.player.isInWater()
				&& !mc.player.isPassenger()
				&& mc.player.getControlledVehicle() == null;
	}

	public float getCooldown() {
		return mc.player.getAttackStrengthScale(0.0f);
	}

	/** Every gate that is about the target or the player's current state, not about timing. */
	public boolean shouldHit(Entity target) {
		if (target == null) {
			return false;
		}
		if (!this.workInScreen.getValue() && mc.screen != null) {
			return false;
		}
		if (this.onLeftClick.getValue() && !KeyUtils.isKeyPressed(0)) {
			return false;
		}
		if (!this.whileUse.getValue()) {
			boolean usingOffhand = GLFW.glfwGetMouseButton(mc.getWindow().handle(), 1) == 1;
			if (usingOffhand && (mc.player.getOffhandItem().getItem().components().has(DataComponents.FOOD)
					|| mc.player.getOffhandItem().getItem() instanceof ShieldItem)) {
				return false;
			}
		}
		if (!this.whileAscending.getValue()) {
			boolean rising = !mc.player.onGround() && mc.player.getDeltaMovement().y > 0.0;
			boolean airborneWithoutFall = !mc.player.onGround() && mc.player.fallDistance <= 0.0;
			if (rising || airborneWithoutFall) {
				return false;
			}
		}
		if (this.samePlayer.getValue() && target != mc.player.getLastHurtMob()) {
			return false;
		}
		if (target instanceof Player player
				&& this.checkShield.getValue() && player.isBlocking() && !WorldUtils.isShieldFacingAway(player)) {
			return false;
		}
		boolean isPlayer = target instanceof Player;
		boolean isStray = this.strayBypass.getValue() && target instanceof Zombie;
		return isPlayer || isStray || this.allEntities.getValue();
	}

	/**
	 * Performs the hit and rolls the next delay for the weapon that was used.
	 *
	 * @param isSword true for the sword timing branch, false for the axe one; also
	 *                decides whether an equipped shield is released or raised first.
	 */
	public void hitEntity(Entity target, boolean isSword) {
		if (this.useShield.getValue() && mc.player.getOffhandItem().getItem() == Items.SHIELD) {
			if (isSword && mc.player.isBlocking()) {
				MouseSimulation.mouseRelease(1);
			} else if (!isSword) {
				MouseSimulation.mouseClick(1, this.shieldTime.getValue());
			}
		}
		WorldUtils.hitEntity(target, this.swingHand.getValue());
		if (this.clickSimulation.getValue()) {
			int holdMs = this.clickHoldMs.getValue() + this.random.nextInt(21) - 10;
			MouseSimulation.mouseClick(0, Math.max(10, holdMs));
		}
		if (isSword) {
			this.swordDelay = MathUtils.randomInt(this.swordMinDelay.getValue(), this.swordMaxDelay.getValue())
					+ this.random.nextInt(this.jitterMs.getValue() + 1);
		} else {
			this.axeDelay = MathUtils.randomInt(this.axeMinDelay.getValue(), this.axeMaxDelay.getValue())
					+ this.random.nextInt(this.jitterMs.getValue() + 1);
		}
		this.timer.reset();
		this.pendingCritHit = false;
		this.pendingTarget = null;
		this.currentTarget = null;
	}

	@Override
	public void onTick() {
		try {
			if (mc.player == null || mc.level == null) {
				return;
			}
			int selectedSlot = mc.player.getInventory().getSelectedSlot();
			if (selectedSlot != this.lastSlot) {
				this.lastSlotChangeTime = System.currentTimeMillis();
				this.lastSlot = selectedSlot;
			}
			// A swing right after a hotbar switch is the obvious tell, so sit out the change.
			if (System.currentTimeMillis() - this.lastSlotChangeTime < this.slotChangeCooldownMs.getValue()) {
				return;
			}
			HitResult hitResult = mc.hitResult;
			if (!(hitResult instanceof EntityHitResult entityHit)) {
				this.pendingCritHit = false;
				this.pendingTarget = null;
				this.currentTarget = null;
				return;
			}
			Entity target = entityHit.getEntity();
			if (!this.shouldHit(target)) {
				this.pendingCritHit = false;
				this.pendingTarget = null;
				this.currentTarget = null;
				return;
			}
			boolean isSword = this.allItems.getValue() || mc.player.getMainHandItem().is(ItemTags.SWORDS);
			boolean isAxe = !this.allItems.getValue() && mc.player.getMainHandItem().getItem() instanceof AxeItem;
			if (!isSword && !isAxe) {
				return;
			}
			if (this.cooldownCheck.getValue() && this.getCooldown() < this.cooldownPercent.getValue() / 100.0f) {
				if (this.pendingCritHit && this.pendingTarget != target) {
					this.pendingCritHit = false;
					this.pendingTarget = null;
				}
				return;
			}
			// A fresh target costs a reaction delay before anything else may fire.
			if (target != this.currentTarget) {
				this.currentTarget = target;
				this.targetAcquiredTime = System.currentTimeMillis();
				this.reactionMs = MathUtils.randomInt(this.reactionMinMs.getValue(), this.reactionMaxMs.getValue());
				return;
			}
			if (System.currentTimeMillis() - this.targetAcquiredTime < this.reactionMs) {
				return;
			}
			int hitDelay = isSword ? this.swordDelay : this.axeDelay;
			if (!this.timer.delay(hitDelay)) {
				return;
			}
			if (isSword && this.onlyCritSword.getValue() && !this.isCritWindow()) {
				return;
			}
			if (!isSword && this.onlyCritAxe.getValue() && !this.isCritWindow()) {
				return;
			}
			if (MathUtils.randomInt(1, 100) > this.hitChance.getValue()) {
				return;
			}
			if (MathUtils.randomInt(1, 100) <= this.missChance.getValue()) {
				return;
			}
			// Prioritise crits: hold the ready hit back until a crit window opens, but only
			// while "only crit" is off for this weapon - that setting already blocks non-crits.
			if (this.prioritizeCrits.getValue()
					&& !(isSword ? this.onlyCritSword.getValue() : this.onlyCritAxe.getValue())) {
				if (this.isCritWindow()) {
					this.pendingCritHit = false;
					this.pendingTarget = null;
					this.hitEntity(target, isSword);
					return;
				}
				if (!this.pendingCritHit || this.pendingTarget != target) {
					this.pendingCritHit = true;
					this.pendingTarget = target;
					this.pendingIsSword = isSword;
					this.critWaitTimer.reset();
					return;
				}
				int patienceMs = this.critPatienceMs.getValue();
				if (patienceMs > 0 && this.critWaitTimer.delay(patienceMs)) {
					this.pendingCritHit = false;
					this.pendingTarget = null;
					this.hitEntity(target, isSword);
				}
				return;
			}
			this.hitEntity(target, isSword);
		} catch (Exception e) {
			// Swallowed on purpose: a torn world/player state mid-tick must not kill the tick loop.
		}
	}
}
