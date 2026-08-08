package com.zenya.module.modules.smps;

import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.module.modules.common.ClientModuleTools;
import com.zenya.setting.Setting;
import com.zenya.utils.RenderUtils;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Flags chunks whose underground has been dug or built in: rotated deepslate, or a
 * block that never generates below sea level (planks, torches, rails, containers).
 *
 * <p>Every incoming chunk is scanned column by column, which is only affordable
 * because the scan stops at y=0. {@link #modifiedChunks} doubles as the de-duplication
 * set, so a chunk is scanned and announced once until the module is toggled.
 */
public class PlayerChunkFinder extends Module {
	public Setting<Boolean> detectUnnaturalBlocks;
	public Setting<Integer> renderRadius;
	public Setting<Double> renderY;
	public Setting<Boolean> filledEsp;
	public Setting<Color> sideColor;
	public Setting<Color> lineColor;
	public Map<ChunkPos, String> modifiedChunks;

	public PlayerChunkFinder() {
		super("Player Chunk Finder", Category.SMPS);
		this.detectUnnaturalBlocks = new Setting<>("Detect Unnatural Blocks", true);
		this.renderRadius = new Setting<>("Grid Radius", 8, 1, 32);
		this.renderY = new Setting<>("Render Y", -60.0, -64.0, 320.0);
		this.filledEsp = new Setting<>("Filled ESP", true);
		this.sideColor = new Setting<>("Side Color", new Color(255, 82, 82, 80));
		this.lineColor = new Setting<>("Line Color", new Color(255, 82, 82, 255));
		this.modifiedChunks = new LinkedHashMap<>();
		this.setDescription("Detects underground player-modified chunks.");
		this.addSetting(this.detectUnnaturalBlocks);
		this.addSetting(this.renderRadius);
		this.addSetting(this.renderY);
		this.addSetting(this.filledEsp);
		this.addSetting(this.sideColor);
		this.addSetting(this.lineColor);
	}

	@Override
	public void onEnable() {
		this.modifiedChunks.clear();
	}

	@Override
	public void onDisable() {
		this.modifiedChunks.clear();
	}

	@Override
	public void onPacketReceive(Packet packet) {
		if (!(packet instanceof ClientboundLevelChunkWithLightPacket chunkPacket)) {
			return;
		}
		if (mc.level == null || mc.player == null) {
			return;
		}
		ChunkPos chunkPos = new ChunkPos(chunkPacket.getX(), chunkPacket.getZ());
		if (this.modifiedChunks.containsKey(chunkPos)) {
			return;
		}
		// the packet can arrive before the chunk is in the chunk source; there is nothing to read yet
		if (mc.level.getChunkSource().getChunk(chunkPos.x, chunkPos.z, false) == null) {
			return;
		}
		String reason = this.scanChunk(chunkPos);
		if (reason == null) {
			return;
		}
		this.modifiedChunks.put(chunkPos, reason);
		mc.player.displayClientMessage(Component.literal("§b[Player Chunk Finder]§r Modified chunk at "
				+ chunkPos.x + ", " + chunkPos.z + " §7(" + reason + ")"), false);
	}

	@Override
	public void onRender(PoseStack poseStack, float partialTicks) {
		if (mc.level == null || mc.player == null || this.modifiedChunks.isEmpty()) {
			return;
		}
		Camera camera = RenderUtils.getCamera();
		if (camera == null) {
			return;
		}
		Vec3 cameraPos = RenderUtils.getCameraPos(camera);
		int radius = this.renderRadius.getValue();
		int playerChunkX = mc.player.chunkPosition().x;
		int playerChunkZ = mc.player.chunkPosition().z;
		double y = this.renderY.getValue() - cameraPos.y;
		RenderUtils.WorldBatch batch = RenderUtils.beginWorldBatch(poseStack);
		boolean drewAnything = false;
		// A flat slab covering the whole chunk footprint, drawn only inside the grid radius.
		for (ChunkPos pos : this.modifiedChunks.keySet()) {
			if (Math.abs(pos.x - playerChunkX) > radius || Math.abs(pos.z - playerChunkZ) > radius) continue;
			double x = (double) pos.getMinBlockX() - cameraPos.x;
			double z = (double) pos.getMinBlockZ() - cameraPos.z;
			if (this.filledEsp.getValue()) {
				batch.renderFilledBox(x, y, z, x + 16.0, y + 0.12, z + 16.0, this.sideColor.getValue());
			}
			batch.renderOutlineBox(x, y, z, x + 16.0, y + 0.12, z + 16.0, this.lineColor.getValue());
			drewAnything = true;
		}
		if (drewAnything) {
			batch.flush();
		}
	}

	/**
	 * Walks all 256 columns of the chunk from the world floor up to y=0.
	 *
	 * @return the detection name of the first hit, or null when the chunk looks natural.
	 */
	public String scanChunk(ChunkPos chunkPos) {
		int originX = chunkPos.getMinBlockX();
		int originZ = chunkPos.getMinBlockZ();
		int minY = Math.max(mc.level.getMinY(), -64);
		int maxY = Math.min(mc.level.getMinY() + mc.level.getHeight() - 1, 0);
		for (int x = originX; x < originX + 16; ++x) {
			for (int z = originZ; z < originZ + 16; ++z) {
				BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, minY, z);
				for (int y = minY; y < maxY; ++y) {
					cursor.setY(y);
					BlockState state = mc.level.getBlockState(cursor);
					if (ClientModuleTools.isRotatedDeepslate(state)) {
						return "MODIFIED_DEEPSLATE";
					}
					if (!this.detectUnnaturalBlocks.getValue() || !isUnnaturalUndergroundBlock(state.getBlock())) continue;
					return "UNNATURAL";
				}
			}
		}
		return null;
	}

	/** Blocks that world generation never places below y=0, so finding one means someone built there. */
	public static boolean isUnnaturalUndergroundBlock(Block block) {
		return block == Blocks.COBBLESTONE || block == Blocks.COBBLED_DEEPSLATE || block == Blocks.OAK_PLANKS
				|| block == Blocks.SPRUCE_PLANKS || block == Blocks.BIRCH_PLANKS || block == Blocks.JUNGLE_PLANKS
				|| block == Blocks.ACACIA_PLANKS || block == Blocks.DARK_OAK_PLANKS || block == Blocks.MANGROVE_PLANKS
				|| block == Blocks.CHERRY_PLANKS || block == Blocks.BAMBOO_PLANKS || block == Blocks.CRIMSON_PLANKS
				|| block == Blocks.WARPED_PLANKS || block == Blocks.TORCH || block == Blocks.WALL_TORCH
				|| block == Blocks.LADDER || block == Blocks.RAIL || block == Blocks.CRAFTING_TABLE
				|| block == Blocks.FURNACE || block == Blocks.BLAST_FURNACE || block == Blocks.SMOKER
				|| block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || block == Blocks.BARREL
				|| block == Blocks.GLASS;
	}
}
