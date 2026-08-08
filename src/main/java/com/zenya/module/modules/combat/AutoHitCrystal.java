package com.zenya.module.modules.combat;

import com.zenya.module.ActivatableModule;
import com.zenya.module.Category;
import com.zenya.setting.Setting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.lwjgl.glfw.GLFW;

/**
 * Builds and detonates a one-block crystal tower on top of the block under the crosshair
 * while this module's activation key is held.
 *
 * <p>Exactly one of hit / place obsidian / place crystal runs per tick and each arms
 * {@code cooldown}, so the three packets never land on the same tick and trip a server's
 * rate limit. An unbound activation key counts as permanently held, which is what lets the
 * module double as a plain toggle.
 */
public class AutoHitCrystal extends ActivatableModule {
	public Setting<Integer> delay;
	public int cooldown;

	public AutoHitCrystal() {
		super("AutoHitCrystal", Category.COMBAT);
		this.delay = new Setting<>("Delay (ticks)", 1, 0, 10);
		this.cooldown = 0;
		this.addSetting(this.delay);
	}

	@Override
	public void onEnable() {
		this.cooldown = 0;
	}

	/** The key is polled as a hold in {@link #onTick}, so pressing it must not toggle anything. */
	@Override
	public void onActivationKeyPressed() {
	}

	/** An unbound key (0) reads as always held. */
	public boolean isActivationHeld() {
		int key = this.getActivationKey();
		if (key == 0) {
			return true;
		}
		if (mc.getWindow() == null) {
			return false;
		}
		try {
			return GLFW.glfwGetKey(mc.getWindow().handle(), key) == GLFW.GLFW_PRESS;
		} catch (Exception e) {
			// Swallowed: a bind outside GLFW's key range throws, and a bad bind should
			// only mean "not held" rather than killing the tick loop.
			return false;
		}
	}

	@Override
	public void onTick() {
		if (this.cooldown > 0) {
			this.cooldown -= 1;
			return;
		}
		if (mc.player == null || mc.level == null || mc.gameMode == null) {
			return;
		}
		if (mc.screen != null) {
			return;
		}
		if (!this.isActivationHeld()) {
			return;
		}
		if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) {
			return;
		}
		BlockHitResult crosshairHit = (BlockHitResult) mc.hitResult;
		BlockPos aimedPos = crosshairHit.getBlockPos();
		BlockPos basePos = aimedPos.above();
		BlockPos crystalPos = basePos.above();

		EndCrystal crystal = this.findCrystalAt(crystalPos);
		if (crystal != null) {
			mc.gameMode.attack(mc.player, crystal);
			mc.player.swing(InteractionHand.MAIN_HAND);
			this.cooldown = this.delay.getValue();
			return;
		}

		if (!this.isObsidianLike(basePos)) {
			int obsidianSlot = this.findHotbarSlot(Items.OBSIDIAN);
			if (obsidianSlot < 0) {
				return;
			}
			if (mc.player.getInventory().getSelectedSlot() != obsidianSlot) {
				mc.player.getInventory().setSelectedSlot(obsidianSlot);
			}
			BlockHitResult obsidianPlace = new BlockHitResult(
					Vec3.atCenterOf(aimedPos).add(0.0, 0.5, 0.0), Direction.UP, aimedPos, false);
			mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, obsidianPlace);
			mc.player.swing(InteractionHand.MAIN_HAND);
			this.cooldown = this.delay.getValue();
			return;
		}

		if (!this.canPlaceCrystal(crystalPos)) {
			return;
		}
		int crystalSlot = this.findHotbarSlot(Items.END_CRYSTAL);
		if (crystalSlot < 0) {
			return;
		}
		if (mc.player.getInventory().getSelectedSlot() != crystalSlot) {
			mc.player.getInventory().setSelectedSlot(crystalSlot);
		}
		BlockHitResult crystalPlace = new BlockHitResult(
				Vec3.atCenterOf(basePos).add(0.0, 0.5, 0.0), Direction.UP, basePos, false);
		mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, crystalPlace);
		mc.player.swing(InteractionHand.MAIN_HAND);
		this.cooldown = this.delay.getValue();
	}

	/** The two blocks a crystal is allowed to sit on. */
	public boolean isObsidianLike(BlockPos pos) {
		BlockState state = mc.level.getBlockState(pos);
		return state.is(Blocks.OBSIDIAN) || state.is(Blocks.BEDROCK);
	}

	/** A crystal needs the block free and nothing standing in the 1x2 volume it occupies. */
	public boolean canPlaceCrystal(BlockPos pos) {
		if (!mc.level.isEmptyBlock(pos)) {
			return false;
		}
		AABB box = new AABB(pos.getX(), pos.getY(), pos.getZ(),
				pos.getX() + 1.0, pos.getY() + 2.0, pos.getZ() + 1.0);
		return mc.level.getEntities((Entity) null, box).isEmpty();
	}

	/** First living crystal inside the 1x2 volume at {@code pos}, or null if there is none. */
	public EndCrystal findCrystalAt(BlockPos pos) {
		AABB box = new AABB(pos.getX(), pos.getY(), pos.getZ(),
				pos.getX() + 1.0, pos.getY() + 2.0, pos.getZ() + 1.0);
		for (Entity entity : mc.level.getEntities((Entity) null, box)) {
			if (entity instanceof EndCrystal crystal && crystal.isAlive()) {
				return crystal;
			}
		}
		return null;
	}

	public int findHotbarSlot(Item item) {
		for (int slot = 0; slot < 9; ++slot) {
			if (mc.player.getInventory().getItem(slot).is(item)) {
				return slot;
			}
		}
		return -1;
	}
}
