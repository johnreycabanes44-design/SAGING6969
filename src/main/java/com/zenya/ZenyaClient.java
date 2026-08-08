package com.zenya;

import com.zenya.gui.ClickGUI;
import com.zenya.gui.FrostOptionsScreen;
import com.zenya.module.ActivatableModule;
import com.zenya.module.Module;
import com.zenya.module.ModuleManager;
import com.zenya.module.modules.client.Hud;
import com.zenya.module.modules.donut.RegionMap;
import com.zenya.module.modules.misc.SpotifyHUD;
import com.zenya.module.modules.smps.SpawnerTags;
import com.zenya.sound.SoundManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric entry point. Authenticates, registers the HUD, screen and tick hooks, then
 * forwards every tick to {@link ModuleManager}.
 *
 * <p>Keybinds are polled here instead of using Minecraft's own key events, so the
 * edge state has to be tracked by hand: GLFW only reports whether a key is down now,
 * hence {@link #menuKeyPressed} and the per-module {@code wasBindPressed} flags.
 * {@link #lastWorld} exists for the same reason — there is no world-change callback.
 */
public class ZenyaClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("Frost");

	public static KeyMapping rightShiftKey;
	public static boolean menuKeyPressed;
	public static ClientLevel lastWorld;

	@Override
	public void onInitializeClient() {
		LOGGER.info("Initializing Frost Client");
		SoundManager.registerSounds();
		ModuleManager.INSTANCE.init();

		KeyMapping.Category menuKeyCategory = new KeyMapping.Category(
				Identifier.fromNamespaceAndPath("zenya", "category"));
		rightShiftKey = KeyBindingHelper.registerKeyBinding(
				new KeyMapping("key.zenya.toggle_menu", GLFW.GLFW_KEY_RIGHT_SHIFT, menuKeyCategory));

		HudRenderCallback.EVENT.register((graphics, deltaTracker) -> {
			Minecraft mc = Minecraft.getInstance();
			if (mc.screen instanceof ClickGUI) {
				return;
			}
			SpawnerTags.renderHud(graphics, deltaTracker.getGameTimeDeltaPartialTick(false));
			RegionMap.renderHud(graphics);
			Hud.renderHud(graphics);
			SpotifyHUD.render(graphics, deltaTracker.getGameTimeDeltaPartialTick(false));
		});

		// Bottom-left shortcut into the client options, injected into vanilla's option screens.
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof OptionsSubScreen) {
				Screens.getButtons(screen).add(Button
						.builder(Component.literal("\u00a7b\u2744 \u00a7fFrost Client \u00a77\u25b6"),
								button -> client.setScreen(new FrostOptionsScreen(screen)))
						.bounds(4, screen.height - 26, 150, 20)
						.build());
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.level != lastWorld) {
				lastWorld = client.level;
				ModuleManager.INSTANCE.onWorldChange();
			}
			ModuleManager.INSTANCE.onTick();

			try {
				// Only poll the menu key when nothing but our own GUI is in the way,
				// otherwise typing right shift in a vanilla screen would open it.
				boolean menuKeyAllowed = client.screen == null || client.screen instanceof ClickGUI;
				if (menuKeyAllowed) {
					boolean menuKeyDown = GLFW.glfwGetKey(client.getWindow().handle(),
							GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
					if (menuKeyDown && !menuKeyPressed) {
						if (client.screen instanceof ClickGUI openGui) {
							openGui.requestClose();
						} else {
							client.setScreen(new ClickGUI());
						}
					}
					menuKeyPressed = menuKeyDown;
				} else {
					menuKeyPressed = false;
				}
			} catch (Exception ignored) {
				// A GLFW hiccup during window teardown must not kill the tick loop.
			}

			if (client.screen == null && client.getWindow() != null) {
				for (Module module : ModuleManager.INSTANCE.getModules()) {
					int bind = module.getBind();
					ActivatableModule activatable = module instanceof ActivatableModule ? (ActivatableModule) module : null;
					int activationKey = activatable != null ? activatable.getActivationKey() : 0;

					if (bind != 0) {
						try {
							boolean bindDown = GLFW.glfwGetKey(client.getWindow().handle(), bind) == GLFW.GLFW_PRESS;
							// A bind shared with the activation key is left to the activation handler below.
							if (bindDown && !module.wasBindPressed && activationKey != bind) {
								module.onBindPressed();
							}
							module.wasBindPressed = bindDown;
						} catch (Exception ignored) {
							// Same as above: a failed poll skips this module, it does not stop the loop.
						}
					}

					if (activatable == null || activationKey == 0) continue;
					try {
						boolean activationKeyDown = GLFW.glfwGetKey(client.getWindow().handle(), activationKey) == GLFW.GLFW_PRESS;
						if (activationKeyDown && !activatable.wasActivationKeyPressed) {
							activatable.onActivationKeyPressed();
						}
						activatable.wasActivationKeyPressed = activationKeyDown;
					} catch (Exception ignored) {
						// Same as above.
					}
				}
			}
		});
	}
}
