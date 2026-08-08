package com.zenya.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Friend list picker, opened from the click GUI.
 *
 * <p>The body is empty in this build: no widgets are added and nothing is drawn
 * beyond the vanilla {@link Screen} background, so it renders as a bare titled
 * screen. Kept as its own type because the click GUI opens it by construction.
 */
public class FriendsPickerScreen extends Screen {
	public FriendsPickerScreen(Screen parent) {
		// ponytail: parent is accepted but never stored, so closing this screen
		// returns to the game instead of the screen that opened it
		super(Component.literal("Friends"));
	}
}
