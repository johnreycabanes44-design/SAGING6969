package com.zenya.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zenya.module.modules.render.NoRender;
import com.zenya.utils.NameProtectUtil;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Nametag control on the generic entity renderer: NameProtect rewrites the text,
 * NoRender suppresses the vanilla label pass entirely.
 *
 * <p>{@code submitNameTag} is the base implementation, so cancelling here covers every
 * entity type whose renderer does not override it - players go through
 * {@link PlayerEntityRendererMixin} instead.
 */
@Mixin(EntityRenderer.class)
public class EntityRendererMixin {
	// ponytail: empty inject, kept because the shipped client declared it - it exists only
	// to reserve the TAIL of the extract pass, no observable behaviour.
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void zenya$updateNametagState(Entity entity, EntityRenderState state, float tickDelta, CallbackInfo info) {
	}

	@Inject(method = "submitNameTag", at = @At("HEAD"), cancellable = true)
	private void zenya$renderCustomNametag(EntityRenderState state, PoseStack matrices, SubmitNodeCollector queue,
			CameraRenderState cameraRenderState, CallbackInfo info) {
		if (NoRender.hideNametags()) {
			info.cancel();
		}
	}

	// EntityRenderer<T extends Entity, S> erases T to Entity, so the handler must
	// take Entity — Object fails descriptor validation at apply time.
	@Inject(method = "getNameTag", at = @At("RETURN"), cancellable = true)
	private void zenya$protectNameTag(Entity entity, CallbackInfoReturnable<Component> info) {
		Component original = info.getReturnValue();

		if (original == null) {
			return;
		}

		String plain = original.getString();
		// replace() checks the module itself and returns the input untouched when off.
		String replaced = NameProtectUtil.replace(plain);

		if (!replaced.equals(plain)) {
			info.setReturnValue(Component.literal(replaced));
		}
	}
}
