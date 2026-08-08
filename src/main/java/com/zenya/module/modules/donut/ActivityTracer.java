package com.zenya.module.modules.donut;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.module.modules.common.ClientModuleTools;
import com.zenya.setting.Setting;
import com.zenya.utils.RenderUtils;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guesses where a base is from how much tall grass a chunk still has — a chunk nobody
 * ever walked through keeps its grass, so an unusually bare one is suspicious. Nothing
 * is sent to the server, the scan only reads chunks the client already has.
 *
 * <p>A full chunk scan is expensive, so the work is spread out: {@link #queue} is rebuilt
 * every 150 ticks and drained a few chunks per tick, and {@link #scanned} makes sure a
 * chunk is only ever inspected once. Both, plus {@link #notifiedChunks}, are pruned to
 * view distance + 16 so they cannot grow without bound while the player travels.
 */
public class ActivityTracer extends Module {
	/** Render geometry of the marker boxes; the literals are inlined at the call sites. */
	public static final double THICKNESS = 0.06;
	public static final double CENTER_Y = 69.0;
	public static final double OUTER_Y = 63.0;
	public static final double CENTER_HALF = 16.0;
	public static final double RING_WIDTH = 4.0;

	public Setting<Integer> sensitivity;
	public Setting<Color> color;
	public Setting<Integer> transparency;
	public Setting<Color> basePlateColor;
	public Setting<Integer> basePlateAlpha;
	public Setting<Boolean> soundAlert;
	public Set<ChunkPos> scanned;
	public Map<ChunkPos, Detection> detections;
	public List<ChunkPos> queue;
	public int queueCursor;
	public int tickCounter;
	public Set<ChunkPos> notifiedChunks;

	/** Player chunk at the last requeue, so a drained queue does not rebuild on the spot. */
	private ChunkPos lastRebuildCenter;

	public ActivityTracer() {
		super("Silent Chunk Finder", Category.DONUT);
		this.sensitivity = new Setting<>("Sensitivity", 280, 160, 700);
		this.color = new Setting<>("Color", new Color(30, 230, 80));
		this.transparency = new Setting<>("Transparency", 140, 0, 255);
		this.basePlateColor = new Setting<>("Base Plate Color", new Color(20, 40, 160));
		this.basePlateAlpha = new Setting<>("Base Plate Alpha", 60, 0, 255);
		this.soundAlert = new Setting<>("Sound Alert", true);
		this.scanned = ConcurrentHashMap.newKeySet();
		this.detections = new ConcurrentHashMap<>();
		this.queue = new ArrayList<>();
		this.queueCursor = 0;
		this.tickCounter = 0;
		this.notifiedChunks = ConcurrentHashMap.newKeySet();
		this.setDescription("Silently detects likely base chunks from grass density and renders a fixed predicted area.");
		this.addSetting(this.sensitivity);
		this.addSetting(this.color);
		this.addSetting(this.transparency);
		this.addSetting(this.basePlateColor);
		this.addSetting(this.basePlateAlpha);
		this.addSetting(this.soundAlert);
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

	public void clear() {
		this.scanned.clear();
		this.detections.clear();
		this.queue.clear();
		this.queueCursor = 0;
		this.tickCounter = 0;
		// Forget where the last requeue happened, so a re-enable in the same chunk rebuilds.
		this.lastRebuildCenter = null;
		this.notifiedChunks.clear();
	}

	/** Maps a raw grass count onto the confidence percentage shown to the user. */
	public static int getProbabilityPercent(int grassCount) {
		if (grassCount >= 150) {
			return 100;
		}
		if (grassCount >= 140) {
			return lerp(grassCount, 140, 150, 95, 100);
		}
		if (grassCount >= 130) {
			return lerp(grassCount, 130, 140, 90, 95);
		}
		if (grassCount >= 120) {
			return lerp(grassCount, 120, 130, 85, 90);
		}
		if (grassCount >= 110) {
			return lerp(grassCount, 110, 120, 78, 85);
		}
		if (grassCount >= 90) {
			return lerp(grassCount, 90, 110, 68, 78);
		}
		if (grassCount >= 70) {
			return lerp(grassCount, 70, 90, 58, 68);
		}
		return 50;
	}

	/** Rescales {@code value} from [inMin, inMax] onto [outMin, outMax], rounding to the nearest int. */
	public static int lerp(int value, int inMin, int inMax, int outMin, int outMax) {
		if (inMax == inMin) {
			return outMin;
		}
		return outMin + (int) Math.round((double) (value - inMin) / (double) (inMax - inMin) * (double) (outMax - outMin));
	}

	@Override
	public void onTick() {
		if (mc.level == null || mc.player == null) {
			return;
		}
		int viewDistance = ClientModuleTools.viewDistanceChunks();
		ChunkPos playerChunk = mc.player.chunkPosition();

		// Every 150 ticks, or as soon as the queue runs dry after the player has moved to a
		// new chunk. The shipped build retriggered on `queue.isEmpty()` alone, and once
		// everything in range was scanned the queue stayed empty forever - so it cleared the
		// queue, swept three sets and re-walked the whole (2r+1)^2 grid every single tick
		// while producing nothing.
		if ((this.tickCounter += 1) >= 150 || (this.queue.isEmpty() && !playerChunk.equals(this.lastRebuildCenter))) {
			this.tickCounter = 0;
			this.lastRebuildCenter = playerChunk;
			this.queue.clear();
			this.queueCursor = 0;
			ChunkPos center = playerChunk;
			int keepRadius = viewDistance + 16;
			this.scanned.removeIf(pos -> Math.abs(pos.x - center.x) > keepRadius || Math.abs(pos.z - center.z) > keepRadius);
			this.detections.keySet().removeIf(pos -> Math.abs(pos.x - center.x) > keepRadius || Math.abs(pos.z - center.z) > keepRadius);
			this.notifiedChunks.removeIf(pos -> Math.abs(pos.x - center.x) > keepRadius || Math.abs(pos.z - center.z) > keepRadius);
			for (int x = center.x - viewDistance; x <= center.x + viewDistance; ++x) {
				for (int z = center.z - viewDistance; z <= center.z + viewDistance; ++z) {
					ChunkPos pos = new ChunkPos(x, z);
					if (this.scanned.contains(pos)) {
						continue;
					}
					this.queue.add(pos);
				}
			}
		}
		int budget = 6;
		while (budget-- > 0 && this.queueCursor < this.queue.size()) {
			ChunkPos pos = this.queue.get(this.queueCursor++);

			// Load check first: marking a not-yet-received chunk as scanned (as the shipped
			// build did) drops it permanently, since the requeue above skips anything
			// already in `scanned`.
			LevelChunk chunk = mc.level.getChunkSource().getChunk(pos.x, pos.z, false);
			if (chunk == null) {
				continue;
			}
			if (!this.scanned.add(pos)) {
				continue;
			}
			this.scanChunk(chunk, pos);
		}
		for (Detection detection : this.detections.values()) {
			if (detection.score() < this.sensitivity.getValue() / 2 || !this.notifiedChunks.add(detection.chunk())) {
				continue;
			}
			this.notify(detection.chunk());
		}
	}

	/**
	 * Counts tall grass in the whole column range of one chunk and records a detection if
	 * the count clears the sensitivity threshold, or clears the lower cluster threshold
	 * while forming a contiguous patch.
	 */
	public void scanChunk(LevelChunk chunk, ChunkPos chunkPos) {
		if (mc.level == null) {
			return;
		}
		int minY = mc.level.getMinY();
		int maxY = mc.level.getMaxY();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		List<BlockPos> grassPositions = new ArrayList<>();
		int grassCount = 0;
		for (int dx = 0; dx < 16; ++dx) {
			for (int dz = 0; dz < 16; ++dz) {
				for (int y = minY; y <= maxY; ++y) {
					cursor.set(chunkPos.getMinBlockX() + dx, y, chunkPos.getMinBlockZ() + dz);
					BlockState state = chunk.getBlockState(cursor);
					if (!isTallGrass(state.getBlock())) {
						continue;
					}
					grassPositions.add(cursor.immutable());
					++grassCount;
				}
			}
		}
		boolean contiguous = checkEightContiguous(grassPositions);
		int threshold = this.sensitivity.getValue();
		int clusterThreshold = Math.max(140, (int) Math.round(threshold * 0.65));
		if (grassCount < threshold && (!contiguous || grassCount < clusterThreshold)) {
			return;
		}
		int score = grassCount;
		// Keep whichever reading was stronger: a partly loaded chunk must not downgrade an earlier hit.
		this.detections.compute(chunkPos, (key, existing) -> {
			if (existing != null && existing.score() >= score) {
				return existing;
			}
			return new Detection(chunkPos, score, System.currentTimeMillis());
		});
	}

	/** Plays the alert sound. The chunk is only part of the signature; the sound is not positional. */
	public void notify(ChunkPos chunk) {
		if (this.soundAlert.getValue() && mc.player != null) {
			mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
		}
	}

	/** True as soon as a flood fill over the given positions reaches eight connected blocks. */
	public static boolean checkEightContiguous(List<BlockPos> positions) {
		if (positions.size() < 8) {
			return false;
		}
		Set<BlockPos> lookup = new HashSet<>(positions);
		Set<BlockPos> visited = new HashSet<>();
		for (BlockPos start : positions) {
			if (visited.contains(start)) {
				continue;
			}
			int reached = 0;
			List<BlockPos> stack = new ArrayList<>();
			stack.add(start);
			visited.add(start);
			while (!stack.isEmpty()) {
				BlockPos current = stack.remove(stack.size() - 1);
				if (++reached >= 8) {
					return true;
				}
				for (Direction direction : Direction.values()) {
					BlockPos neighbour = current.relative(direction);
					if (!lookup.contains(neighbour) || !visited.add(neighbour)) {
						continue;
					}
					stack.add(neighbour);
				}
			}
		}
		return false;
	}

	public static boolean isTallGrass(Block block) {
		return block == Blocks.TALL_GRASS || block == Blocks.SHORT_GRASS || block == Blocks.FERN || block == Blocks.LARGE_FERN;
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
		int renderDistance = mc.options == null ? 12 : mc.options.getEffectiveRenderDistance();
		this.renderBasePlate(poseStack, cameraPos, renderDistance);
		if (this.detections.isEmpty()) {
			return;
		}
		for (DetectionGroup group : this.buildDetectionGroups()) {
			this.renderPredictedArea(poseStack, cameraPos, group, renderDistance);
		}
	}

	/** Merges detections in touching chunks into one bounding group carrying the best score. */
	public List<DetectionGroup> buildDetectionGroups() {
		List<Detection> pending = new ArrayList<>(this.detections.values());
		List<DetectionGroup> groups = new ArrayList<>();
		while (!pending.isEmpty()) {
			Detection seed = pending.remove(pending.size() - 1);
			List<Detection> cluster = new ArrayList<>();
			cluster.add(seed);
			int minX = seed.chunk().x;
			int maxX = seed.chunk().x;
			int minZ = seed.chunk().z;
			int maxZ = seed.chunk().z;
			int score = seed.score();
			// cluster grows while we walk it, so newly absorbed members get their own neighbour sweep
			for (int i = 0; i < cluster.size(); ++i) {
				Detection current = cluster.get(i);
				for (int j = pending.size() - 1; j >= 0; --j) {
					Detection candidate = pending.get(j);
					if (!touches(current.chunk(), candidate.chunk())) {
						continue;
					}
					pending.remove(j);
					cluster.add(candidate);
					minX = Math.min(minX, candidate.chunk().x);
					maxX = Math.max(maxX, candidate.chunk().x);
					minZ = Math.min(minZ, candidate.chunk().z);
					maxZ = Math.max(maxZ, candidate.chunk().z);
					score = Math.max(score, candidate.score());
				}
			}
			groups.add(new DetectionGroup(minX, maxX, minZ, maxZ, score));
		}
		return groups;
	}

	/** True when the two chunks are the same or diagonal neighbours. */
	public static boolean touches(ChunkPos a, ChunkPos b) {
		return Math.abs(a.x - b.x) <= 1 && Math.abs(a.z - b.z) <= 1;
	}

	/** Flat slab under the whole loaded area, so the marker rings have something to read against. */
	public void renderBasePlate(PoseStack poseStack, Vec3 cameraPos, int renderDistance) {
		Color base = this.basePlateColor.getValue();
		int alpha = Math.max(0, Math.min(255, this.basePlateAlpha.getValue()));
		Color fill = new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);
		Color outline = new Color(base.getRed(), base.getGreen(), base.getBlue(), Math.min(255, alpha + 60));
		double half = renderDistance * 16.0;
		double x = mc.player.getX() - cameraPos.x;
		double z = mc.player.getZ() - cameraPos.z;
		double y = 63.0 - cameraPos.y;
		RenderUtils.WorldBatch batch = RenderUtils.beginWorldBatch(poseStack);
		GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
		GL11.glPolygonOffset(-0.5f, -0.5f);
		batch.renderFilledBox(x - half, y, z - half, x + half, y + 0.06, z + half, fill);
		batch.renderOutlineBox(x - half, y, z - half, x + half, y + 0.06, z + half, outline);
		GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
		try {
			batch.flush();
		} catch (Exception e) {
			// Swallowed: a buffer thrown away mid-frame (resource reload, world unload) must not kill the render loop.
		}
	}

	/**
	 * Draws the group as a stack of nested rings. The area is clamped into render distance
	 * and faded by how far it had to be dragged, so a base outside the loaded world still
	 * shows a marker while reading as "roughly that way", not "exactly here".
	 */
	public void renderPredictedArea(PoseStack poseStack, Vec3 cameraPos, DetectionGroup group, int renderDistance) {
		double minX = group.minX() * 16.0;
		double maxX = (group.maxX() + 1) * 16.0;
		double minZ = group.minZ() * 16.0;
		double maxZ = (group.maxZ() + 1) * 16.0;
		double centerX = (minX + maxX) * 0.5;
		double centerZ = (minZ + maxZ) * 0.5;
		double playerX = mc.player.getX();
		double playerZ = mc.player.getZ();
		double reach = Math.max(16.0, renderDistance * 16.0);
		double offsetX = centerX - playerX;
		double offsetZ = centerZ - playerZ;
		double clampedX = clamp(offsetX, -reach + 8.0, reach - 8.0);
		double clampedZ = clamp(offsetZ, -reach + 8.0, reach - 8.0);
		double dragged = Math.max(Math.abs(offsetX - clampedX), Math.abs(offsetZ - clampedZ));
		double fade = dragged <= 0.0 ? 1.0 : Math.max(0.28, 1.0 - dragged / 160.0);
		double shiftX = playerX + clampedX - centerX;
		double shiftZ = playerZ + clampedZ - centerZ;
		minX += shiftX;
		maxX += shiftX;
		minZ += shiftZ;
		maxZ += shiftZ;
		int alpha = Math.max(1, Math.min(250, this.transparency.getValue()));
		Color nearColor = this.color.getValue();
		Color farColor = this.basePlateColor.getValue();
		double strength = clamp((double) group.score() / Math.max(1, this.sensitivity.getValue()), 0.0, 2.0);
		double capped = Math.min(1.0, strength);
		int rings = (int) Math.round(12.0 - capped * 5.0 + Math.max(0.0, strength - 1.0) * 2.0);
		rings = Math.max(5, Math.min(14, rings));
		double innerPadding = 4.0 + 16.0 * (0.25 + capped * 0.45);
		double ringSpacing = 4.0 * (0.65 + capped * 0.45 + Math.max(0.0, strength - 1.0) * 0.25);
		RenderUtils.WorldBatch batch = RenderUtils.beginWorldBatch(poseStack);
		GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
		// outermost ring first so the inner, brighter ones draw over it
		for (int ring = rings - 1; ring >= 0; --ring) {
			double padding = innerPadding + (ring + 1) * ringSpacing;
			double ratio = (double) (ring + 1) / rings;
			double y = 69.0 - ratio * 6.0;
			int ringAlpha = (int) Math.round(alpha * fade);
			this.renderAreaBox(batch, cameraPos, minX - padding, minZ - padding, maxX + padding, maxZ + padding, y,
					interpolate(nearColor, farColor, ratio, ringAlpha));
		}
		int coreAlpha = (int) Math.round(alpha * fade);
		this.renderAreaBox(batch, cameraPos, minX - innerPadding, minZ - innerPadding, maxX + innerPadding, maxZ + innerPadding, 69.0,
				new Color(nearColor.getRed(), nearColor.getGreen(), nearColor.getBlue(), coreAlpha));
		GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
		try {
			batch.flush();
		} catch (Exception e) {
			// Swallowed: a buffer thrown away mid-frame (resource reload, world unload) must not kill the render loop.
		}
	}

	/** Absolute world coordinates in, camera-relative slab out. */
	public void renderAreaBox(RenderUtils.WorldBatch batch, Vec3 cameraPos, double minX, double minZ, double maxX, double maxZ,
			double y, Color color) {
		Color outline = new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.min(255, color.getAlpha() + 60));
		double x1 = minX - cameraPos.x;
		double z1 = minZ - cameraPos.z;
		double x2 = maxX - cameraPos.x;
		double z2 = maxZ - cameraPos.z;
		double baseY = y - cameraPos.y;
		batch.renderFilledBox(x1, baseY, z1, x2, baseY + 0.06, z2, color);
		batch.renderOutlineBox(x1, baseY, z1, x2, baseY + 0.06, z2, outline);
	}

	/** Blends RGB from {@code from} to {@code to} by {@code ratio}, thinning alpha as it goes outward. */
	public static Color interpolate(Color from, Color to, double ratio, int baseAlpha) {
		int red = (int) ((double) from.getRed() + (double) (to.getRed() - from.getRed()) * ratio);
		int green = (int) ((double) from.getGreen() + (double) (to.getGreen() - from.getGreen()) * ratio);
		int blue = (int) ((double) from.getBlue() + (double) (to.getBlue() - from.getBlue()) * ratio);
		int alpha = Math.max(3, Math.min(255, (int) ((double) baseAlpha * (1.0 - ratio * 0.9))));
		return new Color(red, green, blue, alpha);
	}

	public static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	/** One chunk flagged as a likely base, with the grass count that flagged it. */
	public record Detection(ChunkPos chunk, int score, long firstSeenMs) {}

	/** Bounding box in chunk coordinates over a run of touching detections. */
	public record DetectionGroup(int minX, int maxX, int minZ, int maxZ, int score) {}
}
