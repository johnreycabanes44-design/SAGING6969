package com.zenya.module.modules.render;

import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.module.ModuleManager;
import com.zenya.setting.Setting;

/**
 * Third-person camera overrides: whether the camera clips into blocks, how far behind
 * the player it sits, and scroll-wheel zoom.
 *
 * <p>Camera mixins run before any module reference exists, so they reach the live
 * instance through {@link #get()} rather than holding a field.
 *
 * <p>{@link #distance} is the value the camera actually uses. {@link #lastSliderValue}
 * mirrors the setting so {@link #onTick} can tell a GUI slider edit from a scroll-zoom
 * edit: a scroll writes both, which is what stops the tick from immediately undoing it.
 */
public class CameraTweaks extends Module {
	public Setting<Boolean> clip;
	public Setting<Float> cameraDistance;
	public Setting<Boolean> scrollingEnabled;
	public Setting<Float> scrollSensitivity;
	public double distance;
	public float lastSliderValue;

	public CameraTweaks() {
		super("Camera Tweaks", Category.RENDER);
		this.clip = new Setting<>("Clip", true);
		this.cameraDistance = new Setting<>("Camera Distance", 4.0f, 0.0f, 20.0f);
		this.scrollingEnabled = new Setting<>("Scroll Zoom", true);
		this.scrollSensitivity = new Setting<>("Scroll Sensitivity", 1.0f, 0.01f, 5.0f);
		this.lastSliderValue = -1.0f;
		this.setDescription("Modify third-person camera clip, distance, scroll-zoom");
		this.addSetting(this.clip);
		this.addSetting(this.cameraDistance);
		this.addSetting(this.scrollingEnabled);
		this.addSetting(this.scrollSensitivity);
	}

	@Override
	public void onEnable() {
		this.distance = this.cameraDistance.getValue();
		this.lastSliderValue = this.cameraDistance.getValue();
	}

	/** Live instance for the camera mixins, or null before the module list is built. */
	public static CameraTweaks get() {
		Module module = ModuleManager.INSTANCE.getModuleByName("Camera Tweaks");
		return module instanceof CameraTweaks tweaks ? tweaks : null;
	}

	public boolean shouldClip() {
		return this.clip.getValue();
	}

	public double getDistance() {
		return this.distance;
	}

	@Override
	public void onTick() {
		float sliderValue = this.cameraDistance.getValue();
		if (sliderValue != this.lastSliderValue) {
			this.distance = sliderValue;
			this.lastSliderValue = sliderValue;
		}
	}

	public boolean isScrollingEnabled() {
		return this.scrollingEnabled.getValue();
	}

	/** Returns true when the scroll was consumed as a zoom, so the caller can cancel it. */
	public boolean onMouseScroll(double scrollDelta) {
		if (!this.isEnabled() || !this.scrollingEnabled.getValue()) {
			return false;
		}
		float sensitivity = this.scrollSensitivity.getValue();
		this.distance = Math.max(0.0, Math.min(20.0, this.distance - scrollDelta * (double) sensitivity * 0.1));
		this.cameraDistance.setValue((float) this.distance);
		this.lastSliderValue = (float) this.distance;
		return true;
	}
}
