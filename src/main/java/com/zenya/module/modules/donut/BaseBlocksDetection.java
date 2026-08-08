package com.zenya.module.modules.donut;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.module.modules.common.ClientModuleTools;
import com.zenya.setting.Setting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.awt.Color;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scans the chunks around the player for the blocks a base gives itself away with —
 * spawners, pistons, rotated deepslate and storage — and paints a box on every hit.
 *
 * <p>Walking every block of a chunk column is far more expensive than reading its block
 * entities, so that pass only runs when a setting actually needs it. {@link #scannedChunks}
 * stops a chunk being walked twice, and {@link #cleanupDistant} prunes both collections to
 * the scan radius so travelling cannot grow them without bound.
 */
public class BaseBlocksDetection extends Module {
	public static final int TYPE_SPAWNER = 0;
	public static final int TYPE_PISTON = 1;
	public static final int TYPE_DEEPSLATE = 2;
	public static final int TYPE_STORAGE = 3;

	public Setting<Integer> scanRadius;
	public Setting<Boolean> scanSpawners;
	public Setting<Boolean> scanPistons;
	public Setting<Boolean> scanDeepslate;
	public Setting<Boolean> scanStorage;
	public Set<ChunkPos> scannedChunks;
	public Map<BlockPos, ClientModuleTools.BlockMark> foundBlocks;
	public int tickCounter;

	public BaseBlocksDetection() {
		super("Base Blocks Detection", Category.DONUT);
		this.scanRadius = new Setting<>("Scan Radius", 4, 1, 10);
		this.scanSpawners = new Setting<>("Spawners", true);
		this.scanPistons = new Setting<>("Pistons", true);
		this.scanDeepslate = new Setting<>("Rotated Deepslate", true);
		this.scanStorage = new Setting<>("Storage", true);
		this.scannedChunks = ClientModuleTools.chunkSet();
		this.foundBlocks = new ConcurrentHashMap<>();
		this.setDescription("Scans loaded chunks for spawners, pistons, rotated deepslate, and storage blocks.");
		this.addSetting(this.scanRadius);
		this.addSetting(this.scanSpawners);
		this.addSetting(this.scanPistons);
		this.addSetting(this.scanDeepslate);
		this.addSetting(this.scanStorage);
	}

	@Override
	public void onEnable() {
		this.scannedChunks.clear();
		this.foundBlocks.clear();
		this.tickCounter = 0;
	}

	@Override
	public void onDisable() {
		this.scannedChunks.clear();
		this.foundBlocks.clear();
	}

	@Override
	public void onWorldChange() {
		this.scannedChunks.clear();
		this.foundBlocks.clear();
		this.tickCounter = 0;
	}

	@Override
	public void onTick() {
		if (mc.level == null || mc.player == null) {
			return;
		}
		if (++this.tickCounter % 60 != 0) {
			return;
		}
		this.cleanupDistant();
		ChunkPos center = mc.player.chunkPosition();
		int radius = this.scanRadius.getValue();
		for (int chunkX = center.x - radius; chunkX <= center.x + radius; ++chunkX) {
			for (int chunkZ = center.z - radius; chunkZ <= center.z + radius; ++chunkZ) {
				LevelChunk chunk;
				ChunkPos pos = new ChunkPos(chunkX, chunkZ);
				if (!this.scannedChunks.add(pos)
						|| (chunk = mc.level.getChunkSource().getChunk(chunkX, chunkZ, false)) == null) {
					continue;
				}
				this.scanChunk(chunk, pos);
			}
		}
	}

	public void scanChunk(LevelChunk chunk, ChunkPos pos) {
		// Block entities are already indexed, so spawners and containers come cheap.
		if (this.scanStorage.getValue() || this.scanSpawners.getValue()) {
			for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
				if (this.scanSpawners.getValue() && blockEntity instanceof SpawnerBlockEntity) {
					this.put(blockEntity.getBlockPos(), TYPE_SPAWNER);
				} else if (this.scanStorage.getValue() && ClientModuleTools.isStorage(blockEntity)) {
					this.put(blockEntity.getBlockPos(), TYPE_STORAGE);
				}
			}
		}
		// Everything below needs a full column walk, so bail out unless one of those
		// detectors is actually on.
		if (!this.scanPistons.getValue() && !this.scanDeepslate.getValue() && !this.scanStorage.getValue()) {
			return;
		}
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		int maxY = mc.level == null ? 320 : mc.level.getMaxY();
		for (int offsetX = 0; offsetX < 16; ++offsetX) {
			for (int offsetZ = 0; offsetZ < 16; ++offsetZ) {
				for (int y = chunk.getMinY(); y <= maxY; ++y) {
					cursor.set(pos.getMinBlockX() + offsetX, y, pos.getMinBlockZ() + offsetZ);
					BlockState state = chunk.getBlockState(cursor);
					Block block = state.getBlock();
					if (this.scanPistons.getValue() && ClientModuleTools.isPiston(block)) {
						this.put(cursor.immutable(), TYPE_PISTON);
					} else if (this.scanDeepslate.getValue() && ClientModuleTools.isRotatedDeepslate(state)) {
						this.put(cursor.immutable(), TYPE_DEEPSLATE);
					} else if (this.scanStorage.getValue() && ClientModuleTools.isStorageBlock(block)) {
						this.put(cursor.immutable(), TYPE_STORAGE);
					} else if (this.scanSpawners.getValue() && block == Blocks.SPAWNER) {
						this.put(cursor.immutable(), TYPE_SPAWNER);
					}
				}
			}
		}
	}

	/** Records one hit; the colour and label are picked from the {@code TYPE_*} constant. */
	public void put(BlockPos pos, int type) {
		Color color = switch (type) {
			case TYPE_PISTON -> new Color(255, 165, 0, 165);
			case TYPE_DEEPSLATE -> new Color(100, 120, 255, 150);
			case TYPE_STORAGE -> new Color(0, 255, 100, 145);
			default -> new Color(255, 0, 0, 170);
		};
		String label = switch (type) {
			case TYPE_PISTON -> "Piston";
			case TYPE_DEEPSLATE -> "Deepslate";
			case TYPE_STORAGE -> "Storage";
			default -> "Spawner";
		};
		this.foundBlocks.put(pos, new ClientModuleTools.BlockMark(pos, label, color));
	}

	/** Drops everything more than two chunks outside the scan radius so nothing leaks. */
	public void cleanupDistant() {
		if (mc.player == null) {
			return;
		}
		ChunkPos center = mc.player.chunkPosition();
		int maxDistance = this.scanRadius.getValue() + 2;
		this.scannedChunks.removeIf(pos ->
				Math.abs(pos.x - center.x) > maxDistance || Math.abs(pos.z - center.z) > maxDistance);
		this.foundBlocks.keySet().removeIf(pos -> {
			int chunkX = pos.getX() >> 4;
			int chunkZ = pos.getZ() >> 4;
			return Math.abs(chunkX - center.x) > maxDistance || Math.abs(chunkZ - center.z) > maxDistance;
		});
	}

	@Override
	public void onRender(PoseStack poseStack, float partialTicks) {
		ClientModuleTools.renderBlocks(poseStack, this.foundBlocks.values(), 102400.0);
	}
}
