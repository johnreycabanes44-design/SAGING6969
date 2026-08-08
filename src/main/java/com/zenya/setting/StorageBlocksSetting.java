package com.zenya.setting;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The storage-ESP block list: a fixed set of interesting blocks, which of them
 * are switched on, and the colour each is drawn in.
 *
 * <p>Values are registry ids rather than {@code Block} instances so a config
 * survives a block being renamed out of the registry.
 */
public class StorageBlocksSetting extends Setting<Set<String>> {
	private final List<Entry> options;
	private final Map<String, Color> colors = new LinkedHashMap<>();

	/** Colour sub-settings handed to the GUI; cached so each row keeps its identity. */
	private final Map<String, Setting<Color>> colorSettings = new LinkedHashMap<>();

	public StorageBlocksSetting(String name, Entry... options) {
		super(name, new LinkedHashSet<>());
		this.options = List.of(options);
		for (Entry entry : options) {
			colors.put(entry.value(), entry.defaultColor());
		}
	}

	/** The stock list — every block the storage ESP knows how to highlight. */
	public static StorageBlocksSetting createDefault(String name) {
		return new StorageBlocksSetting(name,
				new Entry("minecraft:chest", "Chest", new ItemStack(Items.CHEST), new Color(156, 91, 0)),
				new Entry("minecraft:trapped_chest", "Trapped Chest", new ItemStack(Items.TRAPPED_CHEST), new Color(200, 91, 0)),
				new Entry("minecraft:ender_chest", "Ender Chest", new ItemStack(Items.ENDER_CHEST), new Color(117, 0, 255)),
				new Entry("minecraft:spawner", "Spawner", new ItemStack(Items.SPAWNER), new Color(138, 126, 166)),
				new Entry("minecraft:shulker_box", "Shulker Box", new ItemStack(Items.SHULKER_BOX), new Color(134, 0, 158)),
				new Entry("minecraft:furnace", "Furnace", new ItemStack(Items.FURNACE), new Color(125, 125, 125)),
				new Entry("minecraft:barrel", "Barrel", new ItemStack(Items.BARREL), new Color(255, 140, 140)),
				new Entry("minecraft:dispenser", "Dispenser", new ItemStack(Items.DISPENSER), new Color(100, 100, 100)),
				new Entry("minecraft:dropper", "Dropper", new ItemStack(Items.DROPPER), new Color(100, 100, 100)),
				new Entry("minecraft:hopper", "Hopper", new ItemStack(Items.HOPPER), new Color(144, 238, 144)),
				new Entry("minecraft:piston", "Piston", new ItemStack(Items.PISTON), new Color(50, 205, 50)),
				new Entry("minecraft:sticky_piston", "Sticky Piston", new ItemStack(Items.STICKY_PISTON), new Color(50, 205, 50)),
				new Entry("minecraft:crafter", "Crafter", new ItemStack(Items.CRAFTER), new Color(255, 165, 0)),
				new Entry("minecraft:smoker", "Smoker", new ItemStack(Items.SMOKER), new Color(100, 100, 100)),
				new Entry("minecraft:blast_furnace", "Blast Furnace", new ItemStack(Items.BLAST_FURNACE), new Color(80, 80, 80)),
				new Entry("minecraft:note_block", "Note Block", new ItemStack(Items.NOTE_BLOCK), new Color(139, 69, 19)),
				new Entry("minecraft:diamond_block", "Diamond Block", new ItemStack(Items.DIAMOND_BLOCK), new Color(85, 255, 255)),
				new Entry("minecraft:beacon", "Beacon", new ItemStack(Items.BEACON), new Color(110, 255, 255)),
				new Entry("minecraft:observer", "Observer", new ItemStack(Items.OBSERVER), new Color(100, 100, 100)),
				new Entry("minecraft:repeater", "Repeater", new ItemStack(Items.REPEATER), new Color(255, 0, 0)),
				new Entry("minecraft:redstone_wire", "Redstone Dust", new ItemStack(Items.REDSTONE), new Color(255, 0, 0)),
				new Entry("minecraft:redstone_block", "Redstone Block", new ItemStack(Items.REDSTONE_BLOCK), new Color(255, 0, 0)));
	}

	public List<Entry> getOptions() {
		return options;
	}

	public Entry findEntry(String value) {
		for (Entry entry : options) {
			if (entry.value().equalsIgnoreCase(value)) {
				return entry;
			}
		}
		return null;
	}

	public Set<String> getSelected() {
		return getValue();
	}

	public boolean isSelected(String value) {
		return getValue().contains(value);
	}

	public List<Entry> getSelectedEntries() {
		List<Entry> selected = new ArrayList<>();
		for (Entry entry : options) {
			if (isSelected(entry.value())) {
				selected.add(entry);
			}
		}
		return selected;
	}

	public void toggle(String value) {
		Entry entry = findEntry(value);
		if (entry == null) {
			return;
		}
		LinkedHashSet<String> selected = new LinkedHashSet<>(getValue());
		if (!selected.remove(entry.value())) {
			selected.add(entry.value());
		}
		setValue(selected);
	}

	public Color getColor(String value) {
		Color color = colors.get(value);
		if (color != null) {
			return color;
		}
		Entry entry = findEntry(value);
		return entry != null ? entry.defaultColor() : Color.WHITE;
	}

	public void setColor(String value, Color color) {
		if (findEntry(value) == null || color == null) {
			return;
		}
		colors.put(value, color);
		// Colours live outside the value set, so re-set it to raise a change event.
		setValue(new LinkedHashSet<>(getValue()));
	}

	/**
	 * The colour picker the GUI edits for one block. Writes land back on this
	 * setting, so the picker needs no knowledge of the block it belongs to.
	 */
	public Setting<Color> colorSettingFor(String value) {
		return colorSettings.computeIfAbsent(value, key -> new Setting<Color>(labelFor(key), getColor(key)) {
			@Override
			public Color getValue() {
				// Read through to the map: a config load or restoreColors() changes the
				// colour without going through this setting, and a cached copy would
				// leave the picker showing the old one.
				return StorageBlocksSetting.this.getColor(value);
			}

			@Override
			public void setValue(Color color) {
				super.setValue(color);
				StorageBlocksSetting.this.setColor(value, color);
			}
		});
	}

	public String labelFor(String value) {
		Entry entry = findEntry(value);
		return entry != null ? entry.label() : value;
	}

	public Map<String, Color> getColorsSnapshot() {
		return new LinkedHashMap<>(colors);
	}

	/** Ignores ids that are no longer in the option list, so old configs load cleanly. */
	public void restoreColors(Map<String, Color> saved) {
		if (saved == null) {
			return;
		}
		for (Map.Entry<String, Color> entry : saved.entrySet()) {
			if (findEntry(entry.getKey()) == null || entry.getValue() == null) {
				continue;
			}
			colors.put(entry.getKey(), entry.getValue());
		}
	}

	/** One selectable block: its registry id, GUI label, icon and stock colour. */
	public record Entry(String value, String label, ItemStack icon, Color defaultColor) {
	}
}
