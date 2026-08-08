package com.zenya.module.modules.render;

import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.BlocksSetting;
import com.zenya.setting.Setting;
import com.zenya.utils.RenderUtils;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders coloured boxes around the block types picked in the block picker.
 *
 * <p>A full chunk scan walks every section, so the work is spread out: chunks go on
 * {@link #scanQueue} and only {@link #CHUNKS_PER_TICK} come off it per tick, with block
 * update packets jumping the queue. {@link #cachedBlocks} keeps the hits per chunk and
 * {@link #posTypeMap} the type per position, so the render path never reads the world.
 *
 * <p>{@link #queueLock} guards the queue and its dedup set because chunk packets are
 * handled off the client thread.
 */
public class BlockESP extends Module {
	public static int RESCAN_INTERVAL_TICKS = 40;
	public static int CHUNKS_PER_TICK = 6;
	public static double BOX_INSET = 0.0625;

	public BlocksSetting blocks;
	public Setting<Integer> opacity;
	public Setting<Boolean> esp;
	public Map<Long, Set<BlockPos>> cachedBlocks;
	public Map<BlockPos, Block> posTypeMap;
	public ArrayDeque<Long> scanQueue;
	public Set<Long> queuedChunks;
	public Object queueLock;
	public volatile Set<Block> targets;
	public long lastBlocksVersion;
	public int tickCounter;
	public boolean fullRescanRequested;
	public ChunkPos lastCenterChunk;
	public int lastChunkRadius;

	public BlockESP() {
		super("Block ESP", Category.RENDER);
		this.blocks = new BlocksSetting("Blocks");
		this.opacity = new Setting<>("Alpha", 110, 1, 255);
		this.esp = new Setting<>("ESP", true);
		this.cachedBlocks = new ConcurrentHashMap<>();
		this.posTypeMap = new ConcurrentHashMap<>();
		this.scanQueue = new ArrayDeque<>();
		this.queuedChunks = new HashSet<>();
		this.queueLock = new Object();
		this.targets = Collections.emptySet();
		this.lastBlocksVersion = -1L;
		this.tickCounter = 0;
		this.fullRescanRequested = true;
		this.lastChunkRadius = -1;
		this.setDescription("Renders coloured boxes around any block types you select in nearby loaded chunks.");
		this.addSetting(this.blocks);
		this.addSetting(this.opacity);
		this.addSetting(this.esp);
	}

	@Override
	public void toggle() {
		this.setEnabled(!this.isEnabled());
	}

	public Color getCustomBlockColor(Block block) {
		return this.blocks.getColor(block);
	}

	public void setCustomBlockColor(Block block, Color color) {
		this.blocks.setColor(block, color);
	}

	public Map<Block, Color> getColorMap() {
		return this.blocks.getColors();
	}

	@Override
	public void onEnable() {
		this.clearCaches();
		this.lastBlocksVersion = -1L;
		this.fullRescanRequested = true;
		this.tickCounter = 0;
		this.lastCenterChunk = null;
		this.lastChunkRadius = -1;
	}

	@Override
	public void onDisable() {
		this.clearCaches();
		this.lastCenterChunk = null;
		this.lastChunkRadius = -1;
	}

	@Override
	public void onWorldChange() {
		this.clearCaches();
		this.fullRescanRequested = true;
		this.tickCounter = 0;
		this.lastCenterChunk = null;
		this.lastChunkRadius = -1;
	}

	@Override
	public void onTick() {
		if (mc.level == null || mc.player == null) {
			return;
		}
		this.updateTargets();
		if (this.targets.isEmpty()) {
			this.clearCaches();
			return;
		}
		this.tickCounter++;
		ChunkPos center = mc.player.chunkPosition();
		int radius = this.getChunkRadius();
		boolean rescan = this.fullRescanRequested || this.tickCounter % RESCAN_INTERVAL_TICKS == 0;
		// A move to another chunk or a render distance change invalidates the queue just as
		// hard as the periodic rescan does.
		if (rescan || this.lastCenterChunk == null || !this.lastCenterChunk.equals(center) || this.lastChunkRadius != radius) {
			this.rebuildLoadedChunkQueue(rescan);
			this.fullRescanRequested = false;
			this.lastCenterChunk = center;
			this.lastChunkRadius = radius;
		}
		for (int scanned = 0; scanned < CHUNKS_PER_TICK; ++scanned) {
			Long packedChunkPos;
			synchronized (this.queueLock) {
				packedChunkPos = this.scanQueue.poll();
				if (packedChunkPos != null) {
					this.queuedChunks.remove(packedChunkPos);
				}
			}
			if (packedChunkPos == null) {
				break;
			}
			int chunkX = ChunkPos.getX(packedChunkPos);
			int chunkZ = ChunkPos.getZ(packedChunkPos);
			LevelChunk chunk = mc.level.getChunkSource().getChunk(chunkX, chunkZ, false);
			if (chunk == null) {
				continue;
			}
			this.scanChunk(chunk);
		}
	}

	@Override
	public void onPacketReceive(Packet packet) {
		if (mc.level == null) {
			return;
		}
		if (packet instanceof ClientboundLevelChunkWithLightPacket chunkPacket) {
			this.queueChunk(ChunkPos.asLong(chunkPacket.getX(), chunkPacket.getZ()), true);
			return;
		}
		if (packet instanceof ClientboundSectionBlocksUpdatePacket sectionPacket) {
			sectionPacket.runUpdates((pos, state) -> this.queueChunk(new ChunkPos(pos).toLong(), true));
			return;
		}
		if (packet instanceof ClientboundBlockUpdatePacket blockPacket) {
			this.queueChunk(new ChunkPos(blockPacket.getPos()).toLong(), true);
		}
	}

	@Override
	public void onRender(PoseStack poseStack, float partialTicks) {
		if (mc.level == null || mc.player == null || this.cachedBlocks.isEmpty()) {
			return;
		}
		if (!this.esp.getValue()) {
			return;
		}
		Set<Block> selected = this.targets;
		if (selected.isEmpty()) {
			return;
		}
		Camera camera = RenderUtils.getCamera();
		if (camera == null) {
			return;
		}
		int alpha = Math.max(1, Math.min(255, this.opacity.getValue()));
		Vec3 cameraPos = RenderUtils.getCameraPos(camera);
		double cameraX = cameraPos.x;
		double cameraY = cameraPos.y;
		double cameraZ = cameraPos.z;
		double maxDistanceSq = this.getMaxRenderDistanceSq();
		double playerX = mc.player.getX();
		double playerY = mc.player.getY();
		double playerZ = mc.player.getZ();
		RenderUtils.WorldBatch batch = RenderUtils.beginWorldBatch(poseStack);
		boolean drewAnything = false;
		Block lastBlock = null;
		Color lastColor = null;
		for (Set<BlockPos> chunkBlocks : this.cachedBlocks.values()) {
			for (BlockPos pos : chunkBlocks) {
				if (pos.distToLowCornerSqr(playerX, playerY, playerZ) > maxDistanceSq) {
					continue;
				}
				Block block = this.posTypeMap.get(pos);
				if (block == null) {
					BlockState state = mc.level.getBlockState(pos);
					block = state.getBlock();
					this.posTypeMap.put(pos, block);
				}
				if (!selected.contains(block)) {
					continue;
				}
				// The cache is grouped per chunk, so runs of the same block type are common —
				// remember the last colour instead of resolving one per box.
				if (block != lastBlock || lastColor == null) {
					lastBlock = block;
					lastColor = this.getBlockColor(block, alpha);
				}
				double minX = pos.getX() - cameraX;
				double minY = pos.getY() - cameraY;
				double minZ = pos.getZ() - cameraZ;
				batch.renderFilledBox(minX + BOX_INSET, minY + BOX_INSET, minZ + BOX_INSET,
						minX + 1.0 - BOX_INSET, minY + 1.0 - BOX_INSET, minZ + 1.0 - BOX_INSET, lastColor);
				Color outline = new Color(lastColor.getRed(), lastColor.getGreen(), lastColor.getBlue(),
						Math.min(255, lastColor.getAlpha() + 60));
				batch.renderOutlineBox(minX + BOX_INSET, minY + BOX_INSET, minZ + BOX_INSET,
						minX + 1.0 - BOX_INSET, minY + 1.0 - BOX_INSET, minZ + 1.0 - BOX_INSET, outline);
				drewAnything = true;
			}
		}
		if (drewAnything) {
			batch.flush();
		}
	}

	/** Re-snapshots the picked blocks, but only when the picker's version moved on. */
	public void updateTargets() {
		long version = this.blocks.getVersion();
		if (version == this.lastBlocksVersion) {
			return;
		}
		this.lastBlocksVersion = version;
		this.targets = Set.copyOf(this.blocks.getSelectedBlocks());
		this.clearCaches();
		this.fullRescanRequested = true;
	}

	public boolean isSelected(Block block) {
		this.updateTargets();
		return this.blocks.contains(block);
	}

	public int getSelectedCount() {
		this.updateTargets();
		return this.blocks.size();
	}

	/**
	 * Refills the scan queue with the loaded chunks in render distance, nearest first.
	 * Chunks that already have a cache entry are skipped unless {@code forceRescan}.
	 */
	public void rebuildLoadedChunkQueue(boolean forceRescan) {
		if (mc.level == null || mc.player == null) {
			return;
		}
		int radius = this.getChunkRadius();
		ChunkPos center = mc.player.chunkPosition();
		List<LevelChunk> loadedChunks = new ArrayList<>();
		Set<Long> loadedKeys = new HashSet<>();
		for (int offsetX = -radius; offsetX <= radius; ++offsetX) {
			for (int offsetZ = -radius; offsetZ <= radius; ++offsetZ) {
				LevelChunk chunk = mc.level.getChunkSource().getChunk(center.x + offsetX, center.z + offsetZ, false);
				if (chunk == null) {
					continue;
				}
				loadedChunks.add(chunk);
				loadedKeys.add(chunk.getPos().toLong());
			}
		}
		loadedChunks.sort(Comparator.comparingInt(chunk -> this.getChunkDistanceSq(center, chunk.getPos())));
		synchronized (this.queueLock) {
			this.scanQueue.removeIf(packedChunkPos -> !loadedKeys.contains(packedChunkPos));
			this.queuedChunks.retainAll(loadedKeys);
			for (LevelChunk chunk : loadedChunks) {
				long packedChunkPos = chunk.getPos().toLong();
				if ((forceRescan || !this.cachedBlocks.containsKey(packedChunkPos)) && this.queuedChunks.add(packedChunkPos)) {
					this.scanQueue.addLast(packedChunkPos);
				}
			}
		}
		this.pruneOutOfRange(center, radius);
	}

	/** Queues a chunk for scanning; {@code priority} moves it to the front, ahead of the sweep. */
	public void queueChunk(long packedChunkPos, boolean priority) {
		synchronized (this.queueLock) {
			if (priority && this.queuedChunks.contains(packedChunkPos)) {
				this.scanQueue.remove(packedChunkPos);
				this.scanQueue.addFirst(packedChunkPos);
				return;
			}
			if (!this.queuedChunks.add(packedChunkPos)) {
				return;
			}
			if (priority) {
				this.scanQueue.addFirst(packedChunkPos);
			} else {
				this.scanQueue.add(packedChunkPos);
			}
		}
	}

	/** Rebuilds one chunk's cache entry; a chunk with no hits loses its entry entirely. */
	public void scanChunk(LevelChunk chunk) {
		Set<Block> selected = this.targets;
		if (selected.isEmpty()) {
			return;
		}
		int worldMinY = mc.level.getMinY();
		int worldMaxY = worldMinY + mc.level.getHeight();
		int minSectionY = mc.level.getMinSectionY();
		ChunkPos chunkPos = chunk.getPos();
		long packedChunkPos = chunkPos.toLong();
		Set<BlockPos> previous = this.cachedBlocks.get(packedChunkPos);
		Set<BlockPos> found = new HashSet<>();
		LevelChunkSection[] sections = chunk.getSections();
		for (int sectionIndex = 0; sectionIndex < sections.length; ++sectionIndex) {
			LevelChunkSection section = sections[sectionIndex];
			if (section == null || section.hasOnlyAir()) {
				continue;
			}
			int sectionMinY = (minSectionY + sectionIndex) * 16;
			if (sectionMinY + 16 <= worldMinY || sectionMinY >= worldMaxY) {
				continue;
			}
			for (int x = 0; x < 16; ++x) {
				for (int z = 0; z < 16; ++z) {
					for (int y = 0; y < 16; ++y) {
						BlockState state = section.getBlockState(x, y, z);
						Block block = state.getBlock();
						if (!selected.contains(block)) {
							continue;
						}
						BlockPos pos = new BlockPos(chunkPos.getMinBlockX() + x, sectionMinY + y, chunkPos.getMinBlockZ() + z);
						found.add(pos);
						this.posTypeMap.put(pos, block);
					}
				}
			}
		}
		// Positions that dropped out of this chunk keep no type entry behind them.
		if (previous != null) {
			for (BlockPos pos : previous) {
				if (found.contains(pos)) {
					continue;
				}
				this.posTypeMap.remove(pos);
			}
		}
		if (found.isEmpty()) {
			this.removeChunkCache(packedChunkPos);
			return;
		}
		this.cachedBlocks.put(packedChunkPos, found);
	}

	public int getChunkDistanceSq(ChunkPos from, ChunkPos to) {
		int deltaX = to.x - from.x;
		int deltaZ = to.z - from.z;
		return deltaX * deltaX + deltaZ * deltaZ;
	}

	public int getChunkRadius() {
		return mc.options.getEffectiveRenderDistance();
	}

	public double getMaxRenderDistanceSq() {
		double range = this.getChunkRadius() * 16.0 + 16.0;
		return range * range;
	}

	/** Drops cached chunks outside the square of {@code radius} chunks around {@code center}. */
	public void pruneOutOfRange(ChunkPos center, int radius) {
		List<Long> expired = new ArrayList<>();
		for (Long packedChunkPos : this.cachedBlocks.keySet()) {
			ChunkPos chunkPos = new ChunkPos(ChunkPos.getX(packedChunkPos), ChunkPos.getZ(packedChunkPos));
			if (Math.abs(chunkPos.x - center.x) <= radius && Math.abs(chunkPos.z - center.z) <= radius) {
				continue;
			}
			expired.add(packedChunkPos);
		}
		for (Long packedChunkPos : expired) {
			this.removeChunkCache(packedChunkPos);
		}
	}

	public void removeChunkCache(long packedChunkPos) {
		Set<BlockPos> removed = this.cachedBlocks.remove(packedChunkPos);
		if (removed == null) {
			return;
		}
		for (BlockPos pos : removed) {
			this.posTypeMap.remove(pos);
		}
	}

	/**
	 * The picker's colour for the block, or a stable colour derived from its registry id
	 * hash, floored at 80 per channel so nothing comes out near-black against terrain.
	 */
	public Color getBlockColor(Block block, int alpha) {
		Color custom = this.blocks.getColor(block);
		if (custom != null) {
			return new Color(custom.getRed(), custom.getGreen(), custom.getBlue(), alpha);
		}
		Identifier id = BuiltInRegistries.BLOCK.getKey(block);
		int hash = id.toString().hashCode();
		int red = (hash & 0xFF0000) >> 16;
		int green = (hash & 0xFF00) >> 8;
		int blue = hash & 0xFF;
		red = Math.max(80, red);
		green = Math.max(80, green);
		blue = Math.max(80, blue);
		return new Color(red, green, blue, alpha);
	}

	public void clearCaches() {
		this.cachedBlocks.clear();
		this.posTypeMap.clear();
		synchronized (this.queueLock) {
			this.scanQueue.clear();
			this.queuedChunks.clear();
		}
	}
}
