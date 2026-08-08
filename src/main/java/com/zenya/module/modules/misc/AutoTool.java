package com.zenya.module.modules.misc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.block.BambooSaplingBlock;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import com.zenya.module.Category;
import com.zenya.module.Module;

import java.util.function.Predicate;

/**
 * While attack is held, swaps the hotbar to the fastest tool for the block under the
 * crosshair, or to the highest-damage weapon when an entity is under it.
 *
 * <p>A stack that cannot harvest the block scores -1 instead of its raw destroy speed, so a
 * fast-but-useless tool never wins. The swap only fires when the best slot actually beats
 * what is held, or the held item is not a tool at all. Only hotbar slots 0-8 are scanned.
 */
public class AutoTool extends Module {
	public AutoTool() {
		super("Auto Tool", Category.MISC);
		this.setDescription("Automatically swaps to the best tool or weapon in your hotbar when mining a block or attacking an entity.");
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.level == null || mc.gameMode == null) {
			return;
		}

		if (!mc.options.keyAttack.isDown() || mc.hitResult == null) {
			return;
		}

		HitResult crosshairHit = mc.hitResult;

		if (crosshairHit.getType() == HitResult.Type.ENTITY && crosshairHit instanceof EntityHitResult) {
			this.switchToBestWeapon();
			return;
		}

		if (crosshairHit.getType() == HitResult.Type.BLOCK && crosshairHit instanceof BlockHitResult blockHit) {
			this.switchToBestTool(blockHit.getBlockPos());
		}
	}

	public void switchToBestTool(BlockPos pos) {
		BlockState state = mc.level.getBlockState(pos);
		ItemStack heldStack = mc.player.getMainHandItem();
		int bestSlot = -1;
		double bestEfficiency = -1.0;

		for (int slot = 0; slot < 9; ++slot) {
			ItemStack stack = mc.player.getInventory().getItem(slot);
			double efficiency = calculateToolEfficiency(stack, state, candidate -> true);

			if (efficiency > bestEfficiency) {
				bestEfficiency = efficiency;
				bestSlot = slot;
			}
		}

		if (bestSlot == -1) {
			return;
		}

		double heldEfficiency = calculateToolEfficiency(heldStack, state, candidate -> true);

		// Also swap off a non-tool even when it happens to score as well as the best slot.
		if (bestEfficiency > heldEfficiency || !isToolItemStack(heldStack)) {
			this.swapToSlot(bestSlot);
		}
	}

	public void switchToBestWeapon() {
		int bestSlot = -1;
		double bestDamage = 0.0;

		for (int slot = 0; slot < 9; ++slot) {
			ItemStack stack = mc.player.getInventory().getItem(slot);

			if (stack.isEmpty()) {
				continue;
			}

			double damage = this.getAttackDamage(stack);

			if (damage > bestDamage) {
				bestDamage = damage;
				bestSlot = slot;
			}
		}

		if (bestSlot != -1) {
			this.swapToSlot(bestSlot);
		}
	}

	/**
	 * Summed attack-damage modifiers on the stack, falling back to a name-based guess
	 * (sword or axe plus material rank) for items that carry no modifiers at all.
	 */
	public double getAttackDamage(ItemStack stack) {
		ItemAttributeModifiers attributeModifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
		double damage = 0.0;

		if (attributeModifiers != null) {
			for (ItemAttributeModifiers.Entry entry : attributeModifiers.modifiers()) {
				if (!entry.attribute().toString().contains("attack_damage")) {
					continue;
				}

				damage += entry.modifier().amount();
			}
		}

		if (damage > 0.0) {
			return damage;
		}

		String itemPath = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();

		if (itemPath.endsWith("_sword")) {
			return 10.0 + this.getMaterialRank(itemPath);
		}

		if (itemPath.endsWith("_axe")) {
			return 5.0 + this.getMaterialRank(itemPath);
		}

		return 0.0;
	}

	public void swapToSlot(int slot) {
		if (slot < 0 || slot > 8) {
			return;
		}

		if (mc.player.getInventory().getSelectedSlot() != slot) {
			mc.player.getInventory().setSelectedSlot(slot);
		}
	}

	/**
	 * Destroy speed scaled by 1000, or -1 when {@code filter} rejects the stack, the stack is
	 * not a tool, or it cannot harvest the block. Swords on bamboo and shears on leaves are
	 * allowed through because vanilla's correct-tool check does not cover them.
	 */
	public static double calculateToolEfficiency(ItemStack stack, BlockState state, Predicate<ItemStack> filter) {
		if (!filter.test(stack) || !isToolItemStack(stack)) {
			return -1.0;
		}

		String itemPath = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		boolean isSword = itemPath.endsWith("_sword");

		// ponytail: the WOOL test is not gated on shears, so any tool scores on wool.
		if (!(stack.isCorrectToolForDrops(state)
				|| isSword && (state.getBlock() instanceof BambooStalkBlock || state.getBlock() instanceof BambooSaplingBlock)
				|| stack.getItem() instanceof ShearsItem && state.getBlock() instanceof LeavesBlock
				|| state.is(BlockTags.WOOL))) {
			return -1.0;
		}

		return stack.getDestroySpeed(state) * 1000.0f;
	}

	public static boolean isToolItemStack(ItemStack stack) {
		return isToolItem(stack.getItem());
	}

	public static boolean isToolItem(Item item) {
		if (item instanceof ShearsItem) {
			return true;
		}

		String itemPath = BuiltInRegistries.ITEM.getKey(item).getPath();

		return itemPath.endsWith("_pickaxe")
				|| itemPath.endsWith("_axe")
				|| itemPath.endsWith("_shovel")
				|| itemPath.endsWith("_hoe")
				|| itemPath.endsWith("_sword");
	}

	/** Tier score used to break ties between two items of the same kind; 0 for anything unknown. */
	public double getMaterialRank(String itemPath) {
		if (itemPath.startsWith("netherite_")) {
			return 6.0;
		}

		if (itemPath.startsWith("diamond_")) {
			return 5.0;
		}

		if (itemPath.startsWith("iron_")) {
			return 4.0;
		}

		if (itemPath.startsWith("golden_")) {
			return 3.0;
		}

		if (itemPath.startsWith("stone_")) {
			return 2.0;
		}

		if (itemPath.startsWith("wooden_")) {
			return 1.0;
		}

		return 0.0;
	}
}
