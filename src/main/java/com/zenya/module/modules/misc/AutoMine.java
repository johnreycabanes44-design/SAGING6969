package com.zenya.module.modules.misc;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;

/**
 * Keeps block-breaking held down on whatever the crosshair is on, optionally pinning the
 * view to a fixed yaw/pitch so the target cannot drift.
 *
 * <p>Every path that is not "a solid block is targeted" calls {@code stopDestroyBlock}, so
 * the server is never left holding a half-broken block when a screen opens, an item starts
 * being used, or the block finishes and turns to air.
 */
public class AutoMine extends Module {
	public Setting<Boolean> lockView;
	public Setting<Float> pitch;
	public Setting<Float> yaw;

	public AutoMine() {
		super("Auto Mine", Category.MISC);
		this.lockView = new Setting<>("Lock View", true);
		this.pitch = new Setting<>("Pitch", 0.0f, -180.0f, 180.0f);
		this.yaw = new Setting<>("Yaw", 0.0f, -180.0f, 180.0f);
		this.setDescription("Automatically mines the block you are looking at, with optional fixed yaw and pitch.");
		this.addSetting(this.lockView);
		this.addSetting(this.pitch);
		this.addSetting(this.yaw);
	}

	@Override
	public void onDisable() {
		if (mc.gameMode != null) {
			mc.gameMode.stopDestroyBlock();
		}
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.level == null || mc.gameMode == null) {
			return;
		}

		if (mc.screen != null) {
			mc.gameMode.stopDestroyBlock();
			return;
		}

		if (this.lockView.getValue()) {
			mc.player.setYRot(this.yaw.getValue());
			mc.player.setXRot(this.pitch.getValue());
		}

		if (mc.player.isUsingItem()
				|| mc.hitResult == null
				|| mc.hitResult.getType() != HitResult.Type.BLOCK
				|| !(mc.hitResult instanceof BlockHitResult blockHit)) {
			mc.gameMode.stopDestroyBlock();
			return;
		}

		// The block finished breaking, or someone else broke it first.
		if (mc.level.getBlockState(blockHit.getBlockPos()).isAir()) {
			mc.gameMode.stopDestroyBlock();
			return;
		}

		if (mc.gameMode.continueDestroyBlock(blockHit.getBlockPos(), blockHit.getDirection())) {
			mc.player.swing(InteractionHand.MAIN_HAND);
		}
	}
}
