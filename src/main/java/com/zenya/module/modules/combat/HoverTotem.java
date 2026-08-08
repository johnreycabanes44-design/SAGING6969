package com.zenya.module.modules.combat;

import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.ClickType;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;

/**
 * Tops up the chosen hotbar slot and the off-hand with totems while the inventory
 * screen is open.
 *
 * <p>Container clicks are only sent through the open {@link InventoryScreen}, so
 * {@code delayClock} is reloaded from the delay setting on every tick the screen is
 * closed: reopening the inventory always costs the full delay before the first swap.
 */
public class HoverTotem extends Module {
	public Setting<Float> delay;
	public Setting<Boolean> hotbar;
	public Setting<Integer> totemSlot;
	public Setting<Boolean> autoSwitch;
	public int delayClock;

	public HoverTotem() {
		super("Hover Totem", Category.COMBAT);
		this.delay = new Setting<>("Delay", 0.0f, 0.0f, 20.0f);
		this.hotbar = new Setting<>("Hotbar", true);
		this.totemSlot = new Setting<>("Totem Slot", 1, 1, 9);
		this.autoSwitch = new Setting<>("Auto Switch", false);
		this.setDescription("Equips a totem in your hotbar and offhand slots if a totem is hovered");
		this.addSetting(this.delay);
		this.addSetting(this.hotbar);
		this.addSetting(this.totemSlot);
		this.addSetting(this.autoSwitch);
	}

	@Override
	public void onEnable() {
		this.delayClock = 0;
	}

	@Override
	public void onTick() {
		if (!(mc.screen instanceof InventoryScreen screen)) {
			this.delayClock = this.delay.getValue().intValue();
			return;
		}

		// ponytail: the carried ("hovered") stack is never inspected, so despite the name
		// and description this swaps totems whenever the inventory is open. The original
		// computed getMenu().getCarried().isEmpty() and threw the result away.

		if (this.autoSwitch.getValue()) {
			mc.player.getInventory().setSelectedSlot(this.totemSlot.getValue() - 1);
		}

		if (this.delayClock > 0) {
			this.delayClock -= 1;
			return;
		}

		int targetHotbarSlot = this.totemSlot.getValue() - 1;

		for (int slot = 0; slot < 36; ++slot) {
			if (mc.player.getInventory().getItem(slot).getItem() != Items.TOTEM_OF_UNDYING) {
				continue;
			}

			if (this.hotbar.getValue()
					&& mc.player.getInventory().getItem(targetHotbarSlot).getItem() != Items.TOTEM_OF_UNDYING) {
				mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, slot, targetHotbarSlot,
						ClickType.SWAP, mc.player);
				this.delayClock = this.delay.getValue().intValue();
				return;
			}

			if (mc.player.getOffhandItem().getItem() == Items.TOTEM_OF_UNDYING) {
				continue;
			}

			mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, slot, 40, ClickType.SWAP, mc.player);
			this.delayClock = this.delay.getValue().intValue();
			return;
		}
	}
}
