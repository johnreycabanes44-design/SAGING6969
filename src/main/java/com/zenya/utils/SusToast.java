package com.zenya.utils;

import com.zenya.module.modules.client.ZenyaPlus;
import com.zenya.utils.renderer.RenderUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * A themed toast that rides vanilla's toast queue instead of a custom overlay, so it
 * stacks and animates with the game's own notifications.
 *
 * <p>Rendering happens in toast-local space (origin at the toast's top-left), and the
 * accent stripe is recoloured from the current theme every frame. {@code startTime} is
 * latched on the first render or update, whichever the manager calls first, because the
 * toast may be constructed well before it becomes visible.
 */
public class SusToast implements Toast {
	public static int WIDTH = 160;
	public static int HEIGHT = 32;
	public static long DURATION_MS = 5000L;

	public Component title;
	public Component description;
	public ItemStack icon;
	public long startTime;
	public Toast.Visibility visibility = Toast.Visibility.SHOW;

	public SusToast(Component title, Component description, ItemStack icon) {
		this.title = title;
		this.description = description;
		this.icon = icon == null ? ItemStack.EMPTY : icon;
	}

	@Override
	public void render(GuiGraphics graphics, Font font, long time) {
		if (this.startTime == 0L) {
			this.startTime = time;
		}
		int accent = ZenyaPlus.getAccentARGB();
		int background = 0xF0121418;
		int outline = accent & 0xFFFFFF | 0xAA000000;
		RenderUtil.drawRoundedRect(graphics, 0.0f, 0.0f, 160.0f, 32.0f, 6.0f, background, false);
		RenderUtil.drawOutline(graphics, 0.0f, 0.0f, 160.0f, 32.0f, 6.0f, 1.0f, outline, false);
		graphics.fill(0, 0, 160, 2, accent);
		if (!this.icon.isEmpty()) {
			graphics.renderItem(this.icon, 6, 8);
		}
		int textX = this.icon.isEmpty() ? 8 : 28;
		graphics.drawString(font, this.title, textX, 7, 0xFFFFFFFF);
		graphics.drawString(font, this.description, textX, 18, 0xFF9AA3B2);
	}

	@Override
	public Toast.Visibility getWantedVisibility() {
		return this.visibility;
	}

	@Override
	public void update(ToastManager manager, long time) {
		if (this.startTime == 0L) {
			this.startTime = time;
		}
		if (time - this.startTime >= 5000L) {
			this.visibility = Toast.Visibility.HIDE;
		}
	}
}
