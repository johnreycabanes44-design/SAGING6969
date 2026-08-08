package com.zenya.setting;

import com.zenya.module.ModuleManager;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * A single user-editable value attached to a {@link com.zenya.module.Module}.
 *
 * <p>One class covers every value type — the GUI decides how to draw a setting from
 * the runtime type of {@link #getValue()}, and {@code min}/{@code max} are only
 * populated for the numeric ones. Subclasses exist for the values that need extra
 * state (block pickers, dropdowns), not for the plain ones.
 */
public class Setting<T> {
	/**
	 * Labels that {@link #splitCamelCase} alone cannot produce — acronyms, renames
	 * ("Opacity" reads as "Alpha" in the GUI) and names that are already spaced.
	 * Keys are lower case; lookup happens on a lower-cased name.
	 */
	private static final Map<String, String> DISPLAY_NAMES = Map.ofEntries(
			Map.entry("y-level", "Y Level"),
			Map.entry("maxy", "Max Y"),
			Map.entry("chunkradius", "Chunk Radius"),
			Map.entry("fakename", "Fake Name"),
			Map.entry("mainhand", "Main Hand"),
			Map.entry("offhand", "Off Hand"),
			Map.entry("blockcolors", "Block Colors"),
			Map.entry("mobcolors", "Mob Colors"),
			Map.entry("opacity", "Alpha"),
			Map.entry("outline color", "Outline Color"),
			Map.entry("fill color", "Fill Color"),
			Map.entry("tracer color", "Tracer Color"),
			Map.entry("chest color", "Chest Color"),
			Map.entry("ender chests", "Ender Chests"),
			Map.entry("shulker boxes", "Shulker Boxes"),
			Map.entry("enchanting tables", "Enchanting Tables"),
			Map.entry("trapped chest", "Trapped Chest"),
			Map.entry("ender chest", "Ender Chest"));

	private final String name;
	private final T defaultValue;
	private T value;
	private final T min;
	private final T max;

	/** Guards GUI visibility; never null, so {@link #isVisible()} needs no branch. */
	private Supplier<Boolean> visibility = () -> true;

	public Setting(String name, T value) {
		this(name, value, null, null);
	}

	public Setting(String name, T value, T min, T max) {
		this.name = name;
		this.value = value;
		this.defaultValue = value;
		this.min = min;
		this.max = max;
	}

	public String getName() {
		return name;
	}

	/** The label the GUI shows: an override if one exists, otherwise "AutoTotem" -> "Auto Totem". */
	public String getDisplayName() {
		if (name == null || name.isBlank()) {
			return "";
		}
		return DISPLAY_NAMES.getOrDefault(name.toLowerCase(Locale.ROOT), splitCamelCase(name));
	}

	public T getValue() {
		return value;
	}

	public T getDefaultValue() {
		return defaultValue;
	}

	public void setValue(T value) {
		if (Objects.equals(this.value, value)) {
			return;
		}
		this.value = value;
		ModuleManager.INSTANCE.onSettingChanged();
	}

	public T getMin() {
		return min;
	}

	public T getMax() {
		return max;
	}

	public boolean matchesName(String other) {
		return name.equalsIgnoreCase(other);
	}

	/** Hides this setting in the GUI while the supplier returns false. */
	public Setting<T> visibleWhen(Supplier<Boolean> predicate) {
		this.visibility = predicate == null ? () -> true : predicate;
		return this;
	}

	public boolean isVisible() {
		try {
			return visibility == null || visibility.get();
		} catch (Exception e) {
			// A predicate that reads a not-yet-initialised module must not hide the row.
			return true;
		}
	}

	/**
	 * Inserts spaces at camel-case and letter-to-digit boundaries, leaving names that
	 * already contain spaces untouched. "AutoCrystal" -> "Auto Crystal",
	 * "ESPRendering" -> "ESP Rendering", "Delay2" -> "Delay 2".
	 */
	public static String splitCamelCase(String name) {
		StringBuilder out = new StringBuilder(name.length() + 8);
		char previous = 0;
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			char next = i + 1 < name.length() ? name.charAt(i + 1) : 0;
			boolean camelBoundary = Character.isUpperCase(c) && Character.isLowerCase(previous);
			// the tail of an acronym run: the last capital belongs to the next word
			boolean acronymTail = Character.isUpperCase(c) && Character.isUpperCase(previous)
					&& Character.isLowerCase(next);
			boolean digitBoundary = Character.isDigit(c) && !Character.isDigit(previous) && previous != ' ';

			if (i > 0 && c != ' ' && (camelBoundary || acronymTail || digitBoundary)) {
				out.append(' ');
			}
			out.append(c);
			previous = c;
		}
		return out.toString();
	}
}
