package com.zenya.module;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;

import com.zenya.module.modules.client.Hud;
import com.zenya.setting.Setting;
import com.zenya.sound.SoundManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Base of every feature: identity, keybind, settings and the on/off state.
 *
 * <p>{@link #setEnabled} is the user-facing toggle — it plays a sound, pushes a HUD
 * notification and saves the config. {@link #applyEnabled} and {@link #applyBind} are the
 * config-loading path and deliberately do none of that, otherwise loading a config would
 * spam notifications and immediately re-save it.
 */
public abstract class Module {
	public String name;
	public Category category;
	public boolean enabled;
	public int bind = 0;
	public boolean expanded = false;
	public boolean wasBindPressed = false;
	public List<Setting<?>> settings = new ArrayList<>();
	public String description = "";
	public static Minecraft mc;

	public Module(String name, Category category) {
		this.name = name;
		this.category = category;
		this.enabled = false;
	}

	public void setDescription(String description) {
		this.description = description == null ? "" : description;
	}

	public String getDescription() {
		return this.description;
	}

	public void addSetting(Setting<?> setting) {
		this.settings.add(setting);
	}

	public List<Setting<?>> getSettings() {
		return this.settings;
	}

	public String getName() {
		return this.name;
	}

	/**
	 * GUI label. Hand-written for the names {@link #splitCamelCase} cannot produce
	 * (acronyms, brand names, words that are already one token); everything else falls
	 * through to the camel-case splitter.
	 */
	public String getDisplayName() {
		if (this.name == null || this.name.isBlank()) {
			return "";
		}
		String key = this.name.toLowerCase(Locale.ROOT).replace(" ", "");
		return switch (key) {
			case "zenya+", "frost+", "ҥλl0+", "halo+" -> "Frost+";
			case "hud" -> "HUD";
			case "autodoublehand" -> "Auto Double Hand";
			case "autohitcrystal" -> "Auto Hit Crystal";
			case "activitydebug" -> "Activity Debug";
			case "antitrap" -> "Anti Trap";
			case "bonedropper", "bonedropperbot" -> "Bone Dropper";
			case "chunkfinder" -> "Chunk Finder";
			case "fakestats" -> "Fake Stats";
			case "fakepay" -> "Fake Pay";
			case "basemarker" -> "Base Marker";
			case "autolog" -> "Auto Log";
			case "freelook" -> "Freelook";
			case "homesetter" -> "Home Setter";
			case "nameprotect" -> "Name Protect";
			case "nametags" -> "Nametags";
			case "skinchanger", "skinprotect" -> "Skin Protect";
			case "swingspeed" -> "Swing Speed";
			case "tabdetector" -> "Tab Detector";
			case "weathernotifier" -> "Weather Notifier";
			case "amethystesp" -> "Amethyst ESP";
			case "spotifyhud" -> "Spotify HUD";
			case "fullbright" -> "Full Bright";
			case "jumpcircles" -> "Jump Circles";
			case "norender" -> "No Render";
			case "spearswap" -> "Spear Swap";
			default -> Module.splitCamelCase(this.name);
		};
	}

	/**
	 * Inserts a space before every word boundary: lower-to-upper, the last capital of an
	 * acronym followed by a lower-case letter ("ESPTracer" -> "ESP Tracer"), and the first
	 * digit of a number.
	 */
	public static String splitCamelCase(String raw) {
		StringBuilder out = new StringBuilder(raw.length() + 8);
		char prev = 0;
		for (int i = 0; i < raw.length(); ++i) {
			char c = raw.charAt(i);
			char next = i + 1 < raw.length() ? raw.charAt(i + 1) : 0;
			if (i > 0 && c != ' ' && (Character.isUpperCase(c) && Character.isLowerCase(prev)
					|| Character.isUpperCase(c) && Character.isUpperCase(prev) && Character.isLowerCase(next)
					|| Character.isDigit(c) && !Character.isDigit(prev) && prev != ' ')) {
				out.append(' ');
			}
			out.append(c);
			prev = c;
		}
		return out.toString();
	}

	public Category getCategory() {
		return this.category;
	}

	/** User-facing toggle: fires the callbacks, the sound, the notification and a save. */
	public void setEnabled(boolean enabled) {
		if (this.enabled == enabled) {
			return;
		}
		this.enabled = enabled;
		if (enabled) {
			this.onEnable();
			SoundManager.playModuleEnable();
			Hud.pushModuleNotification(this, true);
		} else {
			this.onDisable();
			SoundManager.playModuleDisable();
			Hud.pushModuleNotification(this, false);
		}
		ModuleManager.INSTANCE.saveConfig();
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public void toggle() {
		this.setEnabled(!this.enabled);
	}

	public void onBindPressed() {
		this.toggle();
	}

	public int getBind() {
		return this.bind;
	}

	public void setBind(int bind) {
		this.bind = bind;
		ModuleManager.INSTANCE.saveConfig();
	}

	/** Config-loading counterpart of {@link #setBind}: no save, or loading would re-write the config. */
	public void applyBind(int bind) {
		this.bind = bind;
	}

	/** Config-loading counterpart of {@link #setEnabled}: callbacks only, no sound/notification/save. */
	public void applyEnabled(boolean enabled) {
		if (this.enabled == enabled) {
			return;
		}
		this.enabled = enabled;
		if (enabled) {
			this.onEnable();
		} else {
			this.onDisable();
		}
	}

	public boolean isExpanded() {
		return this.expanded;
	}

	public void setExpanded(boolean expanded) {
		this.expanded = expanded;
	}

	public void onEnable() {
	}

	public void onDisable() {
	}

	public void onTick() {
	}

	public void onRender(PoseStack poseStack, float partialTicks) {
	}

	public void onWorldChange() {
	}

	public void onPacketReceive(Packet packet) {
	}

	/** @return true to cancel the packet. */
	public boolean onPacketSend(Packet packet) {
		return false;
	}

	static {
		mc = Minecraft.getInstance();
	}
}
