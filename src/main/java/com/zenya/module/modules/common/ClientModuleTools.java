package com.zenya.module.modules.common;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.TrappedChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zenya.utils.RenderUtils;

import java.awt.Color;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Grab-bag of helpers the world-scanning modules all need: chat/toast output, block
 * classification, and the shared ESP box rendering.
 * <p>The scan collections handed out by {@link #chunkMap()} and {@link #chunkSet()} are
 * concurrent because chunk-load events populate them off the render thread.
 */
public class ClientModuleTools {

	/** Prints a prefixed client-side line, e.g. {@code §b[Stash Notifier]§r found ...}. */
	public static void chat(String prefix, String message) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.displayClientMessage(Component.literal("§b[" + prefix + "]§r " + message), false);
		}
	}

	public static void toast(String title, String message) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getToastManager() != null) {
			SystemToast.addOrUpdate(mc.getToastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
					Component.literal(title), Component.literal(message));
		}
	}

	/** Sends a command; a leading slash is optional because the network layer wants it stripped. */
	public static void command(String command) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getConnection() != null && command != null && !command.isBlank()) {
			mc.getConnection().sendCommand(command.startsWith("/") ? command.substring(1) : command);
		}
	}

	public static void clickSlot(AbstractContainerMenu menu, Slot slot, ClickType clickType) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.gameMode == null || mc.player == null || menu == null || slot == null) {
			return;
		}
		mc.gameMode.handleInventoryMouseClick(menu.containerId, slot.index, 0, clickType, mc.player);
	}

	public static int viewDistanceChunks() {
		Minecraft mc = Minecraft.getInstance();
		return mc.options == null ? 8 : mc.options.getEffectiveRenderDistance();
	}

	/** Parses user input like {@code "1.5k"}, {@code "$2,000"} or {@code "3m"}, falling back on junk. */
	public static double parseCompactNumber(String input, double fallback) {
		if (input == null || input.isBlank()) {
			return fallback;
		}
		String trimmed = input.trim().toLowerCase(Locale.ROOT);
		String digits = trimmed.replaceAll("[,$\\s]", "");
		double multiplier = 1.0;
		if (digits.endsWith("k")) {
			multiplier = 1000.0;
			digits = digits.substring(0, digits.length() - 1);
		} else if (digits.endsWith("m")) {
			multiplier = 1000000.0;
			digits = digits.substring(0, digits.length() - 1);
		} else if (digits.endsWith("b")) {
			multiplier = 1.0E9;
			digits = digits.substring(0, digits.length() - 1);
		}
		try {
			return Double.parseDouble(digits) * multiplier;
		} catch (NumberFormatException notANumber) {
			// Swallowed: unparseable input is a user typo, not an error worth surfacing.
			return fallback;
		}
	}

	/** Matches {@code query} against the stack's display name or its registry id, both lowercased. */
	public static boolean itemMatches(ItemStack stack, String query) {
		if (stack == null || stack.isEmpty() || query == null || query.isBlank()) {
			return false;
		}
		String needle = query.toLowerCase(Locale.ROOT);
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(needle)
				|| id != null && id.toString().toLowerCase(Locale.ROOT).contains(needle);
	}

	/** Heuristic for the "yes" button in a server GUI: named confirm/accept, or green glass. */
	public static boolean isConfirmItem(ItemStack stack) {
		if (stack == null) {
			return false;
		}
		if (stack.isEmpty()) {
			return false;
		}
		String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
		Identifier key = BuiltInRegistries.ITEM.getKey(stack.getItem());
		String id = key == null ? "" : key.toString();
		return name.contains("confirm")
				|| name.contains("accept")
				|| id.contains("lime_stained_glass")
				|| id.contains("green_stained_glass");
	}

	public static boolean isStorage(BlockEntity blockEntity) {
		return blockEntity instanceof ChestBlockEntity
				|| blockEntity instanceof TrappedChestBlockEntity
				|| blockEntity instanceof EnderChestBlockEntity
				|| blockEntity instanceof ShulkerBoxBlockEntity
				|| blockEntity instanceof FurnaceBlockEntity
				|| blockEntity instanceof BlastFurnaceBlockEntity
				|| blockEntity instanceof SmokerBlockEntity
				|| blockEntity instanceof BarrelBlockEntity
				|| blockEntity instanceof HopperBlockEntity;
	}

	public static boolean isStorageOrSpawner(BlockEntity blockEntity) {
		return isStorage(blockEntity) || blockEntity instanceof SpawnerBlockEntity;
	}

	public static boolean isPiston(Block block) {
		return block == Blocks.PISTON
				|| block == Blocks.STICKY_PISTON
				|| block == Blocks.PISTON_HEAD
				|| block == Blocks.MOVING_PISTON;
	}

	public static boolean isAmethyst(Block block) {
		return block == Blocks.AMETHYST_CLUSTER || block == Blocks.BUDDING_AMETHYST;
	}

	public static boolean isBeeNest(Block block) {
		return block == Blocks.BEEHIVE || block == Blocks.BEE_NEST;
	}

	public static boolean isStorageBlock(Block block) {
		return block == Blocks.CHEST
				|| block == Blocks.TRAPPED_CHEST
				|| block == Blocks.ENDER_CHEST
				|| block == Blocks.BARREL
				|| block == Blocks.HOPPER
				|| block == Blocks.FURNACE
				|| block == Blocks.BLAST_FURNACE
				|| block == Blocks.SMOKER
				|| block instanceof ShulkerBoxBlock;
	}

	/**
	 * Deepslate only generates on the Y axis, so a sideways one was placed by a player —
	 * a cheap tell for a hand-built base.
	 */
	public static boolean isRotatedDeepslate(BlockState state) {
		Block block = state.getBlock();
		if (block != Blocks.DEEPSLATE
				&& block != Blocks.POLISHED_DEEPSLATE
				&& block != Blocks.DEEPSLATE_BRICKS
				&& block != Blocks.DEEPSLATE_TILES
				&& block != Blocks.CHISELED_DEEPSLATE) {
			return false;
		}
		if (!state.hasProperty(BlockStateProperties.AXIS)) {
			return false;
		}
		return state.getValue(BlockStateProperties.AXIS) != Direction.Axis.Y;
	}

	public static int surfaceY(int x, int z) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return 64;
		}
		return mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
	}

	/** True when block light at {@code pos} clears {@code minLight} and beats sky light — a torch, not the sun. */
	public static boolean hasBlockLight(BlockPos pos, int minLight) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return false;
		}
		int blockLight = mc.level.getBrightness(LightLayer.BLOCK, pos);
		int skyLight = mc.level.getBrightness(LightLayer.SKY, pos);
		return blockLight >= minLight && blockLight > skyLight;
	}

	/** True when at least one of the six neighbours is not a full solid render, i.e. the block is visible. */
	public static boolean hasExposedFace(BlockPos pos) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return false;
		}
		for (Direction direction : Direction.values()) {
			if (mc.level.getBlockState(pos.relative(direction)).isSolidRender()) continue;
			return true;
		}
		return false;
	}

	/** Draws a slab over each marked chunk at surface height; {@code maxDistanceSq} culls on squared distance. */
	public static void renderChunks(PoseStack poseStack, Iterable<ChunkMark> marks, boolean fill, double maxDistanceSq) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		Camera camera = RenderUtils.getCamera();
		if (camera == null) {
			return;
		}
		Vec3 cameraPos = RenderUtils.getCameraPos(camera);
		RenderUtils.WorldBatch batch = RenderUtils.beginWorldBatch(poseStack);
		boolean drew = false;
		for (ChunkMark mark : marks) {
			ChunkPos pos = mark.pos();
			if (cameraPos.distanceToSqr(pos.getMiddleBlockX(), 64.0, pos.getMiddleBlockZ()) > maxDistanceSq) continue;
			double x = (double) pos.getMinBlockX() - cameraPos.x;
			double z = (double) pos.getMinBlockZ() - cameraPos.z;
			double y = (double) surfaceY(pos.getMiddleBlockX(), pos.getMiddleBlockZ()) - cameraPos.y + 0.05;
			if (fill) {
				batch.renderFilledBox(x, y, z, x + 16.0, y + 0.18, z + 16.0, mark.color());
			}
			batch.renderOutlineBox(x, y, z, x + 16.0, y + 0.22, z + 16.0, outline(mark.color()));
			drew = true;
		}
		if (drew) {
			batch.flush();
		}
	}

	/** Draws a filled-plus-outline box on each marked block; {@code maxDistanceSq} culls on squared distance. */
	public static void renderBlocks(PoseStack poseStack, Iterable<BlockMark> marks, double maxDistanceSq) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		Camera camera = RenderUtils.getCamera();
		if (camera == null) {
			return;
		}
		Vec3 cameraPos = RenderUtils.getCameraPos(camera);
		RenderUtils.WorldBatch batch = RenderUtils.beginWorldBatch(poseStack);
		boolean drew = false;
		for (BlockMark mark : marks) {
			BlockPos pos = mark.pos();
			if (cameraPos.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) > maxDistanceSq) continue;
			double x = (double) pos.getX() - cameraPos.x;
			double y = (double) pos.getY() - cameraPos.y;
			double z = (double) pos.getZ() - cameraPos.z;
			batch.renderFilledBox(x + 0.05, y + 0.05, z + 0.05, x + 0.95, y + 0.95, z + 0.95, mark.color());
			batch.renderOutlineBox(x + 0.02, y + 0.02, z + 0.02, x + 0.98, y + 0.98, z + 0.98, outline(mark.color()));
			drew = true;
		}
		if (drew) {
			batch.flush();
		}
	}

	public static Map<ChunkPos, ChunkMark> chunkMap() {
		return new ConcurrentHashMap<>();
	}

	public static Set<ChunkPos> chunkSet() {
		return ConcurrentHashMap.newKeySet();
	}

	/** Same colour, more opaque, so the outline reads against its own fill. */
	public static Color outline(Color color) {
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.min(255, color.getAlpha() + 80));
	}

	public static void disconnect(String reason) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && mc.getConnection() != null && mc.getConnection().getConnection() != null) {
			mc.getConnection().getConnection().disconnect(Component.literal(reason));
		}
	}

	/**
	 * Paints a slab over every chunk within {@code radius} of a stepped-on chunk, fading height
	 * and colour from centre to edge. Chunks are collapsed to their smallest ring distance first,
	 * so overlapping trails draw once at the strongest step.
	 */
	public static void renderSilentSteppedOverlay(PoseStack poseStack, Set<ChunkPos> stepped, double centerY,
			double edgeY, int radius, double height, Color centerColor, Color edgeColor, int alpha) {
		if (stepped == null || stepped.isEmpty()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		Camera camera = RenderUtils.getCamera();
		if (camera == null) {
			return;
		}
		Vec3 cameraPos = RenderUtils.getCameraPos(camera);
		ChunkPos playerChunk = mc.player.chunkPosition();
		int maxChunkDistance = Math.min(viewDistanceChunks() + radius + 2, 32);
		Map<ChunkPos, Integer> ringByChunk = new HashMap<>();
		for (ChunkPos chunk : stepped) {
			if (Math.abs(chunk.x - playerChunk.x) > maxChunkDistance + radius
					|| Math.abs(chunk.z - playerChunk.z) > maxChunkDistance + radius) continue;
			for (int dx = -radius; dx <= radius; ++dx) {
				for (int dz = -radius; dz <= radius; ++dz) {
					int ring = Math.max(Math.abs(dx), Math.abs(dz));
					ChunkPos neighbour = new ChunkPos(chunk.x + dx, chunk.z + dz);
					if (Math.abs(neighbour.x - playerChunk.x) > maxChunkDistance
							|| Math.abs(neighbour.z - playerChunk.z) > maxChunkDistance) continue;
					ringByChunk.merge(neighbour, ring, Math::min);
				}
			}
		}
		if (ringByChunk.isEmpty()) {
			return;
		}
		RenderUtils.WorldBatch batch = RenderUtils.beginWorldBatch(poseStack);
		boolean drew = false;
		Color near = centerColor != null ? centerColor : new Color(30, 230, 80);
		Color far = edgeColor != null ? edgeColor : new Color(255, 215, 0);
		int fillAlpha = Math.max(0, Math.min(255, alpha));
		int outlineAlpha = Math.min(255, fillAlpha + 80);
		for (Map.Entry<ChunkPos, Integer> entry : ringByChunk.entrySet()) {
			ChunkPos pos = entry.getKey();
			int ring = entry.getValue();
			int step = Math.max(0, ring - 1);
			// ponytail: step maxes out at radius - 1, so this cull never fires and the outermost
			// ring never reaches the full edge colour or height.
			if (step > radius) continue;
			double blend = radius > 0 ? (double) step / (double) radius : 0.0;
			double topY = centerY - blend * (centerY - edgeY);
			int red = (int) Math.round((double) near.getRed() + (double) (far.getRed() - near.getRed()) * blend);
			int green = (int) Math.round((double) near.getGreen() + (double) (far.getGreen() - near.getGreen()) * blend);
			int blue = (int) Math.round((double) near.getBlue() + (double) (far.getBlue() - near.getBlue()) * blend);
			Color fillColor = new Color(red, green, blue, fillAlpha);
			Color outlineColor = new Color(red, green, blue, outlineAlpha);
			double minX = (double) pos.getMinBlockX() - cameraPos.x;
			double minZ = (double) pos.getMinBlockZ() - cameraPos.z;
			double maxX = minX + 16.0;
			double maxZ = minZ + 16.0;
			double y = topY - cameraPos.y;
			batch.renderFilledBox(minX, y, minZ, maxX, y + height, maxZ, fillColor);
			batch.renderOutlineBox(minX, y, minZ, maxX, y + height + 0.005, maxZ, outlineColor);
			drew = true;
		}
		if (drew) {
			batch.flush();
		}
	}

	/** Overload using the default green-to-gold fade at alpha 140. */
	public static void renderSilentSteppedOverlay(PoseStack poseStack, Set<ChunkPos> stepped, double centerY,
			double edgeY, int radius, double height) {
		renderSilentSteppedOverlay(poseStack, stepped, centerY, edgeY, radius, height,
				new Color(30, 230, 80), new Color(255, 215, 0), 140);
	}

	/** A block a finder wants drawn. */
	public record BlockMark(BlockPos pos, String label, Color color) {}

	/** A chunk a finder wants drawn. */
	public record ChunkMark(ChunkPos pos, String label, Color color) {}
}
