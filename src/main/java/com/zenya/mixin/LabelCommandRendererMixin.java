package com.zenya.mixin;

import com.zenya.utils.NametagRenderState;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Redirects the second nametag draw — the health/label line — so labels the client
 * marked as outlined get the 8x outline pass instead of vanilla's shadowed draw.
 *
 * <p>ordinal=1 is load-bearing: {@code render} calls {@code drawInBatch} twice and
 * only the second call is the label this client owns.
 */
@Mixin(NameTagFeatureRenderer.class)
public class LabelCommandRendererMixin {
	@Redirect(method = "render",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V",
					ordinal = 1))
	private void zenya$drawHealthLabelsWithOutline(Font font, Component text, float x, float y, int color,
			boolean shadow, Matrix4f matrix, MultiBufferSource buffers, Font.DisplayMode displayMode,
			int backgroundColor, int light) {
		if (!NametagRenderState.isOutlinedLabel(text)) {
			font.drawInBatch(text, x, y, color, shadow, matrix, buffers, displayMode, backgroundColor, light);
			return;
		}

		font.drawInBatch8xOutline(text.getVisualOrderText(), x, y, color, 0xFF000000, matrix, buffers, light);
	}
}
