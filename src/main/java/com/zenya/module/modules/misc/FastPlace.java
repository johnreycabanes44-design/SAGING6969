package com.zenya.module.modules.misc;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;

import com.zenya.mixin.MinecraftClientAccessor;
import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;

/**
 * Holds the client's item-use cooldown down to "Delay" so held right-click repeats every tick.
 *
 * <p>Vanilla refills that field after every use, so it has to be pushed back down each tick
 * rather than once on enable. Food, respawn anchors, glowstone and bows are excluded because
 * their vanilla timing is what makes them usable at all -- with the cooldown gone they would
 * never finish. Both hands are inspected, since either one can be the item being used.
 */
public class FastPlace extends Module {
	public Setting<Boolean> onlyXP;
	public Setting<Boolean> allowBlocks;
	public Setting<Boolean> allowItems;
	public Setting<Float> useDelay;

	public FastPlace() {
		super("Fast Place", Category.MISC);
		this.onlyXP = new Setting<>("Only XP", false);
		this.allowBlocks = new Setting<>("Blocks", true);
		this.allowItems = new Setting<>("Items", true);
		this.useDelay = new Setting<>("Delay", 0.0f, 0.0f, 10.0f);
		this.setDescription("Removes the right-click placement cooldown so blocks and items like experience bottles can be used as fast as you click.");
		this.addSetting(this.onlyXP);
		this.addSetting(this.allowBlocks);
		this.addSetting(this.allowItems);
		this.addSetting(this.useDelay);
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.screen != null) {
			return;
		}

		if (!mc.options.keyUse.isDown()) {
			return;
		}

		ItemStack mainHand = mc.player.getMainHandItem();
		ItemStack offHand = mc.player.getOffhandItem();

		if (!this.shouldAffectCooldown(mainHand, offHand)) {
			return;
		}

		MinecraftClientAccessor accessor = (MinecraftClientAccessor) mc;
		int cooldown = Math.max(0, this.useDelay.getValue().intValue());

		if (accessor.zenya$getItemUseCooldown() != cooldown) {
			accessor.zenya$setItemUseCooldown(cooldown);
		}
	}

	/** Whether either hand holds something this module is allowed to speed up. */
	public boolean shouldAffectCooldown(ItemStack mainHand, ItemStack offHand) {
		boolean mainIsExperienceBottle = mainHand.is(Items.EXPERIENCE_BOTTLE);
		boolean offIsExperienceBottle = offHand.is(Items.EXPERIENCE_BOTTLE);

		if (this.onlyXP.getValue()) {
			return mainIsExperienceBottle || offIsExperienceBottle;
		}

		Item mainItem = mainHand.getItem();
		Item offItem = offHand.getItem();

		if (this.isFood(mainHand) || this.isFood(offHand)) {
			return false;
		}

		// Anchors and glowstone need the vanilla delay, or charging one explodes it in your face.
		if (mainHand.is(Items.RESPAWN_ANCHOR) || mainHand.is(Items.GLOWSTONE)
				|| offHand.is(Items.RESPAWN_ANCHOR) || offHand.is(Items.GLOWSTONE)) {
			return false;
		}

		if (mainItem instanceof ProjectileWeaponItem || offItem instanceof ProjectileWeaponItem) {
			return false;
		}

		if (mainItem instanceof BlockItem || offItem instanceof BlockItem) {
			return this.allowBlocks.getValue();
		}

		return this.allowItems.getValue();
	}

	public boolean isFood(ItemStack stack) {
		return stack.getComponents().has(DataComponents.FOOD);
	}
}
