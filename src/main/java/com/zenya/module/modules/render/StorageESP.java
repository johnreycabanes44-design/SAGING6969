package com.zenya.module.modules.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;
import com.zenya.setting.StorageBlocksSetting;
import com.zenya.utils.RenderUtils;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.DropperBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.TrappedChestBlockEntity;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Highlights containers and other interesting blocks through walls, drawn from the
 * block's real voxel shape so chests and hoppers keep their silhouette.
 *
 * <p>Containers are found through the chunk's block-entity map, which is cheap. The
 * rest (note blocks, redstone, resting pistons) have no block entity, so they come
 * from an incremental scan of two chunks per tick cached in {@link #cachedSpecialBlocks};
 * that cache is pruned to the render radius plus two chunks.
 */
public class StorageESP extends Module {
	public static String CHEST = "chest";
	public static String TRAPPED_CHEST = "trapped_chest";
	public static String ENDER_CHEST = "ender_chest";
	public static String SPAWNER = "spawner";
	public static String SHULKER_BOX = "shulker_box";
	public static String FURNACE = "furnace";
	public static String BARREL = "barrel";
	public static String DISPENSER = "dispenser";
	public static String HOPPER = "hopper";
	public static String PISTON = "piston";
	public static String STICKY_PISTON = "sticky_piston";
	public static String CRAFTER = "crafter";
	public static String SMOKER = "smoker";
	public static String BLAST_FURNACE = "blast_furnace";
	public static double TRACER_START_DISTANCE = 150.0;
	public static double TRACER_END_DISTANCE = 24.0;
	public static double TRACER_BEHIND_MIN_SPREAD = 2.75;
	public static Color CHEST_COLOR = rgb(156, 91, 0);
	public static Color TRAPPED_COLOR = rgb(200, 91, 0);
	public static Color ENDER_COLOR = rgb(117, 0, 255);
	public static Color SPAWNER_COLOR = rgb(138, 126, 166);
	public static Color SHULKER_COLOR = rgb(134, 0, 158);
	public static Color FURNACE_COLOR = rgb(125, 125, 125);
	public static Color BARREL_COLOR = rgb(255, 140, 140);
	public static Color DISPENSER_COLOR = rgb(100, 100, 100);
	public static Color HOPPER_COLOR = rgb(144, 238, 144);
	public static Color PISTON_COLOR = rgb(50, 205, 50);
	public static Color PISTON_STATIONARY_COLOR = rgb(173, 255, 47);
	public static Color CRAFTER_COLOR = rgb(255, 165, 0);
	public static Color SMOKER_COLOR = rgb(100, 100, 100);
	public static Color BLAST_FURNACE_COLOR = rgb(80, 80, 80);

	public Setting<Integer> espRendering;
	public Setting<Integer> alpha;
	public Setting<Integer> tracerAlpha;
	public Setting<Boolean> tracers;
	public Setting<Boolean> bodyTracers;
	public Setting<Double> tracerWidth;
	public Setting<Boolean> fill;
	public StorageBlocksSetting storageBlocks;
	public ArrayList<RenderBox> renderBoxes;
	public ArrayList<Tracer> renderTracers;
	public Map<ChunkPos, List<BlockPos>> cachedSpecialBlocks;
	/** Unused by the scan; kept because the config and GUI still reference the field. */
	public Set<ChunkPos> scannedChunks;
	public ArrayDeque<ChunkPos> scanQueue;
	public int currentScanX;
	public int currentScanZ;

	public StorageESP() {
		super("Storage ESP", Category.RENDER);
		this.espRendering = new Setting<>("ESP Rendering", 64, 16, 512);
		this.alpha = new Setting<>("Alpha", 125, 0, 255);
		this.tracerAlpha = new Setting<>("Tracer Alpha", 255, 0, 255);
		this.tracers = new Setting<>("Tracers", false);
		this.bodyTracers = new Setting<>("Body Tracers", false);
		this.tracerWidth = new Setting<>("Tracer Width", 1.0, 0.5, 5.0);
		this.fill = new Setting<>("Fill", true);
		this.storageBlocks = StorageBlocksSetting.createDefault("ESP Blocks");
		this.renderBoxes = new ArrayList<>(256);
		this.renderTracers = new ArrayList<>(256);
		this.cachedSpecialBlocks = new ConcurrentHashMap<>();
		this.scannedChunks = ConcurrentHashMap.newKeySet();
		this.scanQueue = new ArrayDeque<>();
		this.currentScanX = 0;
		this.currentScanZ = 0;
		this.setDescription("Renders storage blocks through walls using their real shapes.");

		LinkedHashSet<String> defaultBlocks = new LinkedHashSet<>();
		defaultBlocks.add("minecraft:chest");
		defaultBlocks.add("minecraft:trapped_chest");
		defaultBlocks.add("minecraft:ender_chest");
		defaultBlocks.add("minecraft:spawner");
		defaultBlocks.add("minecraft:shulker_box");
		defaultBlocks.add("minecraft:furnace");
		defaultBlocks.add("minecraft:barrel");
		defaultBlocks.add("minecraft:piston");
		defaultBlocks.add("minecraft:sticky_piston");
		defaultBlocks.add("minecraft:crafter");
		defaultBlocks.add("minecraft:smoker");
		defaultBlocks.add("minecraft:blast_furnace");
		this.storageBlocks.setValue(defaultBlocks);

		this.addSetting(this.espRendering);
		this.addSetting(this.alpha);
		this.addSetting(this.tracerAlpha);
		this.addSetting(this.tracers);
		this.addSetting(this.bodyTracers);
		this.addSetting(this.tracerWidth);
		this.addSetting(this.fill);
		this.addSetting(this.storageBlocks);
	}

	@Override
	public void onWorldChange() {
		this.renderBoxes.clear();
		this.renderTracers.clear();
		this.cachedSpecialBlocks.clear();
		this.scannedChunks.clear();
		this.scanQueue.clear();
	}

	/** Advances the block-entity-less scan by two chunks and drops cache entries that walked out of range. */
	@Override
	public void onTick() {
		if (mc.level == null || mc.player == null) {
			return;
		}
		int chunkRadius = this.espRendering.getValue() / 16;
		ChunkPos playerChunk = mc.player.chunkPosition();
		int minChunkX = playerChunk.x - chunkRadius;
		int maxChunkX = playerChunk.x + chunkRadius;
		int minChunkZ = playerChunk.z - chunkRadius;
		int maxChunkZ = playerChunk.z + chunkRadius;
		for (int scanned = 0; scanned < 2; ++scanned) {
			if (this.currentScanX < minChunkX || this.currentScanX > maxChunkX) {
				this.currentScanX = minChunkX;
			}
			if (this.currentScanZ < minChunkZ || this.currentScanZ > maxChunkZ) {
				this.currentScanZ = minChunkZ;
			}
			ChunkPos chunkPos = new ChunkPos(this.currentScanX, this.currentScanZ);
			LevelChunk chunk = mc.level.getChunkSource().getChunk(chunkPos.x, chunkPos.z, false);
			if (chunk != null) {
				this.scanChunkForNormalBlocks(chunk, chunkPos);
			}
			this.currentScanX += 1;
			if (this.currentScanX <= maxChunkX) continue;
			this.currentScanX = minChunkX;
			this.currentScanZ += 1;
			if (this.currentScanZ <= maxChunkZ) continue;
			this.currentScanZ = minChunkZ;
		}
		int keepRadius = chunkRadius + 2;
		this.cachedSpecialBlocks.keySet().removeIf(cached ->
				Math.abs(cached.x - playerChunk.x) > keepRadius || Math.abs(cached.z - playerChunk.z) > keepRadius);
	}

	/** Collects the interesting blocks of one chunk that carry no block entity to find them by. */
	public void scanChunkForNormalBlocks(LevelChunk chunk, ChunkPos chunkPos) {
		ArrayList<BlockPos> found = new ArrayList<>();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		int originX = chunkPos.getMinBlockX();
		int originZ = chunkPos.getMinBlockZ();
		int minY = mc.level.getMinY();
		int maxY = mc.level.getMaxY();
		for (int offsetX = 0; offsetX < 16; ++offsetX) {
			for (int offsetZ = 0; offsetZ < 16; ++offsetZ) {
				for (int y = minY; y <= maxY; ++y) {
					cursor.set(originX + offsetX, y, originZ + offsetZ);
					BlockState state = chunk.getBlockState(cursor);
					Block block = state.getBlock();
					if (block != Blocks.NOTE_BLOCK && block != Blocks.DIAMOND_BLOCK && block != Blocks.BEACON
							&& block != Blocks.OBSERVER && block != Blocks.REPEATER && block != Blocks.REDSTONE_WIRE
							&& block != Blocks.REDSTONE_BLOCK && block != Blocks.PISTON && block != Blocks.STICKY_PISTON) continue;
					found.add(cursor.immutable());
				}
			}
		}
		if (!found.isEmpty()) {
			// ponytail: an emptied chunk keeps its stale entry, since nothing overwrites it here.
			this.cachedSpecialBlocks.put(chunkPos, found);
		}
	}

	@Override
	public void onRender(PoseStack poseStack, float partialTicks) {
		if (mc.level == null || mc.player == null) {
			return;
		}
		if (!this.fill.getValue() && !this.tracers.getValue()) {
			return;
		}
		Camera camera = RenderUtils.getCamera();
		if (camera == null) {
			return;
		}
		Vec3 cameraPos = RenderUtils.getCameraPos(camera);
		this.renderBoxes.clear();
		this.renderTracers.clear();
		int chunkRadius = this.espRendering.getValue() / 16;
		double range = (double) chunkRadius * 16.0;
		double rangeSq = range * range;
		ChunkPos playerChunk = mc.player.chunkPosition();
		HashSet<Long> visited = new HashSet<>(256);
		ClientChunkCache chunkSource = mc.level.getChunkSource();
		for (int chunkX = playerChunk.x - chunkRadius; chunkX <= playerChunk.x + chunkRadius; ++chunkX) {
			for (int chunkZ = playerChunk.z - chunkRadius; chunkZ <= playerChunk.z + chunkRadius; ++chunkZ) {
				ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
				LevelChunk chunk = chunkSource.getChunk(chunkX, chunkZ, false);
				if (chunk != null) {
					this.collectChunk(chunk, cameraPos, rangeSq, visited);
				}
				List<BlockPos> specialBlocks = this.cachedSpecialBlocks.get(chunkPos);
				if (specialBlocks == null) continue;
				for (BlockPos pos : specialBlocks) {
					if (!visited.add(pos.asLong())) continue;
					double dx = (double) pos.getX() + 0.5 - cameraPos.x;
					double dy = (double) pos.getY() + 0.5 - cameraPos.y;
					double dz = (double) pos.getZ() + 0.5 - cameraPos.z;
					if (dx * dx + dy * dy + dz * dz > rangeSq) continue;
					BlockState state = mc.level.getBlockState(pos);
					String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
					if (!this.shouldHighlight(blockId)) continue;
					Color color = this.baseColor(blockId);
					if (blockId.equals("minecraft:piston") || blockId.equals("minecraft:sticky_piston")) {
						if (state.hasProperty(BlockStateProperties.EXTENDED) && !state.getValue(BlockStateProperties.EXTENDED)) {
							color = PISTON_STATIONARY_COLOR;
						}
					}
					Color fillColor = withAlpha(color, this.alpha.getValue());
					Color outlineColor = withAlpha(color, 255);
					VoxelShape shape = state.getShape(mc.level, pos);
					List<AABB> parts = shape == null ? List.of() : shape.toAabbs();
					if (parts.isEmpty()) {
						this.addBox(new AABB(pos).inflate(-0.02), cameraPos, fillColor, outlineColor);
					} else {
						for (AABB part : parts) {
							AABB box = part.move(pos).inflate(-0.02);
							if (!(box.maxX > box.minX) || !(box.maxY > box.minY) || !(box.maxZ > box.minZ)) continue;
							this.addBox(box, cameraPos, fillColor, outlineColor);
						}
					}
					this.renderTracers.add(new Tracer(new Vec3(dx, dy, dz), withAlpha(color, this.tracerAlpha.getValue())));
				}
			}
		}
		if (this.renderBoxes.isEmpty() && this.renderTracers.isEmpty()) {
			return;
		}
		if (!this.renderBoxes.isEmpty()) {
			RenderUtils.WorldBatch batch = RenderUtils.beginWorldBatch(poseStack);
			for (RenderBox box : this.renderBoxes) {
				if (!this.fill.getValue() || box.fillColor().getAlpha() <= 0) continue;
				double centerX = (box.minX() + box.maxX()) / 2.0;
				double centerY = (box.minY() + box.maxY()) / 2.0;
				double centerZ = (box.minZ() + box.maxZ()) / 2.0;
				// Shrink by a hair so touching boxes do not z-fight along their shared face.
				double halfX = Math.max(0.001, (box.maxX() - box.minX()) / 2.0 - 0.03);
				double halfY = Math.max(0.001, (box.maxY() - box.minY()) / 2.0 - 0.03);
				double halfZ = Math.max(0.001, (box.maxZ() - box.minZ()) / 2.0 - 0.03);
				batch.renderFilledBox(centerX - halfX, centerY - halfY, centerZ - halfZ,
						centerX + halfX, centerY + halfY, centerZ + halfZ, box.fillColor());
			}
			batch.flush();
		}
		if (this.tracers.getValue() && this.fill.getValue() && !this.renderTracers.isEmpty()) {
			boolean useBodyTracers = this.bodyTracers.getValue();
			boolean freecam = Freecam.instance != null && Freecam.instance.isEnabled();
			boolean thirdPerson = this.isThirdPersonView();
			// Only start tracers at the player's head when the camera is not on it.
			boolean fromHead = !useBodyTracers && (freecam || thirdPerson);
			Vec3 headPos = fromHead ? this.getPlayerHeadPosition(partialTicks) : Vec3.ZERO;
			Vec3 forward = RenderUtils.getCameraForward(camera);
			Vec3 right = RenderUtils.getCameraRight(camera);
			Vec3 up = RenderUtils.getCameraUp(forward, right);
			Vec3 origin = fromHead ? headPos : forward.scale(150.0);
			RenderUtils.WorldBatch batch = RenderUtils.beginWorldBatch(poseStack);
			for (Tracer tracer : this.renderTracers) {
				Vec3 end = RenderUtils.getSpreadTracerEnd(tracer.target().x, tracer.target().y, tracer.target().z,
						forward, right, up, 24.0, 2.75);
				Vec3 start = fromHead
						? new Vec3(origin.x - cameraPos.x, origin.y - cameraPos.y, origin.z - cameraPos.z)
						: origin;
				batch.renderLine(tracer.color(), start, end, this.tracerWidth.getValue().floatValue());
			}
			batch.flush();
		}
	}

	/** Queues every highlighted block entity of one chunk that is inside {@code rangeSq} of the camera. */
	public void collectChunk(LevelChunk chunk, Vec3 cameraPos, double rangeSq, HashSet<Long> visited) {
		for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
			BlockPos pos = blockEntity.getBlockPos();
			if (!visited.add(pos.asLong())) continue;
			double dx = (double) pos.getX() + 0.5 - cameraPos.x;
			double dy = (double) pos.getY() + 0.5 - cameraPos.y;
			double dz = (double) pos.getZ() + 0.5 - cameraPos.z;
			if (dx * dx + dy * dy + dz * dz > rangeSq) continue;
			String type = this.classify(blockEntity);
			if (type == null || !this.shouldHighlight(type)) continue;
			Color color = this.baseColor(type);
			if (type.equals("piston") || type.equals("sticky_piston")) {
				BlockState state = mc.level.getBlockState(pos);
				if (state != null && state.hasProperty(BlockStateProperties.EXTENDED)
						&& !state.getValue(BlockStateProperties.EXTENDED)) {
					color = PISTON_STATIONARY_COLOR;
				}
			}
			Color fillColor = withAlpha(color, this.alpha.getValue());
			Color outlineColor = withAlpha(color, 255);
			VoxelShape shape = blockEntity.getBlockState().getShape(mc.level, pos);
			List<AABB> parts = shape == null ? List.of() : shape.toAabbs();
			// A hopper's shape is a thin funnel; the full cube reads better at range.
			if (parts.isEmpty() || type.equals("hopper")) {
				this.addBox(new AABB(pos).inflate(-0.02), cameraPos, fillColor, outlineColor);
			} else {
				for (AABB part : parts) {
					AABB box = part.move(pos).inflate(-0.02);
					if (!(box.maxX > box.minX) || !(box.maxY > box.minY) || !(box.maxZ > box.minZ)) continue;
					this.addBox(box, cameraPos, fillColor, outlineColor);
				}
			}
			this.renderTracers.add(new Tracer(new Vec3(dx, dy, dz), withAlpha(color, this.tracerAlpha.getValue())));
		}
	}

	/** Stores the box camera-relative, so the draw pass needs no world translation. */
	public void addBox(AABB box, Vec3 cameraPos, Color fillColor, Color outlineColor) {
		this.renderBoxes.add(new RenderBox(
				box.minX - cameraPos.x, box.minY - cameraPos.y, box.minZ - cameraPos.z,
				box.maxX - cameraPos.x, box.maxY - cameraPos.y, box.maxZ - cameraPos.z,
				fillColor, outlineColor));
	}

	/**
	 * Names a block entity for the colour and selection lookups. Returns a bare type
	 * ("chest") where the block entity class is enough, and a full registry id where the
	 * state has to be read to tell two blocks apart.
	 */
	public String classify(BlockEntity blockEntity) {
		if (blockEntity instanceof TrappedChestBlockEntity) {
			return "trapped_chest";
		}
		if (blockEntity instanceof ChestBlockEntity) {
			return "chest";
		}
		if (blockEntity instanceof EnderChestBlockEntity) {
			return "ender_chest";
		}
		if (blockEntity instanceof SpawnerBlockEntity) {
			return "spawner";
		}
		if (blockEntity instanceof ShulkerBoxBlockEntity) {
			// The dyed variants are separate blocks, so the id has to come from the state.
			BlockState state = mc.level.getBlockState(blockEntity.getBlockPos());
			return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
		}
		if (blockEntity instanceof FurnaceBlockEntity) {
			return "furnace";
		}
		if (blockEntity instanceof BlastFurnaceBlockEntity) {
			return "blast_furnace";
		}
		if (blockEntity instanceof SmokerBlockEntity) {
			return "smoker";
		}
		if (blockEntity instanceof BarrelBlockEntity) {
			return "barrel";
		}
		if (blockEntity instanceof DispenserBlockEntity) {
			BlockState state = mc.level.getBlockState(blockEntity.getBlockPos());
			if (state.getBlock() == Blocks.DROPPER) {
				return "minecraft:dropper";
			}
			return "dispenser";
		}
		if (blockEntity instanceof DropperBlockEntity) {
			return "minecraft:dropper";
		}
		if (blockEntity instanceof HopperBlockEntity) {
			return "hopper";
		}
		if (blockEntity instanceof PistonMovingBlockEntity) {
			BlockState state = mc.level.getBlockState(blockEntity.getBlockPos());
			if (state.getBlock() == Blocks.PISTON) {
				return "piston";
			}
			if (state.getBlock() == Blocks.STICKY_PISTON) {
				return "sticky_piston";
			}
		}
		BlockState state = mc.level.getBlockState(blockEntity.getBlockPos());
		if (state.getBlock() == Blocks.CRAFTER) {
			return "crafter";
		}
		return null;
	}

	/** Accepts either a registry id or a bare type name from {@link #classify}. */
	public boolean shouldHighlight(String type) {
		if (type.startsWith("minecraft:")) {
			return this.storageBlocks.isSelected(type);
		}
		String blockId = this.typeToBlockId(type);
		if (blockId != null) {
			return this.storageBlocks.isSelected(blockId);
		}
		return false;
	}

	/** The user's colour for a type, falling back to the built-in one. */
	public Color baseColor(String type) {
		if (type.startsWith("minecraft:")) {
			Color color = this.storageBlocks.getColor(type);
			if (color != null) {
				return color;
			}
			StorageBlocksSetting.Entry entry = this.storageBlocks.findEntry(type);
			if (entry != null) {
				return entry.defaultColor();
			}
		}
		String blockId = this.typeToBlockId(type);
		if (blockId != null) {
			Color color = this.storageBlocks.getColor(blockId);
			if (color != null) {
				return color;
			}
		}
		return switch (type) {
			case "trapped_chest" -> TRAPPED_COLOR;
			case "ender_chest" -> ENDER_COLOR;
			case "spawner" -> SPAWNER_COLOR;
			case "shulker_box" -> SHULKER_COLOR;
			case "furnace" -> FURNACE_COLOR;
			case "blast_furnace" -> BLAST_FURNACE_COLOR;
			case "smoker" -> SMOKER_COLOR;
			case "barrel" -> BARREL_COLOR;
			case "dispenser" -> DISPENSER_COLOR;
			case "hopper" -> HOPPER_COLOR;
			case "piston", "sticky_piston" -> PISTON_COLOR;
			case "crafter" -> CRAFTER_COLOR;
			default -> CHEST_COLOR;
		};
	}

	/** Bare type name to registry id, or null for a type the block list does not carry. */
	public String typeToBlockId(String type) {
		return switch (type) {
			case "chest" -> "minecraft:chest";
			case "trapped_chest" -> "minecraft:trapped_chest";
			case "ender_chest" -> "minecraft:ender_chest";
			case "spawner" -> "minecraft:spawner";
			case "shulker_box" -> "minecraft:shulker_box";
			case "furnace" -> "minecraft:furnace";
			case "blast_furnace" -> "minecraft:blast_furnace";
			case "smoker" -> "minecraft:smoker";
			case "barrel" -> "minecraft:barrel";
			case "dispenser" -> "minecraft:dispenser";
			case "hopper" -> "minecraft:hopper";
			case "piston" -> "minecraft:piston";
			case "sticky_piston" -> "minecraft:sticky_piston";
			case "crafter" -> "minecraft:crafter";
			default -> null;
		};
	}

	public static Color rgb(int red, int green, int blue) {
		return new Color(red, green, blue, 255);
	}

	public static Color withAlpha(Color color, int alpha) {
		int clamped = Math.max(0, Math.min(255, alpha));
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), clamped);
	}

	public boolean isThirdPersonView() {
		if (mc.options != null) {
			try {
				return !mc.options.getCameraType().isFirstPerson();
			} catch (Throwable ignored) {
				// Camera type can be unset while options are still loading; assume first person.
			}
		}
		return false;
	}

	public Vec3 getPlayerHeadPosition(float partialTicks) {
		if (mc.player == null) {
			return Vec3.ZERO;
		}
		try {
			Vec3 pos = mc.player.getPosition(partialTicks);
			double eyeHeight = mc.player.getEyeHeight();
			return new Vec3(pos.x, pos.y + eyeHeight, pos.z);
		} catch (Throwable ignored) {
			// Interpolate by hand if the helper is missing on this version.
			double x = Mth.lerp((double) partialTicks, mc.player.xOld, mc.player.getX());
			double y = Mth.lerp((double) partialTicks, mc.player.yOld, mc.player.getY());
			double z = Mth.lerp((double) partialTicks, mc.player.zOld, mc.player.getZ());
			double eyeHeight = mc.player.getEyeHeight();
			return new Vec3(x, y + eyeHeight, z);
		}
	}

	public StorageBlocksSetting getStorageBlocksSetting() {
		return this.storageBlocks;
	}

	/** One queued box, already camera-relative. */
	public record RenderBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, Color fillColor, Color outlineColor) {}

	/** A camera-relative direction to a highlighted block, drawn as a line. */
	public record Tracer(Vec3 target, Color color) {}
}
