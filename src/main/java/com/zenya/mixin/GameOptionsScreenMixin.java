package com.zenya.mixin;

import com.zenya.gui.FrostOptionsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Puts the client-settings shortcut in the bottom-left corner of every vanilla options
 * sub-screen.
 *
 * <p>Injected at TAIL of init so it is added after the screen has laid out its own
 * widgets and cleared the previous ones, and so height is already the final value.
 *
 * <p>ponytail: ZenyaClient registers the identical button through
 * ScreenEvents.AFTER_INIT. If both this mixin and that listener are live the button is
 * added twice. Restored as shipped; the mixin json decides which one is actually used.
 */
@Mixin(OptionsSubScreen.class)
public abstract class GameOptionsScreenMixin {
	@Shadow
	public int height;

	@Shadow
	protected abstract <T extends GuiEventListener & Renderable> T addRenderableWidget(T widget);

	@Inject(method = "init", at = @At("TAIL"))
	private void zenya$addFrostButton(CallbackInfo info) {
		Minecraft mc = Minecraft.getInstance();
		Screen self = (Screen) (Object) this;
		this.addRenderableWidget(Button
				.builder(Component.literal("§b❄ §fFrost Client §7▶"),
						button -> mc.setScreen(new FrostOptionsScreen(self)))
				.bounds(4, this.height - 26, 150, 20)
				.build());
	}
}
