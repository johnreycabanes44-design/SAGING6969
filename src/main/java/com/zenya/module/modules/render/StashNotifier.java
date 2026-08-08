package com.zenya.module.modules.render;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.module.modules.common.ClientModuleTools;
import com.zenya.setting.ModeSetting;
import com.zenya.setting.Setting;

import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashSet;
import java.util.Set;

/**
 * Reports loaded chunks that hold an unusual pile of containers, or a spawner — the
 * signature of a stash while travelling.
 *
 * <p>A chunk is added to {@link #processedChunks} before it is scanned, so it is only
 * ever reported once per session; the set is cleared on toggle and on world change.
 * "Min Distance" is a floor, not a ceiling: finds closer than it are ignored, since a
 * base you are standing in is not news.
 */
public class StashNotifier
extends Module {
	public Setting<Integer> minimumStorageCount;
	public Setting<Integer> minimumDistance;
	public Setting<Boolean> criticalSpawner;
	public Setting<Boolean> disconnectOnFind;
	public Setting<Boolean> notifications;
	public ModeSetting notificationMode;
	public Set<ChunkPos> processedChunks;

	public StashNotifier() {
		super("Stash Notifier", Category.RENDER);
		this.minimumStorageCount = new Setting<>("Min Storage Count", 4, 1, 500);
		this.minimumDistance = new Setting<>("Min Distance", 0, 0, 10000);
		this.criticalSpawner = new Setting<>("Critical Spawner", true);
		this.disconnectOnFind = new Setting<>("Disconnect on Find", false);
		this.notifications = new Setting<>("Notifications", true);
		this.notificationMode = new ModeSetting("Notification Mode", "Chat", "Chat", "Toast", "Both");
		this.processedChunks = new HashSet<>();
		this.setDescription("Notifies when loaded chunks contain many storage blocks or a critical spawner.");
		this.addSetting(this.minimumStorageCount);
		this.addSetting(this.minimumDistance);
		this.addSetting(this.criticalSpawner);
		this.addSetting(this.disconnectOnFind);
		this.addSetting(this.notifications);
		this.addSetting(this.notificationMode);
	}

	@Override
	public void onEnable() {
		this.processedChunks.clear();
	}

	@Override
	public void onDisable() {
		this.processedChunks.clear();
	}

	@Override
	public void onWorldChange() {
		this.processedChunks.clear();
	}

	@Override
	public void onTick() {
		if (mc.level == null || mc.player == null) {
			return;
		}
		ChunkPos playerChunk = mc.player.chunkPosition();
		int viewDistance = ClientModuleTools.viewDistanceChunks();
		for (int chunkX = playerChunk.x - viewDistance; chunkX <= playerChunk.x + viewDistance; ++chunkX) {
			for (int chunkZ = playerChunk.z - viewDistance; chunkZ <= playerChunk.z + viewDistance; ++chunkZ) {
				LevelChunk chunk;
				ChunkPos pos = new ChunkPos(chunkX, chunkZ);
				// add() first: an already-seen chunk must not even be looked up
				if (!this.processedChunks.add(pos) || (chunk = mc.level.getChunkSource().getChunk(chunkX, chunkZ, false)) == null) continue;
				this.scanChunk(chunk, pos);
			}
		}
	}

	/** Counts the containers in one chunk and fires a notification when it clears the thresholds. */
	public void scanChunk(LevelChunk chunk, ChunkPos pos) {
		int storageCount = 0;
		boolean hasSpawner = false;
		for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
			if (ClientModuleTools.isStorage(blockEntity)) {
				++storageCount;
			}
			if (!(blockEntity instanceof SpawnerBlockEntity)) continue;
			hasSpawner = true;
			++storageCount;
		}
		double dx = mc.player.getX() - (double)pos.getMiddleBlockX();
		double dz = mc.player.getZ() - (double)pos.getMiddleBlockZ();
		// ponytail: the chunk is already in processedChunks by now, so one bailing out here for
		// being too close is never re-scanned once the player moves away
		if (Math.sqrt(dx * dx + dz * dz) < (double)this.minimumDistance.getValue().intValue()) {
			return;
		}
		boolean spawnerAlert = this.criticalSpawner.getValue() && hasSpawner;
		if (storageCount < this.minimumStorageCount.getValue() && !spawnerAlert) {
			return;
		}
		int x = pos.getMiddleBlockX();
		int y = mc.player.getBlockY();
		int z = pos.getMiddleBlockZ();
		String spawnerSuffix = spawnerAlert ? " §c(Spawner!)" : "";
		String message = "Stash with §e" + storageCount + "§r storages at X: " + x + " Y: " + y + " Z: " + z + spawnerSuffix;
		this.notify(message);
		if (this.disconnectOnFind.getValue()) {
			ClientModuleTools.disconnect("Stash found");
		}
	}

	/** Routes one message to chat, to a toast, or to both, per the notification mode. */
	public void notify(String message) {
		if (!this.notifications.getValue()) {
			return;
		}
		if (this.notificationMode.is("Chat") || this.notificationMode.is("Both")) {
			ClientModuleTools.chat("Stash Notifier", message);
		}
		if (!this.notificationMode.is("Toast") && !this.notificationMode.is("Both")) {
			return;
		}
		ToastManager toasts = mc.getToastManager();
		if (toasts == null) {
			return;
		}
		// toasts render no colour codes, so strip the ones the chat line carries
		String plain = message.replace("§e", "").replace("§r", "").replace("§c", "");
		SystemToast.addOrUpdate(toasts, SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
				Component.literal("Stash Notifier"), Component.literal(plain));
	}
}
