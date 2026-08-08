package com.zenya.mixin;

import com.zenya.module.modules.misc.SpotifyHUD;
import com.zenya.module.modules.render.Freecam;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives the SpotifyHUD and Freecam first refusal on raw mouse events.
 *
 * <p>Both hooks bail while a screen is open, so a GUI never loses a click or a scroll.
 * The cursor arrives in framebuffer pixels but the HUD is laid out in GUI space, hence
 * the manual rescale below rather than trusting the window to have a sane size.
 */
@Mixin(MouseHandler.class)
public class MouseMixin {
	private static double toScaledX(Minecraft mc, double rawX) {
		if (mc == null || mc.getWindow() == null) {
			return rawX;
		}
		double screenWidth = mc.getWindow().getScreenWidth();
		if (screenWidth <= 0.0) {
			return rawX;
		}
		return rawX * (mc.getWindow().getGuiScaledWidth() / screenWidth);
	}

	private static double toScaledY(Minecraft mc, double rawY) {
		if (mc == null || mc.getWindow() == null) {
			return rawY;
		}
		double screenHeight = mc.getWindow().getScreenHeight();
		if (screenHeight <= 0.0) {
			return rawY;
		}
		return rawY * (mc.getWindow().getGuiScaledHeight() / screenHeight);
	}

	@Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
	private void zenya$spotifyHudClick(long window, MouseButtonInfo button, int action, CallbackInfo info) {
		// GLFW_PRESS on the left button only.
		if (action != 1 || button.button() != 0) {
			return;
		}

		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.screen != null || mc.mouseHandler == null) {
			return;
		}

		double mouseX = toScaledX(mc, mc.mouseHandler.xpos());
		double mouseY = toScaledY(mc, mc.mouseHandler.ypos());
		if (SpotifyHUD.handleClick(mouseX, mouseY)) {
			info.cancel();
		}
	}

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void zenya$useScrollForFreecamSpeed(long window, double horizontal, double vertical, CallbackInfo info) {
		Freecam freecam = Freecam.instance;
		if (freecam == null || !freecam.isEnabled()) {
			return;
		}
		if (Minecraft.getInstance().screen != null) {
			return;
		}

		// Vertical wins; a horizontal-only wheel (or a tilted one) still steps the speed.
		double scrollAmount = vertical != 0.0 ? vertical : horizontal;
		if (scrollAmount == 0.0) {
			return;
		}

		freecam.adjustSpeed(scrollAmount);
		info.cancel();
	}
}
