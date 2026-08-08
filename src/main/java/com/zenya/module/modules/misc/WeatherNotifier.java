package com.zenya.module.modules.misc;

import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import com.zenya.module.Category;
import com.zenya.module.Module;

/**
 * Pops a vanilla toast and a sound the moment the world starts or stops raining or thundering.
 *
 * <p>{@code wasRaining} and {@code wasThundering} are edge detectors, not state: they are
 * cleared on enable so toggling the module mid-storm does not announce weather that was
 * already there. Rain and thunder are tracked separately because a thunderstorm sets both.
 */
public class WeatherNotifier extends Module {
	public boolean wasRaining;
	public boolean wasThundering;
	public float notifyAnim;

	public WeatherNotifier() {
		super("WeatherNotifier", Category.MISC);
		this.wasRaining = false;
		this.wasThundering = false;
		this.notifyAnim = 0.0f;
		this.setDescription("Notifies you about weather changes with smooth vanilla toasts.");
	}

	@Override
	public void onEnable() {
		this.wasRaining = false;
		this.wasThundering = false;
		this.notifyAnim = 0.0f;
	}

	@Override
	public void onTick() {
		if (mc.level == null) {
			return;
		}

		boolean raining = mc.level.isRaining();
		boolean thundering = mc.level.isThundering();

		if (raining && !this.wasRaining) {
			this.notify("Rain Started", "The sky is getting wet.", SoundEvents.WEATHER_RAIN);
		} else if (!raining && this.wasRaining) {
			this.notify("Rain Cleared", "The sky is clear again.", SoundEvents.WEATHER_RAIN_ABOVE);
		}

		if (thundering && !this.wasThundering) {
			this.notify("Thunderstorm", "Lightning nearby — stay safe.", SoundEvents.LIGHTNING_BOLT_THUNDER);
		} else if (!thundering && this.wasThundering) {
			this.notify("Storm Ended", "Thunder has faded.", SoundEvents.AMBIENT_CAVE.value());
		}

		this.wasRaining = raining;
		this.wasThundering = thundering;
	}

	/**
	 * Queues the toast onto the client thread. The pitch walks over three steps so a burst of
	 * notifications does not sound like one long beep.
	 */
	public void notify(String title, String description, SoundEvent sound) {
		mc.execute(() -> {
			if (mc.player == null) {
				return;
			}

			float pitch = 0.85f + this.notifyAnim % 3.0f * 0.05f;
			this.notifyAnim += 1.0f;

			mc.player.playSound(sound, 0.35f, pitch);
			mc.player.playSound(SoundEvents.UI_TOAST_IN, 0.5f, 1.05f);

			SystemToast.addOrUpdate(mc.getToastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
					Component.literal(title), Component.literal(description));
		});
	}
}
