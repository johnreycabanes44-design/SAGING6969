package com.zenya.module.modules.combat;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.lwjgl.glfw.GLFW;

/**
 * Anchor macro driven off whatever the crosshair is on, rather than off a target scan.
 *
 * <p>Every step (fill the totem slot, wall off one side, swap to glowstone, charge, swap
 * back, detonate) is its own {@link Phase} because the server has to see the slot change
 * before the use packet. {@link #tickFast} re-enters itself after a phase change, so with
 * all delays at zero the whole sequence still collapses into a single tick.
 *
 * <p>Looking away from the anchor resets the machine, which is what keeps a half-finished
 * sequence from firing at the wrong block.
 */
public class SafeAnchor extends Module {
	public Setting<Integer> switchDelay;
	public Setting<Integer> glowstoneDelay;
	public Setting<Boolean> autoExplode;
	public Setting<Integer> explodeDelay;
	public Setting<Integer> totemSlot;
	public Setting<Boolean> autoFillTotem;
	public Setting<Boolean> placeShield;
	public Setting<Integer> shieldDelay;
	public Phase phase;
	public int delay;
	public int chargedWait;

	public SafeAnchor() {
		super("Safe Anchor", Category.COMBAT);
		this.switchDelay = new Setting<>("Switch Delay", 0, 0, 20);
		this.glowstoneDelay = new Setting<>("Glowstone Delay", 0, 0, 20);
		this.autoExplode = new Setting<>("Auto Explode", false);
		this.explodeDelay = new Setting<>("Explode Delay", 0, 0, 20);
		this.totemSlot = new Setting<>("Totem Slot", 1, 1, 9);
		this.autoFillTotem = new Setting<>("Auto Fill Totem", true);
		this.placeShield = new Setting<>("Place Shield", false);
		this.shieldDelay = new Setting<>("Shield Delay", 0, 0, 20);
		this.phase = Phase.IDLE;
		this.setDescription("Safer anchor macro for PvP with glowstone fill, slot switching, and optional shielding.");
		this.addSetting(this.switchDelay);
		this.addSetting(this.glowstoneDelay);
		this.addSetting(this.autoExplode);
		this.addSetting(this.explodeDelay);
		this.addSetting(this.totemSlot);
		this.addSetting(this.autoFillTotem);
		this.addSetting(this.placeShield);
		this.addSetting(this.shieldDelay);
	}

	@Override
	public void onEnable() {
		this.reset();
	}

	@Override
	public void onDisable() {
		this.reset();
	}

	@Override
	public void onTick() {
		if (mc.level == null || mc.player == null || mc.gameMode == null) {
			return;
		}
		if (this.isShieldOrFoodActive()) {
			return;
		}
		HitResult crosshair = mc.hitResult;
		if (!(crosshair instanceof BlockHitResult anchorHit)) {
			this.reset();
			return;
		}
		if (!mc.level.getBlockState(anchorHit.getBlockPos()).is(Blocks.RESPAWN_ANCHOR)) {
			this.reset();
			return;
		}
		// Sneaking would turn the charge click into a block placement against the anchor.
		mc.options.keyShift.setDown(false);
		this.tickFast(anchorHit);
	}

	/**
	 * Advances the state machine one step. Re-enters itself whenever a phase changes with
	 * no delay configured, so a zero-delay setup runs the whole sequence in one tick.
	 */
	public void tickFast(BlockHitResult anchorHit) {
		switch (this.phase) {
			case IDLE: {
				boolean uncharged = this.isUncharged(anchorHit.getBlockPos());
				boolean charged = this.isCharged(anchorHit.getBlockPos());
				if (!uncharged && !charged) {
					return;
				}
				this.phase = this.needsFill() ? Phase.FILL_TOTEM : (charged ? Phase.SWITCH_TOTEM : Phase.PLACE_SHIELD);
				this.tickFast(anchorHit);
				break;
			}
			case FILL_TOTEM: {
				if (!this.needsFill()) {
					this.delay = 0;
					this.phase = this.isCharged(anchorHit.getBlockPos()) ? Phase.SWITCH_TOTEM : Phase.PLACE_SHIELD;
					this.tickFast(anchorHit);
					return;
				}
				int glowstoneSlot = this.findInInventory(Items.GLOWSTONE);
				if (glowstoneSlot == -1) {
					this.delay = 0;
					this.phase = this.isCharged(anchorHit.getBlockPos()) ? Phase.SWITCH_TOTEM : Phase.PLACE_SHIELD;
					return;
				}
				this.moveGlowstoneToTotemSlot(glowstoneSlot);
				break;
			}
			case PLACE_SHIELD: {
				if (!this.placeShield.getValue()) {
					this.delay = 0;
					this.phase = Phase.SWITCH_GLOW;
					this.tickFast(anchorHit);
					return;
				}
				if (this.delay++ < this.shieldDelay.getValue()) {
					return;
				}
				this.delay = 0;
				this.doPlaceShield(anchorHit);
				this.phase = Phase.SWITCH_GLOW;
				if (this.shieldDelay.getValue() <= 0) {
					this.tickFast(anchorHit);
				}
				break;
			}
			case SWITCH_GLOW: {
				if (!this.isHoldingGlowstone()) {
					if (this.delay++ < this.switchDelay.getValue()) {
						return;
					}
					this.delay = 0;
					int glowstoneSlot = this.findInHotbar(Items.GLOWSTONE);
					if (glowstoneSlot == -1) {
						this.reset();
						return;
					}
					this.sendSlot(glowstoneSlot);
					if (this.switchDelay.getValue() > 0) {
						this.phase = Phase.PLACE_GLOW;
						return;
					}
				}
				this.delay = 0;
				this.phase = Phase.PLACE_GLOW;
				this.tickFast(anchorHit);
				break;
			}
			case PLACE_GLOW: {
				if (this.delay++ < this.glowstoneDelay.getValue()) {
					return;
				}
				this.delay = 0;
				this.interactBlock(anchorHit);
				mc.player.swing(InteractionHand.MAIN_HAND);
				this.chargedWait = 0;
				this.phase = Phase.SWITCH_TOTEM;
				break;
			}
			case SWITCH_TOTEM: {
				if (!this.isCharged(anchorHit.getBlockPos())) {
					// Give the server ten ticks to confirm the charge before giving up.
					if (++this.chargedWait > 10) {
						this.chargedWait = 0;
						this.reset();
					}
					return;
				}
				this.chargedWait = 0;
				if (this.needsFill() && this.findInInventory(Items.GLOWSTONE) != -1) {
					this.phase = Phase.FILL_TOTEM;
					this.tickFast(anchorHit);
					return;
				}
				int targetSlot = this.totemSlot.getValue() - 1;
				if (mc.player.getInventory().getSelectedSlot() != targetSlot) {
					if (this.delay++ < this.switchDelay.getValue()) {
						return;
					}
					this.delay = 0;
					this.sendSlot(targetSlot);
					if (this.switchDelay.getValue() > 0) {
						this.phase = Phase.EXPLODE;
						return;
					}
				}
				this.delay = 0;
				if (this.autoExplode.getValue()) {
					this.phase = Phase.EXPLODE;
					this.tickFast(anchorHit);
					break;
				}
				this.reset();
				break;
			}
			case EXPLODE: {
				if (!this.isCharged(anchorHit.getBlockPos())) {
					this.reset();
					return;
				}
				if (this.delay++ < this.explodeDelay.getValue()) {
					return;
				}
				this.delay = 0;
				this.interactBlock(anchorHit);
				mc.player.swing(InteractionHand.MAIN_HAND);
				this.reset();
			}
		}
	}

	/**
	 * Puts a block on the side of us facing the anchor so the blast is partly absorbed.
	 * Silently gives up if the spot is occupied, has nothing to place against, or we have
	 * no block in the hotbar.
	 */
	public void doPlaceShield(BlockHitResult anchorHit) {
		BlockPos shieldPos = this.getShieldPos(anchorHit.getBlockPos());
		if (!mc.level.getBlockState(shieldPos).isAir()) {
			return;
		}
		Direction supportDirection = null;
		BlockPos supportPos = null;
		for (Direction direction : Direction.values()) {
			BlockPos neighbour = shieldPos.relative(direction);
			if (mc.level.getBlockState(neighbour).isAir()) continue;
			supportDirection = direction;
			supportPos = neighbour;
			break;
		}
		if (supportDirection == null || supportPos == null) {
			return;
		}
		int blockSlot = this.findShieldBlockSlot();
		if (blockSlot == -1) {
			return;
		}
		int previousSlot = mc.player.getInventory().getSelectedSlot();
		this.sendSlot(blockSlot);
		Direction placeFace = supportDirection.getOpposite();
		Vec3 hitVec = Vec3.atCenterOf(supportPos).add(Vec3.atLowerCornerOf(placeFace.getUnitVec3i()).scale(0.5));
		this.interactBlock(new BlockHitResult(hitVec, placeFace, supportPos, false));
		mc.player.swing(InteractionHand.MAIN_HAND);
		this.sendSlot(previousSlot);
	}

	/** The block next to us on the dominant horizontal axis towards the anchor. */
	public BlockPos getShieldPos(BlockPos anchorPos) {
		BlockPos playerPos = mc.player.blockPosition();
		int deltaX = anchorPos.getX() - playerPos.getX();
		int deltaZ = anchorPos.getZ() - playerPos.getZ();
		int stepX = Integer.compare(deltaX, 0);
		int stepZ = Integer.compare(deltaZ, 0);
		return Math.abs(deltaX) >= Math.abs(deltaZ) ? playerPos.offset(stepX, 0, 0) : playerPos.offset(0, 0, stepZ);
	}

	/** First hotbar slot holding a block that is not air when placed. */
	public int findShieldBlockSlot() {
		for (int slot = 0; slot < 9; ++slot) {
			ItemStack stack = mc.player.getInventory().getItem(slot);
			if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)
					|| blockItem.getBlock().defaultBlockState().isAir()) continue;
			return slot;
		}
		return -1;
	}

	public boolean isCharged(BlockPos pos) {
		BlockState state = mc.level.getBlockState(pos);
		return state.is(Blocks.RESPAWN_ANCHOR) && state.getValue(RespawnAnchorBlock.CHARGE) > 0;
	}

	public boolean isUncharged(BlockPos pos) {
		BlockState state = mc.level.getBlockState(pos);
		return state.is(Blocks.RESPAWN_ANCHOR) && state.getValue(RespawnAnchorBlock.CHARGE) == 0;
	}

	/** True when the configured totem slot should be topped up with glowstone first. */
	public boolean needsFill() {
		if (!this.autoFillTotem.getValue()) {
			return false;
		}
		int slot = this.totemSlot.getValue() - 1;
		return !mc.player.getInventory().getItem(slot).is(Items.GLOWSTONE);
	}

	public boolean isHoldingGlowstone() {
		return mc.player.getMainHandItem().is(Items.GLOWSTONE);
	}

	public int findInHotbar(Item item) {
		for (int slot = 0; slot < 9; ++slot) {
			if (!mc.player.getInventory().getItem(slot).is(item)) continue;
			return slot;
		}
		return -1;
	}

	public int findInInventory(Item item) {
		int hotbarSlot = this.findInHotbar(item);
		if (hotbarSlot != -1) {
			return hotbarSlot;
		}
		for (int slot = 9; slot < 36; ++slot) {
			if (!mc.player.getInventory().getItem(slot).is(item)) continue;
			return slot;
		}
		return -1;
	}

	/** Hotbar-swap the glowstone into the totem slot; hotbar indices need the +36 shift. */
	public void moveGlowstoneToTotemSlot(int sourceSlot) {
		int targetSlot = this.totemSlot.getValue() - 1;
		int containerSlot = sourceSlot < 9 ? sourceSlot + 36 : sourceSlot;
		mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, containerSlot, targetSlot,
				ClickType.SWAP, mc.player);
	}

	/** Switches the held slot both client- and server-side, ignoring out-of-hotbar values. */
	public void sendSlot(int slot) {
		if (slot < 0 || slot > 8) {
			return;
		}
		mc.player.connection.send(new ServerboundSetCarriedItemPacket(slot));
		mc.player.getInventory().setSelectedSlot(slot);
	}

	public void interactBlock(BlockHitResult hit) {
		mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
	}

	/** Holding right-click on food or a shield outranks the macro, so it stands down. */
	public boolean isShieldOrFoodActive() {
		boolean holdingFood = mc.player.getMainHandItem().get(DataComponents.FOOD) != null
				|| mc.player.getOffhandItem().get(DataComponents.FOOD) != null;
		boolean holdingShield = mc.player.getMainHandItem().is(Items.SHIELD)
				|| mc.player.getOffhandItem().is(Items.SHIELD);
		boolean usingItem = GLFW.glfwGetMouseButton(mc.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
		return (holdingFood || holdingShield) && usingItem;
	}

	public void reset() {
		this.phase = Phase.IDLE;
		this.delay = 0;
		this.chargedWait = 0;
	}

	/** Steps of the charge-and-detonate sequence, in the order they run. */
	public enum Phase {
		IDLE,
		FILL_TOTEM,
		PLACE_SHIELD,
		SWITCH_GLOW,
		PLACE_GLOW,
		SWITCH_TOTEM,
		EXPLODE
	}
}
