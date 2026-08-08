package com.zenya.module.modules.combat;

import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.ClickType;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;

/**
 * Pulls a potion out of the backpack into the hotbar once health drops below the
 * configured percentage.
 *
 * <p>The swap is a container click, which needs an open inventory, so "Auto Open"
 * opens the screen and the same tick continues into the swap. With a full hotbar the
 * potion lands on the selected slot instead — that is what {@code delayClock} guards
 * against, or the module would swap on every tick while health stays low.
 */
public class AutoPotRefill extends Module {
	public Setting<Integer> delay;
	public Setting<Integer> healthThreshold;
	public Setting<Boolean> autoOpen;
	public int delayClock;

	public AutoPotRefill() {
		super("Auto Pot Refill", Category.COMBAT);
		this.delay = new Setting<>("Delay", 0, 0, 20);
		this.healthThreshold = new Setting<>("Health %", 50, 1, 100);
		this.autoOpen = new Setting<>("Auto Open", true);
		this.delayClock = 0;
		this.addSetting(this.delay);
		this.addSetting(this.healthThreshold);
		this.addSetting(this.autoOpen);
	}

	@Override
	public void onEnable() {
		this.delayClock = 0;
	}

	@Override
	public void onTick() {
		if (mc.player == null) {
			return;
		}

		float healthPercent = mc.player.getHealth() / mc.player.getMaxHealth() * 100.0f;

		if (healthPercent > this.healthThreshold.getValue()) {
			return;
		}

		if (this.autoOpen.getValue() && !(mc.screen instanceof InventoryScreen)) {
			mc.setScreen(new InventoryScreen(mc.player));
		}

		if (!(mc.screen instanceof InventoryScreen screen)) {
			return;
		}

		if (this.delayClock > 0) {
			this.delayClock -= 1;
			return;
		}

		// Slots 9-35 are the backpack; the hotbar is the destination, never the source.
		for (int sourceSlot = 9; sourceSlot < 36; ++sourceSlot) {
			if (mc.player.getInventory().getItem(sourceSlot).getItem() != Items.POTION) {
				continue;
			}

			int targetSlot = -1;

			for (int hotbarSlot = 0; hotbarSlot < 9; ++hotbarSlot) {
				if (!mc.player.getInventory().getItem(hotbarSlot).isEmpty()) {
					continue;
				}

				targetSlot = hotbarSlot;
				break;
			}

			// Hotbar full: overwrite whatever is held rather than give up.
			if (targetSlot == -1) {
				targetSlot = mc.player.getInventory().getSelectedSlot();
			}

			mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, sourceSlot, targetSlot,
					ClickType.SWAP, mc.player);
			this.delayClock = this.delay.getValue();
			return;
		}
	}
}
