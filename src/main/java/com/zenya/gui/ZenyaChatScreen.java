package com.zenya.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * The client's own chat screen.
 *
 * <p>Every override forwards straight to {@link ChatScreen}, so behaviour is
 * vanilla down to the last pixel. The subclass exists purely so the rest of the
 * client can recognise its own chat screen by type rather than by guesswork.
 */
public class ZenyaChatScreen extends ChatScreen {
	public ZenyaChatScreen(String initialText) {
		super(initialText, false);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
		return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
	}
}
