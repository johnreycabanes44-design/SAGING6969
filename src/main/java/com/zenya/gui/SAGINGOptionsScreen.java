package com.zenya.gui;

import com.zenya.module.modules.client.ZenyaPlus;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Standalone screen for rebinding the key that opens the Frost GUI.
 *
 * <p>While {@code awaitingBind} is set the screen eats every key and mouse press so
 * the captured input goes to the bind instead of the widgets underneath, which is why
 * both handlers return true without calling super.
 */
public class SAGINGOptionsScreen extends Screen {
	private final Screen parent;
	private boolean awaitingBind;
	private Button bindButton;

	public FrostOptionsScreen(Screen parent) {
		super(Component.literal("SAGING Client Settings"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int centerX = width / 2;
		int centerY = height / 2;
		bindButton = Button.builder(buildBindLabel(), button -> {
			awaitingBind = !awaitingBind;
			updateBindLabel();
		}).bounds(centerX - 155, centerY - 20, 310, 20).build();
		addRenderableWidget(bindButton);
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> {
			awaitingBind = false;
			minecraft.setScreen(parent);
		}).bounds(centerX - 100, centerY + 30, 200, 20).build());
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fillGradient(0, 0, width, height, -1072689136, -804911610);
		int centerX = width / 2;
		int centerY = height / 2;
		graphics.fill(centerX - 170, centerY - 44, centerX + 170, centerY + 58, -1441458923);
		// One-pixel accent line along the top edge of the panel.
		graphics.fill(centerX - 170, centerY - 44, centerX + 170, centerY - 43, -12877066);
		graphics.drawCenteredString(font, title, centerX, centerY - 60, -1);
		graphics.drawCenteredString(font, Component.literal("§7Frost Client GUI Keybind"), centerX, centerY - 38, -5592406);
		super.render(graphics, mouseX, mouseY, partialTick);
		if (awaitingBind) {
			graphics.drawCenteredString(font, Component.literal("§ePress any key to bind  •  §cESC to clear"), centerX, centerY + 6, -1);
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (awaitingBind) {
			int key = event.key();
			// Escape clears the bind instead of binding Escape itself.
			ZenyaPlus.setMenuBind(key == GLFW.GLFW_KEY_ESCAPE ? 0 : key);
			awaitingBind = false;
			updateBindLabel();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (awaitingBind) {
			awaitingBind = false;
			updateBindLabel();
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	private void updateBindLabel() {
		if (bindButton != null) {
			bindButton.setMessage(buildBindLabel());
		}
	}

	/** Prefers the name GLFW reports for the key, falling back for keys it cannot name. */
	private Component buildBindLabel() {
		if (awaitingBind) {
			return Component.literal("§e> Press a key... <");
		}
		// ponytail: the label is hardcoded to Right Shift instead of reading
		// ZenyaPlus.getMenuBind(), so it never reflects a rebind
		String glfwName = GLFW.glfwGetKeyName(GLFW.GLFW_KEY_RIGHT_SHIFT, 0);
		String keyName = glfwName == null || glfwName.isBlank()
				? friendlyKeyName(GLFW.GLFW_KEY_RIGHT_SHIFT)
				: glfwName.toUpperCase();
		return Component.literal("§fFrost GUI Bind: §b" + keyName);
	}

	/** Readable names for the keys GLFW returns no name for: modifiers, function and navigation keys. */
	private static String friendlyKeyName(int key) {
		return switch (key) {
			case 344 -> "Right Shift";
			case 340 -> "Left Shift";
			case 345 -> "Right Ctrl";
			case 341 -> "Left Ctrl";
			case 346 -> "Right Alt";
			case 342 -> "Left Alt";
			case 290 -> "F1";
			case 291 -> "F2";
			case 292 -> "F3";
			case 293 -> "F4";
			case 294 -> "F5";
			case 295 -> "F6";
			case 296 -> "F7";
			case 297 -> "F8";
			case 298 -> "F9";
			case 299 -> "F10";
			case 300 -> "F11";
			case 301 -> "F12";
			case 260 -> "Insert";
			case 261 -> "Delete";
			case 268 -> "Home";
			case 269 -> "End";
			case 266 -> "Page Up";
			case 267 -> "Page Down";
			case 265 -> "Up Arrow";
			case 264 -> "Down Arrow";
			case 263 -> "Left Arrow";
			case 262 -> "Right Arrow";
			case 259 -> "Backspace";
			case 258 -> "Tab";
			case 280 -> "Caps Lock";
			case 257 -> "Enter";
			case 335 -> "Numpad Enter";
			default -> "Key " + key;
		};
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
