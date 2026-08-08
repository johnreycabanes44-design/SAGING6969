package com.zenya.module.modules.combat;

import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.ModeSetting;
import com.zenya.setting.Setting;
import com.zenya.utils.InventoryUtils;
import com.zenya.utils.TimerUtils;

/**
 * Keeps a totem in the off-hand, and optionally in a fixed hotbar slot, by opening
 * the inventory screen itself when something is missing.
 *
 * <p>The server only accepts these swaps as clicks on an open container, so every
 * action is gated on {@code mc.screen} being an {@link InventoryScreen}. Both clocks
 * hold -1 while no inventory is open, which is the "not loaded yet" marker that makes
 * the delay and stay-open timers restart from their settings on the next open.
 */
public class AutoInvTotem extends Module {
	public ModeSetting mode;
	public Setting<Float> delay;
	public Setting<Boolean> hotbar;
	public Setting<Integer> totemSlot;
	public Setting<Boolean> autoSwitch;
	public Setting<Boolean> forceTotem;
	public Setting<Boolean> autoOpen;
	public Setting<Float> stayOpenFor;
	public int openClock;
	public int closeClock;
	public TimerUtils openTimer;
	public TimerUtils closeTimer;

	public AutoInvTotem() {
		super("AutoInvTotem", Category.COMBAT);
		this.mode = new ModeSetting("Mode", "Blatant", "Blatant", "Random", "Legit");
		this.delay = new Setting<>("Delay", 0.0f, 0.0f, 20.0f);
		this.hotbar = new Setting<>("Hotbar", true);
		this.totemSlot = new Setting<>("Totem Slot", 1, 1, 9);
		this.autoSwitch = new Setting<>("Auto Switch", false);
		this.forceTotem = new Setting<>("Force Totem", false);
		this.autoOpen = new Setting<>("Auto Open", false);
		this.stayOpenFor = new Setting<>("Stay Open For", 0.0f, 0.0f, 20.0f);
		this.openClock = -1;
		this.closeClock = -1;
		this.openTimer = new TimerUtils();
		this.closeTimer = new TimerUtils();
		this.setDescription("Automatically equips a totem in your offhand and main hand if empty");
		this.addSetting(this.mode);
		this.addSetting(this.delay);
		this.addSetting(this.hotbar);
		this.addSetting(this.totemSlot);
		this.addSetting(this.autoSwitch);
		this.addSetting(this.forceTotem);
		this.addSetting(this.autoOpen);
		this.addSetting(this.stayOpenFor);
	}

	@Override
	public void onEnable() {
		this.openClock = -1;
		this.closeClock = -1;
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.level == null) {
			return;
		}

		if (this.shouldOpen() && this.autoOpen.getValue()) {
			mc.setScreen(new InventoryScreen(mc.player));
		}

		if (!(mc.screen instanceof InventoryScreen screen)) {
			this.openClock = -1;
			this.closeClock = -1;
			return;
		}

		if (this.openClock == -1) {
			this.openClock = this.delay.getValue().intValue();
		}

		if (this.closeClock == -1) {
			this.closeClock = this.stayOpenFor.getValue().intValue();
		}

		if (this.openClock > 0) {
			this.openClock -= 1;
			return;
		}

		Inventory inventory = mc.player.getInventory();

		if (this.autoSwitch.getValue()) {
			inventory.setSelectedSlot(this.totemSlot.getValue() - 1);
		}

		// Slot 40 is the off-hand in the player inventory's index space.
		if (inventory.getItem(40).getItem() != Items.TOTEM_OF_UNDYING) {
			int sourceSlot = this.findTotemSlot();

			if (sourceSlot != -1) {
				mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, sourceSlot, 40, ClickType.SWAP,
						mc.player);
				return;
			}
		}

		if (this.hotbar.getValue() && (inventory.getItem(this.totemSlot.getValue() - 1).isEmpty()
				|| this.forceTotem.getValue()
						&& inventory.getItem(this.totemSlot.getValue() - 1).getItem() != Items.TOTEM_OF_UNDYING)) {
			int sourceSlot = this.findTotemSlot();

			if (sourceSlot != -1) {
				// ponytail: swaps into the *selected* slot, not the configured totem slot,
				// so with Auto Switch off the totem can land anywhere in the hotbar.
				mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, sourceSlot,
						inventory.getSelectedSlot(), ClickType.SWAP, mc.player);
				return;
			}
		}

		if (this.isDone() && this.autoOpen.getValue()) {
			if (this.closeClock != 0) {
				this.closeClock -= 1;
				return;
			}

			screen.onClose();
			this.closeClock = this.stayOpenFor.getValue().intValue();
		}
	}

	/** Everything the module was asked to fill is filled, and the screen is still open. */
	public boolean isDone() {
		if (mc.player == null) {
			return false;
		}

		if (this.hotbar.getValue()) {
			return mc.player.getInventory().getItem(this.totemSlot.getValue() - 1).getItem() == Items.TOTEM_OF_UNDYING
					&& mc.player.getOffhandItem().getItem() == Items.TOTEM_OF_UNDYING
					&& mc.screen instanceof InventoryScreen;
		}

		return mc.player.getOffhandItem().getItem() == Items.TOTEM_OF_UNDYING
				&& mc.screen instanceof InventoryScreen;
	}

	/** Something is missing, no inventory is open yet, and there is a totem left to move. */
	public boolean shouldOpen() {
		if (mc.player == null) {
			return false;
		}

		if (this.hotbar.getValue()) {
			return (mc.player.getOffhandItem().getItem() != Items.TOTEM_OF_UNDYING
					|| mc.player.getInventory().getItem(this.totemSlot.getValue() - 1)
							.getItem() != Items.TOTEM_OF_UNDYING)
					&& !(mc.screen instanceof InventoryScreen)
					&& InventoryUtils.countItemInInventory(item -> item == Items.TOTEM_OF_UNDYING) != 0;
		}

		return mc.player.getOffhandItem().getItem() != Items.TOTEM_OF_UNDYING
				&& !(mc.screen instanceof InventoryScreen)
				&& InventoryUtils.countItemInInventory(item -> item == Items.TOTEM_OF_UNDYING) != 0;
	}

	/** Source slot to pull from: only "Random" differs, "Legit" falls back to the first. */
	private int findTotemSlot() {
		if (this.mode.is("Blatant")) {
			return InventoryUtils.findFirstTotemSlot();
		}

		if (this.mode.is("Random")) {
			return InventoryUtils.findRandomTotemSlot();
		}

		return InventoryUtils.findFirstTotemSlot();
	}
}
