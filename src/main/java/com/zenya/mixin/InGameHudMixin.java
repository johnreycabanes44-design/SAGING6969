package com.zenya.mixin;

import com.zenya.gui.BlockPickerScreen;
import com.zenya.gui.ClickGUI;
import com.zenya.gui.FriendsPickerScreen;
import com.zenya.gui.MobPickerScreen;
import com.zenya.gui.StoragePickerScreen;
import com.zenya.module.modules.client.Hud;
import com.zenya.module.modules.render.CustomCrosshair;
import com.zenya.module.modules.render.NoRender;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Locale;

/**
 * Suppresses the vanilla HUD elements NoRender hides, and draws the custom crosshair.
 *
 * <p>The crosshair is handled by three separate hooks because vanilla, the client GUI and
 * CustomCrosshair each have their own reason to hide it: the custom shape is drawn at TAIL
 * of the whole HUD so it sits above everything, while the vanilla one is cancelled at HEAD.
 * CustomCrosshair.getInstance() may still be null before the module list is built, so the
 * null check stays.
 */
@Mixin(Gui.class)
public abstract class InGameHudMixin {
	@Inject(method = "render", at = @At("TAIL"))
	private void zenya$renderCustomCrosshair(GuiGraphics graphics, DeltaTracker tickCounter, CallbackInfo info) {
		CustomCrosshair crosshair = CustomCrosshair.getInstance();

		if (crosshair != null && crosshair.isEnabled()) {
			crosshair.renderCrosshair(graphics);
		}
	}

	@Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
	private void zenya$cancelVanillaEffectOverlay(GuiGraphics graphics, DeltaTracker tickCounter, CallbackInfo info) {
		if (NoRender.hidePotionIcons() || Hud.hideVanillaPotionEffects()) {
			info.cancel();
		}
	}

	@Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
	private void zenya$cancelCrosshairInZenyaGui(GuiGraphics graphics, DeltaTracker tickCounter, CallbackInfo info) {
		Minecraft client = Minecraft.getInstance();

		if (client.screen instanceof ClickGUI
				|| client.screen instanceof BlockPickerScreen
				|| client.screen instanceof StoragePickerScreen
				|| client.screen instanceof MobPickerScreen
				|| client.screen instanceof FriendsPickerScreen) {
			info.cancel();
		}

		if (NoRender.hideCrosshair()) {
			info.cancel();
			return;
		}

		// Cancelled here so zenya$renderCustomCrosshair draws the replacement instead.
		if (CustomCrosshair.customCrosshairActive()) {
			info.cancel();
		}
	}

	@Inject(method = "renderBossOverlay", at = @At("HEAD"), cancellable = true)
	private void zenya$cancelBossBar(GuiGraphics graphics, DeltaTracker tickCounter, CallbackInfo info) {
		if (NoRender.hideBossBar()) {
			info.cancel();
		}
	}

	// ponytail: this is the only target given as a full descriptor, and it names the yarn
	// types (DrawContext/RenderTickCounter) rather than the mapping this build uses. Kept
	// verbatim - it is the shipped contract.
	@Inject(method = "Lnet/minecraft/client/gui/Gui;renderScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
			at = @At("HEAD"), cancellable = true)
	private void zenya$cancelScoreboard(GuiGraphics graphics, DeltaTracker tickCounter, CallbackInfo info) {
		if (NoRender.hideScoreboard()) {
			info.cancel();
		}
	}

	@Inject(method = "renderTitle", at = @At("HEAD"), cancellable = true)
	private void zenya$cancelTitle(GuiGraphics graphics, DeltaTracker tickCounter, CallbackInfo info) {
		if (NoRender.hideTitle()) {
			info.cancel();
		}
	}

	@Inject(method = "renderSelectedItemName", at = @At("HEAD"), cancellable = true)
	private void zenya$cancelHeldItemTooltip(GuiGraphics graphics, CallbackInfo info) {
		if (NoRender.hideHeldItemName()) {
			info.cancel();
		}
	}

	@Inject(method = "renderVignette", at = @At("HEAD"), cancellable = true)
	private void zenya$cancelVignette(GuiGraphics graphics, Entity entity, CallbackInfo info) {
		if (NoRender.hideVignette()) {
			info.cancel();
		}
	}

	@Inject(method = "renderSpyglassOverlay", at = @At("HEAD"), cancellable = true)
	private void zenya$cancelSpyglass(GuiGraphics graphics, float scale, CallbackInfo info) {
		if (NoRender.hideSpyglassOverlay()) {
			info.cancel();
		}
	}

	@Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true)
	private void zenya$cancelPortal(GuiGraphics graphics, float nauseaStrength, CallbackInfo info) {
		if (NoRender.hidePortalOverlay()) {
			info.cancel();
		}
	}

	@Inject(method = "renderConfusionOverlay", at = @At("HEAD"), cancellable = true)
	private void zenya$cancelNausea(GuiGraphics graphics, float nauseaStrength, CallbackInfo info) {
		if (NoRender.hideNausea()) {
			info.cancel();
		}
	}

	@Inject(method = "renderTextureOverlay", at = @At("HEAD"), cancellable = true)
	private void zenya$cancelEquipmentOverlays(GuiGraphics graphics, Identifier texture, float opacity, CallbackInfo info) {
		// Both overlays go through the same vanilla method, so the texture path is what tells them apart.
		String path = texture == null ? "" : texture.getPath().toLowerCase(Locale.ROOT);

		if (NoRender.hidePumpkinOverlay() && path.contains("pumpkin")) {
			info.cancel();
			return;
		}

		if (NoRender.hidePowderedSnowOverlay() && path.contains("powder_snow")) {
			info.cancel();
		}
	}
}
