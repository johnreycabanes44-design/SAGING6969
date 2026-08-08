package com.zenya.gui.hud;

import com.zenya.gui.ClickGUI;
import com.zenya.utils.renderer.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Card above the hotbar showing the four worn armour pieces with a durability bar
 * under each one.
 *
 * <p>Hidden while the debug screen or the ClickGUI is open so it never draws on top
 * of them. Slots run boots-first, left to right, mirroring the vanilla armour bar.
 */
public final class ArmorHud {
	public static final ArmorHud INSTANCE = new ArmorHud();
	private static final float CARD_RADIUS = 10.0f;
	private static final int ITEM_SIZE = 16;
	private static final int ITEM_SPACING = 4;
	private static final int PADDING = 8;
	private static final int DURABILITY_BAR_HEIGHT = 3;
	private static final int DURABILITY_BAR_WIDTH = 16;
	private static final int DURABILITY_BAR_SPACING = 2;
	private static final int DURABILITY_COLOR = -16711936;
	private static final int DURABILITY_BG_COLOR = -13421773;

	private ArmorHud() {
	}

	public void render(GuiGraphics graphics) {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.player == null) {
			return;
		}
		if (mc.getDebugOverlay().showDebugScreen()) {
			return;
		}
		if (mc.screen instanceof ClickGUI) {
			return;
		}
		ItemStack helmet = mc.player.getItemBySlot(EquipmentSlot.HEAD);
		ItemStack chestplate = mc.player.getItemBySlot(EquipmentSlot.CHEST);
		ItemStack leggings = mc.player.getItemBySlot(EquipmentSlot.LEGS);
		ItemStack boots = mc.player.getItemBySlot(EquipmentSlot.FEET);
		if (helmet.isEmpty() && chestplate.isEmpty() && leggings.isEmpty() && boots.isEmpty()) {
			return;
		}
		int screenWidth = mc.getWindow().getGuiScaledWidth();
		int screenHeight = mc.getWindow().getGuiScaledHeight();
		// Four 16px slots with 4px gaps and PADDING on every side; the card clears the hotbar.
		int cardWidth = 92;
		int cardHeight = 37;
		int cardX = (screenWidth - cardWidth) / 2;
		int cardY = screenHeight - 90;
		RenderUtil.drawRoundedRect(graphics, cardX, cardY, cardWidth, cardHeight, CARD_RADIUS, -15987438, false);
		RenderUtil.drawOutline(graphics, cardX, cardY, cardWidth, cardHeight, CARD_RADIUS, 1.0f, -14013910, false);
		ItemStack[] armor = new ItemStack[]{boots, leggings, chestplate, helmet};
		for (int slot = 0; slot < armor.length; ++slot) {
			ItemStack stack = armor[slot];
			if (stack.isEmpty()) continue;
			int itemX = cardX + PADDING + slot * (ITEM_SIZE + ITEM_SPACING);
			int itemY = cardY + PADDING;
			graphics.renderItem(stack, itemX, itemY);
			if (!stack.isDamageableItem()) continue;
			int maxDamage = stack.getMaxDamage();
			int remaining = maxDamage - stack.getDamageValue();
			float durability = (float)remaining / (float)maxDamage;
			int barY = itemY + ITEM_SIZE + DURABILITY_BAR_SPACING;
			int filledWidth = Math.round((float)DURABILITY_BAR_WIDTH * durability);
			RenderUtil.drawRoundedRect(graphics, itemX, barY, DURABILITY_BAR_WIDTH, DURABILITY_BAR_HEIGHT, 1.5f, DURABILITY_BG_COLOR, false);
			if (filledWidth <= 0) continue;
			RenderUtil.drawRoundedRect(graphics, itemX, barY, filledWidth, DURABILITY_BAR_HEIGHT, 1.5f, getDurabilityColor(durability), false);
		}
	}

	/** Green at full, yellow at half, red at empty. */
	private int getDurabilityColor(float durability) {
		if (durability > 0.5f) {
			float fade = (durability - 0.5f) * 2.0f;
			int red = (int)(255.0f * (1.0f - fade));
			return 0xFF000000 | red << 16 | 0xFF00;
		}
		float fade = durability * 2.0f;
		int green = (int)(255.0f * fade);
		return 0xFFFF0000 | green << 8;
	}
}
