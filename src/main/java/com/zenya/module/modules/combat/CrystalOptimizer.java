package com.zenya.module.modules.combat;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;
import com.zenya.utils.BlockUtils;
import com.zenya.utils.CrystalUtils;
import com.zenya.utils.InventoryUtils;
import com.zenya.utils.MouseSimulation;
import com.zenya.utils.WorldUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Places a crystal on the obsidian or bedrock already under the crosshair, but only while
 * an enemy is inside {@code range}.
 *
 * <p>Deliberately never picks its own spot — aiming stays the player's job, this only takes
 * over the swap and the click. The synthetic mouse click is optional because some servers
 * reject a use packet that has no button state behind it, while others flag the extra input.
 */
public class CrystalOptimizer extends Module {
	public Setting<Float> range;
	public Setting<Boolean> autoSwitch;
	public Setting<Boolean> swingHand;
	public Setting<Boolean> clickSimulation;

	public CrystalOptimizer() {
		super("Crystal Optimizer", Category.COMBAT);
		this.range = new Setting<>("Range", Float.valueOf(5.0f), Float.valueOf(1.0f), Float.valueOf(6.0f));
		this.autoSwitch = new Setting<>("Auto Switch", true);
		this.swingHand = new Setting<>("Swing Hand", true);
		this.clickSimulation = new Setting<>("Click Simulation", true);
		this.addSetting(this.range);
		this.addSetting(this.autoSwitch);
		this.addSetting(this.swingHand);
		this.addSetting(this.clickSimulation);
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.level == null) {
			return;
		}
		if (mc.screen != null) {
			return;
		}
		Player target = WorldUtils.findNearestPlayer(mc.player, this.range.getValue().floatValue(), true, true);
		if (target == null) {
			return;
		}
		if (mc.player.getMainHandItem().getItem() != Items.END_CRYSTAL) {
			if (!this.autoSwitch.getValue()) {
				return;
			}
			if (!InventoryUtils.switchToHotbar(Items.END_CRYSTAL)) {
				return;
			}
		}
		if (!(mc.hitResult instanceof BlockHitResult crosshairHit) || crosshairHit.getType() != HitResult.Type.BLOCK) {
			return;
		}
		BlockPos aimedPos = crosshairHit.getBlockPos();
		if (!BlockUtils.isBlockAt(aimedPos, Blocks.OBSIDIAN) && !BlockUtils.isBlockAt(aimedPos, Blocks.BEDROCK)) {
			return;
		}
		if (!CrystalUtils.isPlaceable(aimedPos)) {
			return;
		}
		if (this.clickSimulation.getValue()) {
			// 1 is MouseSimulation's "use" button, not a GLFW code.
			MouseSimulation.mouseClick(1);
		}
		WorldUtils.interactBlock(crosshairHit, this.swingHand.getValue());
	}
}
