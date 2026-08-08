package com.zenya.mixin;

import com.zenya.utils.NameProtectUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Runs every string the font is about to lay out through NameProtect, so the
 * player's name is hidden wherever it is drawn rather than per call site.
 *
 * <p>The width overloads are hooked too: measuring the original text while drawing
 * the replacement would misalign anything that centres or wraps.
 * {@link NameProtectUtil#replace} is a passthrough while the module is off, so
 * there is no enabled check here.
 */
@Mixin(Font.class)
public class TextRendererMixin {
	@ModifyVariable(method = "prepareText(Ljava/lang/String;FFIZI)Lnet/minecraft/client/gui/Font$PreparedText;",
			at = @At("HEAD"), ordinal = 0, argsOnly = true)
	private String zenya$replacePreparedString(String text) {
		return NameProtectUtil.replace(text);
	}

	@ModifyVariable(method = "prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;",
			at = @At("HEAD"), ordinal = 0, argsOnly = true)
	private FormattedCharSequence zenya$replacePreparedOrderedText(FormattedCharSequence orderedText) {
		return NameProtectUtil.replace(orderedText);
	}

	@ModifyVariable(method = "drawInBatch8xOutline(Lnet/minecraft/util/FormattedCharSequence;FFIILorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At("HEAD"), ordinal = 0, argsOnly = true)
	private FormattedCharSequence zenya$replaceOutlinedOrderedText(FormattedCharSequence orderedText) {
		return NameProtectUtil.replace(orderedText);
	}

	@ModifyVariable(method = "width(Ljava/lang/String;)I", at = @At("HEAD"), ordinal = 0, argsOnly = true)
	private String zenya$replaceWidthString(String text) {
		return NameProtectUtil.replace(text);
	}

	@ModifyVariable(method = "width(Lnet/minecraft/network/chat/FormattedText;)I",
			at = @At("HEAD"), ordinal = 0, argsOnly = true)
	private FormattedText zenya$replaceWidthVisitable(FormattedText visitable) {
		return NameProtectUtil.replace(visitable);
	}

	@ModifyVariable(method = "width(Lnet/minecraft/util/FormattedCharSequence;)I",
			at = @At("HEAD"), ordinal = 0, argsOnly = true)
	private FormattedCharSequence zenya$replaceWidthOrderedText(FormattedCharSequence orderedText) {
		return NameProtectUtil.replace(orderedText);
	}
}
