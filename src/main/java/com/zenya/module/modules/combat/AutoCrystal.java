package com.zenya.module.modules.combat;

import com.zenya.module.ActivatableModule;
import com.zenya.module.Category;
import com.zenya.setting.Setting;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import org.lwjgl.glfw.GLFW;

/**
 * Breaks the closest crystal and places a new one on the block under the crosshair,
 * both gated on right-click being physically held.
 *
 * <p>Breaking and placing keep separate tick counters so a long break delay cannot stall
 * placement, and both are zeroed the moment right-click is released — otherwise the first
 * click after a pause would be eaten by a counter left over from the previous burst.
 */
public class AutoCrystal extends ActivatableModule {
	// ponytail: RANGE is never read - the break and place checks use hard-coded 5.0 / 25.0.
	public static double RANGE = 5.0;

	public Setting<Float> placeDelay;
	public Setting<Float> breakDelay;
	public int placeDelayCounter;
	public int breakDelayCounter;

	public AutoCrystal() {
		super("Auto Crystal", Category.COMBAT);
		this.placeDelay = new Setting<>("Place Delay", Float.valueOf(0.0f), Float.valueOf(0.0f), Float.valueOf(20.0f));
		this.breakDelay = new Setting<>("Break Delay", Float.valueOf(0.0f), Float.valueOf(0.0f), Float.valueOf(20.0f));
		this.addSetting(this.placeDelay);
		this.addSetting(this.breakDelay);
	}

	@Override
	public void onEnable() {
		this.resetCounters();
		super.onEnable();
	}

	@Override
	public void onTick() {
		if (mc.screen != null) {
			return;
		}
		if (mc.player == null || mc.level == null) {
			return;
		}
		this.updateCounters();
		if (!this.isRightClickHeld()) {
			this.resetCounters();
			return;
		}

		Entity nearestCrystal = null;
		double nearestDistance = Double.MAX_VALUE;
		for (Entity entity : mc.level.entitiesForRendering()) {
			if (!(entity instanceof EndCrystal)) {
				continue;
			}
			double distance = mc.player.distanceTo(entity);
			if (distance <= 5.0 && distance < nearestDistance) {
				nearestDistance = distance;
				nearestCrystal = entity;
			}
		}
		if (nearestCrystal != null && this.breakDelayCounter == 0) {
			// ponytail: mc.gameMode is dereferenced without a null check, unlike the guard above.
			mc.gameMode.attack(mc.player, nearestCrystal);
			mc.player.swing(InteractionHand.MAIN_HAND);
			this.breakDelayCounter = Math.max(0, this.breakDelay.getValue().intValue());
		}

		if (!(mc.hitResult instanceof BlockHitResult crosshairHit)) {
			return;
		}
		if (crosshairHit.getType() != HitResult.Type.BLOCK) {
			return;
		}
		BlockPos aimedPos = crosshairHit.getBlockPos();
		if (mc.player.distanceToSqr(aimedPos.getCenter()) > 25.0) {
			return;
		}
		if (!this.isValidCrystalPlacement(aimedPos)) {
			return;
		}
		// The module sends its own use packet; leaving use held would place a second crystal.
		mc.options.keyUse.setDown(false);
		int crystalSlot = this.findHotbarSlot(Items.END_CRYSTAL);
		if (crystalSlot == -1) {
			return;
		}
		if (mc.player.getMainHandItem().getItem() != Items.END_CRYSTAL) {
			mc.player.getInventory().setSelectedSlot(crystalSlot);
		}
		if (this.placeDelayCounter == 0) {
			this.interactWithBlock(crosshairHit);
			this.placeDelayCounter = Math.max(0, this.placeDelay.getValue().intValue());
		}
	}

	public boolean isRightClickHeld() {
		return mc.getWindow() != null
				&& GLFW.glfwGetMouseButton(mc.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
	}

	public void resetCounters() {
		this.placeDelayCounter = 0;
		this.breakDelayCounter = 0;
	}

	public void updateCounters() {
		if (this.placeDelayCounter > 0) {
			this.placeDelayCounter -= 1;
		}
		if (this.breakDelayCounter > 0) {
			this.breakDelayCounter -= 1;
		}
	}

	public int findHotbarSlot(Item item) {
		for (int slot = 0; slot < 9; ++slot) {
			if (mc.player.getInventory().getItem(slot).is(item)) {
				return slot;
			}
		}
		return -1;
	}

	/** Obsidian or bedrock with a clear 1x2 volume above it, which is where the crystal lands. */
	public boolean isValidCrystalPlacement(BlockPos pos) {
		if (!mc.level.getBlockState(pos).is(Blocks.OBSIDIAN) && !mc.level.getBlockState(pos).is(Blocks.BEDROCK)) {
			return false;
		}
		BlockPos above = pos.above();
		if (!mc.level.isEmptyBlock(above)) {
			return false;
		}
		int x = above.getX();
		int y = above.getY();
		int z = above.getZ();
		AABB box = new AABB(x, y, z, x + 1.0, y + 2.0, z + 1.0);
		return mc.level.getEntities((Entity) null, box).isEmpty();
	}

	public void interactWithBlock(BlockHitResult hit) {
		mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
		mc.player.swing(InteractionHand.MAIN_HAND);
	}
}
