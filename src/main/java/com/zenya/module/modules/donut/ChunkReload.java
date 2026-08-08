package com.zenya.module.modules.donut;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.module.modules.common.ClientModuleTools;
import com.zenya.setting.Setting;

/**
 * Forces the client to re-request the chunks around the player by briefly dropping
 * the render distance and then putting it back.
 *
 * <p>Runs as a three-step state machine because the restore has to happen a few ticks
 * after the drop — restoring in the same tick never unloads anything. The cooldown and
 * the {@code hasTriggered} latch keep it from firing every tick while the player stays
 * below the trigger height.
 */
public class ChunkReload
extends Module {
	public Setting<Double> triggerY;
	public Setting<Integer> normalRenderDistance;
	public Setting<Integer> lowRenderDistance;
	public Setting<Integer> reloadDelay;
	public Setting<Integer> cooldownSeconds;
	public State state;
	public int tickCounter;
	public long lastReloadTime;
	public boolean hasTriggered;

	public ChunkReload() {
		super("Chunk Reload", Category.DONUT);
		this.triggerY = new Setting<>("Trigger Y", -1.0, -64.0, 64.0);
		this.normalRenderDistance = new Setting<>("Normal Render Distance", 32, 2, 64);
		this.lowRenderDistance = new Setting<>("Low Render Distance", 2, 2, 16);
		this.reloadDelay = new Setting<>("Reload Delay Ticks", 20, 5, 100);
		this.cooldownSeconds = new Setting<>("Cooldown Seconds", 10, 1, 60);
		this.state = State.IDLE;
		this.setDescription("Temporarily drops render distance to force a chunk reload.");
		this.addSetting(this.triggerY);
		this.addSetting(this.normalRenderDistance);
		this.addSetting(this.lowRenderDistance);
		this.addSetting(this.reloadDelay);
		this.addSetting(this.cooldownSeconds);
	}

	@Override
	public void onEnable() {
		this.state = State.IDLE;
		this.tickCounter = 0;
		this.hasTriggered = false;
	}

	@Override
	public void onDisable() {
		this.setRenderDistance(this.normalRenderDistance.getValue());
		this.state = State.IDLE;
		this.hasTriggered = false;
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.options == null) {
			return;
		}
		boolean belowTrigger = mc.player.getY() <= this.triggerY.getValue();
		long now = System.currentTimeMillis();
		long cooldownMillis = (long) this.cooldownSeconds.getValue().intValue() * 1000L;
		switch (this.state) {
			case IDLE -> {
				if (belowTrigger && !this.hasTriggered && now - this.lastReloadTime >= cooldownMillis) {
					this.hasTriggered = true;
					this.state = State.DROPPED;
					this.tickCounter = 0;
					this.setRenderDistance(this.lowRenderDistance.getValue());
					ClientModuleTools.chat("Chunk Reload",
							"Dropping render distance to " + this.lowRenderDistance.getValue() + " chunks.");
				}
				// Re-arm only once the player climbs back above the trigger height.
				if (!belowTrigger) {
					this.hasTriggered = false;
				}
			}
			case DROPPED -> {
				if (++this.tickCounter >= this.reloadDelay.getValue()) {
					this.state = State.RESTORING;
					this.tickCounter = 0;
					this.setRenderDistance(this.normalRenderDistance.getValue());
					this.lastReloadTime = now;
					ClientModuleTools.chat("Chunk Reload",
							"Chunks reloaded. Render distance restored to " + this.normalRenderDistance.getValue() + ".");
				}
			}
			case RESTORING -> {
				this.state = State.IDLE;
				this.hasTriggered = false;
			}
		}
	}

	public void setRenderDistance(int distance) {
		if (mc.options != null) {
			mc.options.renderDistance().set(distance);
		}
	}

	/** Where the drop/restore cycle currently is. */
	public enum State {
		IDLE,
		DROPPED,
		RESTORING
	}
}
