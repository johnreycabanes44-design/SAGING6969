package com.zenya.setting;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A selection of entity types, backing the mob picker screen.
 *
 * <p>Only living categories are offered — projectiles, item frames and the rest
 * of the registry would bury the mobs a player actually wants to pick.
 */
public class MobsSetting extends Setting<Set<EntityType<?>>> {
	private final List<EntityType<?>> availableMobs = BuiltInRegistries.ENTITY_TYPE.stream()
			.filter(MobsSetting::isLivingMob)
			.sorted(Comparator.comparing(this::getDisplayName, String.CASE_INSENSITIVE_ORDER))
			.toList();

	private long version;

	public MobsSetting(String name, EntityType<?>... defaults) {
		super(name, createDefaultSet(defaults));
	}

	public static boolean isLivingMob(EntityType<?> type) {
		MobCategory category = type.getCategory();
		return category == MobCategory.MONSTER
				|| category == MobCategory.CREATURE
				|| category == MobCategory.AMBIENT
				|| category == MobCategory.AXOLOTLS
				|| category == MobCategory.UNDERGROUND_WATER_CREATURE
				|| category == MobCategory.WATER_CREATURE
				|| category == MobCategory.WATER_AMBIENT;
	}

	@Override
	public void setValue(Set<EntityType<?>> mobs) {
		LinkedHashSet<EntityType<?>> cleaned = new LinkedHashSet<>();
		if (mobs != null) {
			for (EntityType<?> type : mobs) {
				if (type == null) {
					continue;
				}
				cleaned.add(type);
			}
		}
		if (cleaned.equals(getValue())) {
			return;
		}
		super.setValue(cleaned);
		version++;
	}

	public boolean contains(EntityType<?> type) {
		return type != null && getValue().contains(type);
	}

	public void toggle(EntityType<?> type) {
		if (type == null) {
			return;
		}
		LinkedHashSet<EntityType<?>> selected = new LinkedHashSet<>(getValue());
		if (!selected.add(type)) {
			selected.remove(type);
		}
		setValue(selected);
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

	/** Bumped on every mutation so ESP renderers can cache per version. */
	public long getVersion() {
		return version;
	}

	public Set<EntityType<?>> getSelectedMobs() {
		return Collections.unmodifiableSet(getValue());
	}

	public List<EntityType<?>> getAvailableMobs() {
		return availableMobs;
	}

	public List<EntityType<?>> filter(String query) {
		String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
		if (needle.isEmpty()) {
			return availableMobs;
		}
		List<EntityType<?>> matches = new ArrayList<>();
		for (EntityType<?> type : availableMobs) {
			String label = getDisplayName(type).toLowerCase(Locale.ROOT);
			Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
			String key = id == null ? "" : id.toString().toLowerCase(Locale.ROOT);
			if (!label.contains(needle) && !key.contains(needle)) {
				continue;
			}
			matches.add(type);
		}
		return matches;
	}

	public String getDisplayName(EntityType<?> type) {
		try {
			return type.getDescription().getString();
		} catch (Exception e) {
			// Translating a name needs a loaded language file; fall back to the id.
			Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
			return id == null ? "Mob" : id.getPath();
		}
	}

	public String getSummary() {
		if (getValue().isEmpty()) {
			return "None";
		}
		String first = getDisplayName(getValue().iterator().next());
		int others = getValue().size() - 1;
		return others > 0 ? first + " +" + others : first;
	}

	public static Set<EntityType<?>> createDefaultSet(EntityType<?>... types) {
		LinkedHashSet<EntityType<?>> set = new LinkedHashSet<>();
		if (types != null) {
			Collections.addAll(set, types);
			set.remove(null);
		}
		return set;
	}
}
