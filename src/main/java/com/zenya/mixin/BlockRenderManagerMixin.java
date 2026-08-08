package com.zenya.mixin;

import com.zenya.module.modules.render.NoRender;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Swaps the block state NoRender wants hidden before the dispatcher looks at it.
 *
 * <p>Model lookup and batched rendering are both filtered so a substituted state cannot
 * end up drawn with the original's geometry. renderBatched is require = 0 because the
 * model swap alone already covers the chunk path on versions where it is inlined.
 */
@Mixin(BlockRenderDispatcher.class)
public class BlockRenderManagerMixin {
	@ModifyVariable(method = "getBlockModel", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private BlockState zenya$antileakSwapModel(BlockState original) {
		return NoRender.filterBlockState(original);
	}

	@ModifyVariable(method = "renderBatched", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
	private BlockState zenya$antileakSwapRender(BlockState original) {
		return NoRender.filterBlockState(original);
	}
}
