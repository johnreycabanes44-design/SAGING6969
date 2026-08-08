package com.zenya.module.modules.donut;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.module.modules.common.ClientModuleTools;
import com.zenya.setting.Setting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;

import java.awt.Color;
import java.util.Map;
import java.util.Set;

/**
 * Scores chunks for the two things that only accumulate with time: beehives filled to
 * the brim and amethyst clusters somebody has lit up in the deepslate layers. Neither
 * happens by accident, so a high score means the chunk has been lived in, not passed through.
 *
 * <p>A chunk is scanned once and never again — {@link #scannedChunks} is the guard, and it
 * is only emptied when the module is toggled or the world changes. That is deliberate: the
 * scan walks 16x16x(minY..80) block states and is far too costly to repeat.
 */
public class ChunkDetector extends Module {
	public Setting<Integer> scanRadius;
	public Setting<Integer> minScore;
	public Setting<Integer> beehiveWeight;
	public Setting<Integer> amethystWeight;
	public Setting<Boolean> fillChunk;
	public Set<ChunkPos> scannedChunks;
	public Map<ChunkPos, ClientModuleTools.ChunkMark> flaggedChunks;
	public int tickCounter;

	public ChunkDetector() {
		super("Chunk Detector", Category.DONUT);
		this.scanRadius = new Setting<>("Scan Radius", 4, 1, 10);
		this.minScore = new Setting<>("Min Score", 3, 1, 40);
		this.beehiveWeight = new Setting<>("Beehive Weight", 3, 1, 10);
		this.amethystWeight = new Setting<>("Amethyst Weight", 1, 1, 10);
		this.fillChunk = new Setting<>("Fill Chunk", false);
		this.scannedChunks = ClientModuleTools.chunkSet();
		this.flaggedChunks = ClientModuleTools.chunkMap();
		this.setDescription("Scores old grown chunks using full beehives and lit deep amethyst clusters.");
		this.addSetting(this.scanRadius);
		this.addSetting(this.minScore);
		this.addSetting(this.beehiveWeight);
		this.addSetting(this.amethystWeight);
		this.addSetting(this.fillChunk);
	}

	@Override
	public void onEnable() {
		this.scannedChunks.clear();
		this.flaggedChunks.clear();
		this.tickCounter = 0;
	}

	@Override
	public void onDisable() {
		this.scannedChunks.clear();
		this.flaggedChunks.clear();
	}

	@Override
	public void onWorldChange() {
		this.scannedChunks.clear();
		this.flaggedChunks.clear();
		this.tickCounter = 0;
	}

	@Override
	public void onTick() {
		if (mc.level == null || mc.player == null) {
			return;
		}

		// One sweep every three seconds; the per-chunk scan is heavy enough that running it
		// every tick would stall the client whenever the player crosses a chunk border.
		if (++this.tickCounter % 60 != 0) {
			return;
		}

		ChunkPos center = mc.player.chunkPosition();
		int radius = this.scanRadius.getValue();

		for (int chunkX = center.x - radius; chunkX <= center.x + radius; ++chunkX) {
			for (int chunkZ = center.z - radius; chunkZ <= center.z + radius; ++chunkZ) {
				ChunkPos pos = new ChunkPos(chunkX, chunkZ);

				if (!this.scannedChunks.add(pos)) {
					continue;
				}

				LevelChunk chunk = mc.level.getChunkSource().getChunk(chunkX, chunkZ, false);

				if (chunk == null) {
					continue;
				}

				this.scanChunk(chunk, pos);
			}
		}
	}

	/**
	 * Weighs the chunk's full beehives against its lit sub-zero amethyst and, when the total
	 * clears {@link #minScore}, records the chunk for the overlay and announces it in chat.
	 */
	public void scanChunk(LevelChunk chunk, ChunkPos pos) {
		if (mc.level == null) {
			return;
		}

		int beehives = 0;
		int amethysts = 0;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		// Neither marker occurs meaningfully above the surface band, so cap the column at 80.
		int maxScanY = Math.min(80, mc.level.getMaxY());

		for (int offsetX = 0; offsetX < 16; ++offsetX) {
			for (int offsetZ = 0; offsetZ < 16; ++offsetZ) {
				int x = pos.getMinBlockX() + offsetX;
				int z = pos.getMinBlockZ() + offsetZ;

				for (int y = mc.level.getMinY(); y <= maxScanY; ++y) {
					cursor.set(x, y, z);
					BlockState state = chunk.getBlockState(cursor);
					Block block = state.getBlock();

					if (ClientModuleTools.isBeeNest(block)
							&& state.hasProperty(BlockStateProperties.LEVEL_HONEY)
							&& state.getValue(BlockStateProperties.LEVEL_HONEY) >= 5) {
						++beehives;
						continue;
					}

					// Amethyst only counts below y=0 and only when someone has put a light on it —
					// a naturally generated geode is pitch dark.
					if (y >= 0 || !ClientModuleTools.isAmethyst(block)
							|| mc.level.getBrightness(LightLayer.BLOCK, cursor) <= 0) {
						continue;
					}

					++amethysts;
				}
			}
		}

		int score = beehives * this.beehiveWeight.getValue() + amethysts * this.amethystWeight.getValue();

		if (score < this.minScore.getValue()) {
			return;
		}

		String label = "Old Chunk | B:" + beehives + " A:" + amethysts + " S:" + score;

		this.flaggedChunks.put(pos, new ClientModuleTools.ChunkMark(pos, label, new Color(180, 0, 255, 95)));
		ClientModuleTools.chat("Chunk Detector", label + " at " + pos.x + ", " + pos.z);
	}

	@Override
	public void onRender(PoseStack poseStack, float partialTicks) {
		ClientModuleTools.renderChunks(poseStack, this.flaggedChunks.values(), this.fillChunk.getValue(), 102400.0);
	}
}
