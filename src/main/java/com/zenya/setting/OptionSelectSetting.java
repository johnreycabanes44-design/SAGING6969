package com.zenya.setting;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pick exactly one of a list of {@link OptionEntry} rows — the searchable
 * dropdown used where {@link ModeSetting} would be too long to cycle through.
 */
public class OptionSelectSetting extends Setting<String> {
	private final List<OptionEntry> options;

	public OptionSelectSetting(String name, String value, OptionEntry... options) {
		super(name, value);
		this.options = List.of(options);
	}

	public List<OptionEntry> getOptions() {
		return options;
	}

	/** Search matches the stored value as well as the label, so ids are typeable. */
	public List<OptionEntry> filter(String query) {
		String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
		if (needle.isEmpty()) {
			return options;
		}
		List<OptionEntry> matches = new ArrayList<>();
		for (OptionEntry option : options) {
			String value = option.value() == null ? "" : option.value().toLowerCase(Locale.ROOT);
			String label = option.label() == null ? "" : option.label().toLowerCase(Locale.ROOT);
			if (!value.contains(needle) && !label.contains(needle)) {
				continue;
			}
			matches.add(option);
		}
		return matches;
	}

	public void select(String value) {
		if (value == null) {
			reset();
			return;
		}
		for (OptionEntry option : options) {
			if (!option.value().equalsIgnoreCase(value)) {
				continue;
			}
			setValue(option.value());
			return;
		}
	}

	public void reset() {
		setValue(getDefaultValue());
	}

	public boolean isSelected(OptionEntry option) {
		return option != null && getValue() != null && option.value().equalsIgnoreCase(getValue());
	}

	public OptionEntry getSelectedOption() {
		for (OptionEntry option : options) {
			if (isSelected(option)) {
				return option;
			}
		}
		return options.isEmpty() ? null : options.getFirst();
	}

	public String getSummary() {
		OptionEntry selected = getSelectedOption();
		return selected == null ? "Choose" : selected.label();
	}

	public ItemStack getPreviewStack() {
		OptionEntry selected = getSelectedOption();
		return selected == null ? ItemStack.EMPTY : selected.getPreviewStack();
	}
}
