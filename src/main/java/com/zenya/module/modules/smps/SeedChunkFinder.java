package com.zenya.module.modules.smps;

import net.minecraft.client.Camera;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;
import com.zenya.utils.RenderUtils;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Flags chunks holding axis-rotated deepslate. World generation only ever places
 * deepslate on the Y axis, so an X- or Z-rotated one was placed by a player.
 *
 * <p>Only the band up to y=16 is examined — both the chunk scan and the block-update
 * path stop there — and {@link #modifiedChunks} is the de-duplication set as well as
 * the render list, so a chunk is reported at most once per session.
 */
public class SeedChunkFinder extends Module {
	public Setting<Color> sideColor;
	public Setting<Color> lineColor;
	public Setting<Boolean> filledEsp;
	public Setting<Color> espColor;
	public Map<ChunkPos, DetectionType> modifiedChunks;

	public SeedChunkFinder() {
		super("Seed Chunk Finder", Category.SMPS);
		this.sideColor = new Setting<>("Side Color", new Color(255, 150, 0, 75));
		this.lineColor = new Setting<>("Line Color", new Color(255, 150, 0, 255));
		this.filledEsp = new Setting<>("Filled ESP", true);
		this.espColor = new Setting<>("ESP Color", new Color(0, 120, 255, 100));
		this.modifiedChunks = new LinkedHashMap<>();
		this.setDescription("Detects player-modified chunks with rotated deepslate.");
		this.addSetting(this.sideColor);
		this.addSetting(this.lineColor);
		this.addSetting(this.filledEsp);
		this.addSetting(this.espColor);
	}

	@Override
	public void onEnable() {
		this.modifiedChunks.clear();
	}

	@Override
	public void onPacketReceive(Packet packet) {
		if (mc.player == null || mc.level == null) {
			return;
		}
		if (packet instanceof ClientboundLevelChunkWithLightPacket chunkPacket) {
			ChunkPos chunkPos = new ChunkPos(chunkPacket.getX(), chunkPacket.getZ());
			if (this.modifiedChunks.containsKey(chunkPos)) {
				return;
			}
			// the packet arrives off-thread; the chunk only exists in the level once it is applied
			mc.execute(() -> {
				LevelChunk chunk = mc.level.getChunk(chunkPos.x, chunkPos.z);
				if (chunk != null) {
					this.checkChunkForRotatedDeepslate(chunk, chunkPos);
				}
			});
		} else if (packet instanceof ClientboundBlockUpdatePacket blockUpdate) {
			BlockPos pos = blockUpdate.getPos();
			ChunkPos chunkPos = new ChunkPos(pos);
			if (this.modifiedChunks.containsKey(chunkPos)) {
				return;
			}
			if (pos.getY() <= 16 && this.isRotatedDeepslate(blockUpdate.getBlockState())) {
				this.flagChunk(chunkPos, DetectionType.DEEPSLATE);
			}
		}
	}

	/** Walks the chunk up to y=16 and bails out on the first rotated deepslate it hits. */
	public void checkChunkForRotatedDeepslate(LevelChunk chunk, ChunkPos chunkPos) {
		for (int x = 0; x < 16; ++x) {
			for (int z = 0; z < 16; ++z) {
				for (int y = 0; y <= 16; ++y) {
					BlockPos pos = new ChunkPos(chunkPos.x, chunkPos.z).getBlockAt(x, y, z);
					if (!this.isRotatedDeepslate(chunk.getBlockState(pos))) continue;
					this.flagChunk(chunkPos, DetectionType.DEEPSLATE);
					return;
				}
			}
		}
	}

	public void flagChunk(ChunkPos chunkPos, DetectionType type) {
		if (!this.modifiedChunks.containsKey(chunkPos)) {
			this.modifiedChunks.put(chunkPos, type);
			this.notify(chunkPos, type.name());
		}
	}

	/**
	 * Pings and toasts the middle of the flagged chunk. The detection name is not shown —
	 * the toast text is fixed — but it is still part of the call so callers can pass one.
	 */
	public void notify(ChunkPos chunkPos, String detectionName) {
		mc.execute(() -> {
			if (mc.player != null) {
				mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 1.0f);
				if (mc.getToastManager() != null) {
					ToastManager toasts = mc.getToastManager();
					int centerZ = chunkPos.z * 16 + 8;
					int centerX = chunkPos.x * 16 + 8;
					SystemToast.addOrUpdate(toasts, SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
							Component.literal("Deep Seed Chunk Finder"),
							Component.literal("Rotated deepslate at X: " + centerX + " Z: " + centerZ));
				}
			}
		});
	}

	public boolean isRotatedDeepslate(BlockState state) {
		if (state.getBlock() == Blocks.DEEPSLATE && state.hasProperty(BlockStateProperties.AXIS)) {
			return state.getValue(BlockStateProperties.AXIS) != Direction.Axis.Y;
		}
		return false;
	}

	@Override
	public void onRender(PoseStack poseStack, float partialTicks) {
		if (this.modifiedChunks.isEmpty()) {
			return;
		}
		Camera camera = RenderUtils.getCamera();
		if (camera == null) {
			return;
		}
		Vec3 cameraPos = RenderUtils.getCameraPos(camera);
		RenderUtils.WorldBatch batch = RenderUtils.beginWorldBatch(poseStack);
		boolean drewAnything = false;
		// A thin bedrock-to-build-limit pillar through the middle of the chunk.
		for (ChunkPos pos : this.modifiedChunks.keySet()) {
			double x = (double) (pos.x * 16 + 8) - cameraPos.x;
			double z = (double) (pos.z * 16 + 8) - cameraPos.z;
			double minY = -64.0 - cameraPos.y;
			double maxY = 320.0 - cameraPos.y;
			// ponytail: the "Side Color" setting and DetectionType.color are never read —
			// the fill uses "ESP Color", so changing Side Color does nothing here
			if (this.filledEsp.getValue()) {
				batch.renderFilledBox(x - 0.1, minY, z - 0.1, x + 0.1, maxY, z + 0.1, this.espColor.getValue());
			}
			batch.renderOutlineBox(x - 0.1, minY, z - 0.1, x + 0.1, maxY, z + 0.1, this.lineColor.getValue());
			drewAnything = true;
		}
		if (drewAnything) {
			batch.flush();
		}
	}

	/** Why a chunk was flagged. Rotated deepslate is currently the only detector. */
	public enum DetectionType {
		DEEPSLATE(new Color(255, 0, 0, 150));

		public final Color color;

		DetectionType(Color color) {
			this.color = color;
		}
	}
}
