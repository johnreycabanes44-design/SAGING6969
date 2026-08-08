package com.zenya.module.modules.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.module.modules.common.ClientModuleTools;
import com.zenya.setting.Setting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.awt.Color;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Marks block light found in the deepslate layer, where nothing natural glows — a
 * torch down there is somebody's base.
 *
 * <p>Chunks are scanned once and remembered in {@link #scannedChunks}, so the cost is
 * paid only as new chunks load; the 400-tick timer wipes both collections so lights
 * that have since been removed do not linger. Both collections are concurrent because
 * the render pass walks {@link #litBlocks} while the tick thread appends to it.
 */
public class LightDebug extends Module {
	// ponytail: MIN_Y/MAX_Y are never read - scanChunk hardcodes the same -64..-35 range
	public static int MIN_Y = -64;
	public static int MAX_Y = -35;

	public Setting<Integer> red;
	public Setting<Integer> green;
	public Setting<Integer> blue;
	public Setting<Integer> alpha;
	public Setting<Integer> minBlockLight;
	public List<ClientModuleTools.BlockMark> litBlocks;
	public Set<ChunkPos> scannedChunks;
	public int rescanTimer;

	public LightDebug() {
		super("Light Debug", Category.RENDER);
		this.red = new Setting<>("Red", 255, 0, 255);
		this.green = new Setting<>("Green", 255, 0, 255);
		this.blue = new Setting<>("Blue", 0, 0, 255);
		this.alpha = new Setting<>("Alpha", 100, 0, 255);
		this.minBlockLight = new Setting<>("Min Block Light", 5, 1, 15);
		this.litBlocks = new CopyOnWriteArrayList<>();
		this.scannedChunks = ConcurrentHashMap.newKeySet();
		this.setDescription("Highlights suspicious block light from Y=-64 to Y=-35.");
		this.addSetting(this.red);
		this.addSetting(this.green);
		this.addSetting(this.blue);
		this.addSetting(this.alpha);
		this.addSetting(this.minBlockLight);
	}

	@Override
	public void onEnable() {
		this.litBlocks.clear();
		this.scannedChunks.clear();
		this.rescanTimer = 0;
	}

	@Override
	public void onDisable() {
		this.litBlocks.clear();
		this.scannedChunks.clear();
	}

	@Override
	public void onTick() {
		if (mc.level == null || mc.player == null) {
			return;
		}
		if ((this.rescanTimer += 1) >= 400) {
			this.litBlocks.clear();
			this.scannedChunks.clear();
			this.rescanTimer = 0;
		}
		ChunkPos playerChunk = mc.player.chunkPosition();
		for (int offsetX = -10; offsetX <= 10; ++offsetX) {
			for (int offsetZ = -10; offsetZ <= 10; ++offsetZ) {
				ChunkPos chunkPos = new ChunkPos(playerChunk.x + offsetX, playerChunk.z + offsetZ);
				// add() returning false short-circuits the chunk lookup: already scanned.
				if (!this.scannedChunks.add(chunkPos)) continue;
				LevelChunk chunk = mc.level.getChunkSource().getChunk(chunkPos.x, chunkPos.z, false);
				if (chunk == null) continue;
				this.scanChunk(chunk, chunkPos);
			}
		}
	}

	/** {@code chunk} is unused — the scan reads the world through the chunk's block coordinates. */
	public void scanChunk(LevelChunk chunk, ChunkPos chunkPos) {
		Color markColor = new Color(this.red.getValue(), this.green.getValue(), this.blue.getValue(), this.alpha.getValue());
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int x = 0; x < 16; ++x) {
			for (int z = 0; z < 16; ++z) {
				for (int y = -64; y <= -35; ++y) {
					cursor.set(chunkPos.getMinBlockX() + x, y, chunkPos.getMinBlockZ() + z);
					if (!ClientModuleTools.hasBlockLight(cursor, this.minBlockLight.getValue()) || !ClientModuleTools.hasExposedFace(cursor)) continue;
					this.litBlocks.add(new ClientModuleTools.BlockMark(cursor.immutable(), "Light", markColor));
				}
			}
		}
	}

	@Override
	public void onRender(PoseStack poseStack, float partialTicks) {
		// 67600 is 260 blocks squared — renderBlocks culls on squared distance.
		ClientModuleTools.renderBlocks(poseStack, this.litBlocks, 67600.0);
	}
}
