package com.zenya.utils;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Marks the entity render states whose vanilla nametag the client has replaced,
 * so the vanilla label pass knows to skip them.
 *
 * <p>The set is backed by a {@link WeakHashMap}: render states are rebuilt every
 * frame and nothing ever tells us when one dies, so holding them strongly would
 * leak one entry per entity per frame.
 */
public class NametagRenderState {
	public static Set<EntityRenderState> OVERRIDDEN_LABELS = Collections.newSetFromMap(new WeakHashMap<>());

	public static void clear(EntityRenderState state) {
		OVERRIDDEN_LABELS.remove(state);
	}

	public static void mark(EntityRenderState state) {
		OVERRIDDEN_LABELS.add(state);
	}

	public static boolean hasEntry(EntityRenderState state) {
		return OVERRIDDEN_LABELS.contains(state);
	}

	/** Stub: no label is ever treated as outlined. Kept so callers still resolve. */
	public static boolean isOutlinedLabel(Component label) {
		return false;
	}

	/** One queued nametag: the world anchor plus the text and icons drawn at it. */
	public record Entry(Object entity, Vec3 labelPos, Component nameLabel, Component healthLabel,
			List<ItemEntry> items) {
	}

	/** One equipment icon beside a nametag. */
	public record ItemEntry(ItemStack stack) {
	}
}
