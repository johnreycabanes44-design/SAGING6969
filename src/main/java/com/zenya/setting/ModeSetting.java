package com.zenya.setting;

import java.util.List;

/**
 * A named choice out of a fixed list, cycled from the GUI or a bind.
 *
 * <p>{@code legacyNames} lets a mode be renamed without breaking saved configs:
 * the old name still matches this setting when a config is loaded.
 */
public class ModeSetting extends Setting<String> {
	private final List<String> modes;
	private final List<String> legacyNames;

	public ModeSetting(String name, String value, String... modes) {
		this(name, value, new String[0], modes);
	}

	public ModeSetting(String name, String value, String[] legacyNames, String... modes) {
		super(name, value);
		if (modes == null || modes.length == 0) {
			throw new IllegalArgumentException("ModeSetting requires at least one mode");
		}
		this.modes = List.of(modes);
		this.legacyNames = legacyNames == null ? List.of() : List.of(legacyNames);
		// run the value through resolveMode so an unknown default lands on a real mode
		setValue(value);
	}

	public List<String> getModes() {
		return modes;
	}

	public void cycleNext() {
		setValue(getModeRelativeToCurrent(1));
	}

	public void cyclePrevious() {
		setValue(getModeRelativeToCurrent(-1));
	}

	public boolean is(String mode) {
		return normalize(mode).equalsIgnoreCase(getValue());
	}

	@Override
	public void setValue(String mode) {
		super.setValue(resolveMode(mode));
	}

	@Override
	public boolean matchesName(String other) {
		if (super.matchesName(other)) {
			return true;
		}
		String needle = normalize(other);
		for (String legacy : legacyNames) {
			if (legacy.equalsIgnoreCase(needle)) {
				return true;
			}
		}
		return false;
	}

	/** Wraps at both ends, so {@code -1} from the first mode lands on the last. */
	public String getModeRelativeToCurrent(int offset) {
		int count = modes.size();
		if (count == 0) {
			return "";
		}
		String current = getValue();
		for (int i = 0; i < count; i++) {
			if (!modes.get(i).equalsIgnoreCase(current)) {
				continue;
			}
			return modes.get(Math.floorMod(i + offset, count));
		}
		return modes.getFirst();
	}

	/** Unknown names fall back to the first mode rather than leaving the setting invalid. */
	public String resolveMode(String mode) {
		String needle = normalize(mode);
		for (String known : modes) {
			if (known.equalsIgnoreCase(needle)) {
				return known;
			}
		}
		return modes.getFirst();
	}

	public String normalize(String mode) {
		return mode == null ? "" : mode.trim();
	}
}
