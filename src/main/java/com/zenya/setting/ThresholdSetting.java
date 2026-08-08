package com.zenya.setting;

import com.zenya.module.ModuleManager;

/**
 * A number that can be switched off entirely — "health below 8" versus "don't
 * check health at all". The enabled flag rides along in the config string so a
 * disabled threshold keeps the value it had.
 */
public class ThresholdSetting extends Setting<Integer> {
	private boolean enabled;

	public ThresholdSetting(String name, boolean enabled, int value, int min, int max) {
		super(name, value, min, max);
		this.enabled = enabled;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		if (this.enabled == enabled) {
			return;
		}
		this.enabled = enabled;
		ModuleManager.INSTANCE.onSettingChanged();
	}

	public String serialize() {
		return enabled + "|" + getValue();
	}

	public void deserialize(String raw) {
		if (raw == null || raw.isBlank()) {
			return;
		}
		String[] parts = raw.split("\\|", 2);
		try {
			if (parts.length == 2) {
				enabled = Boolean.parseBoolean(parts[0]);
				setValue(Integer.parseInt(parts[1]));
				return;
			}
			// configs written before the enabled flag existed hold just the number
			setValue(Integer.parseInt(raw));
		} catch (NumberFormatException ignored) {
		}
	}
}
