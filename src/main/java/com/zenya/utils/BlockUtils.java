package com.zenya.utils;

import com.zenya.utils.rotation.Rotation;
import com.zenya.utils.rotation.RotationUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.stream.Stream;

/**
 * World lookups the combat modules need before they commit to a placement.
 *
 * <p>Everything here reads the client level directly and assumes a world is
 * loaded; callers are tick handlers that already bailed out otherwise.
 */
public class BlockUtils {
	public static Minecraft mc = Minecraft.getInstance();

	public static boolean isBlockAt(BlockPos pos, Block block) {
		return mc.level.getBlockState(pos).getBlock() == block;
	}

	/** Snaps the player's head straight at the block centre, no interpolation. */
	public static void lookAtBlock(BlockPos pos) {
		Rotation rotation = RotationUtils.getDirection(mc.player, pos.getCenter());
		mc.player.setXRot((float) rotation.pitch());
		mc.player.setYRot((float) rotation.yaw());
	}

	public static boolean isChargedAnchor(BlockPos pos) {
		if (isBlockAt(pos, Blocks.RESPAWN_ANCHOR)) {
			return mc.level.getBlockState(pos).getValue(RespawnAnchorBlock.CHARGE) != 0;
		}

		return false;
	}

	public static boolean isEmptyAnchor(BlockPos pos) {
		if (isBlockAt(pos, Blocks.RESPAWN_ANCHOR)) {
			return mc.level.getBlockState(pos).getValue(RespawnAnchorBlock.CHARGE) == 0;
		}

		return false;
	}

	/** True when the space above {@code pos} is free of blocks and of anything but dropped items. */
	public static boolean canPlaceAbove(BlockPos pos) {
		BlockPos above = pos.above();

		if (!mc.level.isEmptyBlock(above)) {
			return false;
		}

		double x = above.getX();
		double y = above.getY();
		double z = above.getZ();
		List<Entity> entities = mc.level.getEntities((Entity) null, new AABB(x, y, z, x + 1.0, y + 1.0, z + 1.0));
		entities.removeIf(entity -> entity instanceof ItemEntity);

		return entities.isEmpty();
	}

	/**
	 * Every position in the inclusive box spanned by the two corners, walking X
	 * then Y then Z. The stream is bounded by the box volume, so the overflow
	 * throw at the end of Z is unreachable unless that count is wrong.
	 */
	public static Stream<BlockPos> iterateBetween(BlockPos from, BlockPos to) {
		BlockPos min = new BlockPos(Math.min(from.getX(), to.getX()), Math.min(from.getY(), to.getY()), Math.min(from.getZ(), to.getZ()));
		BlockPos max = new BlockPos(Math.max(from.getX(), to.getX()), Math.max(from.getY(), to.getY()), Math.max(from.getZ(), to.getZ()));
		Stream<BlockPos> positions = Stream.iterate(min, current -> {
			int x = current.getX();
			int y = current.getY();
			int z = current.getZ();

			if (++x > max.getX()) {
				x = min.getX();
				++y;
			}

			if (y > max.getY()) {
				y = min.getY();
				++z;
			}

			if (z > max.getZ()) {
				throw new IllegalStateException("Stream limit didn't work.");
			}

			return new BlockPos(x, y, z);
		});
		int volume = (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1);

		return positions.limit(volume);
	}
}
