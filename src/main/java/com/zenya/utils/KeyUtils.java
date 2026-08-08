package com.zenya.utils;

import net.minecraft.client.Minecraft;

import org.lwjgl.glfw.GLFW;

/**
 * Raw GLFW polling for module keybinds.
 *
 * <p>A bind is stored as a single int, so codes at or below 8 are read as mouse
 * buttons and everything above as a keyboard key. Polling rather than a callback
 * is what keeps binds working while a screen is open.
 */
public class KeyUtils {

	public static boolean isKeyPressed(int keyCode) {
		long window = Minecraft.getInstance().getWindow().handle();

		if (keyCode <= 8) {
			return GLFW.glfwGetMouseButton(window, keyCode) == GLFW.GLFW_PRESS;
		}

		return GLFW.glfwGetKey(window, keyCode) == GLFW.GLFW_PRESS;
	}
}
