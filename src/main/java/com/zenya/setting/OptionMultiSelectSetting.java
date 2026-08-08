package com.zenya.setting;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pick any number of {@link OptionEntry} rows.
 *
 * <p>Stored values are always canonicalised against the option list, so a config
 * naming an option that no longer exists drops it instead of keeping a dead
 * value the GUI cannot show.
 */
public class OptionMultiSelectSetting extends Setting<Set<String>> {
	private final List<OptionEntry> options;

	public OptionMultiSelectSetting(String name, OptionEntry... options) {
		super(name, new LinkedHashSet<>());
		this.options = List.of(options);
	}

	@Override
	public void setValue(Set<String> values) {
		LinkedHashSet<String> cleaned = new LinkedHashSet<>();
		if (values != null) {
			for (String value : values) {
				if (value == null) {
					continue;
				}
				for (OptionEntry option : options) {
					if (option.value().equalsIgnoreCase(value)) {
						cleaned.add(option.value());
						break;
					}
				}
			}
		}
		super.setValue(cleaned);
	}

	public List<OptionEntry> getOptions() {
		return options;
	}

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

	public void toggle(String value) {
		if (value == null) {
			return;
		}
		String canonical = null;
		for (OptionEntry option : options) {
			if (option.value().equalsIgnoreCase(value)) {
				canonical = option.value();
				break;
			}
		}
		if (canonical == null) {
			return;
		}
		LinkedHashSet<String> selected = new LinkedHashSet<>(getValue());
		if (!selected.add(canonical)) {
			selected.remove(canonical);
		}
		setValue(selected);
	}

	public void clear() {
		if (getValue().isEmpty()) {
			return;
		}
		setValue(Collections.emptySet());
	}

	public boolean contains(String value) {
		if (value == null) {
			return false;
		}
		for (String selected : getValue()) {
			if (selected.equalsIgnoreCase(value)) {
				return true;
			}
		}
		return false;
	}

	public boolean isSelected(OptionEntry option) {
		return option != null && contains(option.value());
	}

	public int size() {
		return getValue().size();
	}

	/** In option order, not selection order, so the summary is stable. */
	public List<OptionEntry> getSelectedOptions() {
		List<OptionEntry> selected = new ArrayList<>();
		for (OptionEntry option : options) {
			if (contains(option.value())) {
				selected.add(option);
			}
		}
		return selected;
	}

	public String getSummary() {
		List<OptionEntry> selected = getSelectedOptions();
		if (selected.isEmpty()) {
			return "Choose";
		}
		int others = selected.size() - 1;
		String first = selected.getFirst().label();
		return others > 0 ? first + " +" + others : first;
	}

	public ItemStack getPreviewStack() {
		List<OptionEntry> selected = getSelectedOptions();
		return selected.isEmpty() ? ItemStack.EMPTY : selected.getFirst().getPreviewStack();
	}
}
