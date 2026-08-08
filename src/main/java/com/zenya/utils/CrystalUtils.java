package com.zenya.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Whether an end crystal can be placed on a given block.
 *
 * <p>A crystal needs obsidian or bedrock underneath and clear space above. Note
 * the two checks disagree on height on purpose: only the single block above must
 * be air, but the entity sweep covers the crystal's full two-block hitbox, so a
 * player standing on the support blocks the placement.
 */
public class CrystalUtils {
	public static Minecraft mc = Minecraft.getInstance();

	public static boolean isCrystalPos(BlockPos pos) {
		return isObsidianOrBedrock(pos) && isPlaceable(pos);
	}

	/** Air directly above {@code pos} and nothing standing in the crystal's hitbox. */
	public static boolean isPlaceable(BlockPos pos) {
		BlockPos above = pos.above();

		if (!mc.level.isEmptyBlock(above)) {
			return false;
		}

		double x = above.getX();
		double y = above.getY();
		double z = above.getZ();
		List<Entity> blocking = mc.level.getEntities((Entity) null, new AABB(x, y, z, x + 1.0, y + 2.0, z + 1.0));

		return blocking.isEmpty();
	}

	public static boolean isObsidianOrBedrock(BlockPos pos) {
		return mc.level.getBlockState(pos).is(Blocks.OBSIDIAN) || mc.level.getBlockState(pos).is(Blocks.BEDROCK);
	}
}
