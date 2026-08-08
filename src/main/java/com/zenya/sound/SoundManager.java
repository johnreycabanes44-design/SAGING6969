package com.zenya.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * Registers and plays the client's own UI sounds (assets/zenya/sounds.json).
 *
 * <p>Registration is idempotent: the vanilla sound registry rejects a duplicate id,
 * so {@link #register} bails out on anything already present. Playback needs a local
 * player, so every play call is a no-op until the world is loaded.
 */
public class SoundManager {
	public static Identifier GUI_OPEN = Identifier.fromNamespaceAndPath("zenya", "gui_open");
	public static Identifier GUI_CLOSE = Identifier.fromNamespaceAndPath("zenya", "gui_close");
	public static Identifier MODULE_ENABLE = Identifier.fromNamespaceAndPath("zenya", "module_enable");
	public static Identifier MODULE_DISABLE = Identifier.fromNamespaceAndPath("zenya", "module_disable");

	public static void registerSounds() {
		register(GUI_OPEN);
		register(GUI_CLOSE);
		register(MODULE_ENABLE);
		register(MODULE_DISABLE);
	}

	/** Adds the sound to the vanilla registry, skipping ids that are already there. */
	public static void register(Identifier id) {
		if (BuiltInRegistries.SOUND_EVENT.containsKey(id)) {
			return;
		}
		Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void play(Identifier id) {
		play(id, 1.0f, 1.0f);
	}

	public static void playGuiOpen() {
		play(GUI_OPEN);
	}

	public static void playGuiClose() {
		play(GUI_CLOSE);
	}

	public static void playModuleEnable() {
		play(MODULE_ENABLE);
	}

	public static void playModuleDisable() {
		play(MODULE_DISABLE);
	}

	public static void play(Identifier id, float volume, float pitch) {
		try {
			Minecraft mc = Minecraft.getInstance();
			if (mc == null || mc.player == null) {
				return;
			}
			BuiltInRegistries.SOUND_EVENT.get(id).ifPresent(sound -> mc.player.playSound(sound.value(), volume, pitch));
		}
		catch (Exception swallowed) {
			// A missing or malformed sound must never take down the caller's render/tick path.
		}
	}
}
