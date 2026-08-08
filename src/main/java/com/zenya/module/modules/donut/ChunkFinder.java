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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.polarbear.PolarBear;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hunts player bases by scoring nearby chunks against fourteen independent
 * "someone built here" detectors and flagging anything above the score threshold.
 *
 * <p>A full chunk is far too expensive to walk in one tick, so the scan is spread
 * out: {@link #queue} holds the chunks still to look at and {@link #queueCursor}
 * is how far through it we got, with the whole queue rebuilt every 200 ticks or
 * whenever it runs dry. {@link #scanned} stops a chunk being scored twice; both it
 * and {@link #flagged} are pruned to the scan radius so they cannot grow forever.
 */
public class ChunkFinder extends Module {
	public Setting<Integer> scanRadius;
	public Setting<Integer> minScore;
	public Setting<Integer> chunksPerTick;
	public Setting<Boolean> detCobble;
	public Setting<Boolean> detDeepslate;
	public Setting<Boolean> detObsidian;
	public Setting<Boolean> detVeinInt;
	public Setting<Boolean> detVertVein;
	public Setting<Boolean> detNeedle;
	public Setting<Boolean> detHiddenOre;
	public Setting<Boolean> detPiston;
	public Setting<Boolean> detDripstone;
	public Setting<Boolean> detMobCluster;
	public Setting<Boolean> detDropped;
	public Setting<Boolean> detAirPocket;
	public Setting<Boolean> detStair;
	public Setting<Boolean> detPolarBear;
	public Map<ChunkPos, FlaggedChunk> flagged;
	public Set<ChunkPos> scanned;
	public List<ChunkPos> queue;
	public int queueCursor;
	public int tickCounter;

	public ChunkFinder() {
		super("Chunk Finder", Category.DONUT);
		this.scanRadius = new Setting<>("Scan Radius", 5, 1, 12);
		this.minScore = new Setting<>("Min Score", 5, 1, 30);
		this.chunksPerTick = new Setting<>("Chunks/Tick", 2, 1, 8);
		this.detCobble = new Setting<>("Cobble Lines", true);
		this.detDeepslate = new Setting<>("Deepslate Band", true);
		this.detObsidian = new Setting<>("Obsidian Backbone", true);
		this.detVeinInt = new Setting<>("Vein Integrity", true);
		this.detVertVein = new Setting<>("Vertical Vein", true);
		this.detNeedle = new Setting<>("Needle Hole", true);
		this.detHiddenOre = new Setting<>("Hidden Ore", true);
		this.detPiston = new Setting<>("Piston Array", true);
		this.detDripstone = new Setting<>("Dripstone", true);
		this.detMobCluster = new Setting<>("Mob Cluster", true);
		this.detDropped = new Setting<>("Dropped Items", true);
		this.detAirPocket = new Setting<>("Air Pocket", true);
		this.detStair = new Setting<>("Synthetic Stair", true);
		this.detPolarBear = new Setting<>("Polar Bear", true);
		this.flagged = new ConcurrentHashMap<>();
		this.scanned = ConcurrentHashMap.newKeySet();
		this.queue = new ArrayList<>();
		this.queueCursor = 0;
		this.tickCounter = 0;
		this.setDescription("Multi-detector base hunter: scans chunks for 14 signs of player activity.");
		this.addSetting(this.scanRadius);
		this.addSetting(this.minScore);
		this.addSetting(this.chunksPerTick);
		this.addSetting(this.detCobble);
		this.addSetting(this.detDeepslate);
		this.addSetting(this.detObsidian);
		this.addSetting(this.detVeinInt);
		this.addSetting(this.detVertVein);
		this.addSetting(this.detNeedle);
		this.addSetting(this.detHiddenOre);
		this.addSetting(this.detPiston);
		this.addSetting(this.detDripstone);
		this.addSetting(this.detMobCluster);
		this.addSetting(this.detDropped);
		this.addSetting(this.detAirPocket);
		this.addSetting(this.detStair);
		this.addSetting(this.detPolarBear);
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
		this.flagged.clear();
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

		// Rebuild the work list periodically so the player walking around is picked up,
		// and forget anything that has drifted well outside the scan radius.
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
			this.flagged.keySet().removeIf(pos -> Math.abs(pos.x - center.x) > keepRadius || Math.abs(pos.z - center.z) > keepRadius);
		}

		int budget = this.chunksPerTick.getValue();

		while (budget-- > 0 && this.queueCursor < this.queue.size()) {
			ChunkPos pos = this.queue.get(this.queueCursor++);

			if (!this.scanned.add(pos)) {
				continue;
			}

			LevelChunk chunk = mc.level.getChunkSource().getChunk(pos.x, pos.z, false);

			if (chunk == null) {
				continue;
			}

			this.analyzeChunk(chunk, pos);
		}
	}

	/**
	 * Runs every detector over one chunk and, if the combined score clears
	 * {@link #minScore}, records it in {@link #flagged} and announces it in chat.
	 */
	public void analyzeChunk(LevelChunk chunk, ChunkPos pos) {
		if (mc.level == null) {
			return;
		}

		int originX = pos.getMinBlockX();
		int originZ = pos.getMinBlockZ();
		int minY = mc.level.getMinY();
		int maxY = mc.level.getMaxY();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

		int cobble = 0;
		int cobbleLines = 0;
		int deepslate = 0;
		int obsidian = 0;
		int pistons = 0;
		int dripstone = 0;
		int airPocket = 0;
		int stairs = 0;
		int diamond = 0;
		int iron = 0;
		int gold = 0;
		int debris = 0;
		int[] oreByLayer = new int[Math.max(1, maxY - minY + 1)];
		int needles = 0;
		int hiddenOre = 0;
		// Ore blocks stacked in the same column, reset to zero by the first non-ore above them.
		int[][] oreColumns = new int[16][16];

		for (int x = 0; x < 16; ++x) {
			for (int z = 0; z < 16; ++z) {
				int worldX = originX + x;
				int worldZ = originZ + z;
				int airRun = 0;
				int longestAir = 0;

				for (int y = minY; y <= maxY; ++y) {
					cursor.set(worldX, y, worldZ);

					Block block = chunk.getBlockState(cursor).getBlock();
					int layer = y - minY;

					if (block == Blocks.AIR || block == Blocks.CAVE_AIR) {
						if (++airRun > longestAir) {
							longestAir = airRun;
						}
					} else {
						// ponytail: longestAir is never cleared, so every solid block above a
						// big cavity scores again and airPocket runs away on tall columns.
						if (longestAir >= 12 && y < 20) {
							airPocket += longestAir / 4;
						}

						airRun = 0;
					}

					if (y < 0 && (block == Blocks.COBBLESTONE || block == Blocks.COBBLED_DEEPSLATE || block == Blocks.MOSSY_COBBLESTONE)) {
						++cobble;

						if (chunk.getBlockState(cursor.relative(Direction.EAST)).getBlock() == block) {
							++cobbleLines;
						}
					}

					if (y < 0 && (block == Blocks.POLISHED_DEEPSLATE || block == Blocks.DEEPSLATE_BRICKS || block == Blocks.DEEPSLATE_TILES || block == Blocks.CHISELED_DEEPSLATE)) {
						++deepslate;
					}

					if (y < 20 && block == Blocks.OBSIDIAN) {
						++obsidian;
					}

					if (y < 20 && ClientModuleTools.isPiston(block)) {
						++pistons;
					}

					if (y < 0 && (block == Blocks.POINTED_DRIPSTONE || block == Blocks.DRIPSTONE_BLOCK)) {
						++dripstone;
					}

					if (y < 0 && isStairBlock(block)) {
						++stairs;
					}

					if (!isOre(block)) {
						oreColumns[x][z] = 0;
						continue;
					}

					if (layer < oreByLayer.length) {
						++oreByLayer[layer];
					}

					++oreColumns[x][z];

					if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE) {
						++diamond;
					}

					if (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE) {
						++iron;
					}

					if (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE) {
						++gold;
					}

					if (block == Blocks.ANCIENT_DEBRIS) {
						++debris;
					}

					if (this.isHiddenOre(chunk, cursor, originX, originZ)) {
						hiddenOre += 2;
					}
				}
			}
		}

		// Needle holes: a 1x1 shaft mined straight down leaves a long air run ending at y=0.
		for (int x = 0; x < 16; ++x) {
			for (int z = 0; z < 16; ++z) {
				int worldX = originX + x;
				int worldZ = originZ + z;
				int airRun = 0;

				for (int y = minY; y < 0; ++y) {
					cursor.set(worldX, y, worldZ);

					Block block = chunk.getBlockState(cursor).getBlock();

					if (block == Blocks.AIR || block == Blocks.CAVE_AIR) {
						++airRun;
						continue;
					}

					airRun = 0;
				}

				if (airRun < 10) {
					continue;
				}

				++needles;
			}
		}

		int vertVeins = 0;

		for (int x = 0; x < 16; ++x) {
			for (int z = 0; z < 16; ++z) {
				if (oreColumns[x][z] < 4) {
					continue;
				}

				++vertVeins;
			}
		}

		int mobCluster = 0;
		int droppedItems = 0;
		int polarBears = 0;

		if (mc.level != null) {
			int mobs = 0;

			for (Entity entity : mc.level.entitiesForRendering()) {
				if (!new ChunkPos(entity.blockPosition()).equals(pos)) {
					continue;
				}

				if (entity instanceof ItemEntity && entity.getY() < 0.0) {
					++droppedItems;
				}

				if (entity instanceof PolarBear) {
					polarBears += 3;
				}

				if (entity instanceof Player) {
					continue;
				}

				++mobs;
			}

			if (mobs > 20) {
				mobCluster = mobs / 5;
			}
		}

		int storage = 0;

		for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
			if (!(blockEntity instanceof ChestBlockEntity) && !(blockEntity instanceof HopperBlockEntity)) {
				continue;
			}

			++storage;
		}

		int score = 0;
		List<String> tags = new ArrayList<>();

		if (this.detCobble.getValue() && cobbleLines >= 3) {
			score += Math.min(cobbleLines, 6);
			tags.add("CobbleLine(" + cobbleLines + ")");
		}

		if (this.detDeepslate.getValue() && deepslate >= 4) {
			score += Math.min(deepslate / 2, 5);
			tags.add("Deepslate(" + deepslate + ")");
		}

		if (this.detObsidian.getValue() && obsidian >= 3) {
			score += Math.min(obsidian, 6);
			tags.add("Obsidian(" + obsidian + ")");
		}

		if (this.detVeinInt.getValue() && diamond + debris >= 1 && storage >= 1) {
			score += 4;
			tags.add("VeinInt");
		}

		if (this.detVertVein.getValue() && vertVeins >= 2) {
			score += vertVeins;
			tags.add("VertVein(" + vertVeins + ")");
		}

		if (this.detNeedle.getValue() && needles >= 2) {
			score += needles * 2;
			tags.add("Needle(" + needles + ")");
		}

		if (this.detHiddenOre.getValue() && hiddenOre >= 2) {
			score += hiddenOre;
			tags.add("HiddenOre(" + hiddenOre + ")");
		}

		if (this.detPiston.getValue() && pistons >= 3) {
			score += Math.min(pistons, 8);
			tags.add("PistonArray(" + pistons + ")");
		}

		if (this.detDripstone.getValue() && dripstone >= 6) {
			score += dripstone / 3;
			tags.add("Dripstone(" + dripstone + ")");
		}

		if (this.detMobCluster.getValue() && mobCluster >= 2) {
			score += mobCluster;
			tags.add("MobCluster(" + mobCluster * 5 + ")");
		}

		if (this.detDropped.getValue() && droppedItems >= 2) {
			score += droppedItems * 2;
			tags.add("Dropped(" + droppedItems + ")");
		}

		if (this.detAirPocket.getValue() && airPocket >= 5) {
			score += airPocket / 3;
			tags.add("AirPocket(" + airPocket + ")");
		}

		if (this.detStair.getValue() && stairs >= 5) {
			score += stairs / 3;
			tags.add("Stairs(" + stairs + ")");
		}

		if (this.detPolarBear.getValue() && polarBears > 0) {
			score += polarBears;
			tags.add("PolarBear");
		}

		// Storage always counts; there is no toggle for it.
		if (storage >= 3) {
			score += storage;
			tags.add("Storage(" + storage + ")");
		}

		if (score < this.minScore.getValue()) {
			return;
		}

		// Higher score reads redder: red climbs to 255, green falls away to 0.
		int red = Math.min(255, 180 + score * 4);
		int green = Math.max(0, 200 - score * 8);
		Color color = new Color(red, green, 30, 110);
		String label = "Base? S:" + score + " [" + String.join(", ", tags) + "]";

		this.flagged.put(pos, new FlaggedChunk(pos, label, color));
		ClientModuleTools.chat("Chunk Finder", "Flagged " + pos.x + "," + pos.z + " | " + label);
	}

	/**
	 * True when an ore is walled in on five of its six sides by stone or man-made
	 * fill, which is what an ore left behind inside a built wall looks like.
	 * The chunk origin is unused; the surrounding blocks are read from the level.
	 */
	public boolean isHiddenOre(LevelChunk chunk, BlockPos.MutableBlockPos pos, int originX, int originZ) {
		if (mc.level == null) {
			return false;
		}

		int enclosed = 0;

		for (Direction direction : Direction.values()) {
			Block neighbour = mc.level.getBlockState(pos.relative(direction)).getBlock();

			if (neighbour != Blocks.COBBLESTONE && neighbour != Blocks.COBBLED_DEEPSLATE && neighbour != Blocks.STONE && neighbour != Blocks.DEEPSLATE && neighbour != Blocks.OBSIDIAN && neighbour != Blocks.NETHERRACK) {
				continue;
			}

			++enclosed;
		}

		return enclosed >= 5;
	}

	public static boolean isOre(Block block) {
		return block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE
				|| block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE
				|| block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE
				|| block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE
				|| block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE
				|| block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE
				|| block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE
				|| block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE
				|| block == Blocks.ANCIENT_DEBRIS;
	}

	public static boolean isStairBlock(Block block) {
		return block == Blocks.STONE_STAIRS || block == Blocks.COBBLESTONE_STAIRS
				|| block == Blocks.DEEPSLATE_BRICK_STAIRS || block == Blocks.POLISHED_DEEPSLATE_STAIRS
				|| block == Blocks.OAK_STAIRS || block == Blocks.SPRUCE_STAIRS
				|| block == Blocks.DARK_OAK_STAIRS || block == Blocks.COBBLED_DEEPSLATE_STAIRS;
	}

	@Override
	public void onRender(PoseStack poseStack, float partialTicks) {
		if (mc.level == null || mc.player == null || this.flagged.isEmpty()) {
			return;
		}

		Camera camera = RenderUtils.getCamera();

		if (camera == null) {
			return;
		}

		Vec3 cameraPos = RenderUtils.getCameraPos(camera);
		ChunkPos center = mc.player.chunkPosition();
		int radius = this.scanRadius.getValue() + 1;
		Color fill = new Color(0, 255, 80, 45);
		Color outline = new Color(0, 255, 80, 200);
		RenderUtils.WorldBatch batch = RenderUtils.beginWorldBatch(poseStack);
		boolean drewAnything = false;

		// Polygon offset pulls the slab towards the camera so it does not z-fight with the floor.
		GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
		GL11.glPolygonOffset(-1.0f, -1.0f);

		for (FlaggedChunk flaggedChunk : this.flagged.values()) {
			ChunkPos chunkPos = flaggedChunk.pos();

			if (Math.abs(chunkPos.x - center.x) > radius || Math.abs(chunkPos.z - center.z) > radius) {
				continue;
			}

			double x = (double) chunkPos.getMinBlockX() - cameraPos.x;
			double z = (double) chunkPos.getMinBlockZ() - cameraPos.z;
			double y = 63.0 - cameraPos.y;

			batch.renderFilledBox(x, y, z, x + 16.0, y + 0.15, z + 16.0, fill);
			batch.renderOutlineBox(x, y, z, x + 16.0, y + 0.15, z + 16.0, outline);
			drewAnything = true;
		}

		GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);

		if (drewAnything) {
			batch.flush();
		}
	}

	/** A chunk that scored above the threshold, with the chat label and overlay tint it earned. */
	public record FlaggedChunk(ChunkPos pos, String label, Color color) {
	}
}
