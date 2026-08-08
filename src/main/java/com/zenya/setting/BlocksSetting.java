package com.zenya.setting;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A selection of blocks plus a colour per block, backing the block picker screen.
 *
 * <p>{@link #getVersion()} is bumped on every mutation so renderers that cache a
 * block list per frame can tell "unchanged" from "rebuilt" without comparing sets.
 */
public class BlocksSetting extends Setting<Set<Block>> {
	private final List<Block> availableBlocks;
	private final Map<Block, Color> blockColors = new LinkedHashMap<>();
	private long version;

	public BlocksSetting(String name, Block... defaults) {
		super(name, createDefaultSet(defaults));
		// Registry order is meaningless to a human; the picker lists blocks by label.
		this.availableBlocks = BuiltInRegistries.BLOCK.stream()
				.filter(block -> block != Blocks.AIR)
				.filter(block -> !new ItemStack(block).isEmpty())
				.sorted(Comparator.comparing(this::getDisplayName, String.CASE_INSENSITIVE_ORDER))
				.toList();
	}

	@Override
	public void setValue(Set<Block> blocks) {
		LinkedHashSet<Block> cleaned = new LinkedHashSet<>();
		if (blocks != null) {
			for (Block block : blocks) {
				if (block == null || block == Blocks.AIR) {
					continue;
				}
				cleaned.add(block);
			}
		}
		if (cleaned.equals(getValue())) {
			return;
		}
		super.setValue(cleaned);
		version++;
	}

	public boolean contains(Block block) {
		return block != null && getValue().contains(block);
	}

	public void toggle(Block block) {
		if (block == null || block == Blocks.AIR) {
			return;
		}
		LinkedHashSet<Block> selected = new LinkedHashSet<>(getValue());
		if (!selected.add(block)) {
			selected.remove(block);
		} else {
			blockColors.putIfAbsent(block, defaultColor(block));
		}
		setValue(selected);
	}

	public Color getColor(Block block) {
		if (block == null) {
			return new Color(239, 68, 68);
		}
		return blockColors.computeIfAbsent(block, BlocksSetting::defaultColor);
	}

	public void setColor(Block block, Color color) {
		if (block == null || color == null) {
			return;
		}
		blockColors.put(block, color);
		version++;
	}

	public Map<Block, Color> getColors() {
		return blockColors;
	}

	public void setColors(Map<Block, Color> colors) {
		if (colors == null) {
			return;
		}
		blockColors.clear();
		blockColors.putAll(colors);
		version++;
	}

	public void clear() {
		if (getValue().isEmpty()) {
			return;
		}
		setValue(Collections.emptySet());
	}

	public int size() {
		return getValue().size();
	}

	public long getVersion() {
		return version;
	}

	public Set<Block> getSelectedBlocks() {
		return Collections.unmodifiableSet(getValue());
	}

	public List<Block> getAvailableBlocks() {
		return availableBlocks;
	}

	/** Picker search: matches the label a player sees and the registry id they might type. */
	public List<Block> filter(String query) {
		String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
		if (needle.isEmpty()) {
			return availableBlocks;
		}
		List<Block> matches = new ArrayList<>();
		for (Block block : availableBlocks) {
			String label = getDisplayName(block).toLowerCase(Locale.ROOT);
			Identifier id = BuiltInRegistries.BLOCK.getKey(block);
			String key = id == null ? "" : id.toString().toLowerCase(Locale.ROOT);
			if (!label.contains(needle) && !key.contains(needle)) {
				continue;
			}
			matches.add(block);
		}
		return matches;
	}

	public String getDisplayName(Block block) {
		try {
			return block.getName().getString();
		} catch (Exception e) {
			// Translating a name needs a loaded language file; fall back to the id.
			Identifier id = BuiltInRegistries.BLOCK.getKey(block);
			return id == null ? "Block" : id.getPath();
		}
	}

	public String getSummary() {
		return getValue().isEmpty() ? "None" : "Blocks";
	}

	/**
	 * A sensible starting colour: ores and common materials get their real-world
	 * tint, everything else a stable pastel derived from the id's hash so two
	 * different blocks never come up identical between sessions.
	 */
	public static Color defaultColor(Block block) {
		Identifier id = BuiltInRegistries.BLOCK.getKey(block);
		String path = id == null ? "" : id.getPath();

		if (path.contains("diamond")) {
			return new Color(45, 212, 191);
		}
		if (path.contains("emerald")) {
			return new Color(52, 211, 153);
		}
		if (path.contains("gold")) {
			return new Color(251, 191, 36);
		}
		if (path.contains("iron")) {
			return new Color(203, 213, 225);
		}
		if (path.contains("redstone")) {
			return new Color(248, 113, 113);
		}
		if (path.contains("lapis")) {
			return new Color(96, 165, 250);
		}
		if (path.contains("coal")) {
			return new Color(71, 85, 105);
		}
		if (path.contains("copper")) {
			return new Color(249, 115, 22);
		}
		if (path.contains("obsidian")) {
			return new Color(124, 58, 237);
		}
		if (path.contains("water") || path.contains("ice")) {
			return new Color(56, 189, 248);
		}
		if (path.contains("leaves") || path.contains("sapling")
				|| path.contains("grass") || path.contains("moss")) {
			return new Color(74, 222, 128);
		}
		if (path.contains("log") || path.contains("wood") || path.contains("planks")) {
			return new Color(180, 83, 9);
		}

		int hash = path.hashCode();
		float hue = (float) ((hash & Integer.MAX_VALUE) % 360) / 360.0f;
		float saturation = 0.62f + (float) (hash >>> 8 & 255) / 255.0f * 0.25f;
		float brightness = 0.78f + (float) (hash >>> 16 & 255) / 255.0f * 0.18f;
		return Color.getHSBColor(hue, saturation, Math.min(0.96f, brightness));
	}

	public static Set<Block> createDefaultSet(Block... blocks) {
		LinkedHashSet<Block> set = new LinkedHashSet<>();
		if (blocks != null) {
			Collections.addAll(set, blocks);
			set.remove(null);
			set.remove(Blocks.AIR);
		}
		return set;
	}
}
