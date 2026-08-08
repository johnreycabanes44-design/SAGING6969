package com.zenya.mixin;

import com.zenya.module.modules.misc.SwingSpeed;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Divides the local player's arm swing duration by the SwingSpeed multiplier.
 *
 * <p>The injection sits at HEAD, so the vanilla haste / mining-fatigue adjustments have not
 * run yet and are recomputed here before scaling - otherwise the multiplier would apply to
 * the raw item duration and the potion effects would be lost.
 */
@Mixin(LivingEntity.class)
public class LivingEntityMixin {
	@Inject(method = "getCurrentSwingDuration", at = @At("HEAD"), cancellable = true)
	private void onGetHandSwingDuration(CallbackInfoReturnable<Integer> info) {
		SwingSpeed module = SwingSpeed.instance;

		// instance is null until the module is constructed.
		if (module == null || !module.isEnabled()) {
			return;
		}

		Minecraft client = Minecraft.getInstance();

		// Every LivingEntity swings; only the local player's swing is ours to speed up.
		if (client.player == null || (Object) this != client.player) {
			return;
		}

		LivingEntity self = (LivingEntity) (Object) this;
		ItemStack held = self.getItemInHand(InteractionHand.MAIN_HAND);
		int duration = held.getSwingAnimation().duration();

		if (MobEffectUtil.hasDigSpeed(self)) {
			duration -= 1 + MobEffectUtil.getDigSpeedAmplification(self);
		} else if (self.hasEffect(MobEffects.MINING_FATIGUE)) {
			duration += (1 + self.getEffect(MobEffects.MINING_FATIGUE).getAmplifier()) * 2;
		}

		info.setReturnValue(Math.max(1, Math.round(duration / module.getSwingSpeed())));
	}
}
