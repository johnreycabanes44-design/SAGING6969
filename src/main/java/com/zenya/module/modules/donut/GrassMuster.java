package com.zenya.module.modules.donut;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.module.modules.common.ClientModuleTools;
import com.zenya.setting.Setting;
import com.zenya.utils.RenderUtils;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Flags chunks holding an abnormal amount of tall grass: a base is usually built in
 * cleared ground surrounded by whatever was never touched, so a dense pocket next to
 * thinned-out neighbours reads as somebody's back garden.
 *
 * <p>Counting every block of a full-height chunk is far too slow for one tick, so the
 * work is spread out: {@link #queue} is what is left to visit and {@link #queueCursor}
 * how far through it we got, both rebuilt every 200 ticks or as soon as the queue runs
 * dry. {@link #scanned} stops a chunk being counted twice, and it and {@link #chunkScores}
 * are pruned to the scan radius so neither can grow without bound.
 */
public class GrassMuster extends Module {
	public Setting<Integer> scanRadius;
	public Setting<Integer> minGrass;
	public Setting<Integer> sensitivity;
	public Setting<Boolean> fillChunks;
	public Setting<Boolean> chatNotify;
	public Map<ChunkPos, Integer> chunkScores;
	public Set<ChunkPos> scanned;
	public List<ChunkPos> queue;
	public int queueCursor;
	public int tickCounter;

	public GrassMuster() {
		super("Grass Muster", Category.DONUT);
		this.scanRadius = new Setting<>("Scan Radius", 8, 1, 32);
		this.minGrass = new Setting<>("Min Grass", 140, 130, 150);
		this.sensitivity = new Setting<>("Sensitivity", 5, 1, 20);
		this.fillChunks = new Setting<>("Fill Chunks", true);
		this.chatNotify = new Setting<>("Chat Alert", false);
		this.chunkScores = new ConcurrentHashMap<>();
		this.scanned = ConcurrentHashMap.newKeySet();
		this.queue = new ArrayList<>();
		this.queueCursor = 0;
		this.tickCounter = 0;
		this.setDescription("Marks chunks with abnormally dense tall grass — typical base surroundings.");
		this.addSetting(this.scanRadius);
		this.addSetting(this.minGrass);
		this.addSetting(this.sensitivity);
		this.addSetting(this.fillChunks);
		this.addSetting(this.chatNotify);
	}

	@Override
	public void onEnable() {
		this.clear();
	}

	@Override
	public void onDisable() {
		this.clear();
	}

	@Override
	public void onWorldChange() {
		this.clear();
	}

	/** Drops every result and restarts the scan from an empty queue. */
	public void clear() {
		this.chunkScores.clear();
		this.scanned.clear();
		this.queue.clear();
		this.queueCursor = 0;
		this.tickCounter = 0;
	}

	@Override
	public void onTick() {
		if (mc.level == null || mc.player == null) {
			return;
		}

		// Rebuild the work list every 200 ticks (or the moment it runs dry) so chunks the
		// player has walked towards get queued, and forget what has drifted out of range.
		if (++this.tickCounter >= 200 || this.queue.isEmpty()) {
			this.tickCounter = 0;
			this.queue.clear();
			this.queueCursor = 0;

			ChunkPos center = mc.player.chunkPosition();
			int radius = this.scanRadius.getValue();

			for (int chunkX = center.x - radius; chunkX <= center.x + radius; ++chunkX) {
				for (int chunkZ = center.z - radius; chunkZ <= center.z + radius; ++chunkZ) {
					ChunkPos candidate = new ChunkPos(chunkX, chunkZ);

					if (this.scanned.contains(candidate)) {
						continue;
					}

					this.queue.add(candidate);
				}
			}

			int keepRadius = radius + 3;
			this.scanned.removeIf(pos -> Math.abs(pos.x - center.x) > keepRadius || Math.abs(pos.z - center.z) > keepRadius);
			this.chunkScores.keySet().removeIf(pos -> Math.abs(pos.x - center.x) > keepRadius || Math.abs(pos.z - center.z) > keepRadius);
		}

		int budget = Math.max(1, this.sensitivity.getValue() / 4);

		while (budget-- > 0 && this.queueCursor < this.queue.size()) {
			ChunkPos pos = this.queue.get(this.queueCursor++);

			if (!this.scanned.add(pos)) {
				continue;
			}

			LevelChunk chunk = mc.level.getChunkSource().getChunk(pos.x, pos.z, false);

			if (chunk == null) {
				continue;
			}

			this.scanChunk(chunk, pos);
		}
	}

	/**
	 * Counts the tall grass in the whole chunk column and records the total, or clears a
	 * stale entry when the chunk no longer clears {@link #minGrass}.
	 */
	public void scanChunk(LevelChunk chunk, ChunkPos pos) {
		if (mc.level == null) {
			return;
		}

		int grassCount = 0;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		int minY = mc.level.getMinY();
		int maxY = mc.level.getMaxY();

		for (int offsetX = 0; offsetX < 16; ++offsetX) {
			for (int offsetZ = 0; offsetZ < 16; ++offsetZ) {
				for (int y = minY; y <= maxY; ++y) {
					cursor.set(pos.getMinBlockX() + offsetX, y, pos.getMinBlockZ() + offsetZ);
					BlockState state = chunk.getBlockState(cursor);

					if (!isTallGrass(state.getBlock())) {
						continue;
					}

					++grassCount;
				}
			}
		}

		int threshold = this.minGrass.getValue();

		if (grassCount < threshold) {
			this.chunkScores.remove(pos);
			return;
		}

		this.chunkScores.put(pos, grassCount);

		if (this.chatNotify.getValue()) {
			ClientModuleTools.chat("Grass Muster", "Flagged chunk " + pos.x + "," + pos.z + " — " + grassCount + " tall grass");
		}
	}

	public static boolean isTallGrass(Block block) {
		return block == Blocks.TALL_GRASS || block == Blocks.SHORT_GRASS
				|| block == Blocks.FERN || block == Blocks.LARGE_FERN;
	}

	@Override
	public void onRender(PoseStack poseStack, float partialTicks) {
		if (mc.level == null || mc.player == null || this.chunkScores.isEmpty()) {
			return;
		}

		Camera camera = RenderUtils.getCamera();

		if (camera == null) {
			return;
		}

		Vec3 cameraPos = RenderUtils.getCameraPos(camera);
		// The densest chunk on screen is the top of the scale, so the shading is relative
		// to whatever is currently flagged rather than to a fixed count.
		int topScore = this.chunkScores.values().stream().mapToInt(Integer::intValue).max().orElse(this.minGrass.getValue());
		int threshold = this.minGrass.getValue();
		RenderUtils.WorldBatch batch = RenderUtils.beginWorldBatch(poseStack);
		boolean drewAnything = false;

		for (Map.Entry<ChunkPos, Integer> entry : this.chunkScores.entrySet()) {
			ChunkPos pos = entry.getKey();
			int score = entry.getValue();

			if (score < threshold) {
				continue;
			}

			double density = Math.min(1.0, (double) (score - threshold) / (double) Math.max(1, topScore - threshold));
			int fillAlpha = (int) (100.0 + density * 60.0);
			Color fill = new Color(0, 200, 50, fillAlpha);
			Color outline = new Color(0, 240, 70, 200);

			// The marker is an 80x80 slab centred on the chunk, not a single chunk footprint,
			// so a flagged area stays visible from a long way off.
			double centerX = (double) pos.getMinBlockX() + 8.0;
			double centerZ = (double) pos.getMinBlockZ() + 8.0;
			double minX = centerX - 40.0 - cameraPos.x;
			double minZ = centerZ - 40.0 - cameraPos.z;
			double maxX = centerX + 40.0 - cameraPos.x;
			double maxZ = centerZ + 40.0 - cameraPos.z;
			double surface = (double) ClientModuleTools.surfaceY(pos.getMiddleBlockX(), pos.getMiddleBlockZ()) - cameraPos.y;
			double baseY = surface + 0.02;
			double topY = surface + 1.02;

			if (this.fillChunks.getValue()) {
				batch.renderFilledBox(minX, baseY, minZ, maxX, topY, maxZ, fill);
			}

			batch.renderOutlineBox(minX, baseY, minZ, maxX, topY, maxZ, outline);
			drewAnything = true;
		}

		if (drewAnything) {
			batch.flush();
		}
	}

	/** Colour ramp over a 0..1 density, with the alpha scaled from half of the base to all of it. */
	public static Color heatmapColor(double density, int baseAlpha) {
		int red = (int) (10.0 + density * 150.0);
		int green = (int) (10.0 + density * 210.0);
		int blue = (int) (60.0 + density * 195.0);

		red = Math.min(255, red);
		green = Math.min(255, green);
		blue = Math.min(255, blue);

		int alpha = (int) ((double) baseAlpha * (0.5 + 0.5 * density));

		return new Color(red, green, blue, Math.min(255, alpha));
	}
}
