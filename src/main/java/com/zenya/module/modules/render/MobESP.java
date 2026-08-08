package com.zenya.module.modules.render;

import net.minecraft.world.entity.EntityType;

import com.zenya.module.Category;
import com.zenya.module.Module;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

/**
 * Mob highlighting module; currently only the per-entity-type colour overrides.
 *
 * <p>The override map lives on the module rather than on the setting because
 * {@code MobPickerScreen} edits a copy and writes it back one type at a time. A null
 * colour means "no override" and is stored as an absent key, never as a null value.
 */
public class MobESP extends Module {
	public Map<EntityType<?>, Color> colorMap;

	public MobESP() {
		super("Mob ESP", Category.RENDER);
		this.colorMap = new HashMap<>();
	}

	public Map<EntityType<?>, Color> getColorMap() {
		return this.colorMap;
	}

	/** A null colour clears the override instead of storing a null value. */
	public void setCustomMobColor(EntityType<?> type, Color color) {
		if (color == null) {
			this.colorMap.remove(type);
		} else {
			this.colorMap.put(type, color);
		}
	}
}
