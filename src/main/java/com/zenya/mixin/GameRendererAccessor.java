package com.zenya.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the renderer's {@link FogRenderer} so ESP passes can swap in the
 * no-fog buffer before flushing — world geometry drawn through it would
 * otherwise fade with distance like terrain does.
 */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
	@Accessor("fogRenderer")
	FogRenderer zenya$getFogRenderer();
}
