package com.zenya.module.modules.combat;

import com.zenya.module.ActivatableModule;
import com.zenya.module.Category;
import com.zenya.setting.Setting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import org.lwjgl.glfw.GLFW;

/**
 * Charges and detonates the respawn anchor under the crosshair while right-click is held.
 *
 * <p>Driven straight off the mouse button rather than a target scan, so it only ever acts on
 * the block the player is already aiming at. Each of the three delays owns its own counter
 * because a hotbar switch and the use packet must not land on the same tick.
 *
 * <p>Right-click also means "eat" and "block", so the module stands down whenever food or a
 * shield is in hand — otherwise it would steal the click.
 */
public class AnchorMacro extends ActivatableModule {
	public Setting<Float> switchDelay;
	public Setting<Float> glowstoneDelay;
	public Setting<Float> explodeDelay;
	public Setting<Float> totemSlot;
	public Setting<Boolean> switchBack;
	public int switchCounter;
	public int glowstoneDelayCounter;
	public int explodeDelayCounter;
	public boolean waitingForPop;

	public AnchorMacro() {
		super("Anchor Macro", Category.COMBAT);
		this.switchDelay = new Setting<>("Switch Delay", Float.valueOf(0.0f), Float.valueOf(0.0f), Float.valueOf(20.0f));
		this.glowstoneDelay = new Setting<>("Glowstone Delay", Float.valueOf(0.0f), Float.valueOf(0.0f), Float.valueOf(20.0f));
		this.explodeDelay = new Setting<>("Explode Delay", Float.valueOf(0.0f), Float.valueOf(0.0f), Float.valueOf(20.0f));
		this.totemSlot = new Setting<>("Totem Slot", Float.valueOf(1.0f), Float.valueOf(1.0f), Float.valueOf(9.0f));
		this.switchBack = new Setting<>("Switch Back", false);
		this.waitingForPop = false;
		this.addSetting(this.switchDelay);
		this.addSetting(this.glowstoneDelay);
		this.addSetting(this.explodeDelay);
		this.addSetting(this.totemSlot);
		this.addSetting(this.switchBack);
	}

	@Override
	public void onEnable() {
		this.resetCounters();
		this.waitingForPop = false;
		super.onEnable();
	}

	@Override
	public void onDisable() {
		this.resetCounters();
		this.waitingForPop = false;
		super.onDisable();
	}

	/** A health update after the detonation is the totem pop, the cue to go back to the anchor. */
	@Override
	public void onPacketReceive(Packet packet) {
		if (this.switchBack.getValue() && this.waitingForPop && packet instanceof ClientboundSetHealthPacket) {
			this.waitingForPop = false;
			if (mc.player == null) {
				return;
			}
			int anchorSlot = this.findItemSlot(Items.RESPAWN_ANCHOR);
			if (anchorSlot != -1) {
				mc.player.getInventory().setSelectedSlot(anchorSlot);
			}
		}
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.level == null || mc.gameMode == null) {
			return;
		}
		if (mc.screen != null) {
			return;
		}
		if (this.isShieldOrFoodActive()) {
			return;
		}
		if (!this.isRightClickHeld()) {
			this.resetCounters();
			return;
		}
		this.handleAnchorInteraction();
	}

	/** Holding right-click on food or a shield outranks the macro, so it stands down. */
	public boolean isShieldOrFoodActive() {
		boolean holdingFood = mc.player.getMainHandItem().getItem().components().has(DataComponents.FOOD)
				|| mc.player.getOffhandItem().getItem().components().has(DataComponents.FOOD);
		boolean holdingShield = mc.player.getMainHandItem().getItem() instanceof ShieldItem
				|| mc.player.getOffhandItem().getItem() instanceof ShieldItem;
		boolean usingItem = this.isRightClickHeld();
		return (holdingFood || holdingShield) && usingItem;
	}

	public boolean isRightClickHeld() {
		return mc.getWindow() != null
				&& GLFW.glfwGetMouseButton(mc.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
	}

	/** An uncharged anchor wants glowstone, a charged one wants the trigger click. */
	public void handleAnchorInteraction() {
		HitResult crosshair = mc.hitResult;
		if (!(crosshair instanceof BlockHitResult anchorHit)) {
			return;
		}
		if (anchorHit.getType() != HitResult.Type.BLOCK) {
			return;
		}
		BlockPos anchorPos = anchorHit.getBlockPos();
		BlockState anchorState = mc.level.getBlockState(anchorPos);
		if (!anchorState.is(Blocks.RESPAWN_ANCHOR)) {
			return;
		}
		// The macro drives its own clicks; leaving use held would fire an extra one per tick.
		mc.options.keyUse.setDown(false);
		int charges = anchorState.getValue(RespawnAnchorBlock.CHARGE);
		if (charges == 0) {
			this.placeGlowstone(anchorHit);
		} else {
			this.explodeAnchor(anchorHit);
		}
	}

	public void placeGlowstone(BlockHitResult anchorHit) {
		if (!mc.player.getMainHandItem().is(Items.GLOWSTONE)) {
			if (this.switchCounter < this.switchDelay.getValue().intValue()) {
				this.switchCounter += 1;
				return;
			}
			this.switchCounter = 0;
			if (!this.swapToItem(Items.GLOWSTONE)) {
				return;
			}
		}
		if (mc.player.getMainHandItem().is(Items.GLOWSTONE)) {
			if (this.glowstoneDelayCounter < this.glowstoneDelay.getValue().intValue()) {
				this.glowstoneDelayCounter += 1;
				return;
			}
			this.glowstoneDelayCounter = 0;
			this.interactWith(anchorHit);
		}
	}

	/** Detonates from the totem slot, so the pop is already covered when the anchor blows. */
	public void explodeAnchor(BlockHitResult anchorHit) {
		int slot = Math.max(0, Math.min(8, this.totemSlot.getValue().intValue() - 1));
		if (mc.player.getInventory().getSelectedSlot() != slot) {
			if (this.switchCounter < this.switchDelay.getValue().intValue()) {
				this.switchCounter += 1;
				return;
			}
			this.switchCounter = 0;
			mc.player.getInventory().setSelectedSlot(slot);
		}
		if (mc.player.getInventory().getSelectedSlot() == slot) {
			if (this.explodeDelayCounter < this.explodeDelay.getValue().intValue()) {
				this.explodeDelayCounter += 1;
				return;
			}
			this.explodeDelayCounter = 0;
			this.interactWith(anchorHit);
			if (this.switchBack.getValue()) {
				this.waitingForPop = true;
			}
		}
	}

	/** @return the hotbar slot holding the item, or -1. */
	public int findItemSlot(Item item) {
		for (int slot = 0; slot < 9; ++slot) {
			if (mc.player.getInventory().getItem(slot).is(item)) {
				return slot;
			}
		}
		return -1;
	}

	public boolean swapToItem(Item item) {
		int slot = this.findItemSlot(item);
		if (slot != -1) {
			mc.player.getInventory().setSelectedSlot(slot);
			return true;
		}
		return false;
	}

	public void interactWith(BlockHitResult anchorHit) {
		mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, anchorHit);
		mc.player.swing(InteractionHand.MAIN_HAND);
	}

	public void resetCounters() {
		this.switchCounter = 0;
		this.glowstoneDelayCounter = 0;
		this.explodeDelayCounter = 0;
	}
}
