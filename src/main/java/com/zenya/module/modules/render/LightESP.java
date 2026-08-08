package com.zenya.module.modules.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;
import com.zenya.utils.RenderUtils;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Highlights every block-lit position in the loaded chunks around the player, tinted
 * by light level, so torch-lit rooms show through stone.
 *
 * <p>A rescan walks 16x16x(maxY-minY) positions per chunk over a square of chunks, so
 * it is far too slow to run every tick: results are cached per chunk in
 * {@link #lightBlocks} and only rebuilt when the player crosses a chunk border or the
 * 20-tick timer expires. The cache is concurrent because the render pass reads it while
 * the tick thread rewrites it.
 */
public class LightESP extends Module {
	public Setting<Integer> scanRadius;
	public Setting<Integer> minY;
	public Setting<Integer> maxY;
	public Setting<Integer> alpha;
	public Setting<Color> color;
	public Map<ChunkPos, List<LightBlock>> lightBlocks;
	public ChunkPos lastPlayerChunk;
	public int scanTimer;

	public LightESP() {
		super("Light ESP", Category.RENDER);
		this.scanRadius = new Setting<>("Scan Radius", 10, 1, 16);
		this.minY = new Setting<>("Min Y", -63, -64, 320);
		this.maxY = new Setting<>("Max Y", -30, -64, 320);
		this.alpha = new Setting<>("Alpha", 80, 1, 255);
		this.color = new Setting<>("Color", new Color(255, 255, 0, 255));
		this.lightBlocks = new ConcurrentHashMap<>();
		this.setDescription("Highlights block light sources in nearby loaded chunks for underground base finding.");
		this.addSetting(this.scanRadius);
		this.addSetting(this.minY);
		this.addSetting(this.maxY);
		this.addSetting(this.alpha);
		this.addSetting(this.color);
	}

	@Override
	public void onEnable() {
		this.lightBlocks.clear();
		this.scanTimer = 0;
		this.lastPlayerChunk = mc.player == null ? null : mc.player.chunkPosition();
		this.scanAllChunks();
	}

	@Override
	public void onDisable() {
		this.lightBlocks.clear();
		this.lastPlayerChunk = null;
	}

	@Override
	public void onTick() {
		if (mc.level == null || mc.player == null) {
			this.lightBlocks.clear();
			return;
		}
		this.scanTimer += 1;
		ChunkPos playerChunk = mc.player.chunkPosition();
		boolean crossedChunkBorder = this.lastPlayerChunk == null || !this.lastPlayerChunk.equals(playerChunk);
		if (crossedChunkBorder || this.scanTimer >= 20) {
			this.scanAllChunks();
			this.lastPlayerChunk = playerChunk;
			this.scanTimer = 0;
		}
	}

	/** Rescans every loaded chunk in range, then evicts cached chunks that fell out of it. */
	public void scanAllChunks() {
		if (mc.level == null || mc.player == null) {
			return;
		}
		ChunkPos playerChunk = mc.player.chunkPosition();
		int radius = this.scanRadius.getValue();
		for (int offsetX = -radius; offsetX <= radius; ++offsetX) {
			for (int offsetZ = -radius; offsetZ <= radius; ++offsetZ) {
				LevelChunk chunk = mc.level.getChunkSource().getChunk(playerChunk.x + offsetX, playerChunk.z + offsetZ, false);
				if (chunk == null) continue;
				this.scanChunk(chunk);
			}
		}
		this.lightBlocks.keySet().removeIf(cached ->
				Math.abs(cached.x - playerChunk.x) > radius || Math.abs(cached.z - playerChunk.z) > radius);
	}

	/** Replaces this chunk's cache entry, or drops it entirely when the chunk is fully dark. */
	public void scanChunk(LevelChunk chunk) {
		if (mc.level == null || chunk == null) {
			return;
		}
		ChunkPos chunkPos = chunk.getPos();
		int originX = chunkPos.getMinBlockX();
		int originZ = chunkPos.getMinBlockZ();
		int fromY = Math.min(this.minY.getValue(), this.maxY.getValue());
		int toY = Math.max(this.minY.getValue(), this.maxY.getValue());
		int worldBottom = mc.level.getMinY();
		int worldTop = mc.level.getMinY() + mc.level.getHeight() - 1;
		fromY = Math.max(fromY, worldBottom);
		toY = Math.min(toY, worldTop);
		List<LightBlock> found = new ArrayList<>();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int x = 0; x < 16; ++x) {
			for (int z = 0; z < 16; ++z) {
				for (int y = fromY; y <= toY; ++y) {
					cursor.set(originX + x, y, originZ + z);
					int lightLevel = mc.level.getBrightness(LightLayer.BLOCK, cursor);
					if (lightLevel <= 0) continue;
					found.add(new LightBlock(cursor.immutable(), lightLevel));
				}
			}
		}
		if (found.isEmpty()) {
			this.lightBlocks.remove(chunkPos);
		} else {
			this.lightBlocks.put(chunkPos, found);
		}
	}

	@Override
	public void onRender(PoseStack poseStack, float partialTicks) {
		if (mc.level == null || mc.player == null || this.lightBlocks.isEmpty()) {
			return;
		}
		Camera camera = RenderUtils.getCamera();
		if (camera == null) {
			return;
		}
		Vec3 cameraPos = RenderUtils.getCameraPos(camera);
		RenderUtils.WorldBatch batch = RenderUtils.beginWorldBatch(poseStack);
		boolean drewAnything = false;
		for (List<LightBlock> chunkLights : this.lightBlocks.values()) {
			for (LightBlock light : chunkLights) {
				BlockPos pos = light.pos();
				double x = (double) pos.getX() - cameraPos.x;
				double y = (double) pos.getY() - cameraPos.y;
				double z = (double) pos.getZ() - cameraPos.z;
				batch.renderFilledBox(x, y, z, x + 1.0, y + 1.0, z + 1.0, this.interpolateColor(light.lightLevel()));
				drewAnything = true;
			}
		}
		if (drewAnything) {
			batch.flush();
		}
	}

	/** Fades a near-black floor colour up to the configured colour across light levels 1..15. */
	public Color interpolateColor(int lightLevel) {
		float brightness = Math.max(0.0f, Math.min(1.0f, (float) (lightLevel - 1) / 14.0f));
		Color target = this.color.getValue();
		int red = Math.round(19.0f + (float) (target.getRed() - 19) * brightness);
		int green = Math.round(19.0f + (float) (target.getGreen() - 19) * brightness);
		int blue = Math.round(50.0f + (float) (target.getBlue() - 50) * brightness);
		return new Color(red, green, blue, Math.max(1, Math.min(255, this.alpha.getValue())));
	}

	/** One lit position and the block light measured there, cached until the next rescan. */
	public record LightBlock(BlockPos pos, int lightLevel) {}
}
