package com.zenya.module.modules.smps;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;
import com.zenya.utils.renderer.ProjectionUtil;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Draws a nametag with the mob type above every loaded spawner near the player.
 *
 * <p>The spawner map is packet-driven: a full sweep of the scan radius only runs every
 * {@link #SCAN_INTERVAL_TICKS} ticks, and chunk/block updates rescan just the chunk they
 * touch. Entries are keyed by {@link BlockPos#asLong()} so a rescan of the same chunk
 * overwrites rather than duplicates, and the map is concurrent because the packet thread
 * writes it while the render thread reads it.
 */
public class SpawnerTags extends Module {
	/** Ticks between full sweeps of the scan radius; packet updates cover the gap. */
	public static final int SCAN_INTERVAL_TICKS = 10;

	/** Height above the block origin the tag is anchored at, just over the cage. */
	public static final double Y_OFFSET = 0.85;

	/** Mobs whose translated name reads badly once {@link #capitalizeWords} is done with it. */
	private static final Map<EntityType<?>, String> MOB_NAMES = Map.ofEntries(
			Map.entry(EntityType.ZOMBIFIED_PIGLIN, "Zombie Piglin"),
			Map.entry(EntityType.PIGLIN_BRUTE, "Piglin Brute"),
			Map.entry(EntityType.IRON_GOLEM, "Iron Golem"),
			Map.entry(EntityType.SKELETON_HORSE, "Skeleton Horse"),
			Map.entry(EntityType.ZOMBIE_HORSE, "Zombie Horse"),
			Map.entry(EntityType.CAVE_SPIDER, "Cave Spider"),
			Map.entry(EntityType.MAGMA_CUBE, "Magma Cube"),
			Map.entry(EntityType.WITHER_SKELETON, "Wither Skeleton"),
			Map.entry(EntityType.BLAZE, "Blaze"),
			Map.entry(EntityType.SILVERFISH, "Silverfish"));

	/** Set by the constructor so the HUD hook can reach the live instance statically. */
	public static SpawnerTags INSTANCE;

	public Setting<Color> textColor;
	public Setting<Color> backgroundColor;
	public Setting<Double> scale;
	public Setting<Integer> scanRadius;
	public Map<Long, SpawnerTag> spawners;
	public List<SpawnerTag> renderList;
	public int tickCounter;
	public int spawnerCount;

	public SpawnerTags() {
		super("SpawnerTags", Category.SMPS);
		this.textColor = new Setting<>("Text Color", Color.WHITE);
		this.backgroundColor = new Setting<>("Background", new Color(0, 0, 0, 120));
		this.scale = new Setting<>("Scale", 1.15, 0.35, 4.0);
		this.scanRadius = new Setting<>("Scan Radius", 32, 1, 32);
		this.spawners = new ConcurrentHashMap<>();
		this.renderList = new ArrayList<>();
		INSTANCE = this;
		this.setDescription("Shows mob type nametags above nearby loaded spawners.");
		this.addSetting(this.textColor);
		this.addSetting(this.backgroundColor);
		this.addSetting(this.scale);
		this.addSetting(this.scanRadius);
	}

	@Override
	public void onEnable() {
		this.spawners.clear();
		this.renderList.clear();
		this.tickCounter = 0;
		this.spawnerCount = 0;
		this.scanAroundPlayer();
	}

	@Override
	public void onDisable() {
		this.spawners.clear();
		this.renderList.clear();
		this.spawnerCount = 0;
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.level == null) {
			this.spawners.clear();
			this.renderList.clear();
			this.spawnerCount = 0;
			return;
		}
		if ((this.tickCounter += 1) < SCAN_INTERVAL_TICKS) {
			return;
		}
		this.tickCounter = 0;
		this.scanAroundPlayer();
	}

	/** Keeps the map in step between sweeps: rescan a touched chunk, drop a broken spawner. */
	@Override
	public void onPacketReceive(Packet packet) {
		if (mc.level == null) {
			return;
		}
		if (packet instanceof ClientboundLevelChunkWithLightPacket chunkPacket) {
			LevelChunk chunk = mc.level.getChunkSource().getChunk(chunkPacket.getX(), chunkPacket.getZ(), false);
			if (chunk != null) {
				this.scanChunk(chunk);
			}
			return;
		}
		if (packet instanceof ClientboundSectionBlocksUpdatePacket sectionPacket) {
			sectionPacket.runUpdates((pos, state) -> {
				if (state.is(Blocks.SPAWNER)) {
					ChunkPos chunkPos = new ChunkPos(pos);
					LevelChunk chunk = mc.level.getChunkSource().getChunk(chunkPos.x, chunkPos.z, false);
					if (chunk != null) {
						this.scanChunk(chunk);
					}
				} else {
					this.spawners.remove(pos.asLong());
					this.spawnerCount = this.spawners.size();
				}
			});
			return;
		}
		if (packet instanceof ClientboundBlockUpdatePacket blockPacket) {
			BlockPos pos = blockPacket.getPos();
			if (blockPacket.getBlockState().is(Blocks.SPAWNER)) {
				ChunkPos chunkPos = new ChunkPos(pos);
				LevelChunk chunk = mc.level.getChunkSource().getChunk(chunkPos.x, chunkPos.z, false);
				if (chunk != null) {
					this.scanChunk(chunk);
				}
			} else {
				this.spawners.remove(pos.asLong());
				this.spawnerCount = this.spawners.size();
			}
		}
	}

	/** HUD entry point; a no-op unless the module is on. */
	public static void renderHud(GuiGraphics graphics, float tickDelta) {
		SpawnerTags module = INSTANCE;
		if (module == null || !module.isEnabled()) {
			return;
		}
		module.renderHudInternal(graphics);
	}

	public void renderHudInternal(GuiGraphics graphics) {
		if (mc.player == null || mc.level == null || this.spawners.isEmpty()) {
			return;
		}
		this.renderList.clear();
		double maxDistanceSq = this.maxRenderDistanceSq();
		double playerX = mc.player.getX();
		double playerY = mc.player.getY();
		double playerZ = mc.player.getZ();
		for (SpawnerTag tag : this.spawners.values()) {
			// The block can be gone without a packet we saw, so verify before drawing.
			if (!mc.level.getBlockState(tag.pos).is(Blocks.SPAWNER)) {
				this.spawners.remove(tag.key);
				continue;
			}
			double distanceSq = this.squaredDistance(tag.pos, playerX, playerY, playerZ);
			if (distanceSq > maxDistanceSq) {
				continue;
			}
			tag.distanceSq = distanceSq;
			this.renderList.add(tag);
		}
		if (this.renderList.isEmpty()) {
			this.spawnerCount = this.spawners.size();
			return;
		}
		// Farthest first, so the nearest tag is painted last and wins any overlap.
		this.renderList.sort(Comparator.comparingDouble((SpawnerTag tag) -> -tag.distanceSq));
		Font font = mc.font;
		float tagScale = this.scale.getValue().floatValue();
		int textArgb = this.rgb(this.textColor.getValue());
		int backgroundArgb = this.argb(this.backgroundColor.getValue());
		for (SpawnerTag tag : this.renderList) {
			Vec3 world = new Vec3(tag.pos.getX() + 0.5, tag.pos.getY() + Y_OFFSET, tag.pos.getZ() + 0.5);
			Tuple<Vec3, Boolean> projected = ProjectionUtil.project(ProjectionUtil.modelViewMatrix,
					ProjectionUtil.projectionMatrix, world);
			if (projected == null || !projected.getB()) {
				continue;
			}
			Vec3 screen = projected.getA();
			if (screen.z < -1.0 || screen.z > 1.0) {
				continue;
			}
			this.renderTag(graphics, font, tag.label, (float) screen.x, (float) screen.y, tagScale, textArgb,
					backgroundArgb);
		}
		this.spawnerCount = this.spawners.size();
	}

	/** Full sweep: rebuilds the map from every loaded chunk inside the scan radius. */
	public void scanAroundPlayer() {
		if (mc.player == null || mc.level == null) {
			return;
		}
		ChunkPos center = mc.player.chunkPosition();
		int radius = this.scanRadius.getValue();
		Map<Long, SpawnerTag> found = new ConcurrentHashMap<>();
		for (int offsetX = -radius; offsetX <= radius; ++offsetX) {
			for (int offsetZ = -radius; offsetZ <= radius; ++offsetZ) {
				LevelChunk chunk = mc.level.getChunkSource().getChunk(center.x + offsetX, center.z + offsetZ, false);
				if (chunk == null) {
					continue;
				}
				this.collectChunkSpawners(chunk, found);
			}
		}
		this.spawners.clear();
		this.spawners.putAll(found);
		this.spawnerCount = this.spawners.size();
	}

	/** Drops everything previously known about this chunk, then re-reads it. */
	public void scanChunk(LevelChunk chunk) {
		if (chunk == null) {
			return;
		}
		long chunkKey = chunk.getPos().toLong();
		this.spawners.entrySet().removeIf(entry -> new ChunkPos(entry.getValue().pos).toLong() == chunkKey);
		this.collectChunkSpawners(chunk, this.spawners);
		this.spawnerCount = this.spawners.size();
	}

	public void collectChunkSpawners(LevelChunk chunk, Map<Long, SpawnerTag> out) {
		for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
			if (!(blockEntity instanceof SpawnerBlockEntity spawner)) {
				continue;
			}
			BlockPos pos = blockEntity.getBlockPos();
			if (mc.level == null || !mc.level.getBlockState(pos).is(Blocks.SPAWNER)) {
				continue;
			}
			String label = this.resolveSpawnerLabel(spawner, pos);
			if (label == null || label.isBlank()) {
				continue;
			}
			long key = pos.asLong();
			out.put(key, new SpawnerTag(key, pos, label));
		}
	}

	public String resolveSpawnerLabel(SpawnerBlockEntity spawner, BlockPos pos) {
		EntityType<?> type = this.readSpawnerEntityType(spawner, pos);
		if (type == null) {
			return null;
		}
		return this.formatMobName(type);
	}

	/**
	 * Reads the mob from the spawner's display entity — the model spinning in the cage is
	 * already built client-side, so no NBT parsing is needed.
	 */
	public EntityType<?> readSpawnerEntityType(SpawnerBlockEntity spawner, BlockPos pos) {
		if (mc.level == null) {
			return null;
		}
		try {
			Entity display = spawner.getSpawner().getOrCreateDisplayEntity(mc.level, pos);
			return display == null ? null : display.getType();
		} catch (Exception e) {
			// A spawner carrying an unknown or malformed entity id throws while building the
			// display entity; leave it untagged instead of aborting the whole chunk scan.
			return null;
		}
	}

	public String formatMobName(EntityType<?> type) {
		String override = MOB_NAMES.get(type);
		if (override != null) {
			return override;
		}
		String name = type.getDescription().getString();
		if (name == null || name.isBlank()) {
			return type.toString();
		}
		return this.capitalizeWords(name);
	}

	/** Draws one tag centred on the projected point, sitting just above it. */
	public void renderTag(GuiGraphics graphics, Font font, String text, float screenX, float screenY, float tagScale,
			int textArgb, int backgroundArgb) {
		int textWidth = font.width(text);
		int padding = 4;
		int boxWidth = textWidth + padding * 2;
		int boxHeight = 13;
		int left = Math.round((float) (-boxWidth) / 2.0f);
		int top = -boxHeight;
		graphics.pose().pushMatrix();
		graphics.pose().translate(screenX, screenY);
		graphics.pose().scale(tagScale, tagScale);
		graphics.fill(left, top, left + boxWidth, top + boxHeight, backgroundArgb);
		graphics.drawString(font, text, left + padding, top + 2, textArgb, true);
		graphics.pose().popMatrix();
	}

	/** One chunk of slack past the render distance, so tags fade out with the terrain. */
	public double maxRenderDistanceSq() {
		double blocks = (double) (mc.options.getEffectiveRenderDistance() + 1) * 16.0;
		return blocks * blocks;
	}

	/** Distance from the block centre, squared to skip the root. */
	public double squaredDistance(BlockPos pos, double x, double y, double z) {
		double deltaX = (double) pos.getX() + 0.5 - x;
		double deltaY = (double) pos.getY() + 0.5 - y;
		double deltaZ = (double) pos.getZ() + 0.5 - z;
		return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
	}

	/** Packs the colour opaque, discarding whatever alpha the setting carries. */
	public int rgb(Color color) {
		return 0xFF000000 | color.getRed() << 16 | color.getGreen() << 8 | color.getBlue();
	}

	public int argb(Color color) {
		return color.getAlpha() << 24 | color.getRed() << 16 | color.getGreen() << 8 | color.getBlue();
	}

	/** "cave_spider" / "CAVE SPIDER" -> "Cave Spider": title case, separators become spaces. */
	public String capitalizeWords(String raw) {
		String lower = raw.toLowerCase(Locale.ROOT);
		StringBuilder out = new StringBuilder(lower.length());
		boolean startOfWord = true;
		for (int i = 0; i < lower.length(); ++i) {
			char c = lower.charAt(i);
			out.append(startOfWord ? Character.toUpperCase(c) : c);
			startOfWord = c == ' ' || c == '_' || c == '-';
		}
		return out.toString().replace('_', ' ').replace('-', ' ');
	}

	/**
	 * One located spawner. {@code distanceSq} is scratch space refreshed every frame in
	 * {@link #renderHudInternal} purely to sort the draw order.
	 */
	public static class SpawnerTag {
		public long key;
		public BlockPos pos;
		public String label;
		public double distanceSq;

		public SpawnerTag(long key, BlockPos pos, String label) {
			this.key = key;
			this.pos = pos.immutable();
			this.label = label;
		}
	}
}
