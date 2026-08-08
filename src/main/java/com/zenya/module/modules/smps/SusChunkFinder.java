package com.zenya.module.modules.smps;

import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zenya.ZenyaClient;
import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;
import com.zenya.utils.RenderUtils;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.lwjgl.opengl.GL11;

/**
 * Flags chunks that look farmed rather than generated: an unnaturally tall run of
 * kelp/vines/sugar cane in one column, a cluster of ripe berries or cocoa, or a geode
 * whose budding amethyst has had every crystal stripped off it.
 *
 * <p>Scanning a chunk walks every block of all 256 columns, so it is spread over ticks
 * ({@link #CHUNKS_PER_TICK} at a time) and each chunk is only ever scanned once — the
 * {@code scanned} set is what stops a re-scan, and {@link #cleanUpFarHits()} drops it
 * again once the chunk is out of range so revisiting the area re-scans it.
 */
public class SusChunkFinder extends Module {
	public static int CHUNKS_PER_TICK = 3;
	public static int RESCAN_TICKS = 400;

	public Setting<Integer> sensitivity;
	public Setting<Integer> simDistance;
	public Setting<Color> overlayColor;
	public Setting<Integer> alpha;
	public Setting<Boolean> detectJungleVines;
	public Setting<Boolean> detectCaveVines;
	public Setting<Boolean> detectKelp;
	public Setting<Boolean> detectSweetBerries;
	public Setting<Boolean> detectSugarCane;
	public Setting<Boolean> detectCocoaBeans;
	public Setting<Boolean> detectAmethyst;
	public Map<ChunkPos, ChunkHit> hits;
	public Set<ChunkPos> scanned;
	public Set<ChunkPos> queued;
	public ArrayDeque<ChunkPos> scanQueue;
	public int rescanTimer;

	public SusChunkFinder() {
		super("Sus Chunk Finder", Category.DONUT);
		this.sensitivity = new Setting<>("Sensitivity", 5, 1, 20);
		this.simDistance = new Setting<>("Sim Distance", 8, 1, 20);
		this.overlayColor = new Setting<>("Color", new Color(220, 30, 30, 160));
		this.alpha = new Setting<>("Alpha", 160, 0, 255);
		this.detectJungleVines = new Setting<>("Jungle Vines", true);
		this.detectCaveVines = new Setting<>("Cave Vines", true);
		this.detectKelp = new Setting<>("Kelp", true);
		this.detectSweetBerries = new Setting<>("Sweet Berries", true);
		this.detectSugarCane = new Setting<>("Sugar Cane", true);
		this.detectCocoaBeans = new Setting<>("Cocoa Beans", true);
		this.detectAmethyst = new Setting<>("Amethyst", true);
		this.hits = new ConcurrentHashMap<>();
		this.scanned = ConcurrentHashMap.newKeySet();
		this.queued = ConcurrentHashMap.newKeySet();
		this.scanQueue = new ArrayDeque<>();
		this.rescanTimer = 0;
		this.setDescription("Finds suspicious chunks via natural block pattern analysis.");
		this.addSetting(this.sensitivity);
		this.addSetting(this.simDistance);
		this.addSetting(this.overlayColor);
		this.addSetting(this.alpha);
		this.addSetting(this.detectJungleVines);
		this.addSetting(this.detectCaveVines);
		this.addSetting(this.detectKelp);
		this.addSetting(this.detectSweetBerries);
		this.addSetting(this.detectSugarCane);
		this.addSetting(this.detectCocoaBeans);
		this.addSetting(this.detectAmethyst);
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

	@Override
	public void onTick() {
		if (mc.level == null || mc.player == null) {
			return;
		}
		if (++this.rescanTimer >= 400) {
			this.cleanUpFarHits();
			this.rescanTimer = 0;
		}
		this.enqueueChunks();
		ChunkPos pos;
		for (int scannedThisTick = 0; scannedThisTick < 3 && (pos = this.scanQueue.poll()) != null; ++scannedThisTick) {
			this.queued.remove(pos);

			// The chunk has to be loaded BEFORE it is marked scanned. The shipped build
			// did `scanned.add(pos) || getChunk(...) == null`, which burns the position
			// on a chunk the client has not received yet — enqueueChunks then skips it
			// forever and the whole area silently never gets scanned.
			LevelChunk chunk = mc.level.getChunkSource().getChunk(pos.x, pos.z, false);
			if (chunk == null) {
				continue;
			}
			if (!this.scanned.add(pos)) {
				continue;
			}
			this.scanChunk(chunk, pos);
		}
	}

	/** Forgets hits and scan bookkeeping outside the working radius so the area re-scans on return. */
	public void cleanUpFarHits() {
		if (mc.player == null) {
			return;
		}
		ChunkPos center = mc.player.chunkPosition();
		int maxDistance = Math.max(this.simDistance.getValue(), this.viewDist()) + 4;
		this.hits.keySet().removeIf(pos -> Math.abs(pos.x - center.x) > maxDistance || Math.abs(pos.z - center.z) > maxDistance);
		this.scanned.removeIf(pos -> Math.abs(pos.x - center.x) > maxDistance || Math.abs(pos.z - center.z) > maxDistance);
		this.queued.removeIf(pos -> Math.abs(pos.x - center.x) > maxDistance || Math.abs(pos.z - center.z) > maxDistance);
	}

	@Override
	public void onRender(PoseStack poseStack, float partialTicks) {
		if (mc.level == null || mc.player == null) {
			return;
		}
		if (this.hits.isEmpty()) {
			return;
		}
		Camera camera = RenderUtils.getCamera();
		if (camera == null) {
			return;
		}
		Vec3 cameraPos = RenderUtils.getCameraPos(camera);
		int[] dims = overlayDims(this.sensitivity.getValue());
		int spanX = dims[0];
		int spanZ = dims[1];
		Color base = this.overlayColor.getValue();
		int clampedAlpha = Math.max(0, Math.min(255, this.alpha.getValue()));
		int fillAlpha = Math.max(20, clampedAlpha / 3);
		Color fill = new Color(base.getRed(), base.getGreen(), base.getBlue(), fillAlpha);

		// Expand every hit into the span of chunks the overlay covers, de-duplicated so
		// neighbouring hits do not draw the same quad twice.
		HashSet<ChunkPos> overlay = new HashSet<>();
		for (ChunkPos hit : this.hits.keySet()) {
			int minChunkX;
			int maxChunkX;
			int minChunkZ;
			int maxChunkZ;
			int before;
			int after;
			if (spanX == 1) {
				minChunkX = maxChunkX = hit.x;
			} else {
				before = spanX / 2;
				after = spanX - 1 - before;
				minChunkX = hit.x - before;
				maxChunkX = hit.x + after;
			}
			if (spanZ == 1) {
				minChunkZ = maxChunkZ = hit.z;
			} else {
				before = spanZ / 2;
				after = spanZ - 1 - before;
				minChunkZ = hit.z - before;
				maxChunkZ = hit.z + after;
			}
			for (int chunkX = minChunkX; chunkX <= maxChunkX; ++chunkX) {
				for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; ++chunkZ) {
					overlay.add(new ChunkPos(chunkX, chunkZ));
				}
			}
		}

		RenderUtils.WorldBatch batch = RenderUtils.beginWorldBatch(poseStack);
		boolean drewAnything = false;
		// Polygon offset keeps the flat slab from z-fighting with terrain at the same height.
		GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
		GL11.glPolygonOffset(-1.0f, -1.0f);
		double y = 63.0 - cameraPos.y;
		for (ChunkPos pos : overlay) {
			try {
				double x = (double) pos.getMinBlockX() - cameraPos.x;
				double z = (double) pos.getMinBlockZ() - cameraPos.z;
				batch.renderFilledBox(x, y, z, x + 16.0, y + 0.15, z + 16.0, fill);
				drewAnything = true;
			} catch (Exception e) {
				// One bad chunk must not abort the rest of the overlay.
				ZenyaClient.LOGGER.warn("Error rendering sus chunk", e);
			}
		}
		GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
		if (drewAnything) {
			try {
				batch.flush();
			} catch (Exception e) {
				// A failed flush is a render-state problem; logging beats crashing the frame.
				ZenyaClient.LOGGER.warn("Error flushing render batch", e);
			}
		}
	}

	/** Queues every not-yet-scanned chunk in range; the tick loop drains it a few at a time. */
	public void enqueueChunks() {
		ChunkPos center = mc.player.chunkPosition();
		int radius = Math.min(this.simDistance.getValue(), this.viewDist());
		for (int dx = -radius; dx <= radius; ++dx) {
			for (int dz = -radius; dz <= radius; ++dz) {
				ChunkPos pos = new ChunkPos(center.x + dx, center.z + dz);
				if (this.scanned.contains(pos) || !this.queued.add(pos)) {
					continue;
				}
				this.scanQueue.offer(pos);
			}
		}
	}

	/**
	 * Walks all 256 columns full height, recording the longest vertical run of each
	 * farmable plant plus the ripe-berry, cocoa and stripped-budding-amethyst counts,
	 * then records the first detector that trips. Detector order is the priority order.
	 */
	public void scanChunk(LevelChunk chunk, ChunkPos chunkPos) {
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos amethystCursor = new BlockPos.MutableBlockPos();
		int originX = chunkPos.getMinBlockX();
		int originZ = chunkPos.getMinBlockZ();
		int minY = mc.level.getMinY();
		int maxY = mc.level.getMaxY();
		int[] kelpRuns = new int[256];
		int[] caveVineRuns = new int[256];
		int[] vineRuns = new int[256];
		int[] sugarCaneRuns = new int[256];
		int ripeBerries = 0;
		int cocoaCount = 0;
		int strippedBudding = 0;
		for (int dx = 0; dx < 16; ++dx) {
			for (int dz = 0; dz < 16; ++dz) {
				int column = dx * 16 + dz;
				int kelpRun = 0;
				int caveVineRun = 0;
				int vineRun = 0;
				int sugarCaneRun = 0;
				int kelpBest = 0;
				int caveVineBest = 0;
				int vineBest = 0;
				int sugarCaneBest = 0;
				for (int y = minY; y <= maxY; ++y) {
					cursor.set(originX + dx, y, originZ + dz);
					BlockState state = chunk.getBlockState(cursor);
					Block block = state.getBlock();
					if (block == Blocks.KELP || block == Blocks.KELP_PLANT) {
						if (++kelpRun > kelpBest) {
							kelpBest = kelpRun;
						}
					} else {
						kelpRun = 0;
					}
					if (block == Blocks.CAVE_VINES || block == Blocks.CAVE_VINES_PLANT) {
						if (++caveVineRun > caveVineBest) {
							caveVineBest = caveVineRun;
						}
					} else {
						caveVineRun = 0;
					}
					if (block == Blocks.VINE) {
						if (++vineRun > vineBest) {
							vineBest = vineRun;
						}
					} else {
						vineRun = 0;
					}
					if (block == Blocks.SUGAR_CANE) {
						if (++sugarCaneRun > sugarCaneBest) {
							sugarCaneBest = sugarCaneRun;
						}
					} else {
						sugarCaneRun = 0;
					}
					if (block == Blocks.SWEET_BERRY_BUSH && state.hasProperty(BlockStateProperties.AGE_3)) {
						try {
							if (state.getValue(BlockStateProperties.AGE_3) >= 3) {
								++ripeBerries;
							}
						} catch (Exception e) {
							// A block state can lose the property between the check and the read; skip that bush.
						}
					}
					if (block == Blocks.COCOA) {
						++cocoaCount;
					}
					if (block != Blocks.BUDDING_AMETHYST || this.hasAmethystGrowthOnAnyFace(amethystCursor, originX + dx, y, originZ + dz)) {
						continue;
					}
					++strippedBudding;
				}
				kelpRuns[column] = kelpBest;
				caveVineRuns[column] = caveVineBest;
				vineRuns[column] = vineBest;
				sugarCaneRuns[column] = sugarCaneBest;
			}
		}

		String reason = null;
		if (this.detectJungleVines.getValue()) {
			for (int run : vineRuns) {
				if (run < 34 || run > 50) {
					continue;
				}
				reason = "Jungle Vines (" + run + " tall)";
				break;
			}
		}
		if (reason == null && this.detectCaveVines.getValue()) {
			for (int run : caveVineRuns) {
				if (run < 7 || run > 50) {
					continue;
				}
				reason = "Cave Vines (" + run + " tall)";
				break;
			}
		}
		if (reason == null && this.detectKelp.getValue()) {
			for (int run : kelpRuns) {
				if (run < 24 || run > 50) {
					continue;
				}
				reason = "Tall Kelp (" + run + " tall)";
				break;
			}
		}
		if (reason == null && this.detectSweetBerries.getValue() && ripeBerries >= 3 && ripeBerries <= 50) {
			reason = "Sweet Berries (" + ripeBerries + ")";
		}
		if (reason == null && this.detectSugarCane.getValue()) {
			for (int run : sugarCaneRuns) {
				if (run < 5 || run > 50) {
					continue;
				}
				reason = "Sugar Cane (" + run + " tall)";
				break;
			}
		}
		if (reason == null && this.detectCocoaBeans.getValue() && cocoaCount > 0) {
			reason = "Cocoa Beans (" + cocoaCount + ")";
		}
		if (reason == null && this.detectAmethyst.getValue() && strippedBudding >= Math.max(1, this.sensitivity.getValue())) {
			reason = "Stripped Geode (" + strippedBudding + " budding)";
		}
		if (reason != null) {
			this.hits.putIfAbsent(chunkPos, new ChunkHit(chunkPos, reason));
		}
	}

	/**
	 * True when the budding amethyst at the given position still has a crystal or bud on
	 * one of its six faces; a geode where this is false everywhere has been farmed.
	 */
	public boolean hasAmethystGrowthOnAnyFace(BlockPos.MutableBlockPos cursor, int x, int y, int z) {
		int[][] faceOffsets = {
				{ 0, 1, 0 },
				{ 0, -1, 0 },
				{ 1, 0, 0 },
				{ -1, 0, 0 },
				{ 0, 0, 1 },
				{ 0, 0, -1 } };
		for (int[] offset : faceOffsets) {
			cursor.set(x + offset[0], y + offset[1], z + offset[2]);
			if (!isAmethystGrowth(mc.level.getBlockState(cursor).getBlock())) {
				continue;
			}
			return true;
		}
		return false;
	}

	public static boolean isAmethystGrowth(Block block) {
		return block == Blocks.AMETHYST_CLUSTER || block == Blocks.LARGE_AMETHYST_BUD
				|| block == Blocks.MEDIUM_AMETHYST_BUD || block == Blocks.SMALL_AMETHYST_BUD;
	}

	/** Overlay footprint in chunks, as {width, depth}. */
	// ponytail: the sensitivity argument is ignored — the overlay is always a single chunk
	public static int[] overlayDims(int sensitivity) {
		return new int[] { 1, 1 };
	}

	public int viewDist() {
		return mc.options == null ? 8 : mc.options.getEffectiveRenderDistance();
	}

	public void clear() {
		this.hits.clear();
		this.scanned.clear();
		this.queued.clear();
		this.scanQueue.clear();
		this.rescanTimer = 0;
	}

	/** One flagged chunk and the detector text explaining why. */
	public record ChunkHit(ChunkPos pos, String reason) {
	}
}
