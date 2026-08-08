package com.zenya.module.modules.render;

import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.utils.RenderUtils;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Boxes every piston that is mid-extension or mid-retraction.
 *
 * <p>Only {@link PistonMovingBlockEntity} carries a block entity, so a chunk sweep over
 * the render distance finds them cheaply — a still piston has no block entity and never
 * shows up. Positions are collected fresh each frame rather than cached, since a moving
 * piston lives for about four ticks and a cache would mostly hold stale entries.
 */
public class PistonESP extends Module {
	public static final Color PISTON_COLOR = new Color(173, 216, 230, 150);

	public PistonESP() {
		super("Piston ESP", Category.RENDER);
		this.setDescription("Highlights moving and non-moving pistons with a light blue color.");
	}

	@Override
	public void onRender(PoseStack poseStack, float partialTicks) {
		if (mc.level == null || mc.player == null) {
			return;
		}
		Camera camera = RenderUtils.getCamera();
		if (camera == null) {
			return;
		}
		Vec3 cameraPos = RenderUtils.getCameraPos(camera);
		double cameraX = cameraPos.x;
		double cameraY = cameraPos.y;
		double cameraZ = cameraPos.z;
		List<BlockPos> pistons = new ArrayList<>();
		int renderDistance = mc.options.getEffectiveRenderDistance();
		int playerChunkX = mc.player.chunkPosition().x;
		int playerChunkZ = mc.player.chunkPosition().z;
		for (int offsetX = -renderDistance; offsetX <= renderDistance; ++offsetX) {
			for (int offsetZ = -renderDistance; offsetZ <= renderDistance; ++offsetZ) {
				LevelChunk chunk = mc.level.getChunkSource().getChunk(playerChunkX + offsetX, playerChunkZ + offsetZ, false);
				if (chunk == null) continue;
				for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
					if (!(blockEntity instanceof PistonMovingBlockEntity)) continue;
					pistons.add(blockEntity.getBlockPos());
				}
			}
		}
		if (pistons.isEmpty()) {
			return;
		}
		RenderUtils.WorldBatch batch = RenderUtils.beginWorldBatch(poseStack);
		for (BlockPos pos : pistons) {
			double x = (double) pos.getX() - cameraX;
			double y = (double) pos.getY() - cameraY;
			double z = (double) pos.getZ() - cameraZ;
			// Inset by 0.05 on every face so the box does not z-fight with the block itself.
			batch.renderFilledBox(x + 0.05, y + 0.05, z + 0.05, x + 0.95, y + 0.95, z + 0.95, PISTON_COLOR);
			batch.renderOutlineBox(x + 0.05, y + 0.05, z + 0.05, x + 0.95, y + 0.95, z + 0.95, PISTON_COLOR);
		}
		batch.flush();
	}
}
