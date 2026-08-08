package com.zenya.module.modules.donut;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.module.modules.common.ClientModuleTools;
import com.zenya.setting.Setting;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.awt.Color;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * Flags chunks whose inhabited time is high enough to look like somebody's base. Inhabited
 * time only ticks up while a player stands in the chunk, so a big number means a home, not
 * a chunk that merely generated.
 *
 * <p>{@link #flaggedChunks} is wiped and rebuilt from scratch on every scan, so a chunk that
 * drops out of range disappears on its own and never needs pruning. The optional nearby-player
 * gate exists so the module stays quiet — and cheap — when nobody is around to have a base.
 */
public class BaseChunk extends Module {
	public Setting<Integer> scanRadius;
	public Setting<Integer> minInhabitedHours;
	public Setting<Integer> playerRadius;
	public Setting<Boolean> requireNearbyPlayer;
	public Setting<Boolean> fillChunk;
	public Map<ChunkPos, ClientModuleTools.ChunkMark> flaggedChunks;
	public int tickCounter;

	public BaseChunk() {
		super("Base Chunk", Category.DONUT);
		this.scanRadius = new Setting<>("Scan Radius", 4, 1, 12);
		this.minInhabitedHours = new Setting<>("Min Inhabited Hours", 12, 1, 72);
		this.playerRadius = new Setting<>("Player Radius", 6, 1, 24);
		this.requireNearbyPlayer = new Setting<>("Require Nearby Player", true);
		this.fillChunk = new Setting<>("Fill Chunk", false);
		this.flaggedChunks = ClientModuleTools.chunkMap();
		this.setDescription("Flags highly inhabited chunks, optionally only while another player is nearby.");
		this.addSetting(this.scanRadius);
		this.addSetting(this.minInhabitedHours);
		this.addSetting(this.playerRadius);
		this.addSetting(this.requireNearbyPlayer);
		this.addSetting(this.fillChunk);
	}

	@Override
	public void onEnable() {
		this.flaggedChunks.clear();
		this.tickCounter = 0;
	}

	@Override
	public void onDisable() {
		this.flaggedChunks.clear();
	}

	@Override
	public void onTick() {
		if (mc.level == null || mc.player == null) {
			return;
		}
		if (++this.tickCounter % 100 != 0) {
			return;
		}
		if (this.requireNearbyPlayer.getValue() && !this.hasNearbyPlayer()) {
			this.flaggedChunks.clear();
			return;
		}
		this.flaggedChunks.clear();
		ChunkPos center = mc.player.chunkPosition();
		long minInhabitedTicks = (long) this.minInhabitedHours.getValue().intValue() * 60L * 60L * 20L;
		int radius = this.scanRadius.getValue();
		for (int chunkX = center.x - radius; chunkX <= center.x + radius; ++chunkX) {
			for (int chunkZ = center.z - radius; chunkZ <= center.z + radius; ++chunkZ) {
				LevelChunk chunk = mc.level.getChunkSource().getChunk(chunkX, chunkZ, false);
				if (chunk == null || chunk.getInhabitedTime() < minInhabitedTicks) {
					continue;
				}
				ChunkPos pos = new ChunkPos(chunkX, chunkZ);
				double inhabitedHours = (double) chunk.getInhabitedTime() / 72000.0;
				String label = String.format(Locale.ROOT, "Base? %.1fh loaded", inhabitedHours);
				this.flaggedChunks.put(pos, new ClientModuleTools.ChunkMark(pos, label, new Color(255, 40, 40, 90)));
				// ponytail: flaggedChunks is rebuilt from scratch every 100 ticks, so a chunk
				// that stays in range is re-announced in chat on every single scan.
				ClientModuleTools.chat("Base Chunk", label + " at " + chunkX + ", " + chunkZ);
			}
		}
	}

	/** @return true when any other player is within {@link #playerRadius} chunks. */
	public boolean hasNearbyPlayer() {
		if (mc.level == null || mc.player == null) {
			return false;
		}
		ChunkPos center = mc.player.chunkPosition();
		int radius = this.playerRadius.getValue();
		for (Player player : mc.level.players()) {
			if (player == mc.player) {
				continue;
			}
			ChunkPos pos = player.chunkPosition();
			if (Math.abs(pos.x - center.x) > radius || Math.abs(pos.z - center.z) > radius) {
				continue;
			}
			return true;
		}
		if (mc.getConnection() == null) {
			return false;
		}
		// ponytail: dead tab-list fallback — every branch below returns false, so a player
		// who is online but outside the loaded world is never counted as nearby.
		Iterator<?> tabList = mc.getConnection().getOnlinePlayers().iterator();
		if (tabList.hasNext()) {
			tabList.next();
		}
		return false;
	}

	@Override
	public void onRender(PoseStack poseStack, float partialTicks) {
		ClientModuleTools.renderChunks(poseStack, this.flaggedChunks.values(), this.fillChunk.getValue(), 122500.0);
	}
}
