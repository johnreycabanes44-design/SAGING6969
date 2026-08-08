package com.zenya.module.modules.donut;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.utils.RenderUtils;
import net.minecraft.client.Camera;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.TrappedChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Marks chunks holding a large storage setup — 20+ containers, any spawner, or 5+ hoppers —
 * and pings once when a new one turns up.
 *
 * <p>{@link #scannedChunks} records every chunk already looked at, so the 13x13 sweep only
 * pays for chunks it has not seen; that is also why the sweep can be re-run every 1500 ms
 * without walking the same block entities over and over. Both sets are only reset on toggle,
 * so re-enabling the module is what clears a stale world's marks.
 */
public class BaseMarker extends Module {
	/** ARGB of the flat slab drawn over a marked chunk: half-transparent green. */
	public static final int MARKER_COLOR = 0x5500FF00;

	public Set<ChunkPos> markedChunks;
	public Set<ChunkPos> scannedChunks;
	public long lastScan;

	public BaseMarker() {
		super("BaseMarker", Category.DONUT);
		this.markedChunks = ConcurrentHashMap.newKeySet();
		this.scannedChunks = ConcurrentHashMap.newKeySet();
		this.lastScan = 0L;
		this.setDescription("Highlights chunks with large storage setups.");
	}

	@Override
	public void onEnable() {
		this.markedChunks.clear();
		this.scannedChunks.clear();
	}

	@Override
	public void onDisable() {
		this.markedChunks.clear();
		this.scannedChunks.clear();
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.level == null) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now - this.lastScan < 1500L) {
			return;
		}
		this.lastScan = now;
		ChunkPos center = mc.player.chunkPosition();
		for (int offsetX = -6; offsetX <= 6; ++offsetX) {
			for (int offsetZ = -6; offsetZ <= 6; ++offsetZ) {
				ChunkPos pos = new ChunkPos(center.x + offsetX, center.z + offsetZ);
				if (!this.scannedChunks.add(pos)) {
					continue;
				}
				this.scanChunk(pos);
			}
		}
	}

	/** Counts the storage in one chunk and marks it if the setup is big enough. */
	public void scanChunk(ChunkPos pos) {
		LevelChunk chunk;
		if (mc.level == null) {
			return;
		}
		try {
			chunk = mc.level.getChunk(pos.x, pos.z);
			if (chunk == null) {
				return;
			}
		} catch (Exception e) {
			// A chunk can unload between the sweep and this call; nothing to mark then.
			return;
		}
		int containers = 0;
		int spawners = 0;
		int hoppers = 0;
		for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
			if (blockEntity instanceof ChestBlockEntity || blockEntity instanceof TrappedChestBlockEntity
					|| blockEntity instanceof BarrelBlockEntity || blockEntity instanceof ShulkerBoxBlockEntity) {
				++containers;
			} else if (blockEntity instanceof SpawnerBlockEntity) {
				++spawners;
			} else if (blockEntity instanceof HopperBlockEntity) {
				++hoppers;
			}
		}
		boolean interesting = containers >= 20 || spawners >= 1 || hoppers >= 5;
		if (!interesting || this.markedChunks.contains(pos)) {
			return;
		}
		this.markedChunks.add(pos);
		try {
			if (mc.player != null) {
				mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 1.0f);
			}
		} catch (Exception e) {
			// A missing sound must never take the scan down with it.
		}
	}

	@Override
	public void onRender(PoseStack poseStack, float partialTicks) {
		if (!this.isEnabled() || mc.player == null || this.markedChunks.isEmpty()) {
			return;
		}
		Camera camera = RenderUtils.getCamera();
		if (camera == null) {
			return;
		}
		Vec3 cameraPos = RenderUtils.getCameraPos(camera);
		Color color = new Color(MARKER_COLOR, true);
		RenderUtils.WorldBatch batch = RenderUtils.beginWorldBatch(poseStack);
		for (ChunkPos pos : this.markedChunks) {
			if (cameraPos.distanceToSqr(pos.getMiddleBlockX(), 64.0, pos.getMiddleBlockZ()) > 90000.0) {
				continue;
			}
			double minX = pos.getMinBlockX() - cameraPos.x;
			double minZ = pos.getMinBlockZ() - cameraPos.z;
			// ponytail: the slab sits at a fixed y=30, so a marked base above or below that
			// height is drawn at the wrong altitude.
			double minY = 30.0 - cameraPos.y;
			batch.renderFilledBox(minX, minY, minZ, minX + 16.0, minY + 0.12, minZ + 16.0, color);
		}
		batch.flush();
	}
}
