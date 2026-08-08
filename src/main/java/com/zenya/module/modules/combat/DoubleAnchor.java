package com.zenya.module.modules.combat;

import com.zenya.module.ActivatableModule;
import com.zenya.module.Category;
import com.zenya.setting.Setting;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Fires two anchors back to back at whatever the crosshair is on, on one key press.
 *
 * <p>Unlike the other anchor modules this is a one-shot burst, not a toggle: enabling only
 * arms it, the activation key starts the run. {@link #step} then walks a fixed script one
 * entry per tick (delayed by {@link #switchDelay}) because the server must see each hotbar
 * change land before the next use packet.
 *
 * <p>The script re-reads the crosshair every tick and aborts the whole run the moment it
 * leaves a solid block, so a half-finished sequence can never continue against a new target.
 */
public class DoubleAnchor extends ActivatableModule {
	public Setting<Float> switchDelay;
	public Setting<Float> totemSlot;
	public Setting<Boolean> switchBack;
	public int delayCounter;
	public int step;
	public boolean isAnchoring;
	public BlockPos lastAnchorPos;
	public boolean waitingForPop;

	public DoubleAnchor() {
		super("Double Anchor", Category.COMBAT);
		this.switchDelay = new Setting<>("Delay", Float.valueOf(0.0f), Float.valueOf(0.0f), Float.valueOf(20.0f));
		this.totemSlot = new Setting<>("Totem Slot", Float.valueOf(1.0f), Float.valueOf(1.0f), Float.valueOf(9.0f));
		this.switchBack = new Setting<>("Switch Back", false);
		this.delayCounter = 0;
		this.step = 0;
		this.isAnchoring = false;
		this.lastAnchorPos = null;
		this.waitingForPop = false;
		this.setDescription("Two-tap anchor sequence triggered by activation key");
		this.addSetting(this.switchDelay);
		this.addSetting(this.totemSlot);
		this.addSetting(this.switchBack);
	}

	@Override
	public void onEnable() {
		this.resetState();
		this.isAnchoring = false;
		this.waitingForPop = false;
		this.lastAnchorPos = null;
		super.onEnable();
	}

	@Override
	public void onDisable() {
		this.resetState();
		this.isAnchoring = false;
		this.waitingForPop = false;
		this.lastAnchorPos = null;
		super.onDisable();
	}

	/** Arms one burst; enabling the module on its own does nothing until this fires. */
	@Override
	public void onActivationKeyPressed() {
		if (!this.isEnabled()) {
			return;
		}
		this.resetState();
		this.isAnchoring = true;
		this.waitingForPop = false;
		this.lastAnchorPos = null;
	}

	/** A health update after the detonation is the totem pop, the cue to go back to the anchor. */
	@Override
	public void onPacketReceive(Packet packet) {
		if (this.switchBack.getValue() && this.waitingForPop && packet instanceof ClientboundSetHealthPacket) {
			this.waitingForPop = false;
			if (mc.player == null) {
				return;
			}
			int anchorSlot = this.findItemInHotbar(Items.RESPAWN_ANCHOR);
			if (anchorSlot != -1) {
				mc.player.getInventory().setSelectedSlot(anchorSlot);
			}
		}
	}

	@Override
	public void onTick() {
		if (!this.isAnchoring) {
			return;
		}
		if (mc.screen != null) {
			return;
		}
		if (mc.player == null || mc.level == null) {
			return;
		}
		if (!this.hasRequiredItems()) {
			this.isAnchoring = false;
			this.resetState();
			return;
		}
		HitResult crosshair = mc.hitResult;
		if (!(crosshair instanceof BlockHitResult anchorHit)) {
			this.isAnchoring = false;
			this.resetState();
			return;
		}
		if (mc.level.getBlockState(anchorHit.getBlockPos()).is(Blocks.AIR)) {
			this.isAnchoring = false;
			this.resetState();
			return;
		}
		int delayTicks = Math.max(0, this.switchDelay.getValue().intValue());
		if (this.delayCounter < delayTicks) {
			this.delayCounter += 1;
			return;
		}
		switch (this.step) {
			case 0: {
				this.swapToItem(Items.RESPAWN_ANCHOR);
				break;
			}
			case 1: {
				this.interactWithBlock(anchorHit);
				break;
			}
			case 2: {
				this.swapToItem(Items.GLOWSTONE);
				break;
			}
			case 3: {
				this.interactWithBlock(anchorHit);
				break;
			}
			case 4: {
				this.swapToItem(Items.RESPAWN_ANCHOR);
				break;
			}
			case 5: {
				this.interactWithBlock(anchorHit);
				this.interactWithBlock(anchorHit);
				break;
			}
			case 6: {
				this.swapToItem(Items.GLOWSTONE);
				break;
			}
			case 7: {
				this.interactWithBlock(anchorHit);
				break;
			}
			case 8: {
				int slot = Math.max(0, Math.min(8, this.totemSlot.getValue().intValue() - 1));
				mc.player.getInventory().setSelectedSlot(slot);
				this.lastAnchorPos = anchorHit.getBlockPos();
				break;
			}
			case 9: {
				this.interactWithBlock(anchorHit);
				if (this.switchBack.getValue()) {
					this.waitingForPop = true;
				}
				break;
			}
			case 10: {
				this.isAnchoring = false;
				this.resetState();
				return;
			}
		}
		this.step += 1;
	}

	/** Rewinds the script to its first entry; does not disarm the burst. */
	public void resetState() {
		this.delayCounter = 0;
		this.step = 0;
	}

	/** Both halves of the sequence need their own item in the hotbar, or the run is pointless. */
	public boolean hasRequiredItems() {
		boolean hasAnchor = false;
		boolean hasGlowstone = false;
		for (int slot = 0; slot < 9; ++slot) {
			ItemStack stack = mc.player.getInventory().getItem(slot);
			if (stack.is(Items.RESPAWN_ANCHOR)) {
				hasAnchor = true;
			}
			if (stack.is(Items.GLOWSTONE)) {
				hasGlowstone = true;
			}
		}
		return hasAnchor && hasGlowstone;
	}

	/** @return the hotbar slot holding the item, or -1. */
	public int findItemInHotbar(Item item) {
		for (int slot = 0; slot < 9; ++slot) {
			if (mc.player.getInventory().getItem(slot).is(item)) {
				return slot;
			}
		}
		return -1;
	}

	public void swapToItem(Item item) {
		int slot = this.findItemInHotbar(item);
		if (slot != -1) {
			mc.player.getInventory().setSelectedSlot(slot);
		}
	}

	public void interactWithBlock(BlockHitResult anchorHit) {
		mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, anchorHit);
		mc.player.swing(InteractionHand.MAIN_HAND);
	}
}
