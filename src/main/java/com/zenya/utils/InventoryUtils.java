package com.zenya.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

/**
 * Slot lookups and hotbar switching for the local player's inventory.
 *
 * <p>All indices are {@link Inventory} indices, not container-screen slot ids:
 * 0-8 is the hotbar and 9-35 the main inventory. That split is why the "hotbar"
 * scans stop at 9 while the "inventory" scans start there — mixing the two is
 * how modules end up switching to the wrong item. Finders return -1 on no match.
 */
public class InventoryUtils {
	public static Minecraft mc = Minecraft.getInstance();

	public static void setSelectedSlot(int slot) {
		mc.player.getInventory().setSelectedSlot(slot);
	}

	/** Selects the first hotbar slot whose item matches, or leaves the selection alone. */
	public static boolean switchToHotbar(Predicate<Item> filter) {
		Inventory inventory = mc.player.getInventory();

		for (int slot = 0; slot < 9; ++slot) {
			ItemStack stack = inventory.getItem(slot);

			if (!filter.test(stack.getItem())) {
				continue;
			}

			inventory.setSelectedSlot(slot);
			return true;
		}

		return false;
	}

	public static boolean switchToHotbar(Item item) {
		return switchToHotbar(candidate -> candidate == item);
	}

	public static boolean hasItemInHotbar(Predicate<Item> filter) {
		Inventory inventory = mc.player.getInventory();

		for (int slot = 0; slot < 9; ++slot) {
			ItemStack stack = inventory.getItem(slot);

			if (!filter.test(stack.getItem())) {
				continue;
			}

			return true;
		}

		return false;
	}

	/** Total stack size over the whole inventory, hotbar included. */
	public static int countItem(Predicate<Item> filter) {
		Inventory inventory = mc.player.getInventory();
		int total = 0;

		for (int slot = 0; slot < 36; ++slot) {
			ItemStack stack = inventory.getItem(slot);

			if (!filter.test(stack.getItem())) {
				continue;
			}

			total += stack.getCount();
		}

		return total;
	}

	/** Total stack size over the main inventory only, skipping the hotbar. */
	public static int countItemInInventory(Predicate<Item> filter) {
		Inventory inventory = mc.player.getInventory();
		int total = 0;

		for (int slot = 9; slot < 36; ++slot) {
			ItemStack stack = inventory.getItem(slot);

			if (!filter.test(stack.getItem())) {
				continue;
			}

			total += stack.getCount();
		}

		return total;
	}

	public static int findSwordSlot() {
		Inventory inventory = mc.player.getInventory();

		for (int slot = 0; slot < 9; ++slot) {
			if (!inventory.getItem(slot).is(ItemTags.SWORDS)) {
				continue;
			}

			return slot;
		}

		return -1;
	}

	public static boolean switchToSword() {
		int slot = findSwordSlot();

		if (slot != -1) {
			setSelectedSlot(slot);
			return true;
		}

		return false;
	}

	/**
	 * Hotbar slot holding a potion whose contents mention the given effect at the
	 * given duration and amplifier. The match is a substring test on the rendered
	 * {@link MobEffectInstance}, so duration and amplifier must line up exactly.
	 */
	public static int findPotionSlot(MobEffect effect, int duration, int amplifier) {
		Inventory inventory = mc.player.getInventory();
		MobEffectInstance wanted = new MobEffectInstance(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect), duration, amplifier);

		for (int slot = 0; slot < 9; ++slot) {
			ItemStack stack = inventory.getItem(slot);

			if (!(stack.getItem() instanceof PotionItem)
					|| !stack.get(DataComponents.POTION_CONTENTS).toString().contains(wanted.toString())) {
				continue;
			}

			return slot;
		}

		return -1;
	}

	public static boolean hasPotion(MobEffect effect, int duration, int amplifier, ItemStack stack) {
		MobEffectInstance wanted = new MobEffectInstance(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect), duration, amplifier);
		return stack.getItem() instanceof PotionItem
				&& stack.get(DataComponents.POTION_CONTENTS).toString().contains(wanted.toString());
	}

	public static int findFirstTotemSlot() {
		Inventory inventory = mc.player.getInventory();

		for (int slot = 9; slot < 36; ++slot) {
			if (inventory.getItem(slot).getItem() != Items.TOTEM_OF_UNDYING) {
				continue;
			}

			return slot;
		}

		return -1;
	}

	public static boolean switchToAxe() {
		int slot = findAxeSlot();

		if (slot != -1) {
			mc.player.getInventory().setSelectedSlot(slot);
			return true;
		}

		return false;
	}

	/** Random totem slot, so repeated re-totems do not always pull from the same place. */
	public static int findRandomTotemSlot() {
		Inventory inventory = mc.player.getInventory();
		Random random = new Random();
		ArrayList<Integer> slots = new ArrayList<>();

		for (int slot = 9; slot < 36; ++slot) {
			if (inventory.getItem(slot).getItem() != Items.TOTEM_OF_UNDYING) {
				continue;
			}

			slots.add(slot);
		}

		if (!slots.isEmpty()) {
			return slots.get(random.nextInt(slots.size()));
		}

		return -1;
	}

	/**
	 * Scans 27 slots starting at a random main-inventory slot for a potion whose
	 * contents mention {@code name}.
	 */
	// ponytail: gives up with -1 at the first potion that does not match instead of
	// continuing the scan, and `slot >= 36` can never hold after the `% 36`. Behaviour kept.
	public static int findPotionByName(String name) {
		Inventory inventory = mc.player.getInventory();
		Random random = new Random();
		int start = random.nextInt(27) + 9;

		for (int offset = 0; offset < 27; ++offset) {
			int slot = (start + offset) % 36;
			ItemStack stack = inventory.getItem(slot);

			if (!(stack.getItem() instanceof PotionItem) || slot >= 36) {
				continue;
			}

			if (!stack.get(DataComponents.POTION_CONTENTS).toString().contains(name)) {
				return -1;
			}

			return slot;
		}

		return -1;
	}

	/** Same match as {@link #findPotionSlot} but over the main inventory. */
	public static int findPotionEffect(MobEffect effect, int duration, int amplifier) {
		Inventory inventory = mc.player.getInventory();
		MobEffectInstance wanted = new MobEffectInstance(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect), duration, amplifier);

		// ponytail: stops at 34, not 36 — the last two inventory slots are never checked.
		for (int slot = 9; slot < 34; ++slot) {
			if (!(inventory.getItem(slot).getItem() instanceof PotionItem)
					|| !inventory.getItem(slot).get(DataComponents.POTION_CONTENTS).toString().contains(wanted.toString())) {
				continue;
			}

			return slot;
		}

		return -1;
	}

	/** Empty hotbar slots only, in ascending order. */
	public static List<Integer> findEmptySlots() {
		Inventory inventory = mc.player.getInventory();
		ArrayList<Integer> empty = new ArrayList<>();

		for (int slot = 0; slot < 9; ++slot) {
			if (!inventory.getItem(slot).isEmpty()) {
				continue;
			}

			empty.add(slot);
		}

		return empty;
	}

	public static int findAxeSlot() {
		Inventory inventory = mc.player.getInventory();

		for (int slot = 0; slot < 9; ++slot) {
			if (!(inventory.getItem(slot).getItem() instanceof AxeItem)) {
				continue;
			}

			return slot;
		}

		return -1;
	}

	public static int countItem(Item item) {
		return countItem(candidate -> candidate == item);
	}
}
