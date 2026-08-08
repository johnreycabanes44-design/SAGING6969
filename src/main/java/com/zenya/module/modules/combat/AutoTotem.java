package com.zenya.module.modules.combat;

import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;

/**
 * Refills the off-hand with a totem of undying as soon as it empties.
 *
 * <p>The swap is a container click on the player's own inventory menu, so it works
 * without any screen being open. {@code delayCounter} is reloaded both after a swap
 * and while a totem is already held, so the delay throttles consecutive re-totems
 * instead of only spacing out the ticks after the first one.
 */
public class AutoTotem extends Module {
	public Setting<Float> delay;
	public int delayCounter;

	public AutoTotem() {
		super("Auto Totem", Category.COMBAT);
		this.delay = new Setting<>("Delay", 1.0f, 0.0f, 5.0f);
		this.setDescription("Automatically moves a totem of undying into your off-hand whenever you're holding nothing there.");
		this.addSetting(this.delay);
	}

	@Override
	public void onEnable() {
		super.onEnable();
	}

	@Override
	public void onDisable() {
		super.onDisable();
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.gameMode == null) {
			return;
		}

		if (mc.player.getOffhandItem().getItem() == Items.TOTEM_OF_UNDYING) {
			this.delayCounter = this.delay.getValue().intValue();
			return;
		}

		if (this.delayCounter > 0) {
			this.delayCounter -= 1;
			return;
		}

		int inventorySlot = this.findItemSlot(Items.TOTEM_OF_UNDYING);

		if (inventorySlot == -1) {
			return;
		}

		mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, convertSlotIndex(inventorySlot), 40,
				ClickType.SWAP, mc.player);
		this.delayCounter = this.delay.getValue().intValue();
	}

	/** First inventory index (0-35, hotbar included) holding {@code item}, or -1. */
	public int findItemSlot(Item item) {
		if (mc.player == null) {
			return -1;
		}

		for (int slot = 0; slot < 36; ++slot) {
			if (!mc.player.getInventory().getItem(slot).is(item)) {
				continue;
			}

			return slot;
		}

		return -1;
	}

	/**
	 * Inventory index to player-menu slot id. The hotbar is indices 0-8 in the
	 * inventory but sits at the end of the menu, slots 36-44; everything else lines up.
	 */
	public static int convertSlotIndex(int inventorySlot) {
		if (inventorySlot < 9) {
			return 36 + inventorySlot;
		}

		return inventorySlot;
	}
}
